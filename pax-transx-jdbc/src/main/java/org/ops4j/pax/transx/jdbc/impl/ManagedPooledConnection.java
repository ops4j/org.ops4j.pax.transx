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
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnection;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.LocalTransactionException;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;

public class ManagedPooledConnection extends AbstractManagedConnection<Connection, ConnectionHandle> {

    private final LocalTransactionImpl localTx;
    private final LocalTransactionImpl localClientTx;
    private final PooledConnection pooledConnection;

    public ManagedPooledConnection(AbstractManagedConnectionFactory mcf, PooledConnection pooledConnection, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) throws SQLException {
        super(mcf, pooledConnection.getConnection(), credentialExtractor, exceptionSorter);
        this.pooledConnection = pooledConnection;
        pooledConnection.addConnectionEventListener(new ConnectionEventListener() {
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
    public TransactionSupportLevel getTransactionSupport() {
        return TransactionSupportLevel.LocalTransaction;
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
            if (getMCF().isCommitBeforeAutocommit()) {
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

    public XAResource getXAResource() throws ResourceException {
        throw new NotSupportedException("XAResource not available from a LocalTransaction connection");
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
            pooledConnection.close();
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Error attempting to destroy managed connection", e);
        }
    }

    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        return null;
    }

}