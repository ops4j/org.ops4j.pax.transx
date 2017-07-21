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

import org.ops4j.pax.transx.connection.utils.UserPasswordManagedConnectionFactory;
import org.ops4j.pax.transx.jdbc.utils.UserPasswordHandleFactoryRequestInfo;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LazyAssociatableConnectionManager;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DataSource connection factory for JDBC Connections.
 */
public class TransxDataSource implements javax.sql.DataSource, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(TransxDataSource.class.getName());

    protected final UserPasswordManagedConnectionFactory mcf;
    protected final ConnectionManager cm;

    public TransxDataSource(UserPasswordManagedConnectionFactory mcf, ConnectionManager cm) {
        this.mcf = mcf;
        this.cm = cm;
    }

    public void close() throws Exception {
        if (cm instanceof AutoCloseable) {
            ((AutoCloseable) cm).close();
        }
    }

    public Connection getConnection() throws SQLException {
        return getConnection(NULL_CRI);
    }

    public Connection getConnection(String user, String password) throws SQLException {
        TransxUserPasswordHandleFactoryRequestInfo cri = new TransxUserPasswordHandleFactoryRequestInfo(user, password);
        return getConnection(cri);
    }

    private Connection getConnection(TransxUserPasswordHandleFactoryRequestInfo cri) throws SQLException {
        try {
            return (Connection) cm.allocateConnection(mcf, cri);
        } catch (ResourceException e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
            //Failed to allocate!
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            } else {
                throw new SQLException(e);
            }
        }
    }

    private final TransxUserPasswordHandleFactoryRequestInfo NULL_CRI = new TransxUserPasswordHandleFactoryRequestInfo(null, null);

    private class TransxUserPasswordHandleFactoryRequestInfo extends UserPasswordHandleFactoryRequestInfo<ConnectionHandle> {
        TransxUserPasswordHandleFactoryRequestInfo(String userName, String password) {
            super(userName, password);
        }

        @Override
        public ConnectionHandle createConnectionHandle(ConnectionRequestInfo cri) {
            return new ConnectionHandle(asLazyAssociatableConnectionManager(cm), mcf, cri);
        }
    }

    protected LazyAssociatableConnectionManager asLazyAssociatableConnectionManager(ConnectionManager cm) {
        return cm instanceof LazyAssociatableConnectionManager ? (LazyAssociatableConnectionManager) cm : null;
    }

    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    public PrintWriter getLogWriter() throws SQLException {
        try {
            return mcf.getLogWriter();
        } catch (ResourceException e) {
            throw new SQLException(e);
        }
    }

    public void setLoginTimeout(int seconds) throws SQLException {
        // throw an unchecked exception here as code should not be calling this
        throw new UnsupportedOperationException("Cannot set loginTimeout on a connection factory");
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
        // throw an unchecked exception here as code should not be calling this
        throw new UnsupportedOperationException("Cannot set logWriter on a connection factory");
    }

    /* (non-Javadoc)
     * @see java.sql.Wrapper#isWrapperFor(java.lang.Class)
     */
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    /* (non-Javadoc)
     * @see java.sql.Wrapper#unwrap(java.lang.Class)
     */
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger("org.ops4j.pax.transx");
    }
}
