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
import org.ops4j.pax.transx.connection.utils.UserPasswordManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.DissociatableManagedConnection;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.security.auth.Subject;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Function;

public abstract class AbstractManagedConnection<C, CI extends AbstractConnectionHandle<C, CI>>
        implements ManagedConnection, DissociatableManagedConnection {

    protected final AbstractManagedConnectionFactory mcf;
    protected final LinkedList<CI> handles = new LinkedList<>();
    protected final ArrayDeque<ConnectionEventListener> listeners = new ArrayDeque<>(2);
    protected final CredentialExtractor credentialExtractor;
    protected final ExceptionSorter exceptionSorter;
    protected final C physicalConnection;
    protected final XAResource xaResource;

    protected final LocalTransactionImpl localTx;
    protected final LocalTransactionImpl localClientTx;

    protected PrintWriter log;
    protected Subject subject;
    protected ConnectionRequestInfo cri;

    public AbstractManagedConnection(AbstractManagedConnectionFactory mcf, C physicalConnection, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) {
        this(mcf, physicalConnection, null, credentialExtractor, exceptionSorter);
    }

    public AbstractManagedConnection(AbstractManagedConnectionFactory mcf, C physicalConnection, XAResource xaResource, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) {
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

    public boolean matches(ManagedConnectionFactory mcf, Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceAdapterInternalException {
        return credentialExtractor.matches(subject, connectionRequestInfo, (UserPasswordManagedConnectionFactory) mcf);
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
        listeners.clear();
        closePhysicalConnection();
    }

    public void associateConnection(Object o) {
        CI handle = (CI) o;
        AbstractManagedConnection<C, CI> mc = handle.getAssociation();
        // handle is associated to another managed connection - disassociate it
        if (mc != null) {
            mc.handles.remove(handle);
        }
        handle.setAssociation(this);
        handles.add(handle);
    }

    public void dissociateConnections() throws ResourceException {
        while (!handles.isEmpty()) {
            CI handle = handles.removeFirst();
            dissociateConnection(handle);
        }
    }

    protected void dissociateConnection(CI handle) {
        handle.setAssociation(null);
    }

    public void connectionClosed(CI handle) {
        ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle(handle);
        // count down in case sending the event results in a handle getting removed.
        for (ConnectionEventListener listener : reverse(listeners)) {
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
        for (ConnectionEventListener listener : reverse(listeners)) {
            listener.connectionErrorOccurred(event);
        }
    }

    public void addConnectionEventListener(ConnectionEventListener connectionEventListener) {
        listeners.add(connectionEventListener);
    }

    public void removeConnectionEventListener(ConnectionEventListener connectionEventListener) {
        listeners.remove(connectionEventListener);
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
            for (ConnectionEventListener listener : reverse(listeners)) {
                listener.localTransactionStarted(event);
            }
        }
    }

    protected void localTransactionCommit(boolean isSPI) throws ResourceException {
        if (!isSPI) {
            ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.LOCAL_TRANSACTION_COMMITTED);
            for (ConnectionEventListener listener : reverse(listeners)) {
                listener.localTransactionCommitted(event);
            }
        }
    }

    protected void localTransactionRollback(boolean isSPI) throws ResourceException {
        if (!isSPI) {
            ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK);
            for (ConnectionEventListener listener : listeners) {
                listener.localTransactionRolledback(event);
            }
        }
    }

    protected void xaTransactionStart(Xid xid, int flags) throws XAException {
        try {
            doGetXAResource().start(xid, flags);
        } catch (Exception e) {
            throw asXAException(e);
        }
    }

    protected void xaTransactionCommit(Xid xid, boolean onePhase) throws XAException {
        try {
            doGetXAResource().commit(xid, onePhase);
        } catch (Exception e) {
            throw asXAException(e);
        }
    }

    protected void xaTransactionRollback(Xid xid) throws XAException {
        try {
            doGetXAResource().rollback(xid);
        } catch (Exception e) {
            throw asXAException(e);
        }
    }

    private static XAException asXAException(Exception e) throws XAException {
        if (e instanceof XAException) {
            return (XAException) e;
        } else {
            return (XAException) new XAException(e.getMessage()).initCause(e);
        }
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        if (getTransactionSupport() != TransactionSupportLevel.XATransaction) {
            throw new ResourceException("No support for XA transactions");
        }
        return new XAResourceProxy<>(this);
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

    protected XAResource doGetXAResource() {
        return xaResource;
    }

    public TransactionSupportLevel getTransactionSupport() {
        return xaResource != null ? TransactionSupportLevel.XATransaction : TransactionSupportLevel.LocalTransaction;
    }

    public Object getConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        Function<ConnectionRequestInfo, CI> handleFactory = ((UserPasswordHandleFactoryRequestInfo<CI>) connectionRequestInfo).getConnectionHandleFactory();
        CI handle = handleFactory.apply(connectionRequestInfo);
        handle.setAssociation(this);
        handles.add(handle);

        this.subject = subject;
        this.cri = connectionRequestInfo;
        return handle;
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

    static class XAResourceProxy<C,
                                 CI extends AbstractConnectionHandle<C, CI>> implements XAResource {

        private final AbstractManagedConnection<C, CI> mc;

        XAResourceProxy(AbstractManagedConnection<C, CI> mc) {
            this.mc = mc;
        }

        private XAResource getXAResource() throws XAException {
            try {
                return mc.doGetXAResource();
            } catch (Exception e) {
                throw asXAException(e);
            }
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            mc.xaTransactionStart(xid, flags);
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            mc.xaTransactionCommit(xid, onePhase);
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            mc.xaTransactionRollback(xid);
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            getXAResource().end(xid, flags);
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
            if (xaResource instanceof XAResourceProxy) {
                xares = ((XAResourceProxy) xaResource).getXAResource();
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
        return col::descendingIterator;
    }
}
