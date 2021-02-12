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
package org.ops4j.pax.transx.connector;

import java.util.logging.Logger;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.TransactionSupport;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;

import org.ops4j.pax.transx.connector.impl.GenericConnectionManager;
import org.ops4j.pax.transx.tm.TransactionManager;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ConnectionManagerBuilder {

    private static final long MAX_LIFETIME = MINUTES.toMillis(30);
    private static final long IDLE_TIMEOUT = MINUTES.toMillis(10);
    private static final long CONNECTION_TIMEOUT = SECONDS.toMillis(30);
    private static final long HOUSE_KEEPING_PERIOD = SECONDS.toMillis(30);
    private static final long ALIVE_BYPASS_WINDOW = MILLISECONDS.toMillis(500);
    private static final int DEFAULT_POOL_SIZE = 10;

    private static final Logger LOG = Logger.getLogger(ConnectionManagerBuilder.class.getName());

    private ManagedConnectionFactory managedConnectionFactory;
    private TransactionManager transactionManager;
    private TransactionSupportLevel transaction;
    private SubjectSource subjectSource;
    private String name;

    private int minIdle = -1;
    private int maxPoolSize = -1;
    private long connectionTimeout = CONNECTION_TIMEOUT;
    private long idleTimeout = IDLE_TIMEOUT;
    private long maxLifetime = MAX_LIFETIME;
    private long aliveBypassWindow = ALIVE_BYPASS_WINDOW;
    private long houseKeepingPeriod =  HOUSE_KEEPING_PERIOD;

    private ConnectionManagerBuilder() {
    }

    public static ConnectionManagerBuilder builder() {
        return new ConnectionManagerBuilder();
    }

    public ConnectionManagerBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ConnectionManagerBuilder managedConnectionFactory(ManagedConnectionFactory managedConnectionFactory) {
        this.managedConnectionFactory = managedConnectionFactory;
        return this;
    }

    public ConnectionManagerBuilder transaction(TransactionSupportLevel transaction) {
        this.transaction = transaction;
        return this;
    }

    public ConnectionManagerBuilder transactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        return this;
    }

    public ConnectionManagerBuilder minIdle(int minSize) {
        this.minIdle = minSize;
        return this;
    }

    public ConnectionManagerBuilder maxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    public ConnectionManagerBuilder aliveBypassWindow(long aliveBypassWindow) {
        this.aliveBypassWindow = aliveBypassWindow;
        return this;
    }

    public ConnectionManagerBuilder houseKeepingPeriod(long houseKeepingPeriod) {
        this.houseKeepingPeriod = houseKeepingPeriod;
        return this;
    }

    public ConnectionManagerBuilder connectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public ConnectionManagerBuilder idleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    public ConnectionManagerBuilder maxLifetime(long maxLifetime) {
        this.maxLifetime = maxLifetime;
        return this;
    }

    public ConnectionManager build() throws Exception {
        if (transactionManager == null && transaction != TransactionSupportLevel.NoTransaction) {
            throw new IllegalArgumentException("transactionManager must be set");
        }
        if (managedConnectionFactory == null) {
            throw new IllegalArgumentException("managedConnectionFactory must be set");
        }
        // Transaction support
        if (transaction == null && managedConnectionFactory instanceof TransactionSupport) {
            TransactionSupport ts = TransactionSupport.class.cast(managedConnectionFactory);
            TransactionSupportLevel txSupportLevel = ts.getTransactionSupport();
            if (txSupportLevel != null) {
                transaction = txSupportLevel;
            }
        }
        String name = this.name != null ? this.name : managedConnectionFactory.getClass().getSimpleName();
        String poolName = "TransxPool-" + generatePoolNumber() + "-" +  name;
        if (maxLifetime != 0 && maxLifetime < SECONDS.toMillis(30)) {
            LOG.warning(() -> poolName + " - maxLifetime is less than 30000ms, setting to default " + MAX_LIFETIME + " ms.");
            maxLifetime = MAX_LIFETIME;
        }
        if (idleTimeout + SECONDS.toMillis(1) > maxLifetime && maxLifetime > 0) {
            LOG.warning(() -> poolName + " - idleTimeout is close to or more than maxLifetime, disabling it.");
            idleTimeout = 0;
        }
        if (idleTimeout != 0 && idleTimeout < SECONDS.toMillis(10)) {
            LOG.warning(() -> poolName + " - idleTimeout is less than 10000ms, setting to default " + IDLE_TIMEOUT + "ms.");
            idleTimeout = IDLE_TIMEOUT;
        }
        if (connectionTimeout < 250) {
            LOG.warning(() -> poolName + " - connectionTimeout is less than 250ms, setting to " + CONNECTION_TIMEOUT + "ms.");
            connectionTimeout = CONNECTION_TIMEOUT;
        }
        if (maxPoolSize < 1) {
            maxPoolSize = (minIdle <= 0) ? DEFAULT_POOL_SIZE : minIdle;
        }
        if (minIdle < 0 || minIdle > maxPoolSize) {
            minIdle = maxPoolSize;
        }

        return new GenericConnectionManager(
                transactionManager,
                transaction,
                subjectSource,
                getClass().getClassLoader(),
                managedConnectionFactory,
                name,
                poolName,
                minIdle,
                maxPoolSize,
                connectionTimeout,
                idleTimeout,
                maxLifetime,
                aliveBypassWindow,
                houseKeepingPeriod
        );
    }

    private static int generatePoolNumber() {
        // Pool number is global to the VM to avoid overlapping pool numbers in classloader scoped environments
        synchronized (System.getProperties()) {
            final int next = Integer.getInteger("org.ops4j.pax.transx.pool_number", 0) + 1;
            System.setProperty("org.ops4j.pax.transx.pool_number", String.valueOf(next));
            return next;
        }
    }

}
