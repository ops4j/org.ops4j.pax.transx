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
package org.ops4j.pax.transx.jdbc;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connector.ConnectionManagerBuilder;
import org.ops4j.pax.transx.jdbc.impl.ConnectionPoolDataSourceMCF;
import org.ops4j.pax.transx.jdbc.impl.LocalDataSourceMCF;
import org.ops4j.pax.transx.jdbc.impl.XADataSourceMCF;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.resource.spi.ConnectionManager;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.sql.CommonDataSource;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.concurrent.TimeUnit;

public class ManagedDataSourceBuilder {

    public static ManagedDataSourceBuilder builder() {
        return new ManagedDataSourceBuilder();
    }

    private ConnectionManagerBuilder builder = ConnectionManagerBuilder.builder();
    private CommonDataSource dataSource;
    private ExceptionSorter exceptionSorter;
    private String userName;
    private String password;
    private boolean commitBeforeAutocommit;
    private AbstractManagedConnectionFactory managedConnectionFactory;

    private ManagedDataSourceBuilder() {
    }

    public ManagedDataSourceBuilder name(String name) {
        builder.name(name);
        return this;
    }

    public ManagedDataSourceBuilder dataSource(CommonDataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public ManagedDataSourceBuilder userName(String userName) {
        this.userName = userName;
        return this;
    }

    public ManagedDataSourceBuilder password(String password) {
        this.password = password;
        return this;
    }

    public ManagedDataSourceBuilder commitBeforeAutocommit(boolean commitBeforeAutocommit) {
        this.commitBeforeAutocommit = commitBeforeAutocommit;
        return this;
    }

    public ManagedDataSourceBuilder exceptionSorter(ExceptionSorter exceptionSorter) {
        this.exceptionSorter = exceptionSorter;
        return this;
    }

    public ManagedDataSourceBuilder transaction(TransactionSupportLevel tx) {
        builder.transaction(tx);
        return this;
    }

    public ManagedDataSourceBuilder transactionManager(TransactionManager transactionManager) {
        builder.transactionManager(transactionManager);
        return this;
    }

    public ManagedDataSourceBuilder minIdle(int minSize) {
        builder.minIdle(minSize);
        return this;
    }

    public ManagedDataSourceBuilder maxPoolSize(int maxPoolSize) {
        builder.maxPoolSize(maxPoolSize);
        return this;
    }

    public ManagedDataSourceBuilder aliveBypassWindow(long aliveBypassWindowMs) {
        builder.aliveBypassWindow(aliveBypassWindowMs);
        return this;
    }

    public ManagedDataSourceBuilder aliveBypassWindow(long aliveBypassWindow, TimeUnit unit) {
        builder.aliveBypassWindow(unit.toMillis(aliveBypassWindow));
        return this;
    }

    public ManagedDataSourceBuilder houseKeepingPeriod(long houseKeepingPeriodMs) {
        builder.houseKeepingPeriod(houseKeepingPeriodMs);
        return this;
    }

    public ManagedDataSourceBuilder houseKeepingPeriod(long houseKeepingPeriod, TimeUnit unit) {
        builder.houseKeepingPeriod(unit.toMillis(houseKeepingPeriod));
        return this;
    }

    public ManagedDataSourceBuilder connectionTimeout(long connectionTimeoutMs) {
        builder.connectionTimeout(connectionTimeoutMs);
        return this;
    }

    public ManagedDataSourceBuilder connectionTimeout(long connectionTimeout, TimeUnit unit) {
        builder.connectionTimeout(unit.toMillis(connectionTimeout));
        return this;
    }

    public ManagedDataSourceBuilder idleTimeout(long idleTimeoutMs) {
        builder.idleTimeout(idleTimeoutMs);
        return this;
    }

    public ManagedDataSourceBuilder idleTimeout(long idleTimeout, TimeUnit unit) {
        builder.idleTimeout(unit.toMillis(idleTimeout));
        return this;
    }

    public ManagedDataSourceBuilder maxLifetime(long maxLifetimeMs) {
        builder.maxLifetime(maxLifetimeMs);
        return this;
    }

    public ManagedDataSourceBuilder maxLifetime(long maxLifetime, TimeUnit unit) {
        builder.maxLifetime(unit.toMillis(maxLifetime));
        return this;
    }

    public DataSource build() throws Exception {
        if (dataSource == null) {
            throw new NullPointerException("dataSource must be set");
        }
        if (managedConnectionFactory == null) {
            managedConnectionFactory = create(dataSource);
        }
        if (exceptionSorter != null) {
            managedConnectionFactory.setExceptionSorter(exceptionSorter);
        }
        managedConnectionFactory.setUserName(userName);
        managedConnectionFactory.setPassword(password);
        managedConnectionFactory.setCommitBeforeAutocommit(commitBeforeAutocommit);
        builder.managedConnectionFactory(managedConnectionFactory);
        ConnectionManager cm = builder.build();
        return (DataSource) managedConnectionFactory.createConnectionFactory(cm);
    }

    private static AbstractManagedConnectionFactory create(CommonDataSource dataSource) {
        if (dataSource instanceof XADataSource) {
            return new XADataSourceMCF((XADataSource) dataSource);
        }
        else if (dataSource instanceof ConnectionPoolDataSource) {
            return new ConnectionPoolDataSourceMCF((ConnectionPoolDataSource) dataSource);
        }
        else if (dataSource instanceof DataSource) {
            return new LocalDataSourceMCF((DataSource) dataSource);
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

}
