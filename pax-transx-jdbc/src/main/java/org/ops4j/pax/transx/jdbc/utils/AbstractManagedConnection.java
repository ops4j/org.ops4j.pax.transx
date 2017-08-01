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
package org.ops4j.pax.transx.jdbc.utils;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.utils.CredentialExtractor;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.DissociatableManagedConnection;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;

public abstract class AbstractManagedConnection<MCF extends AbstractManagedConnectionFactory<CI>, C, CI extends AbstractConnectionHandle<MCF, C, CI>>
        implements ManagedConnection, DissociatableManagedConnection {

    protected final MCF mcf;
    protected CI handle;
    protected LinkedList<CI> handles;
    private ConnectionEventListener listener;
    protected ArrayDeque<ConnectionEventListener> listeners;
    protected final CredentialExtractor credentialExtractor;
    protected final ExceptionSorter exceptionSorter;
    protected final C physicalConnection;
    protected final XAResource xaResource;

    protected final LocalTransactionImpl localTx;
    protected final LocalTransactionImpl localClientTx;

    protected PrintWriter log;
    protected Subject subject;
    protected ConnectionRequestInfo cri;

    protected boolean isInXaTransaction;

    public AbstractManagedConnection(MCF mcf, C physicalConnection, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) {
        this(mcf, physicalConnection, null, credentialExtractor, exceptionSorter);
    }

    public AbstractManagedConnection(MCF mcf, C physicalConnection, XAResource xaResource, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) {
        assert exceptionSorter != null;
        this.mcf = mcf;
        this.physicalConnection = physicalConnection;
        this.xaResource = xaResource;
        this.credentialExtractor = credentialExtractor;
        this.exceptionSorter = exceptionSorter;
        this.localTx = new LocalTransactionImpl(true);
        this.localClientTx = new LocalTransactionImpl(false);
    }

    public AbstractManagedConnectionFactory getMCF() {
        return mcf;
    }

    public LocalTransaction getClientLocalTransaction() {
        return localClientTx;
    }

    public LocalTransaction getLocalTransaction() throws ResourceException {
        return localTx;
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
        dissociateConnections();
    }

    public void destroy() throws ResourceException {
        dissociateConnections();
        listener = null;
        listeners = null;
        closePhysicalConnection();
    }

    public void associateConnection(Object o) {
        CI handle = (CI) o;
        AbstractManagedConnection<MCF, C, CI> mc = handle.getAssociation();
        // handle is associated to another managed connection - disassociate it
        if (mc != null) {
            mc.handles.remove(handle);
        }
        doAssociate(handle);
    }

    public void dissociateConnections() throws ResourceException {
        if (handle != null) {
            dissociateConnection(handle);
            handle = null;
        }
        if (handles != null) {
            while (!handles.isEmpty()) {
                CI handle = handles.removeFirst();
                dissociateConnection(handle);
            }
        }
    }

    protected void dissociateConnection(CI handle) {
        handle.setAssociation(null);
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

    @Override
    public abstract XAResource getXAResource() throws ResourceException;

    public void setInXaTransaction(boolean inXaTransaction) {
        isInXaTransaction = inXaTransaction;
    }

    public boolean isInXaTransaction() {
        return isInXaTransaction;
    }

    public C getPhysicalConnection() {
        return physicalConnection;
    }

    protected void closePhysicalConnection() throws ResourceException {
        if (physicalConnection instanceof AutoCloseable) {
            try {
                ((AutoCloseable) physicalConnection).close();
            } catch (Exception e) {
                throw new ResourceException(e.getMessage(), e);
            }
        }
    }

    public XAResource doGetXAResource() {
        return xaResource;
    }

    public TransactionSupportLevel getTransactionSupport() {
        return xaResource != null ? TransactionSupportLevel.XATransaction : TransactionSupportLevel.LocalTransaction;
    }

    public Object getConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        CI handle = mcf.createConnectionHandle(connectionRequestInfo);
        doAssociate(handle);

        this.subject = subject;
        this.cri = connectionRequestInfo;
        return handle;
    }

    private void doAssociate(CI handle) {
        handle.setAssociation(this);
        if (this.handle == null) {
            this.handle = handle;
        } else {
            if (handles == null) {
                handles = new LinkedList<>();
            }
            handles.add(handle);
        }
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

    private static <S> Iterable<S> reverse(Deque<S> col) {
        if (col.size() == 1) {
            return col;
        }
        return col::descendingIterator;
    }
}
