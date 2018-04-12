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
package org.ops4j.pax.transx.connection.utils;

import org.ops4j.pax.transx.connection.ExceptionSorter;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.resource.spi.SecurityException;
import javax.security.auth.Subject;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;

public abstract class AbstractManagedConnection<
        MCF extends AbstractManagedConnectionFactory<MCF, MC, C, CI>,
        MC extends AbstractManagedConnection<MCF, MC, C, CI>,
        C,
        CI extends AbstractConnectionHandle<MCF, MC, C, CI>>
            implements ManagedConnection {

    protected final MCF mcf;
    protected CI handle;
    protected LinkedList<CI> handles;
    private ConnectionEventListener listener;
    protected ArrayDeque<ConnectionEventListener> listeners;
    protected final CredentialExtractor credentialExtractor;
    protected final ExceptionSorter exceptionSorter;

    protected final LocalTransactionImpl localTx;
    protected final LocalTransactionImpl localClientTx;

    protected PrintWriter log;
    protected Subject subject;
    protected ConnectionRequestInfo cri;

    protected XAResource xaResource;
    protected boolean inXaTransaction;

    public AbstractManagedConnection(MCF mcf, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) {
        assert exceptionSorter != null;
        this.mcf = mcf;
        this.credentialExtractor = credentialExtractor;
        this.exceptionSorter = exceptionSorter;
        this.localTx = new LocalTransactionImpl(true);
        this.localClientTx = new LocalTransactionImpl(false);
    }

    public MCF getMCF() {
        return mcf;
    }

    public LocalTransaction getClientLocalTransaction() {
        return localClientTx;
    }

    public LocalTransaction getLocalTransaction() throws ResourceException {
        return localTx;
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        if (xaResource == null) {
            throw new NotSupportedException("XAResource not available");
        }
        return new XAResourceProxy();
    }

    protected CredentialExtractor getCredentialExtractor() {
        return credentialExtractor;
    }

    protected abstract boolean isValid();

    /**
     * Default implementation dissociates the connection handles.
     * Sub-classes should override to perform any cleanup needed on the physical connection.
     *
     * @throws ResourceException
     */
    public void cleanup() throws ResourceException {
        if (handle != null) {
            handle.cleanup();
            handle = null;
        }
        if (handles != null) {
            handles.forEach(AbstractConnectionHandle::cleanup);
            handles = null;
        }
    }

    public void destroy() throws ResourceException {
        cleanup();
        listener = null;
        listeners = null;
        closePhysicalConnection();
    }

    public void associateConnection(Object o) {
        throw new UnsupportedOperationException();
    }

    public void connectionClosed(CI handle) {
        ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle(handle);
        // count down in case sending the event results in a handle getting removed.
        if (listeners != null) {
            for (ConnectionEventListener listener : reverse(listeners)) {
                listener.connectionClosed(event);
            }
        }
        if (listener != null) {
            listener.connectionClosed(event);
        }
    }

    //This needs a hook for driver specific subclasses to determine if the specific exception
    //means the physical connection is dead and no longer usable.  Sending this event will
    //result in destroying this managed connection instance.
    public void connectionError(Exception e) {
        if (exceptionSorter.isExceptionFatal(e)) {
            if (exceptionSorter.rollbackOnFatalException()) {
                attemptRollback();
            }
            unfilteredConnectionError(e);
        }
    }

    protected void attemptRollback() {
    }

    protected void unfilteredConnectionError(Exception e) {
        ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_ERROR_OCCURRED, e);
        if (listeners != null) {
            for (ConnectionEventListener listener : reverse(listeners)) {
                listener.connectionErrorOccurred(event);
            }
        }
        if (listener != null) {
            listener.connectionErrorOccurred(event);
        }
    }

    public void addConnectionEventListener(ConnectionEventListener connectionEventListener) {
        if (listener == null) {
            listener = connectionEventListener;
        } else {
            if (listeners == null) {
                listeners = new ArrayDeque<>();
            }
            listeners.add(connectionEventListener);
        }
    }

    public void removeConnectionEventListener(ConnectionEventListener connectionEventListener) {
        if (listener == connectionEventListener) {
            listener = null;
            if (listeners != null) {
                listener = listeners.removeFirst();
            }
        } else if (listeners != null) {
            listeners.remove(connectionEventListener);
        }
    }

    public PrintWriter getLogWriter() throws ResourceException {
        return log;
    }

    public void setLogWriter(PrintWriter printWriter) throws ResourceException {
        log = printWriter;
    }

    protected void localTransactionStart(boolean isSPI) throws ResourceException {
        if (!isSPI) {
            ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.LOCAL_TRANSACTION_STARTED);
            if (listeners != null) {
                for (ConnectionEventListener listener : reverse(listeners)) {
                    listener.localTransactionStarted(event);
                }
            }
            if (listener != null) {
                listener.localTransactionStarted(event);
            }
        }
    }

    protected void localTransactionCommit(boolean isSPI) throws ResourceException {
        if (!isSPI) {
            ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.LOCAL_TRANSACTION_COMMITTED);
            if (listeners != null) {
                for (ConnectionEventListener listener : reverse(listeners)) {
                    listener.localTransactionCommitted(event);
                }
            }
            if (listener != null) {
                listener.localTransactionCommitted(event);
            }
        }
    }

    protected void localTransactionRollback(boolean isSPI) throws ResourceException {
        if (!isSPI) {
            ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK);
            if (listener != null) {
                listener.localTransactionRolledback(event);
            }
            if (listeners != null) {
                for (ConnectionEventListener listener : listeners) {
                    listener.localTransactionRolledback(event);
                }
            }
        }
    }

    protected void setInXaTransaction(boolean inXaTransaction) {
        this.inXaTransaction = inXaTransaction;
    }

    public boolean isInXaTransaction() {
        return inXaTransaction;
    }

    public abstract C getPhysicalConnection();

    protected abstract void closePhysicalConnection() throws ResourceException;

    public Object getConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        // Check user first
        String userName = credentialExtractor.getUserName();
        CredentialExtractor credential = new CredentialExtractor(subject, connectionRequestInfo, mcf);
        // Null users are allowed!
        if (userName != null && !userName.equals(credential.getUserName())) {
            throw new SecurityException("Password credentials not the same, reauthentication not allowed");
        }
        if (userName == null && credential.getUserName() != null) {
            throw new SecurityException("Password credentials not the same, reauthentication not allowed");
        }

        CI handle = mcf.createConnectionHandle(connectionRequestInfo, (MC) this);
        if (this.handle == null) {
            this.handle = handle;
        } else {
            if (handles == null) {
                handles = new LinkedList<>();
            }
            handles.add(handle);
        }

        this.subject = subject;
        this.cri = connectionRequestInfo;
        return handle;
    }

    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        return new ManagedConnectionMetaData() {
            @Override
            public String getEISProductName() throws ResourceException {
                return null;
            }

            @Override
            public String getEISProductVersion() throws ResourceException {
                return null;
            }

            @Override
            public int getMaxConnections() throws ResourceException {
                return -1;
            }

            @Override
            public String getUserName() throws ResourceException {
                return credentialExtractor.getUserName();
            }
        };
    }

    protected class LocalTransactionImpl implements LocalTransaction {
        private final boolean isSPI;

        public LocalTransactionImpl(boolean isSPI) {
            this.isSPI = isSPI;
        }

        public void begin() throws ResourceException {
            localTransactionStart(isSPI);
        }

        public void commit() throws ResourceException {
            localTransactionCommit(isSPI);
        }

        public void rollback() throws ResourceException {
            localTransactionRollback(isSPI);
        }
    }

    class XAResourceProxy implements XAResource {

        private XAResource getXAResource() {
            return xaResource;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            getXAResource().start(xid, flags);
            setInXaTransaction(true);
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            getXAResource().commit(xid, onePhase);
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            getXAResource().rollback(xid);
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            try {
                getXAResource().end(xid, flags);
            } finally {
                setInXaTransaction(false);
            }
        }

        @Override
        public void forget(Xid xid) throws XAException {
            getXAResource().forget(xid);
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return getXAResource().getTransactionTimeout();
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            XAResource xares = xaResource;
            if (xaResource instanceof AbstractManagedConnection<?, ?, ?, ?>.XAResourceProxy) {
                xares = ((AbstractManagedConnection<?, ?, ?, ?>.XAResourceProxy) xaResource).getXAResource();
            }
            return getXAResource().isSameRM(xares);
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            return getXAResource().prepare(xid);
        }

        @Override
        public Xid[] recover(int flags) throws XAException {
            return getXAResource().recover(flags);
        }

        @Override
        public boolean setTransactionTimeout(int timeout) throws XAException {
            return getXAResource().setTransactionTimeout(timeout);
        }

    }

    private static <S> Iterable<S> reverse(Deque<S> col) {
        if (col.size() <= 1) {
            return col;
        }
        return col::descendingIterator;
    }
}
