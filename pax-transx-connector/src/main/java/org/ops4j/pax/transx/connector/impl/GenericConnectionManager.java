/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ops4j.pax.transx.connector.impl;

import com.zaxxer.hikari.util.ConcurrentBag;
import com.zaxxer.hikari.util.UtilityElf;
import org.ops4j.pax.transx.connector.SubjectSource;
import org.ops4j.pax.transx.tm.NamedResource;
import org.ops4j.pax.transx.tm.Transaction;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.resource.spi.ValidatingManagedConnectionFactory;
import javax.security.auth.Subject;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.zaxxer.hikari.util.ClockSource.currentTime;
import static com.zaxxer.hikari.util.ClockSource.elapsedDisplayString;
import static com.zaxxer.hikari.util.ClockSource.elapsedMillis;
import static com.zaxxer.hikari.util.ClockSource.plusMillis;
import static com.zaxxer.hikari.util.ConcurrentBag.IConcurrentBagEntry.STATE_IN_USE;
import static com.zaxxer.hikari.util.ConcurrentBag.IConcurrentBagEntry.STATE_NOT_IN_USE;
import static com.zaxxer.hikari.util.UtilityElf.createThreadPoolExecutor;
import static com.zaxxer.hikari.util.UtilityElf.quietlySleep;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class GenericConnectionManager implements ConnectionManager, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(GenericConnectionManager.class.getName());

    private static final AtomicIntegerFieldUpdater<ManagedConnectionInfo> STATE_UPDATER
            = AtomicIntegerFieldUpdater.newUpdater(ManagedConnectionInfo.class, "state");

    private static final Comparator<ManagedConnectionInfo> LASTACCESS_REVERSE_COMPARABLE =
            (entryOne, entryTwo) -> Long.compare(entryTwo.lastAccessed, entryOne.lastAccessed);


    private final TransactionManager transactionManager;
    private final SubjectSource subjectSource;
    private final ClassLoader classLoader;
    private final ManagedConnectionFactory managedConnectionFactory;
    private final String name;
    private final TransactionSupportLevel transactionSupportLevel;

    private volatile boolean destroyed = false;

    private String poolName;
    private int maxPoolSize;
    private int minIdle;
    private long aliveBypassWindow;
    private long houseKeepingPeriod;
    private long connectionTimeout;
    private long idleTimeout;
    private long maxLifetime;

    private final ThreadPoolExecutor addConnectionExecutor;
    private final ThreadPoolExecutor closeConnectionExecutor;
    private ScheduledExecutorService houseKeepingExecutorService;

    private ScheduledFuture<?> houseKeeperTask;

    private final ConcurrentMap<Transaction, ManagedConnectionInfo> infos = new ConcurrentHashMap<>();
    private final ConcurrentMap<SubjectCRIKey, Pool> pools = new ConcurrentHashMap<>();

    public GenericConnectionManager(
            TransactionManager transactionManager,
            TransactionSupportLevel transactionSupportLevel,
            SubjectSource subjectSource,
            ClassLoader classLoader,
            ManagedConnectionFactory managedConnectionFactory,
            String name,
            String poolName,
            int minIdle,
            int maxPoolSize,
            long connectionTimeout,
            long idleTimeout,
            long maxLifetime,
            long aliveBypassWindow,
            long houseKeepingPeriod) {

        this.transactionManager = transactionManager;
        this.transactionSupportLevel = transactionSupportLevel;
        this.subjectSource = subjectSource;
        this.classLoader = classLoader;
        this.managedConnectionFactory = managedConnectionFactory;
        this.name = name;
        this.poolName = poolName;
        this.minIdle = minIdle;
        this.maxPoolSize = maxPoolSize;
        this.connectionTimeout = connectionTimeout;
        this.idleTimeout = idleTimeout;
        this.maxLifetime = maxLifetime;
        this.aliveBypassWindow = aliveBypassWindow;
        this.houseKeepingPeriod = houseKeepingPeriod;

        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new UtilityElf.DefaultThreadFactory(poolName + " housekeeper", true), new ThreadPoolExecutor.DiscardPolicy());
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
        this.houseKeepingExecutorService = executor;

        this.addConnectionExecutor = createThreadPoolExecutor(this.maxPoolSize, poolName + " connection adder", null, new ThreadPoolExecutor.DiscardPolicy());
        this.closeConnectionExecutor = createThreadPoolExecutor(this.maxPoolSize, poolName + " connection closer", null, new ThreadPoolExecutor.CallerRunsPolicy());

        this.houseKeeperTask = this.houseKeepingExecutorService.scheduleWithFixedDelay(this::houseKeep, 100L, this.houseKeepingPeriod, MILLISECONDS);

        if (transactionManager != null && name != null) {
            transactionManager.registerResource(new RecoverableResourceFactoryImpl(managedConnectionFactory, name));
        }
    }

    private void houseKeep() {
        pools.values().forEach(Pool::houseKeep);
    }

    /**
     * in: jms != null, is a deployed jms
     * out: useable connection object.
     */
    public Object allocateConnection(ManagedConnectionFactory managedConnectionFactory,
                                     ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        assert managedConnectionFactory == this.managedConnectionFactory;
        Subject subject = subjectSource != null ? subjectSource.getSubject() : null;
        return allocateConnection(subject, connectionRequestInfo);
    }

    private Object allocateConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        ManagedConnectionInfo mci = getMci(subject, connectionRequestInfo);
        return mci.getManagedConnection().getConnection(subject, connectionRequestInfo);
    }

    private ManagedConnectionInfo getMci(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        ClassLoader prevClassLoader = null;
        if (classLoader != null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != classLoader) {
                prevClassLoader = cl;
                Thread.currentThread().setContextClassLoader(classLoader);
            }
        }
        try {
            Transaction transaction = transactionSupportLevel != TransactionSupportLevel.NoTransaction
                                        && transactionManager != null ? transactionManager.getTransaction() : null;
            if (transaction != null && transaction.isActive()) {
                ManagedConnectionInfo existing = infos.get(transaction);
                if (existing != null) {
                    return existing;
                }
                ManagedConnectionInfo mci = getMciFromPool(subject, connectionRequestInfo);
                infos.put(transaction, mci);
                transaction.synchronization(null, status -> {
                    infos.remove(transaction);
                    mci.requite();
                });
                mci.enlist(transaction);
                return mci;
            } else {
                return getMciFromPool(subject, connectionRequestInfo);
            }
        } finally {
            if (prevClassLoader != null) {
                Thread.currentThread().setContextClassLoader(prevClassLoader);
            }
        }
    }

    private ManagedConnectionInfo getMciFromPool(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        SubjectCRIKey key = new SubjectCRIKey(subject, connectionRequestInfo);
        Pool pool = pools.computeIfAbsent(key, Pool::new);
        return getMciFromPool(pool, connectionTimeout);
    }

    private ManagedConnectionInfo getMciFromPool(Pool pool, long connectionTimeout) throws ResourceException {
        final long startTime = currentTime();

        long timeout = connectionTimeout;
        ManagedConnectionInfo mci = null;
        try {
            do {
                mci = pool.borrow(timeout, MILLISECONDS);
                if (mci == null) {
                    break; // We timed out... break and throw exception
                }

                final long now = currentTime();
                if (mci.isMarkedEvicted() || (elapsedMillis(mci.lastAccessed, now) > aliveBypassWindow && !isValid(mci))) {
                    pool.closeConnection(mci, "(connection is evicted or dead)"); // Throw away the dead connection (passed max age or failed alive test)
                    timeout = connectionTimeout - elapsedMillis(startTime);
                }
                else {
                    mci.lastBorrowed = now;
                    return mci;
                }
            } while (timeout > 0L);
        }
        catch (InterruptedException e) {
            if (mci != null) {
                mci.lastAccessed = startTime;
                pool.requite(mci);
            }
            Thread.currentThread().interrupt();
            throw new ResourceException(poolName + " - Interrupted during connection acquisition", e);
        }

        throw new ResourceException(poolName + " - Connection is not available, request timed out after " + elapsedMillis(startTime) + "ms.");
    }

    private boolean isValid(ManagedConnectionInfo mci) {
        if (managedConnectionFactory instanceof ValidatingManagedConnectionFactory) {
            try {
                Set s = ((ValidatingManagedConnectionFactory) managedConnectionFactory)
                        .getInvalidConnections(Collections.singleton(mci.getManagedConnection()));
                if (s != null && s.contains(mci.getManagedConnection())) {
                    return false;
                }
            } catch (ResourceException e) {
                // Ignore
            }
        } else {
            LOG.warning("Connection validation configured, but the ManagedConnectionFactory does not implement the ValidatingManagedConnectionFactory interface");
        }
        return true;
    }

    public void close() throws Exception {
        destroyed = true;
        if (houseKeeperTask != null) {
            houseKeeperTask.cancel(false);
            houseKeeperTask = null;
        }

        pools.values().forEach(Pool::softEvictConnections);

        addConnectionExecutor.shutdown();
        addConnectionExecutor.awaitTermination(5L, SECONDS);

        houseKeepingExecutorService.shutdownNow();

        pools.values().forEach(Pool::close);

//        final ExecutorService assassinExecutor = new ThreadPoolExecutor(1, 1, 5, SECONDS, new LinkedBlockingQueue<>(maxPoolSize * pools.size()),
//                new UtilityElf.DefaultThreadFactory(poolName + " connection assassinator", true),
//                new ThreadPoolExecutor.CallerRunsPolicy());
//        try {
//            final long start = currentTime();
//            do {
//                abortActiveConnections(assassinExecutor);
//                softEvictConnections();
//            } while (getTotalConnections() > 0 && elapsedMillis(start) < SECONDS.toMillis(5));
//        }
//        finally {
//            assassinExecutor.shutdown();
//            assassinExecutor.awaitTermination(5L, SECONDS);
//        }

        closeConnectionExecutor.shutdown();
        closeConnectionExecutor.awaitTermination(5L, SECONDS);
    }

    private void quietlyCloseConnection(final ManagedConnectionInfo connection, final String closureReason) {
        LOG.fine(() -> poolName + " - Closing connection " + connection + ": " + closureReason);
        try {
            connection.managedConnection.destroy();
        } catch (ResourceException e) {
            e.printStackTrace();
        }
    }

    final class Pool {

        private final SubjectCRIKey key;
        private final ConcurrentBag<ManagedConnectionInfo> bag;
        private volatile long previous = plusMillis(currentTime(), -houseKeepingPeriod);

        Pool(SubjectCRIKey key) {
            this.key = key;
            this.bag = new ConcurrentBag<>(this::addNewConnection);
        }

        private Future<Boolean> addNewConnection(int waiting) {
            return addConnectionExecutor.submit(() -> createConnection(null));
        }

        /**
         * Fill pool up from current idle connections (as they are perceived at the point of execution) to minIdle connections.
         */
        void fillPool() {
            final int connectionsToAdd = Math.min(maxPoolSize - bag.size(), minIdle - bag.getCount(STATE_NOT_IN_USE));
            for (int i = 0; i < connectionsToAdd; i++) {
                String afterPrefix = i < connectionsToAdd - 1 ? null : "After adding ";
                addConnectionExecutor.submit(() -> createConnection(afterPrefix));
            }
        }

        boolean createConnection(String afterPrefix) {
            long sleepBackoff = 250L;
            while (!destroyed && shouldCreateAnotherConnection()) {
                final ManagedConnectionInfo mci = tryCreateManagedConnection();
                if (mci != null) {
                    bag.add(mci);
                    LOG.fine(poolName + " - Added connection " + mci.getManagedConnection());
                    if (afterPrefix != null) {
                        logPoolState(afterPrefix);
                    }
                    return true;
                }
                // failed to get connection from db, sleep and retry
                quietlySleep(sleepBackoff);
                sleepBackoff = Math.min(SECONDS.toMillis(10), Math.min(connectionTimeout, (long) (sleepBackoff * 1.5)));
            }
            // Pool is suspended or shutdown or at max size
            return false;
        }

        boolean shouldCreateAnotherConnection() {
            // only create connections if we need another idle connection or have threads still waiting
            // for a new connection, otherwise bail
            return bag.size() < maxPoolSize &&
                    (bag.getWaitingThreadCount() > 0 || bag.getCount(STATE_NOT_IN_USE) < minIdle);
        }

        /**
         * Log the current pool state at debug level.
         *
         * @param prefix an optional prefix to prepend the log message
         */
        void logPoolState(String prefix) {
            LOG.log(Level.FINE, () -> poolName + " - " + (prefix != null ? prefix : "") + "stats (" +
                            "total=" + bag.size() + ", " +
                            "active=" + bag.getCount(STATE_IN_USE) + ", " +
                            "idle=" + bag.getCount(STATE_NOT_IN_USE) + ", " +
                            "waiting=" + bag.getWaitingThreadCount() + ")");
        }

        /**
         * The house keeping task to retire and maintain minimum idle connections.
         */
        void houseKeep() {
            try {
                // refresh timeouts in case they changed via MBean
//                connectionTimeout = config.getConnectionTimeout();
//                validationTimeout = config.getValidationTimeout();
//                leakTask.updateLeakDetectionThreshold(config.getLeakDetectionThreshold());

                final long idleTimeout = GenericConnectionManager.this.idleTimeout;
                final long now = currentTime();

                // Detect retrograde time, allowing +128ms as per NTP spec.
                if (plusMillis(now, 128) < plusMillis(previous, houseKeepingPeriod)) {
                    LOG.warning(() -> poolName + " - Retrograde clock change detected " +
                                    "(housekeeper delta=" + elapsedDisplayString(previous, now) + "), " +
                                    "soft-evicting connections from pool.");
                    previous = now;
                    softEvictConnections();
                    fillPool();
                    return;
                }
                else if (now > plusMillis(previous, (3 * houseKeepingPeriod) / 2)) {
                    // No point evicting for forward clock motion, this merely accelerates connection retirement anyway
                    LOG.log(Level.WARNING, () -> poolName + " - Thread starvation or clock leap detected (housekeeper delta=" + elapsedDisplayString(previous, now) + ").");
                }

                previous = now;

                String afterPrefix = "Pool ";
                if (idleTimeout > 0L && minIdle < maxPoolSize) {
                    logPoolState("Before cleanup ");
                    afterPrefix = "After cleanup  ";
                    bag
                            .values(STATE_NOT_IN_USE)
                            .stream()
                            .sorted(LASTACCESS_REVERSE_COMPARABLE)
                            .skip(minIdle)
                            .filter(mci -> elapsedMillis(mci.lastAccessed, now) > idleTimeout)
                            .filter(bag::reserve)
                            .forEachOrdered(mci -> closeConnection(mci, "(connection has passed idleTimeout)"));
                }
                logPoolState(afterPrefix);
                fillPool(); // Try to maintain minimum connections
            }
            catch (Exception e) {
                LOG.log(Level.SEVERE, "Unexpected exception in housekeeping task", e);
            }
        }

        void softEvictConnections() {
            bag.values().forEach(mci -> softEvictConnection(mci, "(connection evicted)", false /* not owner */));
        }

        void softEvictConnection(final ManagedConnectionInfo mci, final String reason, final boolean owner) {
            mci.markEvicted();
            if (owner || bag.reserve(mci)) {
                closeConnection(mci, reason);
            }
        }

        /**
         * Permanently close the real (underlying) connection (eat any exception).
         *
         * @param mci the connection to close
         * @param closureReason reason to close
         */
        void closeConnection(final ManagedConnectionInfo mci, final String closureReason) {
            if (bag.remove(mci)) {
                closeConnectionExecutor.execute(() -> {
                    quietlyCloseConnection(mci, closureReason);
                    if (!destroyed) {
                        fillPool();
                    }
                });
            }
        }

        void requite(ManagedConnectionInfo mci) {
            bag.requite(mci);
        }

        ManagedConnectionInfo borrow(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return bag.borrow(timeout, timeUnit);
        }

        ManagedConnectionInfo tryCreateManagedConnection() {
            try {
                final ManagedConnectionInfo mci = doCreateManagedConnection();

                final long maxLifetime = GenericConnectionManager.this.maxLifetime;
                if (maxLifetime > 0) {
                    // variance up to 2.5% of the maxlifetime
                    final long variance = maxLifetime > 10_000 ? ThreadLocalRandom.current().nextLong(maxLifetime / 40) : 0;
                    final long lifetime = maxLifetime - variance;
                    mci.setFutureEol(houseKeepingExecutorService.schedule(
                            () -> softEvictConnection(mci, "(connection has passed maxLifetime)", false /* not owner */),
                            lifetime, MILLISECONDS));
                }
                return mci;
            } catch (Exception e) {
                if (!destroyed) {
                    LOG.log(Level.FINE, poolName + " - Cannot acquire connection from data source", e);
                }
                return null;
            }
        }

        ManagedConnectionInfo doCreateManagedConnection() throws ResourceException {
            ManagedConnection mc = managedConnectionFactory.createManagedConnection(key.getSubject(), key.getCri());
            NamedResource xares = null;
            switch (transactionSupportLevel) {
                case LocalTransaction:
                    xares = new LocalXAResource(mc.getLocalTransaction(), name);
                    break;
                case XATransaction:
                    xares = new WrapperNamedXAResource(mc.getXAResource(), name);
                    break;
                default:
                    break;
            }
            return new ManagedConnectionInfo(this, mc, xares);
        }

        void close() {
            logPoolState("Before shutdown ");
            bag.close();
            bag.values().forEach(mci -> closeConnection(mci, "pool destroyed"));
        }

    }

    final class ManagedConnectionInfo implements ConcurrentBag.IConcurrentBagEntry, ConnectionEventListener {

        final Pool pool;
        final ManagedConnection managedConnection;
        final NamedResource xares;

        volatile ScheduledFuture<?> endOfLife;
        volatile int state;
        volatile boolean evict;
        long lastAccessed;
        long lastBorrowed;

        Transaction transaction;

        ManagedConnectionInfo(Pool pool, ManagedConnection mc, NamedResource xares) {
            this.pool = pool;
            this.managedConnection = mc;
            this.xares = xares;
            mc.addConnectionEventListener(this);
        }

        @Override
        public int getState() {
            return STATE_UPDATER.get(this);
        }

        @Override
        public boolean compareAndSet(int expect, int update) {
            return STATE_UPDATER.compareAndSet(this, expect, update);
        }

        @Override
        public void setState(int update) {
            STATE_UPDATER.set(this, update);
        }

        boolean isMarkedEvicted() {
            return evict;
        }

        void markEvicted() {
            this.evict = true;
        }

        ManagedConnection getManagedConnection() {
            return managedConnection;
        }

        NamedResource getXAResource() {
            return xares;
        }

        void setFutureEol(ScheduledFuture<?> futureEol) {
            this.endOfLife = futureEol;
        }

        void requite() {
            transaction = null;
            try {
                managedConnection.cleanup();
                pool.requite(this);
            } catch (ResourceException e) {
                pool.closeConnection(this, "Cleanup error: " + e);
            }
        }

        void enlist(Transaction transaction) throws ResourceException {
            assert this.transaction == null;
            try {
                transaction.enlistResource(xares);
                this.transaction = transaction;
            } catch (Exception e) {
                throw new ResourceException("Unable to enlist resource " + name, e);
            }
        }

        @Override
        public void connectionClosed(ConnectionEvent event) {
            if (transaction != null) {
                return;
            }
            requite();
        }

        @Override
        public void localTransactionStarted(ConnectionEvent event) {
        }

        @Override
        public void localTransactionCommitted(ConnectionEvent event) {
        }

        @Override
        public void localTransactionRolledback(ConnectionEvent event) {
        }

        @Override
        public void connectionErrorOccurred(ConnectionEvent event) {
            pool.closeConnection(this, "Connection error: " + event.getException());
        }

//            Connection close()
//            {
//                ScheduledFuture<?> eol = endOfLife;
//                if (eol != null && !eol.isDone() && !eol.cancel(false)) {
//                    LOG.warning(name + " - maxLifeTime expiration task cancellation unexpectedly returned false for connection " + managedConnection);
//                }
//                endOfLife = null;
//            }

        @Override
        public String toString() {
            return "ManagedConnectionInfo[" + Integer.toHexString(hashCode()) + ", mc: " + managedConnection + ", state: " + stateToString() + "]";
        }

        private String stateToString() {
            switch (state) {
                case STATE_IN_USE:
                    return "IN_USE";
                case STATE_NOT_IN_USE:
                    return "NOT_IN_USE";
                case STATE_REMOVED:
                    return "REMOVED";
                case STATE_RESERVED:
                    return "RESERVED";
                default:
                    return "Invalid";
            }
        }

    }

}
