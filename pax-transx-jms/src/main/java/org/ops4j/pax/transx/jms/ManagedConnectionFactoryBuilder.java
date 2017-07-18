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
import java.time.Duration;

public class ManagedConnectionFactoryBuilder {

    public static ManagedConnectionFactoryBuilder builder() {
        return new ManagedConnectionFactoryBuilder();
    }

    private ConnectionManagerFactory.Builder builder = ConnectionManagerFactory.builder();
    private ConnectionFactory connectionFactory;
    private XAConnectionFactory xaConnectionFactory;
    private ExceptionSorter exceptionSorter;
    private String userName;
    private String password;
    private String clientID;
    private ManagedConnectionFactory managedConnectionFactory;

    private ManagedConnectionFactoryBuilder() {
    }

    public ManagedConnectionFactoryBuilder name(String name) {
        builder.name(name);
        return this;
    }

    public ManagedConnectionFactoryBuilder connectionFactory(ConnectionFactory connectionFactory, XAConnectionFactory xaConnectionFactory) {
        this.connectionFactory = connectionFactory;
        this.xaConnectionFactory = xaConnectionFactory;
        return this;
    }

    public ManagedConnectionFactoryBuilder userName(String userName) {
        this.userName = userName;
        return this;
    }

    public ManagedConnectionFactoryBuilder password(String password) {
        this.password = password;
        return this;
    }

    public ManagedConnectionFactoryBuilder clientID(String clientID) {
        this.clientID = clientID;
        return this;
    }

    public ManagedConnectionFactoryBuilder exceptionSorter(ExceptionSorter exceptionSorter) {
        this.exceptionSorter = exceptionSorter;
        return this;
    }

    public ManagedConnectionFactoryBuilder transaction(ConnectionManagerFactory.TransactionSupportLevel tx) {
        builder.transaction(tx);
        return this;
    }

    public ManagedConnectionFactoryBuilder transactionManager(TransactionManager transactionManager) {
        builder.transactionManager(transactionManager);
        return this;
    }

    public ManagedConnectionFactoryBuilder pooling(boolean pooling) {
        builder.pooling(pooling);
        return this;
    }

    public ManagedConnectionFactoryBuilder partition(ConnectionManagerFactory.Partition partition) {
        builder.partition(partition);
        return this;
    }

    public ManagedConnectionFactoryBuilder minSize(int minSize) {
        builder.minSize(minSize);
        return this;
    }

    public ManagedConnectionFactoryBuilder maxSize(int maxSize) {
        builder.maxSize(maxSize);
        return this;
    }

    public ManagedConnectionFactoryBuilder maxIdle(Duration maxIdle) {
        builder.maxIdle(maxIdle);
        return this;
    }

    public ManagedConnectionFactoryBuilder maxWait(Duration maxWait) {
        builder.maxWait(maxWait);
        return this;
    }

    public ManagedConnectionFactoryBuilder backgroundValidation(boolean backgroundValidation) {
        builder.backgroundValidation(backgroundValidation);
        return this;
    }

    public ManagedConnectionFactoryBuilder backgroundValidationPeriod(Duration backgroundValidationPeriod) {
        builder.backgroundValidationPeriod(backgroundValidationPeriod);
        return this;
    }

    public ManagedConnectionFactoryBuilder validateOnMatch(boolean validateOnMatch) {
        builder.validateOnMatch(validateOnMatch);
        return this;
    }

    public ManagedConnectionFactoryBuilder allConnectionsEqual(boolean allConnectionsEqual) {
        builder.allConnectionsEqual(allConnectionsEqual);
        return this;
    }

    public ConnectionFactory build() throws Exception {
        if (connectionFactory == null) {
            throw new NullPointerException("dataSource must be set");
        }
        if (managedConnectionFactory == null) {
            ExceptionSorter es = exceptionSorter != null ? exceptionSorter : new NoExceptionsAreFatalSorter();
            ManagedConnectionFactoryImpl mcf = new ManagedConnectionFactoryImpl(connectionFactory, xaConnectionFactory, es);
            mcf.setUserName(userName);
            mcf.setPassword(password);
            mcf.setClientID(clientID);
            managedConnectionFactory = mcf;
        }
        builder.managedConnectionFactory(managedConnectionFactory);
        ConnectionManager cm = builder.build();
        return (ConnectionFactory) managedConnectionFactory.createConnectionFactory(cm);
    }

}
