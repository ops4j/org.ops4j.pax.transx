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
package org.ops4j.pax.transx.jdbc.impl;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.utils.CredentialExtractor;
import org.ops4j.pax.transx.jdbc.utils.AbstractConnectionHandle;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnection;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.LocalTransactionException;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.SQLException;

public class ManagedXAConnection extends AbstractManagedConnection<XADataSourceMCF, Connection, ConnectionHandle<XADataSourceMCF>> {

    private final LocalTransactionImpl localTx;
    private final LocalTransactionImpl localClientTx;
    private final XAConnection xaConnection;

    public ManagedXAConnection(XADataSourceMCF mcf, XAConnection xaConnection, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) throws SQLException {
        this(mcf, xaConnection, xaConnection.getXAResource(), xaConnection.getConnection(), credentialExtractor, exceptionSorter);
    }

    public ManagedXAConnection(XADataSourceMCF mcf, XAConnection xaConnection, XAResource xaResource, Connection connection, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) {
        super(mcf, connection, xaResource, credentialExtractor, exceptionSorter);
        this.xaConnection = xaConnection;
        xaConnection.addConnectionEventListener(new ConnectionEventListener() {
            public void connectionClosed(ConnectionEvent event) {
                //we should be handling this independently
            }

            public void connectionErrorOccurred(ConnectionEvent event) {
                Exception e = event.getSQLException();
                unfilteredConnectionError(e);
            }
        });
        localTx = new LocalTransactionImpl(true);
        localClientTx = new LocalTransactionImpl(false);
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        if (getTransactionSupport() != TransactionSupportLevel.XATransaction) {
            throw new ResourceException("No support for XA transactions");
        }
        return new XAResourceProxy<>(this);
    }

    @Override
    public TransactionSupportLevel getTransactionSupport() {
        return TransactionSupportLevel.XATransaction;
    }

    public LocalTransaction getClientLocalTransaction() {
        return localClientTx;
    }

    public LocalTransaction getLocalTransaction() throws ResourceException {
        return localTx;
    }

    protected void localTransactionStart(boolean isSPI) throws ResourceException {
        try {
            physicalConnection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new LocalTransactionException("Unable to disable autoCommit", e);
        }
        super.localTransactionStart(isSPI);
    }

    protected void localTransactionCommit(boolean isSPI) throws ResourceException {
        try {
            // according to the JDBC spec, reenabling autoCommit commits any current transaction
            // we need to do both here, so we rely on this behaviour in the driver as otherwise
            // commit followed by setAutoCommit(true) may result in 2 commits in the database
            if (mcf.isCommitBeforeAutocommit()) {
                physicalConnection.commit();
            }
            physicalConnection.setAutoCommit(true);
        } catch (SQLException e) {
            try {
                physicalConnection.rollback();
            } catch (SQLException e1) {
                if (log != null) {
                    e.printStackTrace(log);
                }
            }
            throw new LocalTransactionException("Unable to commit", e);
        }
        super.localTransactionCommit(isSPI);
    }

    protected void localTransactionRollback(boolean isSPI) throws ResourceException {
        try {
            physicalConnection.rollback();
        } catch (SQLException e) {
            throw new LocalTransactionException("Unable to rollback", e);
        }
        super.localTransactionRollback(isSPI);
        try {
            physicalConnection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Unable to enable autoCommit after rollback", e);
        }
    }

    @Override
    protected boolean isValid() {
        try {
            if (getPhysicalConnection().isValid(0)) {
                return true;
            }
        } catch (SQLException e) {
            // no-op
        }
        return false;
    }

    public void cleanup() throws ResourceException {
        super.cleanup();
        try {
            //TODO reset tx isolation level
            if (!physicalConnection.getAutoCommit()) {
                physicalConnection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ResourceException("Could not reset autocommit when returning to pool", e);
        }
    }

    protected void closePhysicalConnection() throws ResourceException {
        try {
            xaConnection.close();
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Error attempting to destroy managed connection", e);
        }
    }

    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        return null;
    }

    static class XAResourceProxy<MCF extends AbstractManagedConnectionFactory<CI>, C,
            CI extends AbstractConnectionHandle<MCF, C, CI>> implements XAResource {

        private final AbstractManagedConnection<MCF, C, CI> mc;

        XAResourceProxy(AbstractManagedConnection<MCF, C, CI> mc) {
            this.mc = mc;
        }

        private XAResource getXAResource() {
            return mc.doGetXAResource();
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            getXAResource().start(xid, flags);
            mc.setInXaTransaction(true);
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
                mc.setInXaTransaction(false);
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


}
