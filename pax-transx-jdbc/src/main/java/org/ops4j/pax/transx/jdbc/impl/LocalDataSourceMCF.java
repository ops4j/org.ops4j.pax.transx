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
import org.ops4j.pax.transx.connection.utils.CredentialExtractor;
import org.ops4j.pax.transx.jdbc.KnownSQLStateExceptionSorter;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.security.auth.Subject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class LocalDataSourceMCF extends AbstractJdbcManagedConnectionFactory<LocalDataSourceMCF, ManagedJDBCConnection, DataSource> {

    public LocalDataSourceMCF(DataSource datasource) {
        this(datasource, new KnownSQLStateExceptionSorter(), true);
    }

    public LocalDataSourceMCF(DataSource dataSource, ExceptionSorter exceptionSorter, boolean commitBeforeAutocommit) {
        super(dataSource, exceptionSorter);
        this.commitBeforeAutocommit = commitBeforeAutocommit;
    }

    @Override
    public TransactionSupportLevel getTransactionSupport() {
        return TransactionSupportLevel.LocalTransaction;
    }

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        CredentialExtractor credentialExtractor = new CredentialExtractor(subject, connectionRequestInfo, this);
        Connection jdbcConnection = getPhysicalConnection(credentialExtractor);
        return new ManagedJDBCConnection(this, wrap(jdbcConnection), credentialExtractor, exceptionSorter);
    }

    protected Connection getPhysicalConnection(CredentialExtractor credentialExtractor) throws ResourceException {
        try {
            String username = credentialExtractor.getUserName();
            String password = credentialExtractor.getPassword();
            if (username != null) {
                return dataSource.getConnection(username, password);
            } else {
                return dataSource.getConnection();
            }
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Unable to obtain physical connection to " + dataSource, e);
        }
    }

}
