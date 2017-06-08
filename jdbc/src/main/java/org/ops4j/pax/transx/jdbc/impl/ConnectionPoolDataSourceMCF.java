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

import org.ops4j.pax.transx.connection.CredentialExtractor;
import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.NoExceptionsAreFatalSorter;
import org.ops4j.pax.transx.connection.UserPasswordManagedConnectionFactory;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.security.auth.Subject;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import java.io.PrintWriter;
import java.sql.SQLException;

public class ConnectionPoolDataSourceMCF extends AbstractManagedConnectionFactory implements UserPasswordManagedConnectionFactory, AutocommitSpecCompliant {

    private final ConnectionPoolDataSource connectionPoolDataSource;
    private int transactionIsolationLevel = -1;

    public ConnectionPoolDataSourceMCF(ConnectionPoolDataSource connectionPoolDataSource) {
        this(connectionPoolDataSource, new NoExceptionsAreFatalSorter());
    }

    public ConnectionPoolDataSourceMCF(ConnectionPoolDataSource connectionPoolDataSource, ExceptionSorter exceptionSorter) {
        super(exceptionSorter);
        this.connectionPoolDataSource = connectionPoolDataSource;
    }

   public Object createConnectionFactory(ConnectionManager connectionManager) throws ResourceException {
        return new TransxDataSource(this, connectionManager);
    }

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        CredentialExtractor credentialExtractor = new CredentialExtractor(subject, connectionRequestInfo, this);

        PooledConnection sqlConnection = getPhysicalConnection(credentialExtractor);
        try {
            return new ManagedPooledConnection(this, sqlConnection, credentialExtractor, exceptionSorter);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Could not set up ManagedPooledConnection", e);
        }
    }

    protected PooledConnection getPhysicalConnection(CredentialExtractor credentialExtractor) throws ResourceException {
        try {
            return connectionPoolDataSource.getPooledConnection(credentialExtractor.getUserName(), credentialExtractor.getPassword());
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Unable to obtain physical connection to " + connectionPoolDataSource, e);
        }
    }

    public PrintWriter getLogWriter() throws ResourceException {
        try {
            return connectionPoolDataSource.getLogWriter();
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException(e.getMessage(), e);
        }
    }

    public void setLogWriter(PrintWriter log) throws ResourceException {
        try {
            connectionPoolDataSource.setLogWriter(log);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException(e.getMessage(), e);
        }
    }

    public int getLoginTimeout() {
        int timeout;
        try {
            timeout = connectionPoolDataSource.getLoginTimeout();
        } catch (SQLException e) {
            timeout = 0;
        }
        return timeout;
    }

    public void setLoginTimeout(int timeout) throws ResourceException {
        try {
            connectionPoolDataSource.setLoginTimeout(timeout);
        } catch (SQLException e) {
            throw new InvalidPropertyException(e.getMessage());
        }
    }

    public int getTransactionIsolationLevel() {
        return transactionIsolationLevel;
    }

    public void setTransactionIsolationLevel(int transactionIsolationLevel) {
        this.transactionIsolationLevel = transactionIsolationLevel;
    }

    @Override
	public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof ConnectionPoolDataSourceMCF) {
            ConnectionPoolDataSourceMCF other = (ConnectionPoolDataSourceMCF) obj;
            return this.connectionPoolDataSource.equals(other.connectionPoolDataSource);
        }
        return false;
    }

    @Override
	public int hashCode() {
        return connectionPoolDataSource.hashCode();
    }

    @Override
	public String toString() {
        return "ConnectionPoolDataSourceMCF[" + connectionPoolDataSource + "]";
    }

}