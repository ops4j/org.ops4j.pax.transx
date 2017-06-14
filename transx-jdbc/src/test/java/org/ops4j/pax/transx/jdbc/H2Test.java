package org.ops4j.pax.transx.jdbc;

import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.transx.connector.ConnectionManagerFactory;
import org.ops4j.pax.transx.connector.PartitionedPool;
import org.ops4j.pax.transx.connector.XATransactions;
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

public class H2Test {

    int poolMaxSize = 8;
    int poolMinSize = 1;
    int connectionMaxWaitMilliseconds = 5000;
    int connectionMaxIdleMinutes = 15;
    boolean allConnectionsEqual = true;
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
                new XATransactions(true, false),
                new PartitionedPool(poolMaxSize, poolMinSize, connectionMaxWaitMilliseconds, connectionMaxIdleMinutes, allConnectionsEqual, !allConnectionsEqual, false, true, false),
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
