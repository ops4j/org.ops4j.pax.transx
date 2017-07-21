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
import org.ops4j.pax.transx.connection.NoExceptionsAreFatalSorter;
import org.ops4j.pax.transx.connection.utils.CredentialExtractor;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.security.auth.Subject;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;

public class XADataSourceMCF extends AbstractJdbcManagedConnectionFactory<XADataSource> {

    public XADataSourceMCF(XADataSource xaDataSource) {
        this(xaDataSource, new NoExceptionsAreFatalSorter());
    }

    protected XADataSourceMCF(XADataSource xaDataSource, ExceptionSorter exceptionSorter) {
        super(xaDataSource, exceptionSorter);
    }

    @Override
    public TransactionSupportLevel getTransactionSupport() {
        return TransactionSupportLevel.XATransaction;
    }

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        CredentialExtractor credentialExtractor = new CredentialExtractor(subject, connectionRequestInfo, this);
        XAConnection sqlConnection = getPhysicalConnection(credentialExtractor);
        try {
            XAResource xares = sqlConnection.getXAResource();
            Connection pc = wrap(sqlConnection.getConnection());
            return new ManagedXAConnection(this, sqlConnection, xares, pc, credentialExtractor, exceptionSorter);
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Could not set up ManagedXAConnection", e);
        }
    }

    protected XAConnection getPhysicalConnection(CredentialExtractor credentialExtractor) throws ResourceException {
        try {
            String username = credentialExtractor.getUserName();
            String password = credentialExtractor.getPassword();
            if (username != null) {
                return dataSource.getXAConnection(username, password);
            } else {
                return dataSource.getXAConnection();
            }
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("Unable to obtain physical connection to " + dataSource, e);
        }
    }

}
