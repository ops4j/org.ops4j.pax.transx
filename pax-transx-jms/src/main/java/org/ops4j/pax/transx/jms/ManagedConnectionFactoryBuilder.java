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
import org.ops4j.pax.transx.connector.ConnectionManagerBuilder;
import org.ops4j.pax.transx.jms.impl.ManagedConnectionFactoryImpl;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import java.util.concurrent.TimeUnit;

public class ManagedConnectionFactoryBuilder {

    public static ManagedConnectionFactoryBuilder builder() {
        return new ManagedConnectionFactoryBuilder();
    }

    private ConnectionManagerBuilder builder = ConnectionManagerBuilder.builder();
    private ConnectionFactory connectionFactory;
    private XAConnectionFactory xaConnectionFactory;
    private ExceptionSorter exceptionSorter;
    private String userName;
    private String password;
    private String clientID;
    private ManagedConnectionFactory managedConnectionFactory;

    private ManagedConnectionFactoryBuilder() {
        builder.aliveBypassWindow(Long.MAX_VALUE);
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

    public ManagedConnectionFactoryBuilder transaction(TransactionSupportLevel tx) {
        builder.transaction(tx);
        return this;
    }

    public ManagedConnectionFactoryBuilder transactionManager(TransactionManager transactionManager) {
        builder.transactionManager(transactionManager);
        return this;
    }

    public ManagedConnectionFactoryBuilder minIdle(int minSize) {
        builder.minIdle(minSize);
        return this;
    }

    public ManagedConnectionFactoryBuilder maxPoolSize(int maxPoolSize) {
        builder.maxPoolSize(maxPoolSize);
        return this;
    }

    public ManagedConnectionFactoryBuilder aliveBypassWindow(long aliveBypassWindowMs) {
        builder.aliveBypassWindow(aliveBypassWindowMs);
        return this;
    }

    public ManagedConnectionFactoryBuilder aliveBypassWindow(long aliveBypassWindow, TimeUnit unit) {
        builder.aliveBypassWindow(unit.toMillis(aliveBypassWindow));
        return this;
    }

    public ManagedConnectionFactoryBuilder houseKeepingPeriod(long houseKeepingPeriodMs) {
        builder.houseKeepingPeriod(houseKeepingPeriodMs);
        return this;
    }

    public ManagedConnectionFactoryBuilder houseKeepingPeriod(long houseKeepingPeriod, TimeUnit unit) {
        builder.houseKeepingPeriod(unit.toMillis(houseKeepingPeriod));
        return this;
    }

    public ManagedConnectionFactoryBuilder connectionTimeout(long connectionTimeoutMs) {
        builder.connectionTimeout(connectionTimeoutMs);
        return this;
    }

    public ManagedConnectionFactoryBuilder connectionTimeout(long connectionTimeout, TimeUnit unit) {
        builder.connectionTimeout(unit.toMillis(connectionTimeout));
        return this;
    }

    public ManagedConnectionFactoryBuilder idleTimeout(long idleTimeoutMs) {
        builder.idleTimeout(idleTimeoutMs);
        return this;
    }

    public ManagedConnectionFactoryBuilder idleTimeout(long idleTimeout, TimeUnit unit) {
        builder.idleTimeout(unit.toMillis(idleTimeout));
        return this;
    }

    public ManagedConnectionFactoryBuilder maxLifetime(long maxLifetimeMs) {
        builder.maxLifetime(maxLifetimeMs);
        return this;
    }

    public ManagedConnectionFactoryBuilder maxLifetime(long maxLifetime, TimeUnit unit) {
        builder.maxLifetime(unit.toMillis(maxLifetime));
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
