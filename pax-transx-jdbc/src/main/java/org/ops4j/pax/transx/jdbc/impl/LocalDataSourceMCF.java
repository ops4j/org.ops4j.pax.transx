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
import org.ops4j.pax.transx.connection.utils.UserPasswordManagedConnectionFactory;
import org.ops4j.pax.transx.jdbc.KnownSQLStateExceptionSorter;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.security.auth.Subject;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

public class LocalDataSourceMCF extends AbstractManagedConnectionFactory {

	private final DataSource dataSource;
    private int transactionIsolationLevel = -1;

    public LocalDataSourceMCF(DataSource datasource) {
        this(datasource, new KnownSQLStateExceptionSorter(), true);
    }

    public LocalDataSourceMCF(DataSource dataSource, ExceptionSorter exceptionSorter, boolean commitBeforeAutocommit) {
        this.dataSource = dataSource;
        this.exceptionSorter = exceptionSorter;
        this.commitBeforeAutocommit = commitBeforeAutocommit;
    }

    @Override
    public TransactionSupportLevel getTransactionSupport() {
        return TransactionSupportLevel.LocalTransaction;
    }

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        CredentialExtractor credentialExtractor = new CredentialExtractor(subject, connectionRequestInfo, this);
        Connection jdbcConnection = getPhysicalConnection(credentialExtractor);
        return new ManagedJDBCConnection(this, jdbcConnection, credentialExtractor, exceptionSorter);
    }

    protected Connection getPhysicalConnection(CredentialExtractor credentialExtractor) throws ResourceException {
        try {
            return dataSource.getConnection(credentialExtractor.getUserName(), credentialExtractor.getPassword());
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Unable to obtain physical connection to " + dataSource, e);
        }
    }

    public PrintWriter getLogWriter() throws ResourceException {
        try {
            return dataSource.getLogWriter();
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException(e.getMessage(), e);
        }
    }

    public void setLogWriter(PrintWriter log) throws ResourceException {
        try {
            dataSource.setLogWriter(log);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException(e.getMessage(), e);
        }
    }

    public int getLoginTimeout() {
        int timeout;
        try {
            timeout = dataSource.getLoginTimeout();
        } catch (SQLException e) {
            timeout = 0;
        }
        return timeout;
    }

    public void setLoginTimeout(int timeout) throws ResourceException {
        try {
            dataSource.setLoginTimeout(timeout);
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
        if (obj instanceof LocalDataSourceMCF) {
            LocalDataSourceMCF other = (LocalDataSourceMCF) obj;
            return this.dataSource.equals(other.dataSource);
        }
        return false;
    }

    @Override
	public int hashCode() {
        return dataSource.hashCode();
    }

    @Override
	public String toString() {
        return "LocalDataSourceMCF[" + dataSource + "]";
    }

}
