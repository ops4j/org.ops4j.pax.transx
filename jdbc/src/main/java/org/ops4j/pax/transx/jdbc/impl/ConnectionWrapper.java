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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/*
 * ConnectionWrapper provides a simple wrapper around a physical connection
 * object. This wrappering allows for calls to prepareStatement calls to be
 * intercepted and provides for additional monitoring of connection level
 * statistics.
 *
 * ConnectionWrapper provides additional connection level capabilities on top of
 * a regular connection.  The current set of capabilities include:
 *  - StatementCaching
 *  - IsolationLevel caching
 */
public class ConnectionWrapper implements Connection {

	private static final Logger LOG = Logger.getLogger(ConnectionWrapper.class.getName());

	private final Connection connection;
	private HashMap<PreparedStatementKey, PreparedStatementWrapper> pStmtCache;
	private int     maxCacheSize = 0;
	private boolean caching = false;
	private int     cacheSize = 0;
    private int     isolationLevel = 0;
	private boolean isolationCachingEnabled = false;

    /**
     * Constructs a new ConnectionWrapper object.  This constructor creates a connection wrapper
     * that does not provide a prepared statement cache.
     *
     * @param connection
     */
    public ConnectionWrapper(Connection connection) {
		this(connection, 0);
	}

    /**
     * Creates a connection wrapper that adds the ability to cache prepared statements.
     *
     * @param connection
     * @param cacheSize
     */
    public ConnectionWrapper(Connection connection, int cacheSize) {
		this.connection = connection;
		caching = false;
		maxCacheSize = cacheSize <= 0 ? 0 : cacheSize;
		if (maxCacheSize > 0) {
			caching = true;
			pStmtCache = new HashMap<>(maxCacheSize * 2);
		}
        try {
            isolationLevel = connection.getTransactionIsolation();
            isolationCachingEnabled = true;
        } catch (SQLException e) {
        }
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
		if (!caching)
			return connection.prepareStatement(sql);

		PreparedStatementKey psk = new PreparedStatementKey(this, sql);
		PreparedStatementWrapper psw = pStmtCache.get(psk);
		if (psw == null) {
			long startTime = System.currentTimeMillis();
			PreparedStatement ps = connection.prepareStatement(sql);
			long endTime = System.currentTimeMillis();
			psw = new PreparedStatementWrapper(this, sql, ps, endTime - startTime);
			psk.setPreparedStatementWrapper(psw);
			addStatementToCache(psk, psw);
		}
		psw.checkOutStatement();
		return psw;
	}

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		if (!caching)
			return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);

		PreparedStatementKey psk = new PreparedStatementKey(this, sql, resultSetType, resultSetConcurrency);
		PreparedStatementWrapper psw = pStmtCache.get(psk);
		if (psw == null) {
			long startTime = System.currentTimeMillis();
			PreparedStatement ps = connection.prepareStatement(sql, resultSetType,
					resultSetConcurrency);
			long endTime = System.currentTimeMillis();
			psw = new PreparedStatementWrapper(this, sql, ps, endTime - startTime);
			psk.setPreparedStatementWrapper(psw);
			addStatementToCache(psk, psw);
		}
		psw.checkOutStatement();
		return psw;
	}

    public PreparedStatement prepareStatement(String sql, int resultSetType,
			int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		if (!caching)
			return connection.prepareStatement(sql, resultSetType, resultSetConcurrency,
					resultSetHoldability);

		PreparedStatementKey psk = new PreparedStatementKey(this, sql, resultSetType,	resultSetConcurrency, resultSetHoldability);
		PreparedStatementWrapper psw = pStmtCache
				.get(psk);
		if (psw == null) {
			long startTime = System.currentTimeMillis();
			PreparedStatement ps = connection.prepareStatement(sql, resultSetType,
					resultSetConcurrency, resultSetHoldability);
			long endTime = System.currentTimeMillis();
			psw = new PreparedStatementWrapper(this, sql, ps, endTime - startTime);
			psk.setPreparedStatementWrapper(psw);
			addStatementToCache(psk, psw);
		}
		psw.checkOutStatement();
		return psw;
	}

	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		PreparedStatementKey psk = new PreparedStatementKey(this, sql, autoGeneratedKeys);
		PreparedStatementWrapper psw = pStmtCache
				.get(psk);
		if (psw == null) {
			long startTime = System.currentTimeMillis();
			PreparedStatement ps = connection.prepareStatement(sql, autoGeneratedKeys);
			long endTime = System.currentTimeMillis();
			psw = new PreparedStatementWrapper(this, sql, ps, endTime - startTime);
			psk.setPreparedStatementWrapper(psw);
			addStatementToCache(psk, psw);
		}
		psw.checkOutStatement();
		return psw;
	}

    public PreparedStatement prepareStatement(String sql, int columnIndexes[])
			throws SQLException {
		if (!caching)
			return connection.prepareStatement(sql, columnIndexes);

		PreparedStatementKey psk = new PreparedStatementKey(this, sql, columnIndexes);
		PreparedStatementWrapper psw = pStmtCache.get(psk);
		if (psw == null) {
			long startTime = System.currentTimeMillis();
			PreparedStatement ps = connection.prepareStatement(sql, columnIndexes);
			long endTime = System.currentTimeMillis();
			psw = new PreparedStatementWrapper(this, sql, ps, endTime - startTime);
			psk.setPreparedStatementWrapper(psw);
			addStatementToCache(psk, psw);
		}
		psw.checkOutStatement();
		return psw;
	}

	public PreparedStatement prepareStatement(String sql, String columnNames[])
			throws SQLException {
		if (!caching)
			return connection.prepareStatement(sql, columnNames);

		PreparedStatementKey psk = new PreparedStatementKey(this, sql, columnNames);
		PreparedStatementWrapper psw = pStmtCache.get(psk);

		if (psw == null) {
			long startTime = System.currentTimeMillis();
			PreparedStatement ps = connection.prepareStatement(sql, columnNames);
			long endTime = System.currentTimeMillis();
			psw = new PreparedStatementWrapper(this, sql, ps, endTime - startTime);
			psk.setPreparedStatementWrapper(psw);
			addStatementToCache(psk, psw);
		}
		psw.checkOutStatement();
		return psw;
	}

    @Override
    public Clob createClob() throws SQLException {
        return connection.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return connection.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return connection.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return connection.createSQLXML();
    }

    @Override
    public boolean isValid(int i) throws SQLException {
        return connection.isValid(i);
    }

    @Override
    public void setClientInfo(String s, String s1) throws SQLClientInfoException {
        connection.setClientInfo(s, s1);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        connection.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String s) throws SQLException {
        return connection.getClientInfo(s);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return connection.getClientInfo();
    }

    @Override
    public Array createArrayOf(String s, Object[] objects) throws SQLException {
        return connection.createArrayOf(s, objects);
    }

    @Override
    public Struct createStruct(String s, Object[] objects) throws SQLException {
        return connection.createStruct(s, objects);
    }

    private void addStatementToCache(PreparedStatementKey psk, PreparedStatementWrapper psw) {
		if (!caching)
			return;

		if (cacheSize >= maxCacheSize) evictStatement();
		pStmtCache.put(psk, psw);
		cacheSize++;
	}

    /**
     * evictStatement looks for the statement that is the least used and oldest.  It removes this statement from the
     * cache.
     */
    private void evictStatement() {
		Iterator<PreparedStatementKey> keyList = pStmtCache.keySet().iterator();

        PreparedStatementKey oldestPsk 	= null;
		PreparedStatementKey currentPsk = null;
		PreparedStatementWrapper oldestPsw 	= null;
		PreparedStatementWrapper currentPsw = null;

        do {
			if (keyList.hasNext()) {
                currentPsk = keyList.next();
            } else {
                if (oldestPsk != null) {
					pStmtCache.remove(oldestPsk);
					oldestPsk.getPreparedStatementWrapper().closeStatement();
					LOG.info("Statement --> "+oldestPsk.getSql()+" <-- is removed from PreparedStatement Cache" );
					break;
				}
			}

			if (oldestPsk == null) {
				oldestPsk = currentPsk;
			} else {
				oldestPsw  = oldestPsk.getPreparedStatementWrapper();
				currentPsw = currentPsk.getPreparedStatementWrapper();

				// Let's keep the statement that's been used the most.
				if (oldestPsw.getTimesUsed() > currentPsw.getTimesUsed()) {
					oldestPsk = currentPsk;
				} else {
					// If they've been used an equal number of times keep the one
					// most recently used.
					if (oldestPsw.getTimesUsed() == currentPsw.getTimesUsed() &&
                            oldestPsw.getLastTimeUsed() > currentPsw.getLastTimeUsed())
						oldestPsk = currentPsk;
				}
			}
		} while (true);
		
	}

	void returnStatementToCache(PreparedStatementWrapper psw) {
		if ( psw.decrementUseCount() < 0)
			LOG.severe("Counting error in PreparedStatementCaching System.\n" + psw.toString());
	}


    public void setTransactionIsolation(int isolationLevel) throws SQLException {
        if (isolationCachingEnabled && this.isolationLevel == isolationLevel) return;
        
        connection.setTransactionIsolation(isolationLevel);
        this.isolationLevel = isolationLevel;
    }

    public int getTransactionIsolation() throws SQLException {
        return isolationCachingEnabled ? isolationLevel : connection.getTransactionIsolation();
    }


    /**
     * Retrieve prepared statement cache size
     * @return An integer that indicates the maximum number of statements that will be cached.
     */
    public int getMaxCacheSize() {
		return maxCacheSize;
	}



    /**
     *  All statements after this comment are delegated to the actual connection object.
     */

    public Statement createStatement() throws SQLException {
		return connection.createStatement();
	}

	public CallableStatement prepareCall(String arg0) throws SQLException {
		return connection.prepareCall(arg0);
	}

	public String nativeSQL(String arg0) throws SQLException {
		return connection.nativeSQL(arg0);
	}

	public void setAutoCommit(boolean arg0) throws SQLException {
		connection.setAutoCommit(arg0);
	}

	public boolean getAutoCommit() throws SQLException {
		return connection.getAutoCommit();
	}

	public void commit() throws SQLException {
		connection.commit();
	}

	public void rollback() throws SQLException {
		connection.rollback();
	}

	public void close() throws SQLException {
		connection.close();
	}

	public boolean isClosed() throws SQLException {
		return connection.isClosed();
	}

	public DatabaseMetaData getMetaData() throws SQLException {
		return connection.getMetaData();
	}

	public void setReadOnly(boolean arg0) throws SQLException {
		connection.setReadOnly(arg0);
	}

	public boolean isReadOnly() throws SQLException {
		return connection.isReadOnly();
	}

	public void setCatalog(String arg0) throws SQLException {
		connection.setCatalog(arg0);
	}

	public String getCatalog() throws SQLException {
		return connection.getCatalog();
	}

	public SQLWarning getWarnings() throws SQLException {
		return connection.getWarnings();
	}

	public void clearWarnings() throws SQLException {
		connection.clearWarnings();
	}

	public Statement createStatement(int arg0, int arg1) throws SQLException {
		return connection.createStatement(arg0, arg1);
	}

	public CallableStatement prepareCall(String arg0, int arg1, int arg2)
			throws SQLException {
		return connection.prepareCall(arg0, arg1, arg2);
	}

	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return connection.getTypeMap();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setTypeMap(java.util.Map)
	 */
	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		connection.setTypeMap(map);
	}

	public void setHoldability(int arg0) throws SQLException {
		connection.setHoldability(arg0);
	}

	public int getHoldability() throws SQLException {
		return connection.getHoldability();
	}

	public Savepoint setSavepoint() throws SQLException {
		return connection.setSavepoint();
	}

	public Savepoint setSavepoint(String arg0) throws SQLException {
		return connection.setSavepoint(arg0);
	}

	public void rollback(Savepoint arg0) throws SQLException {
		connection.rollback();
	}

	public void releaseSavepoint(Savepoint arg0) throws SQLException {
		connection.releaseSavepoint(arg0);
	}

	public Statement createStatement(int arg0, int arg1, int arg2) throws SQLException {
		return connection.createStatement(arg0, arg1, arg2);
	}

	public CallableStatement prepareCall(String arg0, int arg1, int arg2, int arg3) throws SQLException {
		return connection.prepareCall(arg0, arg1, arg2, arg3);
	}

    @Override
    public void setSchema(String schema) throws SQLException {
        connection.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return connection.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        connection.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        connection.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return connection.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> tClass) throws SQLException {
        if (tClass.isInstance(this)) {
            return tClass.cast(this);
        }
        return connection.unwrap(tClass);
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
		return aClass.isInstance(this) || connection.isWrapperFor(aClass);
	}
}
