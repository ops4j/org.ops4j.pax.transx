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
import org.ops4j.pax.transx.jdbc.impl.JDBCDriverMCF;
import org.ops4j.pax.transx.jdbc.impl.LocalDataSourceMCF;
import org.ops4j.pax.transx.jdbc.impl.XADataSourceMCF;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.sql.CommonDataSource;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;

public class ManagedConnectionFactoryFactory {

    public static ManagedConnectionFactory create(String driver, String connectionUrl) throws ResourceException {
        JDBCDriverMCF mcf = new JDBCDriverMCF();
        mcf.setDriver(driver);
        mcf.setConnectionURL(connectionUrl);
        return mcf;
    }

    public static ManagedConnectionFactory create(CommonDataSource dataSource) {
        if (dataSource instanceof XADataSource) {
            return create((XADataSource) dataSource);
        }
        else if (dataSource instanceof ConnectionPoolDataSource) {
            return create((ConnectionPoolDataSource) dataSource);
        }
        else if (dataSource instanceof DataSource) {
            return create((DataSource) dataSource);
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    public static ManagedConnectionFactory create(DataSource dataSource) {
        return new LocalDataSourceMCF(dataSource);
    }

    public static ManagedConnectionFactory create(ConnectionPoolDataSource dataSource) {
        return new ConnectionPoolDataSourceMCF(dataSource);
    }

    public static ManagedConnectionFactory create(XADataSource dataSource) {
        return new XADataSourceMCF(dataSource);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ConnectionManagerFactory.Builder builder = ConnectionManagerFactory.builder();
        private CommonDataSource dataSource;
        private ExceptionSorter exceptionSorter;
        private ManagedConnectionFactory managedConnectionFactory;

        private Builder() {
        }

        public Builder name(String name) {
            builder.name(name);
            return this;
        }

        public Builder dataSource(CommonDataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder exceptionSorter(ExceptionSorter exceptionSorter) {
            this.exceptionSorter = exceptionSorter;
            return this;
        }

        public Builder transaction(ConnectionManagerFactory.TransactionSupportLevel tx) {
            builder.transaction(tx);
            return this;
        }

        public Builder transactionManager(TransactionManager transactionManager) {
            builder.transactionManager(transactionManager);
            return this;
        }

        public Builder pooling(boolean pooling) {
            builder.pooling(pooling);
            return this;
        }

        public Builder partition(ConnectionManagerFactory.Partition partition) {
            builder.partition(partition);
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
                ((AbstractManagedConnectionFactory) managedConnectionFactory).setExceptionSorter(exceptionSorter);
            }
            builder.managedConnectionFactory(managedConnectionFactory);
            ConnectionManager cm = builder.build();
            return (DataSource) managedConnectionFactory.createConnectionFactory(cm);
        }

    }
}
