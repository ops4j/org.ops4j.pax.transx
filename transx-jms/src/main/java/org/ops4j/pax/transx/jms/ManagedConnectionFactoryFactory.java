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
package org.ops4j.pax.transx.jms;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.NoExceptionsAreFatalSorter;
import org.ops4j.pax.transx.connector.ConnectionManagerFactory;
import org.ops4j.pax.transx.jms.impl.ManagedConnectionFactoryImpl;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;

public class ManagedConnectionFactoryFactory {

    public static ManagedConnectionFactory create(ConnectionFactory cf, XAConnectionFactory xaCf) {
        return create(cf, xaCf, new NoExceptionsAreFatalSorter());
    }

    public static ManagedConnectionFactory create(ConnectionFactory cf, XAConnectionFactory xaCf, ExceptionSorter exceptionSorter) {
        return new ManagedConnectionFactoryImpl(cf, xaCf, exceptionSorter);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ConnectionManagerFactory.Builder builder = ConnectionManagerFactory.builder();
        private ConnectionFactory connectionFactory;
        private XAConnectionFactory xaConnectionFactory;
        private ExceptionSorter exceptionSorter;
        private ManagedConnectionFactory managedConnectionFactory;

        private Builder() {
        }

        public Builder name(String name) {
            builder.name(name);
            return this;
        }

        public Builder connectionFactory(ConnectionFactory connectionFactory, XAConnectionFactory xaConnectionFactory) {
            this.connectionFactory = connectionFactory;
            this.xaConnectionFactory = xaConnectionFactory;
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

        public ConnectionFactory build() throws Exception {
            if (connectionFactory == null) {
                throw new NullPointerException("dataSource must be set");
            }
            if (managedConnectionFactory == null) {
                ExceptionSorter es = exceptionSorter != null ? exceptionSorter : new NoExceptionsAreFatalSorter();
                managedConnectionFactory = create(connectionFactory, xaConnectionFactory, es);
            }
            builder.managedConnectionFactory(managedConnectionFactory);
            ConnectionManager cm = builder.build();
            return (ConnectionFactory) managedConnectionFactory.createConnectionFactory(cm);
        }

    }
}
