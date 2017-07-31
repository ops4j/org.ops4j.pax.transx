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
import org.ops4j.pax.transx.jdbc.utils.AbstractConnectionHandle;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnection;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LazyAssociatableConnectionManager;
import javax.resource.spi.LocalTransaction;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class ConnectionHandle<MCF extends AbstractManagedConnectionFactory>
        extends AbstractConnectionHandle<MCF, Connection, ConnectionHandle<MCF>> implements Connection {

    public ConnectionHandle(LazyAssociatableConnectionManager cm, UserPasswordManagedConnectionFactory mcf, ConnectionRequestInfo cri) {
        super(cm, mcf, cri);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E extends Exception> E wrapException(String msg, Exception e) {
        if (msg == null && e instanceof SQLException) {
            return (E) e;
        }
        if (msg == null && e != null && e.getCause() instanceof SQLException) {
            return (E) e.getCause();
        }
        return (E) new SQLException(msg, e);
    }

    public void commit() throws SQLException {
        AbstractManagedConnection<MCF, Connection, ConnectionHandle<MCF>> mc = getManagedConnection();
        if (mc.isInXaTransaction()) {
            throw new SQLException("Can not commit within an XA transaction");
        }
        Connection c = mc.getPhysicalConnection();
        if (c.getAutoCommit()) {
            return;
        }

        try {
            LocalTransaction tx = mc.getClientLocalTransaction();
            tx.commit();
            tx.begin();
        } catch (ResourceException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            } else {
                throw new SQLException(e);
            }
        }
    }

    public void rollback() throws SQLException {
        AbstractManagedConnection<MCF, Connection, ConnectionHandle<MCF>> mc = getManagedConnection();
        if (mc.isInXaTransaction()) {
            throw new SQLException("Can not rollback within an XA transaction");
        }
        Connection c = mc.getPhysicalConnection();
        if (c.getAutoCommit()) {
            return;
        }

        try {
            LocalTransaction tx = mc.getClientLocalTransaction();
            tx.rollback();
            tx.begin();
        } catch (ResourceException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            } else {
                throw new SQLException(e);
            }
        }
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        AbstractManagedConnection<MCF, Connection, ConnectionHandle<MCF>> mc = getManagedConnection();
        if (mc.isInXaTransaction()) {
            throw new SQLException("Can not set autoCommit within an XA transaction");
        }
        Connection c = mc.getPhysicalConnection();
        if (autoCommit == c.getAutoCommit()) {
            // nothing to do
            return;
        }

        try {
            LocalTransaction tx = mc.getClientLocalTransaction();
            if (autoCommit) {
                // reenabling autoCommit - JDBC spec says current transaction is committed
                tx.commit();
            } else {
                // disabling autoCommit
                tx.begin();
            }
        } catch (ResourceException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            } else {
                throw new SQLException(e);
            }
        }
    }

    public boolean getAutoCommit() throws SQLException {
        return call(Connection::getAutoCommit);
    }

    public Statement createStatement() throws SQLException {
        return wrapStatement(call(Connection::createStatement));
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return wrapStatement(call(c -> c.createStatement(resultSetType, resultSetConcurrency)));
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return wrapStatement(call(c -> c.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability)));
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return wrapPreparedStatement(call(c -> c.prepareStatement(sql)));
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return wrapPreparedStatement(call(c -> c.prepareStatement(sql, autoGeneratedKeys)));
    }

    public PreparedStatement prepareStatement(String sql, int columnIndexes[]) throws SQLException {
        return wrapPreparedStatement(call(c -> c.prepareStatement(sql, columnIndexes)));
    }

    public PreparedStatement prepareStatement(String sql, String columnNames[]) throws SQLException {
        return wrapPreparedStatement(call(c -> c.prepareStatement(sql, columnNames)));
    }

    @Override
    public Clob createClob() throws SQLException {
        return call(Connection::createClob);
    }

    @Override
    public Blob createBlob() throws SQLException {
        return call(Connection::createBlob);
    }

    @Override
    public NClob createNClob() throws SQLException {
        return call(Connection::createNClob);
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return call(Connection::createSQLXML);
    }

    @Override
    public boolean isValid(int i) throws SQLException {
        return call(c -> c.isValid(i));
    }

    @Override
    public void setClientInfo(String s, String s1) throws SQLClientInfoException {
        execute(c -> c.setClientInfo(s, s1));
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        execute(c -> c.setClientInfo(properties));
    }

    @Override
    public String getClientInfo(String s) throws SQLException {
        return call(c -> c.getClientInfo(s));
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return call(Connection::getClientInfo);
    }

    @Override
    public Array createArrayOf(String s, Object[] objects) throws SQLException {
        return call(c -> c.createArrayOf(s, objects));
    }

    @Override
    public Struct createStruct(String s, Object[] objects) throws SQLException {
        return call(c -> c.createStruct(s, objects));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return wrapPreparedStatement(call(c -> c.prepareStatement(sql, resultSetType, resultSetConcurrency)));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return wrapPreparedStatement(call(c -> c.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability)));
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return wrapCallableStatement(call(c -> c.prepareCall(sql)));
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return wrapCallableStatement(call(c -> c.prepareCall(sql, resultSetType, resultSetConcurrency)));
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return wrapCallableStatement(call(c -> c.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability)));
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        return wrapMetaData(call(Connection::getMetaData));
    }

    public String getCatalog() throws SQLException {
        return call(Connection::getCatalog);
    }

    public void setCatalog(String catalog) throws SQLException {
        execute(c -> c.setCatalog(catalog));
    }

    public int getHoldability() throws SQLException {
        return call(Connection::getHoldability);
    }

    public void setHoldability(int holdability) throws SQLException {
        execute(c -> c.setHoldability(holdability));
    }

    @SuppressWarnings("all")
    public int getTransactionIsolation() throws SQLException {
        return call(Connection::getTransactionIsolation);
    }

    public void setTransactionIsolation(int level) throws SQLException {
        execute(c -> c.setTransactionIsolation(level));
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return call(Connection::getTypeMap);
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        execute(c -> c.setTypeMap(map));
    }

    public SQLWarning getWarnings() throws SQLException {
        return call(Connection::getWarnings);
    }

    public void clearWarnings() throws SQLException {
        execute(Connection::clearWarnings);
    }

    public boolean isReadOnly() throws SQLException {
        return call(Connection::isReadOnly);
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
        execute(c -> c.setReadOnly(readOnly));
    }

    public Savepoint setSavepoint() throws SQLException {
        return call(Connection::setSavepoint);
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        return call(c -> c.setSavepoint(name));
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        execute(c -> c.releaseSavepoint(savepoint));
    }

    public void rollback(Savepoint savepoint) throws SQLException {
        // rollback(Savepoint) simply delegates as this does not interact with the transaction
        execute(c -> c.rollback(savepoint));
    }

    public String nativeSQL(String sql) throws SQLException {
        return call(c -> c.nativeSQL(sql));
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        execute(c -> c.setSchema(schema));
    }

    @Override
    public String getSchema() throws SQLException {
        return call(Connection::getSchema);
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        execute(c -> c.abort(executor));
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        execute(c -> c.setNetworkTimeout(executor, milliseconds));
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return call(Connection::getNetworkTimeout);
    }

    @Override
    public <T> T unwrap(Class<T> tClass) throws SQLException {
        if (tClass.isInstance(this)) {
            return tClass.cast(this);
        }
        return call(c -> c.unwrap(tClass));
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        if (aClass.isInstance(this)) {
            return true;
        }
        return call(c -> c.isWrapperFor(aClass));
    }

    private Statement wrapStatement(Statement s) {
        return Wrappers.wrap(Statement.class, this, s);
    }

    private PreparedStatement wrapPreparedStatement(PreparedStatement ps) {
        return Wrappers.wrap(PreparedStatement.class, this, ps);
    }

    private CallableStatement wrapCallableStatement(CallableStatement cs) {
        return Wrappers.wrap(CallableStatement.class, this, cs);
    }

    private DatabaseMetaData wrapMetaData(DatabaseMetaData dbmd) {
        return Wrappers.wrap(DatabaseMetaData.class, this, dbmd);
    }

}
