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

import java.util.Properties;
import javax.inject.Inject;
import javax.resource.spi.TransactionSupport;
import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.util.Filter;
import org.ops4j.pax.transx.jdbc.ManagedDataSourceBuilder;
import org.ops4j.pax.transx.tm.TransactionManager;
import org.osgi.service.jdbc.DataSourceFactory;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class AtomikosTest extends AbstractControlledTestBase {

    @Inject
    @Filter(value = "(osgi.jdbc.driver.name=H2 JDBC Driver)")
    private DataSourceFactory dsf;

    @Inject
    private TransactionManager tm;

    @Configuration
    public Option[] config() throws Exception {
        return combine(baseConfigure(),
                mavenBundle("javax.transaction", "javax.transaction-api").versionAsInProject(),
                mavenBundle("javax.interceptor", "javax.interceptor-api").versionAsInProject(),
                mavenBundle("javax.el", "javax.el-api").versionAsInProject(),
                mavenBundle("javax.enterprise", "cdi-api").versionAsInProject(),
                jcaApiBundle(),
                mavenBundle("javax.jms", "javax.jms-api").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-tm-api").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-tm-atomikos").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-connector").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-jms").versionAsInProject(),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-jdbc").versionAsInProject(),
                mavenBundle("org.osgi", "org.osgi.service.jdbc").versionAsInProject(),
                mavenBundle("com.h2database", "h2").versionAsInProject(),
                systemProperty("com.atomikos.icatch.recovery_delay").value("1000")
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
        Assert.assertNotNull(ds);

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
        Assert.assertNotNull(ds);

        ((AutoCloseable) ds).close();
    }

}
