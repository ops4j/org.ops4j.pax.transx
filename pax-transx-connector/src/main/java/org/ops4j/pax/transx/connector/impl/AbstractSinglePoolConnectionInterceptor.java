/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ops4j.pax.transx.connector.impl;

import org.ops4j.pax.transx.connector.PoolingAttributes;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ValidatingManagedConnectionFactory;
import javax.security.auth.Subject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractSinglePoolConnectionInterceptor implements ConnectionInterceptor, PoolingAttributes {

    protected Logger LOG = Logger.getLogger(getClass().getName());

    protected static final Timer timer = new Timer("PoolIdleReleaseTimer", true);

    protected final ConnectionInterceptor next;
    private final ReadWriteLock resizeLock = new ReentrantReadWriteLock();
    protected Semaphore permits;
    protected Duration blockingTimeout;
    protected int connectionCount = 0;
    protected Duration idleTimeout;
    private IdleReleaser idleReleaser;
    protected int maxSize = 0;
    protected int minSize = 0;
    protected int shrinkLater = 0;
    protected volatile boolean destroyed = false;
    private boolean backgroundValidation;
    private Duration validatingPeriod;
    private boolean validateOnMatch;
    private ValidationTask validationTask;

    public AbstractSinglePoolConnectionInterceptor(ConnectionInterceptor next,
                                                   int maxSize,
                                                   int minSize,
                                                   Duration blockingTimeout,
                                                   Duration idleTimeout,
                                                   boolean backgroundValidation,
                                                   Duration validatingPeriod,
                                                   boolean validateOnMatch) {
        this.next = next;
        this.maxSize = maxSize;
        this.minSize = minSize;
        this.blockingTimeout = blockingTimeout;
        this.backgroundValidation = backgroundValidation;
        this.validatingPeriod = validatingPeriod;
        this.validateOnMatch = validateOnMatch;
        setIdleTimeout(idleTimeout);
        permits = new Semaphore(maxSize, true);
    }

    @Override
    public ConnectionInterceptor next() {
        return next;
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        if (connectionInfo.getManagedConnectionInfo().getManagedConnection() != null) {
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, "supplying already assigned connection from pool " + this + " " + connectionInfo);
            }
            return;
        }
        try {
            resizeLock.readLock().lock();
            try {
                if (permits.tryAcquire(blockingTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    while (true) {
                        try {
                            internalGetConnection(connectionInfo);
                        } catch (ResourceException e) {
                            permits.release();
                            throw e;
                        }
                        if (validateOnMatch) {
                            ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
                            if (!isValid(mci)) {
                                returnConnection(new ConnectionInfo(mci), ConnectionReturnAction.RETURN_HANDLE);
                                continue;
                            }
                        }
                        break;
                    }
                } else {
                    throw new ResourceException("No ManagedConnections available "
                            + "within configured blocking timeout ( "
                            + blockingTimeout
                            + " [ms] ) for pool " + this);

                }
            } finally {
                resizeLock.readLock().unlock();
            }

        } catch (InterruptedException ie) {
            throw new ResourceException("Interrupted while requesting permit.", ie);
        } // end of try-catch
    }

    protected abstract void internalGetConnection(ConnectionInfo connectionInfo) throws ResourceException;

    public void returnConnection(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "returning connection " + connectionInfo.getConnectionHandle() + " for MCI " + connectionInfo.getManagedConnectionInfo() + " and MC " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " to pool " + this);
        }
        // not strictly synchronized with destroy(), but pooled operations in internalReturn() are...
        if (destroyed) {
            try {
                connectionInfo.getManagedConnectionInfo().getManagedConnection().destroy();
            } catch (ResourceException re) {
                // empty
            }
            return;
        }
        resizeLock.readLock().lock();
        try {
            ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
            if (connectionReturnAction == ConnectionReturnAction.RETURN_HANDLE && mci.hasConnectionHandles()) {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, "Return request at pool with connection handles! " + connectionInfo.getConnectionHandle() + " for MCI " + connectionInfo.getManagedConnectionInfo() + " and MC " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " to pool " + this, new Exception("Stack trace"));
                }
                return;
            }
            boolean releasePermit = internalReturn(connectionInfo, connectionReturnAction);
            if (releasePermit) {
                permits.release();
            }
        } finally {
            resizeLock.readLock().unlock();
        }
    }


    /**
     *
     * @param connectionInfo connection info to return to pool
     * @param connectionReturnAction whether to return to pool or destroy
     * @return true if a connection for which a permit was issued was returned (so the permit should be released),
     * false if no permit was issued (for instance if the connection was already in the pool and we are destroying it).
     */
    protected boolean internalReturn(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
        ManagedConnection mc = mci.getManagedConnection();
        try {
            mc.cleanup();
        } catch (ResourceException e) {
            connectionReturnAction = ConnectionReturnAction.DESTROY;
        }

        boolean releasePermit;
        synchronized (getPool()) {
            // a bit redundant, but this closes a small timing hole...
            if (destroyed) {
                try {
                    mc.destroy();
                }
                catch (ResourceException re) {
                    //ignore
                }
                return doRemove(mci);
            }
            if (shrinkLater > 0) {
                //nothing can get in the pool while shrinkLater > 0, so releasePermit is false here.
                connectionReturnAction = ConnectionReturnAction.DESTROY;
                shrinkLater--;
                releasePermit = false;
            } else if (connectionReturnAction == ConnectionReturnAction.RETURN_HANDLE) {
                mci.setLastUsed(Instant.now());
                doAdd(mci);
                return true;
            } else {
                releasePermit = doRemove(mci);
            }
        }
        //we must destroy connection.
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "Discarding connection in pool " + this + " " + connectionInfo);
        }
        next.returnConnection(connectionInfo, connectionReturnAction);
        connectionCount--;
        return releasePermit;
    }

    protected abstract void internalDestroy();

    // Cancel the IdleReleaser TimerTask (fixes memory leak) and clean up the pool
    public void destroy() {
        destroyed = true;
        if (idleReleaser != null)
            idleReleaser.cancel();
        internalDestroy();
        next.destroy();
    }

    public int getPartitionCount() {
        return 1;
    }

    public int getPartitionMaxSize() {
        return maxSize;
    }

    public void setPartitionMaxSize(int newMaxSize) throws InterruptedException {
        if (newMaxSize <= 0) {
            throw new IllegalArgumentException("Max size must be positive, not " + newMaxSize);
        }
        if (newMaxSize != getPartitionMaxSize()) {
            resizeLock.writeLock().lock();
            try {
                ResizeInfo resizeInfo = new ResizeInfo(this.minSize, permits.availablePermits(), connectionCount, newMaxSize);
                permits = new Semaphore(newMaxSize, true);
                //pre-acquire permits for the existing checked out connections that will not be closed when they are returned.
                for (int i = 0; i < resizeInfo.transferCheckedOut; i++) {
                    permits.acquire();
                }
                //make sure shrinkLater is 0 while discarding excess connections
                this.shrinkLater = 0;
                //transfer connections we are going to keep
                transferConnections(newMaxSize, resizeInfo.shrinkNow);
                this.shrinkLater = resizeInfo.shrinkLater;
                this.minSize = resizeInfo.newMinSize;
                this.maxSize = newMaxSize;
            } finally {
                resizeLock.writeLock().unlock();
            }
        }
    }

    protected abstract boolean doRemove(ManagedConnectionInfo mci);

    protected abstract void doAdd(ManagedConnectionInfo mci);

    protected abstract Object getPool();


    static final class ResizeInfo {

        final int newMinSize;
        final int shrinkNow;
        final int shrinkLater;
        final int transferCheckedOut;

        ResizeInfo(final int oldMinSize, final int oldPermitsAvailable, final int oldConnectionCount, final int newMaxSize) {
            final int checkedOut = oldConnectionCount - oldPermitsAvailable;
            int shrinkLater = checkedOut - newMaxSize;
            if (shrinkLater < 0) {
                shrinkLater = 0;
            }
            this.shrinkLater = shrinkLater;
            int shrinkNow = oldConnectionCount - newMaxSize - shrinkLater;
            if (shrinkNow < 0) {
                shrinkNow = 0;
            }
            this.shrinkNow = shrinkNow;
            if (newMaxSize >= oldMinSize) {
                newMinSize = oldMinSize;
            } else {
                newMinSize = newMaxSize;
            }
            this.transferCheckedOut = checkedOut - shrinkLater;
        }
    }

    protected abstract void transferConnections(int maxSize, int shrinkNow);

    public abstract int getIdleConnectionCount();

    public int getConnectionCount() {
        return connectionCount;
    }

    public int getPartitionMinSize() {
        return minSize;
    }

    public void setPartitionMinSize(int minSize) {
        this.minSize = minSize;
    }

    public Duration getBlockingTimeout() {
        return blockingTimeout;
    }

    public void setBlockingTimeout(Duration blockingTimeout) {
        if (blockingTimeout != null && blockingTimeout.getSeconds() < 0) {
            throw new IllegalArgumentException("blockingTimeout must be positive, zero or null, not " + blockingTimeout);
        }
        if (blockingTimeout == null) {
            this.blockingTimeout = Duration.ofDays(Integer.MAX_VALUE);
        } else {
            this.blockingTimeout = blockingTimeout;
        }
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        if (idleTimeout == null || idleTimeout.getSeconds() < 0) {
            throw new IllegalArgumentException("idleTimeout must be positive or 0, not " + idleTimeout);
        }
        if (idleReleaser != null) {
            idleReleaser.cancel();
        }
        this.idleTimeout = idleTimeout;
        idleReleaser = new IdleReleaser(this);
        long p = idleTimeout.toMillis();
        timer.schedule(idleReleaser, p, p);
    }

    @Override
    public Duration getValidatingPeriod() {
        return validatingPeriod;
    }

    @Override
    public void setValidatingPeriod(Duration validatingPeriod) {
        if (validatingPeriod != null && validatingPeriod.getSeconds() < 0) {
            throw new IllegalArgumentException("validatingPeriod must be positive, zero or null, not " + validatingPeriod);
        }
        if (validationTask != null) {
            validationTask.cancel();
        }
        this.validatingPeriod = validatingPeriod;
        if (validatingPeriod != null) {
            validationTask = new ValidationTask(this);
            long p = idleTimeout.toMillis();
            timer.schedule(validationTask, p, p);
        }
    }

    protected abstract void getExpiredManagedConnectionInfos(Instant threshold, List<ManagedConnectionInfo> killList);

    abstract void getManagedConnectionInfos(List<ManagedConnectionInfo> mcis);

    protected boolean addToPool(ManagedConnectionInfo mci) {
        boolean added;
        synchronized (getPool()) {
            connectionCount++;
            added = getPartitionMaxSize() > getIdleConnectionCount();
            if (added) {
                doAdd(mci);
            }
        }
        return added;
    }

    boolean isValid(ManagedConnectionInfo mci) {
        if (mci.getManagedConnectionFactory() instanceof ValidatingManagedConnectionFactory) {
            try {
                Set s = ((ValidatingManagedConnectionFactory) mci.getManagedConnectionFactory())
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

    // static class to permit chain of strong references from preventing ClassLoaders
    // from being GC'ed.
    private class ValidationTask extends TimerTask {
        private AbstractSinglePoolConnectionInterceptor parent;

        public ValidationTask(AbstractSinglePoolConnectionInterceptor parent) {
            this.parent = parent;
        }

        public boolean cancel() {
            this.parent = null;
            return super.cancel();
        }

        public void run() {
            // protect against interceptor being set to null mid-execution
            AbstractSinglePoolConnectionInterceptor interceptor = parent;
            if (interceptor == null)
                return;

            List<ManagedConnectionInfo> mcis = new ArrayList<>();
            interceptor.getManagedConnectionInfos(mcis);
            for (ManagedConnectionInfo mci : mcis) {
                if (mci.getManagedConnectionFactory() instanceof ValidatingManagedConnectionFactory) {
                    try {
                        Set s = ((ValidatingManagedConnectionFactory) mci.getManagedConnectionFactory())
                                .getInvalidConnections(Collections.singleton(mci.getManagedConnection()));
                        if (s != null && s.contains(mci.getManagedConnection())) {
                            interceptor.resizeLock.readLock().lock();
                            try {
                                ConnectionInfo killInfo = new ConnectionInfo(mci);
                                parent.next.returnConnection(killInfo, ConnectionReturnAction.DESTROY);
                            } finally {
                                interceptor.resizeLock.readLock().unlock();
                            }
                        }
                    } catch (ResourceException e) {
                    }
                }
            }
        }

    }

    // static class to permit chain of strong references from preventing ClassLoaders
    // from being GC'ed.
    private class IdleReleaser extends TimerTask {
        private AbstractSinglePoolConnectionInterceptor parent;

        private IdleReleaser(AbstractSinglePoolConnectionInterceptor parent) {
            this.parent = parent;
        }

        public boolean cancel() {
            this.parent = null;
            return super.cancel();
        }

        public void run() {
            // protect against interceptor being set to null mid-execution
            AbstractSinglePoolConnectionInterceptor interceptor = parent;
            if (interceptor == null)
                return;

            interceptor.resizeLock.readLock().lock();
            try {
                Instant threshold = Instant.now().minus(interceptor.idleTimeout);
                List<ManagedConnectionInfo> killList = new ArrayList<>(interceptor.getPartitionMaxSize());
                interceptor.getExpiredManagedConnectionInfos(threshold, killList);
                for (ManagedConnectionInfo managedConnectionInfo : killList) {
                    ConnectionInfo killInfo = new ConnectionInfo(managedConnectionInfo);
                    parent.next.returnConnection(killInfo, ConnectionReturnAction.DESTROY);
                }
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Error occurred during execution of idle TimerTask", t);
            } finally {
                interceptor.resizeLock.readLock().unlock();
            }
        }

    }

    // Currently only a short-lived (10 millisecond) task.
    // So, FillTask, unlike IdleReleaser, shouldn't cause GC problems.
    protected class FillTask extends TimerTask {
        private final ManagedConnectionFactory managedConnectionFactory;
        private final Subject subject;
        private final ConnectionRequestInfo cri;

        public FillTask(ConnectionInfo connectionInfo) {
            managedConnectionFactory = connectionInfo.getManagedConnectionInfo().getManagedConnectionFactory();
            subject = connectionInfo.getManagedConnectionInfo().getSubject();
            cri = connectionInfo.getManagedConnectionInfo().getConnectionRequestInfo();
        }

        public void run() {
            resizeLock.readLock().lock();
            try {
                while (connectionCount < minSize) {
                    ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, cri);
                    mci.setSubject(subject);
                    ConnectionInfo ci = new ConnectionInfo(mci);
                    try {
                        next.getConnection(ci);
                    } catch (ResourceException e) {
                        return;
                    }
                    boolean added = addToPool(mci);
                    if (!added) {
                        internalReturn(ci, ConnectionReturnAction.DESTROY);
                        return;
                    }
                }
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "FillTask encountered error in run method", t);
            } finally {
                resizeLock.readLock().unlock();
            }
        }

    }
}
