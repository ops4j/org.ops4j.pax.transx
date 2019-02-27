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
package org.ops4j.pax.transx.itests;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.util.Filter;
import org.ops4j.pax.transx.jdbc.ManagedDataSourceBuilder;
import org.ops4j.pax.transx.tm.TransactionManager;
import org.osgi.service.jdbc.DataSourceFactory;

import javax.inject.Inject;
import javax.resource.spi.TransactionSupport;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.Properties;

import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.transx.itests.TestConfiguration.mvnBundle;
import static org.ops4j.pax.transx.itests.TestConfiguration.regressionDefaults;

@RunWith(PaxExam.class)
public class NarayanaTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Inject
    @Filter(value = "(osgi.jdbc.driver.name=H2 JDBC Driver)")
    private DataSourceFactory dsf;

    @Inject
    private TransactionManager tm;

    @Configuration
    public Option[] config() throws Exception {
        return options(
                regressionDefaults(),
                mvnBundle("org.apache.geronimo.specs", "geronimo-jta_1.1_spec"),
                mvnBundle("org.apache.geronimo.specs", "geronimo-j2ee-connector_1.6_spec"),
                mvnBundle("javax.jms", "javax.jms-api"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-tm-api"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-tm-narayana"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-connector"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-jms"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-jdbc"),
                mvnBundle("com.h2database", "h2"),
                systemProperty("com.arjuna.ats.arjuna.recovery.periodicRecoveryInitilizationOffset").value("1")
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

        AutoCloseable.class.cast(ds).close();
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

        AutoCloseable.class.cast(ds).close();
    }
}
