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
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

public class ConnectionManagerFactory {

    public static ConnectionManager create(
            TransactionSupport transactionSupport,
            PoolingSupport pooling,
            SubjectSource subjectSource,
            TransactionManager transactionManager,
            TransactionSynchronizationRegistry transactionSynchronizationRegistry,
            ManagedConnectionFactory mcf,
            String name,
            ClassLoader classLoader)
    {
        if (transactionSupport.isRecoverable()) {
            if (GeronimoConnectionManager.TMCheck.isGeronimo(transactionManager)) {
                return new GeronimoConnectionManager(transactionSupport, pooling, subjectSource, transactionManager, transactionSynchronizationRegistry, mcf, name, classLoader);
            } else if (NarayanaConnectionManager.TMCheck.isNarayana(transactionManager)) {
                return new NarayanaConnectionManager(transactionSupport, pooling, subjectSource, transactionManager, transactionSynchronizationRegistry, mcf, name, classLoader);
            }
        }
        return new GenericConnectionManager(transactionSupport, pooling, subjectSource, transactionManager, transactionSynchronizationRegistry, mcf, name, classLoader);
    }

    public static TransactionSupport noTransactions() {
        return NoTransactions.INSTANCE;
    }

    public static TransactionSupport localTransactions() {
        return LocalTransactions.INSTANCE;
    }

    public static TransactionSupport xaTransactions(boolean useTransactionCaching, boolean useThreadCaching) {
        return new XATransactions(useTransactionCaching, useThreadCaching);
    }

    public static PoolingSupport noPool() {
        return NoPool.INSTANCE;
    }

    public static PoolingSupport singlePool(int maxSize, int minSize, int blockingTimeoutMilliseconds, int idleTimeoutMinutes, boolean matchOne, boolean matchAll, boolean selectOneAssumeMatch) {
        return new SinglePool(maxSize, minSize, blockingTimeoutMilliseconds, idleTimeoutMinutes, matchOne, matchAll, selectOneAssumeMatch);
    }

    public static PoolingSupport partitionedPool(int maxSize, int minSize, int blockingTimeoutMilliseconds, int idleTimeoutMinutes, boolean matchOne, boolean matchAll, boolean selectOneAssumeMatch, boolean partitionByConnectionRequestInfo, boolean partitionBySubject) {
        return new PartitionedPool(maxSize, minSize, blockingTimeoutMilliseconds, idleTimeoutMinutes, matchOne, matchAll, selectOneAssumeMatch, partitionByConnectionRequestInfo, partitionBySubject);
    }

}
