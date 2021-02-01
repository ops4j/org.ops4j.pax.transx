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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.transx.jdbc.ManagedDataSourceBuilder;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.inject.Inject;
import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.transx.itests.TestConfiguration.mvnBundle;
import static org.ops4j.pax.transx.itests.TestConfiguration.regressionDefaults;

@RunWith(PaxExam.class)
public class AtomikosRecoveryTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

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
                mvnBundle("org.ops4j.pax.transx", "pax-transx-tm-atomikos"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-connector"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-jms"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-jdbc"),
                systemProperty("com.atomikos.icatch.recovery_delay").value("1000")
        );
    }

    @Test
    public void testRecovery() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        XADataSource xaDs = Mockito.mock(XADataSource.class);
        XAConnection xaCon = Mockito.mock(XAConnection.class);
        XAResource xaRes = Mockito.mock(XAResource.class);
        Connection con = Mockito.mock(Connection.class);

        Mockito.when(xaDs.getXAConnection()).thenReturn(xaCon);
        Mockito.when(xaCon.getXAResource()).thenReturn(xaRes);
        Mockito.when(xaCon.getConnection()).thenReturn(con);

        Mockito.when(xaRes.recover(anyInt())).thenAnswer(invocation -> {
            latch.countDown();
            return new Xid[0];
        });
        Mockito.when(con.getAutoCommit()).thenReturn(true);
        Mockito.when(con.isValid(anyInt())).thenReturn(true);

        DataSource ds = ManagedDataSourceBuilder.builder()
                .dataSource(xaDs)
                .transactionManager(tm)
                .name("h2")
                .build();
        assertNotNull(ds);
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        AutoCloseable.class.cast(ds).close();
    }
}
