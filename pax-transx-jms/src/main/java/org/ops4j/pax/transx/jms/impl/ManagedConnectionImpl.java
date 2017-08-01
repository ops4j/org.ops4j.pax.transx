/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.ops4j.pax.transx.jms.impl;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.utils.AbstractManagedConnection;
import org.ops4j.pax.transx.connection.utils.CredentialExtractor;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.resource.ResourceException;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.TransactionSupport;
import javax.security.auth.Subject;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.ops4j.pax.transx.jms.impl.Utils.trace;

public class ManagedConnectionImpl extends AbstractManagedConnection<ManagedConnectionFactoryImpl, Session, SessionImpl> implements ManagedConnection {

    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
    private ReentrantLock lock = new ReentrantLock();

    private final XAConnection xaConnection;
    private final XASession xaSession;
    private final Session xaSessionSession;
    private final Connection connection;
    private final Session session;

    private XAResource xaResource;
    private boolean inManagedTx;

    public ManagedConnectionImpl(ManagedConnectionFactoryImpl mcf,
                                 Subject subject,
                                 ExceptionSorter exceptionSorter,
                                 ConnectionRequestInfoImpl cri) throws ResourceException {
        super(mcf, new CredentialExtractor(subject, cri, mcf), exceptionSorter);
        this.cri = cri;

        try {
            boolean transacted = cri != null && cri.isTransacted();
            int acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
            String userName = credentialExtractor.getUserName();
            String password = credentialExtractor.getPassword();
            if (userName != null && password != null) {
                connection = mcf.getConnectionFactory().createConnection(userName, password);
                xaConnection = mcf.getXaConnectionFactory().createXAConnection(userName, password);
            } else {
                connection = mcf.getConnectionFactory().createConnection();
                xaConnection = mcf.getXaConnectionFactory().createXAConnection();
            }
            connection.setExceptionListener(this::onException);
            xaConnection.setExceptionListener(this::onException);
            xaSession = xaConnection.createXASession();
            xaSessionSession = xaSession.getSession();
            session = connection.createSession(transacted, acknowledgeMode);
        } catch (JMSException e) {
            throw new ResourceException(e.getMessage(), e);
        }
    }

    @Override
    public Session getPhysicalConnection() {
        return inManagedTx ? xaSessionSession : session;
    }

    public TransactionSupport.TransactionSupportLevel getTransactionSupport() {
        return TransactionSupport.TransactionSupportLevel.XATransaction;
    }

    ConnectionMetaData getConnectionMetaData() throws JMSException {
        return connection.getMetaData();
    }

    private void onException(final JMSException exception) {
        if (isDestroyed.get()) {
            return;
        }
        safe(() -> connection.setExceptionListener(null), "Unable to unset exception listener");
        safe(() -> xaConnection.setExceptionListener(null), "Unable to unset exception listener");
        unfilteredConnectionError(exception);
    }

    void lock() {
        lock.lock();
    }

    void tryLock() throws ResourceAllocationException {
        Integer tryLock = mcf.getUseTryLock();
        if (tryLock == null || tryLock <= 0) {
            lock();
            return;
        }
        try {
            if (!lock.tryLock(tryLock, TimeUnit.SECONDS)) {
                throw new ResourceAllocationException("Unable to obtain lock in " + tryLock + " seconds: " + this);
            }
        } catch (InterruptedException e) {
            throw new ResourceAllocationException("Interrupted attempting lock: " + this);
        }
    }

    void unlock() {
        lock.unlock();
    }

    Session getSession() throws JMSException {
        if (xaResource != null && inManagedTx) {
            return xaSession.getSession();
        } else {
            return session;
        }
    }

    void removeHandle(SessionImpl handle) {
        handles.remove(handle);
    }

    void start() throws JMSException {
        if (xaConnection != null) {
            xaConnection.start();
        }
        if (connection != null) {
            connection.start();
        }
    }

    void stop() throws JMSException {
        if (xaConnection != null) {
            xaConnection.stop();
        }
        if (connection != null) {
            connection.stop();
        }
    }

    private void cleanupHandles() throws ResourceException {
    }

    /**
     * Destroy the physical xaConnection.
     *
     * @throws ResourceException Could not property close the session and xaConnection.
     */
    @Override
    public void destroy() throws ResourceException {
        if (!isDestroyed.compareAndSet(false, true)) {
            return;
        }
        super.destroy();
    }

    @Override
    protected void closePhysicalConnection() throws ResourceException {
        try {
            try {
                connection.close();
            } finally {
                xaConnection.close();
            }
        } catch (JMSException e) {
            throw new ResourceException("Could not properly close the connection", e);
        }
    }

    @Override
    public void cleanup() throws ResourceException {
        super.cleanup();

        safe(connection::stop, "Error stopping connection");
        safe(xaConnection::stop, "Error stopping xaConnection");

        inManagedTx = false;

        // I'm recreating the lock object when we return to the pool
        // because it looks too nasty to expect the xaConnection handle
        // to unlock properly in certain race conditions
        // where the dissociation of the managed xaConnection is "random".
        lock = new ReentrantLock();
    }

    protected boolean isValid() {
        try {
            session.createMessage();
            xaSession.createMessage();
            connection.getMetaData();
            return true;
        } catch (JMSException e) {
            return false;
        }
    }

    @Override
    public LocalTransaction getLocalTransaction() throws ResourceException {
        return new LocalTransaction() {
            @Override
            public void begin() throws ResourceException {
            }

            @Override
            public void commit() throws ResourceException {
                lock();
                try {
                    if (getSession().getTransacted()) {
                        getSession().commit();
                    }
                } catch (JMSException e) {
                    throw new ResourceException("Could not commit LocalTransaction", e);
                } finally {
                    unlock();
                }
            }

            @Override
            public void rollback() throws ResourceException {
                lock();
                try {
                    if (getSession().getTransacted()) {
                        getSession().rollback();
                    }
                } catch (JMSException ex) {
                    throw new ResourceException("Could not rollback LocalTransaction", ex);
                } finally {
                    unlock();
                }
            }
        };
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        if (xaResource == null) {
            XAResource xares = xaSession.getXAResource();
            xaResource = new XAResourceProxy(xares);
        }
        return xaResource;
    }

    private class XAResourceProxy implements XAResource {

        private final XAResource xares;

        public XAResourceProxy(XAResource xares) {
            this.xares = xares;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            lock();
            try {
                xares.start(xid, flags);
            } finally {
                setInManagedTx(true);
                unlock();
            }
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            lock();
            try {
                xares.end(xid, flags);
            } finally {
                setInManagedTx(false);
                unlock();
            }
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            return xares.prepare(xid);
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            xares.commit(xid, onePhase);
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            xares.rollback(xid);
        }

        @Override
        public void forget(Xid xid) throws XAException {
            lock();
            try {
                xares.forget(xid);
            } finally {
                setInManagedTx(false);
                unlock();
            }
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return xares.isSameRM(xaResource);
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            return xares.recover(flag);
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return xares.getTransactionTimeout();
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            return xares.setTransactionTimeout(seconds);
        }

        private void setInManagedTx(boolean inManagedTx) {
            ManagedConnectionImpl.this.inManagedTx = inManagedTx;
        }

    }

    static private void safe(Utils.RunnableWithException<JMSException> cb, String msg) {
        try {
            cb.run();
        } catch (JMSException e) {
            trace(msg, e);
        }
    }

}
