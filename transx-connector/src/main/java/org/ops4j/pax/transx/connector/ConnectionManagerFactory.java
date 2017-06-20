/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ops4j.pax.transx.connector;

import org.ops4j.pax.transx.connector.impl.GenericConnectionManager;
import org.ops4j.pax.transx.connector.pool.NoPool;
import org.ops4j.pax.transx.connector.pool.PartitionedPool;
import org.ops4j.pax.transx.connector.pool.SinglePool;
import org.ops4j.pax.transx.connector.recovery.geronimo.GeronimoConnectionManager;
import org.ops4j.pax.transx.connector.recovery.narayana.NarayanaConnectionManager;
import org.ops4j.pax.transx.connector.transaction.LocalTransactions;
import org.ops4j.pax.transx.connector.transaction.NoTransactions;
import org.ops4j.pax.transx.connector.transaction.XATransactions;

import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ValidatingManagedConnectionFactory;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.time.Duration;

public class ConnectionManagerFactory {

    public enum TransactionSupportLevel {
        None, Local, Xa
    }

    public enum Partition {
        None,
        ByConnectorProperties,
        BySubject
    }

    public static class Builder {
        private final ConnectionManagerFactory connectionManagerFactory = new ConnectionManagerFactory();

        public Builder name(String name) {
            connectionManagerFactory.setName(name);
            return this;
        }

        public Builder managedConnectionFactory(ManagedConnectionFactory managedConnectionFactory) {
            connectionManagerFactory.setManagedConnectionFactory(managedConnectionFactory);
            return this;
        }

        public Builder transaction(TransactionSupportLevel tx) {
            connectionManagerFactory.setTransaction(tx);
            return this;
        }

        public Builder transactionManager(TransactionManager transactionManager, TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
            connectionManagerFactory.setTransactionManager(transactionManager);
            connectionManagerFactory.setTransactionSynchronizationRegistry(transactionSynchronizationRegistry);
            return this;
        }

        public Builder pooling(boolean pooling) {
            connectionManagerFactory.setPooling(pooling);
            return this;
        }

        public Builder partition(Partition partition) {
            connectionManagerFactory.setPartitionStrategy(partition);
            return this;
        }

        public ConnectionManager build() throws Exception {
            connectionManagerFactory.init();
            return connectionManagerFactory.getConnectionManager();
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    private TransactionManager transactionManager;
    private TransactionSynchronizationRegistry transactionSynchronizationRegistry;
    private ManagedConnectionFactory managedConnectionFactory;

    private String name;

    private TransactionSupportLevel transaction;

    private boolean pooling = true;
    private Partition partitionStrategy; //: none, by-subject, by-connector-properties
    private int poolMaxSize = 10;
    private int poolMinSize = 0;
    private boolean allConnectionsEqual = true;
    private Duration connectionMaxWait = Duration.ofSeconds(5);
    private Duration connectionMaxIdle = Duration.ofMinutes(15);

    private Boolean backgroundValidation;
    private Duration validatingPeriod = Duration.ofMinutes(10);
    private Boolean validateOnMatch;

    private SubjectSource subjectSource;

    private GenericConnectionManager connectionManager;

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void init() throws Exception {
        if (transactionManager == null && transaction == TransactionSupportLevel.Xa) {
            throw new IllegalArgumentException("transactionManager must be set");
        }
        if (transactionSynchronizationRegistry == null && transaction == TransactionSupportLevel.Xa) {
            throw new IllegalArgumentException("transactionSynchronizationRegistry must be set");
        }
        if (managedConnectionFactory == null) {
            throw new IllegalArgumentException("managedConnectionFactory must be set");
        }
        if (backgroundValidation == null) {
            backgroundValidation = managedConnectionFactory instanceof ValidatingManagedConnectionFactory;
        }
        if (validateOnMatch == null) {
            validateOnMatch = managedConnectionFactory instanceof ValidatingManagedConnectionFactory;
        }
        // Transaction support
        TransactionSupport transactionSupport = null;
        if (transaction != null) {
            switch (transaction) {
                case None:
                    transactionSupport = NoTransactions.INSTANCE;
                    break;
                case Local:
                    transactionSupport = LocalTransactions.INSTANCE;
                    break;
                case Xa:
                    transactionSupport = XATransactions.INSTANCE;
                    break;
            }
        }
        if (managedConnectionFactory instanceof javax.resource.spi.TransactionSupport) {
            javax.resource.spi.TransactionSupport ts = javax.resource.spi.TransactionSupport.class.cast(managedConnectionFactory);
            javax.resource.spi.TransactionSupport.TransactionSupportLevel txSupportLevel = ts.getTransactionSupport();
            if (txSupportLevel != null) {
                if (txSupportLevel == javax.resource.spi.TransactionSupport.TransactionSupportLevel.NoTransaction) {
                    transactionSupport = NoTransactions.INSTANCE;
                } else if (txSupportLevel == javax.resource.spi.TransactionSupport.TransactionSupportLevel.LocalTransaction) {
                    if (transactionSupport != NoTransactions.INSTANCE) {
                        transactionSupport = LocalTransactions.INSTANCE;
                    }
                } else {
                    if (transactionSupport != NoTransactions.INSTANCE && transactionSupport != LocalTransactions.INSTANCE) {
                        transactionSupport = XATransactions.INSTANCE;
                    }
                }
            }
        }
        if (transactionSupport == null) {
            transactionSupport = NoTransactions.INSTANCE;
        }
        // Pooling support
        PoolingSupport poolingSupport;
        if (!pooling) {
            // No pool
            poolingSupport = new NoPool();
        } else {
            switch (partitionStrategy == null ? Partition.None : partitionStrategy) {
                // unpartitioned pool
                default:
                case None:
                    poolingSupport = new SinglePool(poolMaxSize,
                        poolMinSize,
                        connectionMaxWait,
                        connectionMaxIdle,
                        backgroundValidation,
                        validatingPeriod,
                        validateOnMatch,
                        allConnectionsEqual,
                        !allConnectionsEqual,
                        false);
                    break;
                // partition by connector properties such as username and password on a jdbc connection
                case ByConnectorProperties:
                    poolingSupport = new PartitionedPool(poolMaxSize,
                        poolMinSize,
                        connectionMaxWait,
                        connectionMaxIdle,
                        backgroundValidation,
                        validatingPeriod,
                        validateOnMatch,
                        allConnectionsEqual,
                        !allConnectionsEqual,
                        false,
                        true,
                        false);
                    break;
                // partition by caller subject
                case BySubject:
                    if (subjectSource == null) {
                        throw new IllegalStateException("To use Subject in pooling, you need a SecurityDomain");
                    }
                    poolingSupport = new PartitionedPool(poolMaxSize,
                        poolMinSize,
                        connectionMaxWait,
                        connectionMaxIdle,
                        backgroundValidation,
                        validatingPeriod,
                        validateOnMatch,
                        allConnectionsEqual,
                        !allConnectionsEqual,
                        false,
                        false,
                        true);
                    break;
            }
        }
        if (connectionManager == null) {
            // Instantiate the Connection Manager
            if (transactionSupport.isRecoverable()) {
                if (GeronimoConnectionManager.TMCheck.isGeronimo(transactionManager)) {
                    connectionManager = new GeronimoConnectionManager(
                            transactionSupport,
                            poolingSupport,
                            subjectSource,
                            transactionManager,
                            transactionSynchronizationRegistry,
                            name != null ? name : getClass().getName(),
                            getClass().getClassLoader(),
                            managedConnectionFactory);
                } else if (NarayanaConnectionManager.TMCheck.isNarayana(transactionManager)) {
                    connectionManager = new NarayanaConnectionManager(
                            transactionSupport,
                            poolingSupport,
                            subjectSource,
                            transactionManager,
                            transactionSynchronizationRegistry,
                            name != null ? name : getClass().getName(),
                            getClass().getClassLoader(),
                            managedConnectionFactory);
                }
            }
            connectionManager.doStart();
        }
    }

    public void destroy() throws Exception {
        if (connectionManager != null) {
            connectionManager.doStop();
            connectionManager = null;
        }
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
        return transactionSynchronizationRegistry;
    }

    public void setTransactionSynchronizationRegistry(TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
    }

    public ManagedConnectionFactory getManagedConnectionFactory() {
        return managedConnectionFactory;
    }

    public void setManagedConnectionFactory(ManagedConnectionFactory managedConnectionFactory) {
        this.managedConnectionFactory = managedConnectionFactory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TransactionSupportLevel getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionSupportLevel transaction) {
        this.transaction = transaction;
    }

    public boolean isPooling() {
        return pooling;
    }

    public void setPooling(boolean pooling) {
        this.pooling = pooling;
    }

    public Partition getPartitionStrategy() {
        return partitionStrategy;
    }

    public void setPartitionStrategy(Partition partitionStrategy) {
        this.partitionStrategy = partitionStrategy;
    }

    public int getPoolMaxSize() {
        return poolMaxSize;
    }

    public void setPoolMaxSize(int poolMaxSize) {
        this.poolMaxSize = poolMaxSize;
    }

    public int getPoolMinSize() {
        return poolMinSize;
    }

    public void setPoolMinSize(int poolMinSize) {
        this.poolMinSize = poolMinSize;
    }

    public boolean isAllConnectionsEqual() {
        return allConnectionsEqual;
    }

    public void setAllConnectionsEqual(boolean allConnectionsEqual) {
        this.allConnectionsEqual = allConnectionsEqual;
    }

    public Duration getConnectionMaxWait() {
        return connectionMaxWait;
    }

    public void setConnectionMaxWait(Duration connectionMaxWait) {
        this.connectionMaxWait = connectionMaxWait;
    }

    public Duration getConnectionMaxIdle() {
        return connectionMaxIdle;
    }

    public void setConnectionMaxIdle(Duration connectionMaxIdle) {
        this.connectionMaxIdle = connectionMaxIdle;
    }

    public boolean isBackgroundValidation() {
        return backgroundValidation;
    }

    public void setBackgroundValidation(boolean backgroundValidation) {
        this.backgroundValidation = backgroundValidation;
    }

    public Duration getValidatingPeriod() {
        return validatingPeriod;
    }

    public void setValidatingPeriod(Duration validatingPeriod) {
        this.validatingPeriod = validatingPeriod;
    }

    public boolean isValidateOnMatch() {
        return validateOnMatch;
    }

    public void setValidateOnMatch(boolean validateOnMatch) {
        this.validateOnMatch = validateOnMatch;
    }

    public SubjectSource getSubjectSource() {
        return subjectSource;
    }

    public void setSubjectSource(SubjectSource subjectSource) {
        this.subjectSource = subjectSource;
    }
}
