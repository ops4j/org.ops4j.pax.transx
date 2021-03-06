/*
 * Copyright 2021 OPS4J.
 *
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
 */
package org.ops4j.pax.transx.jdbc.impl;

import java.util.Arrays;
import java.util.Objects;

public class PreparedStatementKey {

    private static final int PREPARED_STMT_FORMAT_1 = 1;
    private static final int PREPARED_STMT_FORMAT_2 = 2;
    private static final int PREPARED_STMT_FORMAT_3 = 3;
    private static final int PREPARED_STMT_FORMAT_4 = 4;
    private static final int PREPARED_STMT_FORMAT_5 = 5;
    private static final int PREPARED_STMT_FORMAT_6 = 6;

    private static final int NULL_INT_ARRAY[] = { 0 };
    private static final String NULL_STRING_ARRAY[] = { "" };

    private final ConnectionWrapper c;
    private final String sql;
    private final int stmtFormat;
    private final int parm0;
    private final int parm1;
    private final int parm2;
    private final int columnIndexes[];
    private final String columnNames[];
    private PreparedStatementWrapper psw = null;

    public PreparedStatementKey(ConnectionWrapper c, String sql) {
        this(c, sql, PREPARED_STMT_FORMAT_1,
                0, 0, 0,
                NULL_INT_ARRAY, NULL_STRING_ARRAY);
    }

    public PreparedStatementKey(ConnectionWrapper c, String sql, int autoGeneratedKeys) {
        this(c, sql, PREPARED_STMT_FORMAT_2,
                autoGeneratedKeys, 0, 0,
                NULL_INT_ARRAY, NULL_STRING_ARRAY);
    }

    public PreparedStatementKey(ConnectionWrapper c, String sql, int resultSetType, int resultSetConcurrency) {
        this(c, sql, PREPARED_STMT_FORMAT_3,
                resultSetType, resultSetConcurrency, 0,
                NULL_INT_ARRAY, NULL_STRING_ARRAY);
    }

    public PreparedStatementKey(ConnectionWrapper c, String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        this(c, sql, PREPARED_STMT_FORMAT_4,
                resultSetType, resultSetConcurrency, resultSetHoldability,
                NULL_INT_ARRAY, NULL_STRING_ARRAY);
    }

    public PreparedStatementKey(ConnectionWrapper c, String sql, int columnIndexes[]) {
        this(c, sql, PREPARED_STMT_FORMAT_5,
                0, 0, 0,
                columnIndexes.clone(), NULL_STRING_ARRAY);
    }

    public PreparedStatementKey(ConnectionWrapper c, String sql, String columnNames[]) {
        this(c, sql, PREPARED_STMT_FORMAT_6,
                0, 0, 0,
                NULL_INT_ARRAY, columnNames.clone());
    }

    public PreparedStatementKey(ConnectionWrapper c, String sql, int stmtFormat,
                                int parm0, int parm1, int parm2,
                                int[] columnIndexes, String[] columnNames) {
        this.c = c;
        this.sql = sql;
        this.stmtFormat = stmtFormat;
        this.parm0 = parm0;
        this.parm1 = parm1;
        this.parm2 = parm2;
        this.columnIndexes = columnIndexes;
        this.columnNames = columnNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PreparedStatementKey that = (PreparedStatementKey) o;
        return stmtFormat == that.stmtFormat &&
                parm0 == that.parm0 &&
                parm1 == that.parm1 &&
                parm2 == that.parm2 &&
                Objects.equals(sql, that.sql) &&
                Arrays.equals(columnIndexes, that.columnIndexes) &&
                Arrays.equals(columnNames, that.columnNames);
    }

    public String getSql() {
        return sql;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stmtFormat, sql, parm0, parm1, parm2, columnIndexes, columnNames);
    }

    public void setPreparedStatementWrapper(PreparedStatementWrapper psw) {
        this.psw = psw;
    }

    public PreparedStatementWrapper getPreparedStatementWrapper() {
        return psw;
    }
}
