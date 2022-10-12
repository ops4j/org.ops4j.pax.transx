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
package org.ops4j.pax.transx.itests;

import java.sql.Connection;
import java.util.Properties;
import javax.inject.Inject;
import javax.resource.spi.TransactionSupport;
import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.util.Filter;
import org.ops4j.pax.transx.jdbc.ManagedDataSourceBuilder;
import org.ops4j.pax.transx.log4j2.extra.AssertionAppender;
import org.ops4j.pax.transx.tm.TransactionManager;
import org.osgi.service.jdbc.DataSourceFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class GeronimoTest extends AbstractControlledTestBase {

    @Inject
    @Filter(value = "(osgi.jdbc.driver.name=H2 JDBC Driver)")
    private DataSourceFactory dsf;

    @Inject
    private TransactionManager tm;

    @Inject
    private javax.transaction.TransactionManager tmgr;

    @Configuration
    public Option[] config() throws Exception {
        return combine(baseConfigure(),
                mavenBundle("javax.transaction", "javax.transaction-api").versionAsInProject(),
                mavenBundle("javax.interceptor", "javax.interceptor-api").versionAsInProject(),
                mavenBundle("jakarta.el", "jakarta.el-api").versionAsInProject(),
                mavenBundle("javax.enterprise", "cdi-api").versionAsInProject(),
                jcaApiBundle(),
                mavenBundle("javax.jms", "javax.jms-api").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-tm-api").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-tm-geronimo").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-connector").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-jms").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-jdbc").versionAsInProject(),
                mavenBundle("org.osgi", "org.osgi.service.jdbc").versionAsInProject(),
                mavenBundle("com.h2database", "h2").versionAsInProject()
        );
    }

    @Test
    public void createLocaTxJdbcResource() throws Exception {
        Properties jdbc = new Properties();
        jdbc.setProperty("url", "jdbc:h2:mem:test");
        jdbc.setProperty("user", "sa");
        jdbc.setProperty("password", "");
        DataSource localTxDs = dsf.createDataSource(jdbc);

        DataSource ds = ManagedDataSourceBuilder.builder()
                .dataSource(localTxDs)
                .transactionManager(tm)
                .transaction(TransactionSupport.TransactionSupportLevel.LocalTransaction)
                .name("h2")
                .build();
        assertNotNull(ds);

        ((AutoCloseable) ds).close();
    }

    @Test
    public void createJdbcResource() throws Exception {
        Properties jdbc = new Properties();
        jdbc.setProperty("url", "jdbc:h2:mem:test");
        jdbc.setProperty("user", "sa");
        jdbc.setProperty("password", "");
        XADataSource xaDs = dsf.createXADataSource(jdbc);

        DataSource ds = ManagedDataSourceBuilder.builder()
                .dataSource(xaDs)
                .transactionManager(tm)
                .name("h2")
                .build();
        assertNotNull(ds);

        ((AutoCloseable) ds).close();
    }

    @Test
    public void roundtripWithSingleResource() throws Exception {
        Properties jdbc = new Properties();
        jdbc.setProperty("url", "jdbc:h2:mem:test");
        jdbc.setProperty("user", "sa");
        jdbc.setProperty("password", "");

        DataSource localTxDs = dsf.createDataSource(jdbc);

        DataSource ds = ManagedDataSourceBuilder.builder()
                .dataSource(localTxDs)
                .transactionManager(tm)
                .transaction(TransactionSupport.TransactionSupportLevel.LocalTransaction)
                .name("h2")
                .build();
        assertNotNull(ds);

        tmgr.begin();
        try (Connection conn = ds.getConnection()) {
        }
        tmgr.commit();

        ((AutoCloseable) ds).close();
    }

    @Test
    public void roundtripWith2Resources() throws Exception {
        AssertionAppender.startCapture();

        Properties jdbc = new Properties();
        jdbc.setProperty("url", "jdbc:h2:mem:test");
        jdbc.setProperty("user", "sa");
        jdbc.setProperty("password", "");

        XADataSource xaDs1 = dsf.createXADataSource(jdbc);

        DataSource ds1 = ManagedDataSourceBuilder.builder()
                .dataSource(xaDs1)
                .transactionManager(tm)
                .name("h2")
                .build();
        assertNotNull(ds1);

        XADataSource xaDs2 = dsf.createXADataSource(jdbc);

        DataSource ds2 = ManagedDataSourceBuilder.builder()
                .dataSource(xaDs2)
                .transactionManager(tm)
                .name("h2")
                .build();
        assertNotNull(ds2);

        tmgr.begin();
        try (Connection conn1 = ds1.getConnection(); Connection conn2 = ds2.getConnection()) {
        }
        tmgr.commit();

        assertTrue(AssertionAppender.findText("Recover called on XAResource h2"));
        assertTrue(AssertionAppender.findText("Start called on XAResource h2"));
        assertTrue(AssertionAppender.findText("End called on XAResource h2"));
        assertTrue(AssertionAppender.findText("Prepare called on XAResource h2"));
        assertTrue(AssertionAppender.findText("Commit called on XAResource h2"));

        assertFalse(AssertionAppender.findText(
                "Please correct the integration and supply a NamedXAResource",
                "Cannot log transactions ",
                " is not a NamedXAResource."));

        ((AutoCloseable) ds1).close();
        ((AutoCloseable) ds2).close();

        AssertionAppender.stopCapture();
    }

}
