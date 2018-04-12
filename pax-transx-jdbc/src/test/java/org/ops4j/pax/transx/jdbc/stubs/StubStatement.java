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
package org.ops4j.pax.transx.jdbc.stubs;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

/**
 *
 * @author Brett Wooldridge
 */
public class StubStatement implements Statement {

    private static long executeDelay;

    protected int count;
    private boolean closed;

    public static void setExecuteDelayMs(final long delay) {
        executeDelay = delay;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return (T) this;
        }

        throw new SQLException("Wrapped connection is not an instance of " + iface);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        if (executeDelay > 0) {
//         final long start = nanoTime();
//         do {
//            // spin
//         } while (nanoTime() - start < MILLISECONDS.toNanos(executeDelayMs));
            try {
                Thread.sleep(executeDelay);
            } catch (InterruptedException var3) {
                Thread.currentThread().interrupt();
            }
        }
        return new StubResultSet();
    }

    /** {@inheritDoc} */
    @Override
    public int executeUpdate(String sql) throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws SQLException {
        closed = true;
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void setMaxFieldSize(int max) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void setMaxRows(int max) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public void cancel() throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void clearWarnings() throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public void setCursorName(String name) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(String sql) throws SQLException {
        return sql.startsWith("I");
    }

    /** {@inheritDoc} */
    @Override
    public ResultSet getResultSet() throws SQLException {
        return new StubResultSet();
    }

    /** {@inheritDoc} */
    @Override
    public int getUpdateCount() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getMoreResults() throws SQLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void setFetchDirection(int direction) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void setFetchSize(int rows) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getResultSetConcurrency() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getResultSetType() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void addBatch(String sql) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public void clearBatch() throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public int[] executeBatch() throws SQLException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return new StubResultSet();
    }

    /** {@inheritDoc} */
    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public int getResultSetHoldability() throws SQLException {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    /** {@inheritDoc} */
    @Override
    public void setPoolable(boolean poolable) throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void closeOnCompletion() throws SQLException {
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }

    public int getCount() {
        return count;
    }
}
