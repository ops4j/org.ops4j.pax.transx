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
package org.ops4j.pax.transx.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;
import org.ops4j.pax.transx.jdbc.stubs.StubDataSource;

import javax.resource.spi.TransactionSupport;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class Bench {

    @Test
    public void benchmarkTransx() throws Exception {
        System.out.println("TransX Pool");
        warmupTransx();
        for (int i = 0; i < 4; i++) {
            doBenchTransx();
        }
    }

    protected void warmupTransx() throws Exception {
        doBenchTransx();
    }


    protected void doBenchTransx() throws Exception {
        DataSource ds = ManagedDataSourceBuilder.builder()
                .dataSource(new StubDataSource())
                .transaction(TransactionSupport.TransactionSupportLevel.NoTransaction)
                .userName("brettw")
                .password("")
                .minIdle(0)
                .maxPoolSize(32)
                .connectionTimeout(8000)
                .build();
        doBench(ds);
        ((AutoCloseable) ds).close();
    }

    @Test
    public void benchmarkHikari() throws Exception {
        System.out.println("Hikari Pool");
        warmupHikari();
        for (int i = 0; i < 4; i++) {
            doBenchHikari();
        }
    }

    private void warmupHikari() throws Exception {
        doBenchHikari();
    }

    private void doBenchHikari() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setDataSource(new StubDataSource());
        config.setUsername("brettw");
        config.setPassword("");
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(32);
        config.setConnectionTimeout(8000);
        config.setAutoCommit(false);
        DataSource ds = new HikariDataSource(config);
        doBench(ds);
        ((AutoCloseable) ds).close();
    }

    private void doBench(DataSource ds) throws SQLException {
        long t0 = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            doBenchOne(ds);
        }
        long t1 = System.nanoTime();
        System.out.println("Time: " + ((t1 - t0 + 500_000L) / 1_000_000L) + " ms");
    }

    public void doBenchOne(DataSource ds) throws SQLException {
        Connection con = ds.getConnection();
        con.close();
    }
}
