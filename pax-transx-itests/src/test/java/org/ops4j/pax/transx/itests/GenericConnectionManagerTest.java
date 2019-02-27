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

import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Test;
import org.ops4j.pax.transx.jdbc.ManagedDataSourceBuilder;
import org.ops4j.pax.transx.tm.TransactionManager;
import org.ops4j.pax.transx.tm.impl.geronimo.TransactionManagerWrapper;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.util.Set;

import org.ops4j.pax.transx.connector.impl.PoolConfigMXBean;
import org.ops4j.pax.transx.connector.impl.PoolMXBean;

public class GenericConnectionManagerTest {

    @Test
    public void testMbean() throws Exception {
        TransactionManager tm = new TransactionManagerWrapper(new GeronimoTransactionManager());

        JdbcDataSource xaDs = new JdbcDataSource();
        xaDs.setURL("jdbc:h2:mem:test");
        xaDs.setUser("sa");
        xaDs.setPassword("");
        DataSource ds = ManagedDataSourceBuilder.builder()
                .dataSource(xaDs)
                .transactionManager(tm)
                .name("h2")
                .build();

        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

        try (Connection conn = ds.getConnection()) {
            ObjectName queryConfig = new ObjectName("org.ops4j.pax.transx:type=Pool,*");
            Set<ObjectName> queryResult = mBeanServer.queryNames(queryConfig, null);
            Assert.assertEquals(2, queryResult.size());
            for (ObjectName on : queryResult) {
                if (mBeanServer.isInstanceOf(on, PoolConfigMXBean.class.getName())) {
                    Assert.assertEquals(Integer.valueOf(10), (Integer) mBeanServer.getAttribute(on, "MinIdle"));
                    Assert.assertEquals(Integer.valueOf(10), (Integer) mBeanServer.getAttribute(on, "MaxPoolSize"));
                } else if (mBeanServer.isInstanceOf(on, PoolMXBean.class.getName())) {
                    Assert.assertEquals(Integer.valueOf(1), (Integer) mBeanServer.getAttribute(on, "ActiveConnections"));
                }
            }
        }
    }
}
