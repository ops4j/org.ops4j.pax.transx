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
package org.ops4j.pax.transx.connector.recovery;

import org.ops4j.pax.transx.connector.PoolingSupport;
import org.ops4j.pax.transx.connector.SubjectSource;
import org.ops4j.pax.transx.connector.TransactionSupport;
import org.ops4j.pax.transx.connector.impl.GenericConnectionManager;
import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;

import javax.resource.spi.ManagedConnectionFactory;
import javax.transaction.TransactionSynchronizationRegistry;

public class RecoverableGenericConnectionManager extends GenericConnectionManager {

    private transient final RecoverableTransactionManager transactionManager;
    private transient final ManagedConnectionFactory managedConnectionFactory;
    private final String name;

    public RecoverableGenericConnectionManager(
            TransactionSupport transactionSupport,
            PoolingSupport pooling,
            SubjectSource subjectSource,
            RecoverableTransactionManager transactionManager,
            TransactionSynchronizationRegistry transactionSynchronizationRegistry,
            ManagedConnectionFactory mcf,
            String name,
            ClassLoader classLoader) {
        super(transactionSupport, pooling, subjectSource, transactionManager, transactionSynchronizationRegistry, mcf, name, classLoader);
        this.transactionManager = transactionManager;
        this.managedConnectionFactory = mcf;
        this.name = name;
    }

    public void doRecovery() {
        if (!getIsRecoverable()) {
            return;
        }
        transactionManager.registerNamedXAResourceFactory(new OutboundNamedXAResourceFactory(name, getRecoveryStack(), managedConnectionFactory));
    }

    public void doStop() throws Exception {
        if (transactionManager != null) {
            transactionManager.unregisterNamedXAResourceFactory(name);
        }
        super.doStop();
    }

    public void doFail() {
        if (transactionManager != null) {
            transactionManager.unregisterNamedXAResourceFactory(name);
        }
        super.doFail();
    }

}
