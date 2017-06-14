/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ops4j.pax.transx.jdbc;

import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.transx.connector.ConnectionManagerFactory;
import org.ops4j.pax.transx.jdbc.impl.XADataSourceMCF;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.ops4j.pax.transx.connector.ConnectionManagerFactory.partitionedPool;
import static org.ops4j.pax.transx.connector.ConnectionManagerFactory.xaTransactions;

public class H2Test {

    GeronimoTransactionManager tm;

    @Before
    public void setUp() throws Exception {
        tm = new GeronimoTransactionManager();
    }

    @Test
    public void testContextWithXaTx() throws Exception {
        DataSource ds = wrap(createH2DataSource());

        tm.begin();
        try (Connection con = ds.getConnection()) {
            Statement st = con.createStatement();
            st.execute("CREATE TABLE USER (ID INT, NAME VARCHAR(50));");
        }
        tm.commit();

        tm.begin();
        try (Connection con = ds.getConnection()) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM USER");
            Statement st2 = rs.getStatement();
            assertSame(st, st2);
        }
        tm.commit();

        try (Connection con = ds.getConnection()) {
            DatabaseMetaData dmd = con.getMetaData();
            ResultSet rs = dmd.getSchemas();
            assertNull(rs.getStatement());
            Connection con2 = dmd.getConnection();
            assertSame(con, con2);
        }
    }


    private DataSource wrap(XADataSource xaDs) throws ResourceException {
        ManagedConnectionFactory mcf = ManagedConnectionFactoryFactory.create(xaDs);
        ((XADataSourceMCF) mcf).setUserName("sa");
        ((XADataSourceMCF) mcf).setPassword("");
        ConnectionManager cm = ConnectionManagerFactory.create(
                xaTransactions(true, false),
                partitionedPool(8, 1, 5000, 15, true, false, false, true, false),
                null,
                tm,
                tm,
                mcf,
                "h2invm",
                null
        );
        return (DataSource) mcf.createConnectionFactory(cm);
    }

    private XADataSource createH2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
