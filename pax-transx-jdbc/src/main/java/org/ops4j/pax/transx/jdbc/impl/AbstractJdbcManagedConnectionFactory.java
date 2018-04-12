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
package org.ops4j.pax.transx.jdbc.impl;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.utils.AbstractManagedConnection;
import org.ops4j.pax.transx.connection.utils.AbstractManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.sql.CommonDataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class AbstractJdbcManagedConnectionFactory<
        MCF extends AbstractManagedConnectionFactory<MCF, MC, Connection, ConnectionHandle<MCF, MC>>,
        MC extends AbstractManagedConnection<MCF, MC, Connection, ConnectionHandle<MCF, MC>>,
        T extends CommonDataSource>
            extends AbstractManagedConnectionFactory<MCF, MC, Connection, ConnectionHandle<MCF, MC>>
            implements AutocommitSpecCompliant{

    protected final T dataSource;
    protected boolean commitBeforeAutocommit = false;
    protected int preparedStatementCacheSize = 0;
    protected int transactionIsolationLevel = -1;

    protected AbstractJdbcManagedConnectionFactory(T dataSource, ExceptionSorter exceptionSorter) {
        this.dataSource = dataSource;
        this.exceptionSorter = exceptionSorter;
    }

    @Override
    public ConnectionHandle<MCF, MC> createConnectionHandle(ConnectionRequestInfo cri, MC mc) {
        return new ConnectionHandle<>((MCF) this, cri, mc);
    }

    public Object createConnectionFactory(ConnectionManager connectionManager) throws ResourceException {
        return new TransxDataSource(this, connectionManager);
    }

    /**
     * Return whether the Driver requires a commit before enabling auto-commit.
     *
     * @return TRUE if the Driver requires a commit before enabling auto-commit.
     */
    public boolean isCommitBeforeAutocommit() {
        return commitBeforeAutocommit;
    }

    /**
     * Set whether the Driver requires a commit before enabling auto-commit.
     * Although the JDBC specification requires any pending work to be committed
     * when auto-commit is enabled, not all drivers respect this. Setting this property
     * to true will cause the connector to explicitly commit the transaction before
     * re-enabling auto-commit; for compliant drivers this may result in two commits.
     *
     * @param commitBeforeAutocommit set TRUE if a commit should be performed before enabling auto-commit
     */
    public void setCommitBeforeAutocommit(boolean commitBeforeAutocommit) {
        this.commitBeforeAutocommit = commitBeforeAutocommit;
    }

    public int getPreparedStatementCacheSize() {
        return preparedStatementCacheSize;
    }

    public void setPreparedStatementCacheSize(int preparedStatementCacheSize) {
        this.preparedStatementCacheSize = preparedStatementCacheSize;
    }

    protected Connection wrap(Connection connection) {
        if (preparedStatementCacheSize > 0) {
            return new ConnectionWrapper(connection, preparedStatementCacheSize);
        } else {
            return connection;
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
        if (obj.getClass() == this.getClass()) {
            AbstractJdbcManagedConnectionFactory other = (AbstractJdbcManagedConnectionFactory) obj;
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
        return getClass().getSimpleName() + "[" + dataSource + "]";
    }

}
