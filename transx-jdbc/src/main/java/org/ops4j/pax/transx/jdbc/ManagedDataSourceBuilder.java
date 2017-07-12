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
import org.ops4j.pax.transx.connector.ConnectionManagerFactory;
import org.ops4j.pax.transx.jdbc.impl.ConnectionPoolDataSourceMCF;
import org.ops4j.pax.transx.jdbc.impl.LocalDataSourceMCF;
import org.ops4j.pax.transx.jdbc.impl.XADataSourceMCF;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.resource.spi.ConnectionManager;
import javax.sql.CommonDataSource;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.time.Duration;

public class ManagedDataSourceBuilder {

    public static ManagedDataSourceBuilder builder() {
        return new ManagedDataSourceBuilder();
    }

    private ConnectionManagerFactory.Builder builder = ConnectionManagerFactory.builder();
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

    public ManagedDataSourceBuilder transaction(ConnectionManagerFactory.TransactionSupportLevel tx) {
        builder.transaction(tx);
        return this;
    }

    public ManagedDataSourceBuilder transactionManager(TransactionManager transactionManager) {
        builder.transactionManager(transactionManager);
        return this;
    }

    public ManagedDataSourceBuilder pooling(boolean pooling) {
        builder.pooling(pooling);
        return this;
    }

    public ManagedDataSourceBuilder partition(ConnectionManagerFactory.Partition partition) {
        builder.partition(partition);
        return this;
    }

    public ManagedDataSourceBuilder minSize(int minSize) {
        builder.minSize(minSize);
        return this;
    }

    public ManagedDataSourceBuilder maxSize(int maxSize) {
        builder.maxSize(maxSize);
        return this;
    }

    public ManagedDataSourceBuilder maxIdle(Duration maxIdle) {
        builder.maxIdle(maxIdle);
        return this;
    }

    public ManagedDataSourceBuilder maxWait(Duration maxWait) {
        builder.maxWait(maxWait);
        return this;
    }

    public ManagedDataSourceBuilder backgroundValidation(boolean backgroundValidation) {
        builder.backgroundValidation(backgroundValidation);
        return this;
    }

    public ManagedDataSourceBuilder backgroundValidationPeriod(Duration backgroundValidationPeriod) {
        builder.backgroundValidationPeriod(backgroundValidationPeriod);
        return this;
    }

    public ManagedDataSourceBuilder validateOnMatch(boolean validateOnMatch) {
        builder.validateOnMatch(validateOnMatch);
        return this;
    }

    public ManagedDataSourceBuilder allConnectionsEqual(boolean allConnectionsEqual) {
        builder.allConnectionsEqual(allConnectionsEqual);
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
