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

import org.ops4j.pax.transx.connection.utils.CredentialExtractor;
import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.NoExceptionsAreFatalSorter;
import org.ops4j.pax.transx.connection.utils.UserPasswordManagedConnectionFactory;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.security.auth.Subject;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

public class XADataSourceMCF extends AbstractManagedConnectionFactory implements UserPasswordManagedConnectionFactory, AutocommitSpecCompliant {

    private final XADataSource xaDataSource;
    private int transactionIsolationLevel = -1;
    private int preparedStatementCacheSize = 0;

    public XADataSourceMCF(XADataSource xaDataSource) {
        this(xaDataSource, new NoExceptionsAreFatalSorter());
    }

    protected XADataSourceMCF(XADataSource xaDataSource, ExceptionSorter exceptionSorter) {
        this.xaDataSource = xaDataSource;
        this.exceptionSorter = exceptionSorter;
    }

    public int getPreparedStatementCacheSize() {
        return preparedStatementCacheSize;
    }

    public void setPreparedStatementCacheSize(int preparedStatementCacheSize) {
        this.preparedStatementCacheSize = preparedStatementCacheSize;
    }

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        CredentialExtractor credentialExtractor = new CredentialExtractor(subject, connectionRequestInfo, this);
        XAConnection sqlConnection = getPhysicalConnection(credentialExtractor);
        try {
            XAResource xares = sqlConnection.getXAResource();
            Connection pc;
            if (preparedStatementCacheSize > 0) {
                pc = new ConnectionWrapper(sqlConnection.getConnection(), preparedStatementCacheSize);
            } else {
                pc = sqlConnection.getConnection();
            }
            return new ManagedXAConnection(this, sqlConnection, xares, pc, credentialExtractor, exceptionSorter);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Could not set up ManagedXAConnection", e);
        }
    }

    protected XAConnection getPhysicalConnection(CredentialExtractor credentialExtractor) throws ResourceException {
        try {
            return xaDataSource.getXAConnection(credentialExtractor.getUserName(), credentialExtractor.getPassword());
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Unable to obtain physical connection to " + xaDataSource, e);
        }
    }

    public PrintWriter getLogWriter() throws ResourceException {
        try {
            return xaDataSource.getLogWriter();
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException(e.getMessage(), e);
        }
    }

    public void setLogWriter(PrintWriter log) throws ResourceException {
        try {
            xaDataSource.setLogWriter(log);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException(e.getMessage(), e);
        }
    }

    public int getLoginTimeout() {
        int timeout;
        try {
            timeout = xaDataSource.getLoginTimeout();
        } catch (SQLException e) {
            timeout = 0;
        }
        return timeout;
    }

    public void setLoginTimeout(int timeout) throws ResourceException {
        try {
            xaDataSource.setLoginTimeout(timeout);
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
        if (obj instanceof XADataSourceMCF) {
            XADataSourceMCF other = (XADataSourceMCF) obj;
            return this.xaDataSource.equals(other.xaDataSource);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return xaDataSource.hashCode();
    }

    @Override
    public String toString() {
        return "XADataSourceMCF[" + xaDataSource + "]";
    }

}
