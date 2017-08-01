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
import org.ops4j.pax.transx.connection.utils.AbstractManagedConnection;
import org.ops4j.pax.transx.connection.utils.CredentialExtractor;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.LocalTransactionException;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Implementation of ManagedConnection that manages a physical JDBC connection.
 */
public class ManagedJDBCConnection extends AbstractManagedConnection<LocalDataSourceMCF, Connection, ConnectionHandle<LocalDataSourceMCF>> {

    private final Connection physicalConnection;
    private final LocalTransactionImpl localTx;
    private final LocalTransactionImpl localClientTx;

    /**
     * Constructor for initializing the manager.
     *
     * @param mcf the ManagedConnectionFactory that created this ManagedConnection
     * @param physicalConnection the connection to manage
     * @param credentialExtractor credential extractor
     * @param exceptionSorter the ExceptionSorter to use for classifying Exceptions raised on the physical connection
     */
    public ManagedJDBCConnection(LocalDataSourceMCF mcf, Connection physicalConnection, CredentialExtractor credentialExtractor, ExceptionSorter exceptionSorter) {
        super(mcf, credentialExtractor, exceptionSorter);
        this.physicalConnection = physicalConnection;
        localTx = new LocalTransactionImpl(true);
        localClientTx = new LocalTransactionImpl(false);
    }

    @Override
    public Connection getPhysicalConnection() {
        return physicalConnection;
    }

    public LocalTransaction getClientLocalTransaction() {
        return localClientTx;
    }

    public LocalTransaction getLocalTransaction() throws ResourceException {
        return localTx;
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

    protected void localTransactionStart(boolean isSPI) throws ResourceException {
        try {
            getPhysicalConnection().setAutoCommit(false);
        } catch (SQLException e) {
            throw new LocalTransactionException("Unable to disable autoCommit", e);
        }
        super.localTransactionStart(isSPI);
    }

	protected void localTransactionCommit(boolean isSPI) throws ResourceException {
        try {
            if (mcf.isCommitBeforeAutocommit()) {
                getPhysicalConnection().commit();
            }
            getPhysicalConnection().setAutoCommit(true);
        } catch (SQLException e) {
            try {
                getPhysicalConnection().rollback();
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
            getPhysicalConnection().rollback();
        } catch (SQLException e) {
            throw new LocalTransactionException("Unable to rollback", e);
        }
        super.localTransactionRollback(isSPI);
        try {
            getPhysicalConnection().setAutoCommit(true);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Unable to enable autoCommit after rollback", e);
        }
    }

    public XAResource getXAResource() throws ResourceException {
        throw new NotSupportedException("XAResource not available from a LocalTransaction connection");
    }

	public void cleanup() throws ResourceException {
        super.cleanup();
        try {
            //TODO reset tx isolation level
            if (!getPhysicalConnection().getAutoCommit()) {
                getPhysicalConnection().setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ResourceException("Could not reset autocommit when returning to pool", e);
        }
    }
    
	protected void closePhysicalConnection() throws ResourceException {
        try {
            getPhysicalConnection().close();
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Error attempting to destroy managed connection", e);
        }
    }

	protected void attemptRollback() {
        try {
            getPhysicalConnection().rollback();
        } catch (SQLException e) {
            //ignore.... presumably the connection is actually dead
        }
    }
}
