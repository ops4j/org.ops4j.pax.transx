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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreparedStatementWrapper implements PreparedStatement {

    private static final Logger LOG = Logger.getLogger(PreparedStatementWrapper.class.getName());

    private final PreparedStatement ps;
    private final ConnectionWrapper cw;
    private final String sql;
    private boolean statementClosed = false;
    private long lastTimeUsed = 0;
    private long timesUsed = 0;
    private long useCount = 0;
    private long actualPrepareTime = 0;

    public PreparedStatementWrapper(ConnectionWrapper cw, String sql, PreparedStatement ps, long prepareTime) {
        this.ps = ps;
        this.cw = cw;
        this.sql = sql;
        setActualPrepareTime(prepareTime);
    }

    /*
     * (non-Javadoc)
     * @see java.sql.Statement#close()
     */
    public void close() throws SQLException {
        ps.clearWarnings();
        ps.clearBatch();
        ps.clearParameters();
        cw.returnStatementToCache(this);
    }

    /*
     *
     */
    protected void closeStatement() {
        try {
            if (!statementClosed) {
                ps.close();
                statementClosed = true;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "TRANQL DB2 EmbeddedXA Driver. Error closing PreparedStatement in closeStatement.\n" +
                    "  Error message = " + e.getMessage() + "\n" +
                    "     Error code = " + Integer.toString(e.getErrorCode()) +
                    "       SQLState = " + e.getSQLState(), e);
        }
    }

    public long getActualPrepareTime() {
        return actualPrepareTime;
    }

    protected void setActualPrepareTime(long actualPrepareTime) {
        this.actualPrepareTime = actualPrepareTime;
    }

    public long getLastTimeUsed() {
        return lastTimeUsed;
    }

    protected void updateLastTimeUsed() {
        lastTimeUsed = System.currentTimeMillis();
    }

    public long getTimesUsed() {
        return timesUsed;
    }

    protected long incrementTimesUsed() {
        return ++timesUsed;
    }

    protected void setTimesUsed(long timesUsed) {
        this.timesUsed = timesUsed;
    }

    public long getUseCount() {
        return useCount;
    }

    protected long incrementUseCount() {
        return ++useCount;
    }

    protected long decrementUseCount() {
        return --useCount;
    }

    protected synchronized void setUseCount(long useCount) {
        this.useCount = useCount;
    }

    @Override
    public String toString() {
        String details = "                     SQL: " + sql + "\n" +
                "          Last time used: " + new Date(lastTimeUsed).toString() + "\n" +
                "    Number of times used: " + timesUsed + "\n" +
                "    Current in Use Count: " + useCount + "\n" +
                "    Initial Prepare time: " + actualPrepareTime + " milliseconds\n";
        return details;
    }

    /**
     *
     */
    public synchronized void checkOutStatement() {
        incrementTimesUsed();
        incrementUseCount();
        updateLastTimeUsed();
    }

    public ResultSet executeQuery() throws SQLException {
        return ps.executeQuery();
    }

    public int executeUpdate() throws SQLException {
        return ps.executeUpdate();
    }

    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        ps.setNull(parameterIndex, sqlType);
    }

    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        ps.setBoolean(parameterIndex, x);
    }

    public void setByte(int parameterIndex, byte x) throws SQLException {
        ps.setByte(parameterIndex, x);
    }

    public void setShort(int parameterIndex, short x) throws SQLException {
        ps.setShort(parameterIndex, x);
    }

    public void setInt(int parameterIndex, int x) throws SQLException {
        ps.setInt(parameterIndex, x);
    }

    public void setLong(int parameterIndex, long x) throws SQLException {
        ps.setLong(parameterIndex, x);
    }

    public void setFloat(int arg0, float arg1) throws SQLException {
        ps.setFloat(arg0, arg1);
    }

    public void setDouble(int arg0, double arg1) throws SQLException {
        ps.setDouble(arg0, arg1);
    }

    public void setBigDecimal(int arg0, BigDecimal arg1) throws SQLException {
        ps.setBigDecimal(arg0, arg1);
    }

    public void setString(int arg0, String arg1) throws SQLException {
        ps.setString(arg0, arg1);
    }

    public void setBytes(int parameterIndex, byte x[]) throws SQLException {
        ps.setBytes(parameterIndex, x);
    }

    public void setDate(int parameterIndex, Date x) throws SQLException {
        ps.setDate(parameterIndex, x);
    }

    public void setTime(int parameterIndex, Time x) throws SQLException {
        ps.setTime(parameterIndex, x);
    }

    public void setTimestamp(int parameterIndex, Timestamp x)
            throws SQLException {
        ps.setTimestamp(parameterIndex, x);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, int length)
            throws SQLException {
        ps.setAsciiStream(parameterIndex, x, length);
    }

    /* (non-Javadoc)
     * @see java.sql.PreparedStatement#setUnicodeStream(int, java.io.InputStream, int)
     */
    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        ps.setUnicodeStream(parameterIndex, x, length);
    }

    public void setBinaryStream(int arg0, InputStream arg1, int arg2)
            throws SQLException {
        ps.setBinaryStream(arg0, arg1, arg2);
    }

    public void clearParameters() throws SQLException {
        ps.clearParameters();
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType,
                          int scale) throws SQLException {
        ps.setObject(parameterIndex, x, targetSqlType, scale);
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream, long l) throws SQLException {
        ps.setAsciiStream(i, inputStream, l);
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream, long l) throws SQLException {
        ps.setBinaryStream(i, inputStream, l);
    }

    @Override
    public void setCharacterStream(int i, Reader reader, long l) throws SQLException {
        ps.setCharacterStream(i, reader, l);
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream) throws SQLException {
        ps.setAsciiStream(i, inputStream);
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream) throws SQLException {
        ps.setBinaryStream(i, inputStream);
    }

    @Override
    public void setCharacterStream(int i, Reader reader) throws SQLException {
        ps.setCharacterStream(i, reader);
    }

    @Override
    public void setNCharacterStream(int i, Reader reader) throws SQLException {
        ps.setNCharacterStream(i, reader);
    }

    @Override
    public void setClob(int i, Reader reader) throws SQLException {
        ps.setClob(i, reader);
    }

    @Override
    public void setBlob(int i, InputStream inputStream) throws SQLException {
        ps.setBlob(i, inputStream);
    }

    @Override
    public void setNClob(int i, Reader reader) throws SQLException {
        ps.setNClob(i, reader);
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType)
            throws SQLException {
        ps.setObject(parameterIndex, x, targetSqlType);
    }

    public void setObject(int parameterIndex, Object x) throws SQLException {
        ps.setObject(parameterIndex, x);
    }

    public boolean execute() throws SQLException {
        return ps.execute();
    }

    public void addBatch() throws SQLException {
        ps.addBatch();
    }

    public void setCharacterStream(int parameterIndex, Reader reader, int length)
            throws SQLException {
        ps.setCharacterStream(parameterIndex, reader, length);
    }

    public void setRef(int i, Ref x) throws SQLException {
        ps.setRef(i, x);
    }

    public void setBlob(int i, Blob x) throws SQLException {
        ps.setBlob(i, x);
    }

    public void setClob(int i, Clob x) throws SQLException {
        ps.setClob(i, x);
    }

    public void setArray(int i, Array x) throws SQLException {
        ps.setArray(i, x);
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        return ps.getMetaData();
    }

    public void setDate(int parameterIndex, Date x, Calendar cal)
            throws SQLException {
        ps.setDate(parameterIndex, x, cal);
    }

    public void setTime(int parameterIndex, Time time, Calendar cal)
            throws SQLException {
        ps.setTime(parameterIndex, time, cal);
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal)
            throws SQLException {
        ps.setTimestamp(parameterIndex, x, cal);
    }

    public void setNull(int paramIndex, int sqlType, String typeName)
            throws SQLException {
        ps.setNull(paramIndex, sqlType, typeName);
    }

    public void setURL(int parameterIndex, URL x) throws SQLException {
        ps.setURL(parameterIndex, x);
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        return ps.getParameterMetaData();
    }

    @Override
    public void setRowId(int i, RowId rowId) throws SQLException {
        ps.setRowId(i, rowId);
    }

    @Override
    public void setNString(int i, String s) throws SQLException {
        ps.setNString(i, s);
    }

    @Override
    public void setNCharacterStream(int i, Reader reader, long l) throws SQLException {
        ps.setNCharacterStream(i, reader, l);
    }

    @Override
    public void setNClob(int i, NClob nClob) throws SQLException {
        ps.setNClob(i, nClob);
    }

    @Override
    public void setClob(int i, Reader reader, long l) throws SQLException {
        ps.setClob(i, reader, l);
    }

    @Override
    public void setBlob(int i, InputStream inputStream, long l) throws SQLException {
        ps.setBlob(i, inputStream, l);
    }

    @Override
    public void setNClob(int i, Reader reader, long l) throws SQLException {
        ps.setNClob(i, reader, l);
    }

    @Override
    public void setSQLXML(int i, SQLXML sqlxml) throws SQLException {
        ps.setSQLXML(i, sqlxml);
    }

    public ResultSet executeQuery(String arg0) throws SQLException {
        return ps.executeQuery(arg0);
    }

    public int executeUpdate(String arg0) throws SQLException {
        return ps.executeUpdate(arg0);
    }

    public int getMaxFieldSize() throws SQLException {
        return ps.getMaxFieldSize();
    }

    public void setMaxFieldSize(int arg0) throws SQLException {
        ps.setMaxFieldSize(arg0);
    }

    public int getMaxRows() throws SQLException {
        return ps.getMaxRows();
    }

    public void setMaxRows(int arg0) throws SQLException {
        ps.setMaxRows(arg0);
    }

    public void setEscapeProcessing(boolean arg0) throws SQLException {
        ps.setEscapeProcessing(arg0);
    }

    public int getQueryTimeout() throws SQLException {
        return ps.getQueryTimeout();
    }

    public void setQueryTimeout(int arg0) throws SQLException {
        ps.setQueryTimeout(arg0);
    }

    public void cancel() throws SQLException {
        ps.cancel();
    }

    public SQLWarning getWarnings() throws SQLException {
        return ps.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        ps.clearWarnings();
    }

    public void setCursorName(String arg0) throws SQLException {
        ps.setCursorName(arg0);
    }

    public boolean execute(String arg0) throws SQLException {
        return ps.execute(arg0);
    }

    public ResultSet getResultSet() throws SQLException {
        return ps.getResultSet();
    }

    public int getUpdateCount() throws SQLException {
        return ps.getUpdateCount();
    }

    public boolean getMoreResults() throws SQLException {
        return ps.getMoreResults();
    }

    public void setFetchDirection(int arg0) throws SQLException {
        ps.setFetchDirection(arg0);
    }

    public int getFetchDirection() throws SQLException {
        return ps.getFetchDirection();
    }

    public void setFetchSize(int arg0) throws SQLException {
        ps.setFetchSize(arg0);
    }

    public int getFetchSize() throws SQLException {
        return ps.getFetchSize();
    }

    public int getResultSetConcurrency() throws SQLException {
        return ps.getResultSetConcurrency();
    }

    public int getResultSetType() throws SQLException {
        return ps.getResultSetType();
    }

    public void addBatch(String arg0) throws SQLException {
        ps.addBatch(arg0);
    }

    public void clearBatch() throws SQLException {
        ps.clearBatch();
    }

    public int[] executeBatch() throws SQLException {
        return ps.executeBatch();
    }

    public Connection getConnection() throws SQLException {
        return ps.getConnection();
    }

    public boolean getMoreResults(int arg0) throws SQLException {
        return ps.getMoreResults(arg0);
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        return ps.getGeneratedKeys();
    }

    public int executeUpdate(String arg0, int arg1) throws SQLException {
        return ps.executeUpdate(arg0, arg1);
    }

    public int executeUpdate(String arg0, int arg1[]) throws SQLException {
        return ps.executeUpdate(arg0, arg1);
    }

    public int executeUpdate(String arg0, String arg1[]) throws SQLException {
        return ps.executeUpdate(arg0, arg1);
    }

    public boolean execute(String arg0, int arg1) throws SQLException {
        return ps.execute(arg0, arg1);
    }

    public boolean execute(String arg0, int arg1[]) throws SQLException {
        return ps.execute(arg0, arg1);
    }

    public boolean execute(String arg0, String arg1[]) throws SQLException {
        return ps.execute(arg0, arg1);
    }

    public int getResultSetHoldability() throws SQLException {
        return ps.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return ps.isClosed();
    }

    @Override
    public void setPoolable(boolean b) throws SQLException {
        ps.setPoolable(b);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return ps.isPoolable();
    }

    @Override
    public <T> T unwrap(Class<T> tClass) throws SQLException {
        if (tClass.isInstance(this)) {
            return tClass.cast(this);
        }
        return ps.unwrap(tClass);
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        if (aClass.isInstance(this)) {
            return true;
        }
        return ps.isWrapperFor(aClass);
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        ps.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return ps.isCloseOnCompletion();
    }
}
