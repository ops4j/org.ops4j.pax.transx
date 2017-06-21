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
package org.ops4j.pax.transx.connector.impl.geronimo;

import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;
import org.ops4j.pax.transx.connector.RecoverableConnectionManager;
import org.ops4j.pax.transx.connector.SubjectSource;
import org.ops4j.pax.transx.connector.impl.GenericConnectionManager;
import org.ops4j.pax.transx.connector.impl.JtaTransactionManager;
import org.ops4j.pax.transx.connector.impl.PoolingSupport;
import org.ops4j.pax.transx.connector.TransactionManager;
import org.ops4j.pax.transx.connector.impl.TransactionSupport;

import javax.resource.spi.ManagedConnectionFactory;

public class GeronimoConnectionManager extends GenericConnectionManager implements RecoverableConnectionManager {

    private final RecoverableTransactionManager transactionManager;
    private final ManagedConnectionFactory managedConnectionFactory;
    private final String name;

    public static class TMCheck {
        public static boolean isGeronimo(TransactionManager transactionManager) {
            try {
                if (transactionManager instanceof JtaTransactionManager) {
                    javax.transaction.TransactionManager tm = ((JtaTransactionManager) transactionManager).getTm();
                    return tm instanceof RecoverableTransactionManager;
                }
            } catch (Throwable t) {
            }
            return false;
        }
    }

    public GeronimoConnectionManager(
            TransactionSupport transactionSupport,
            PoolingSupport pooling,
            SubjectSource subjectSource,
            TransactionManager transactionManager,
            String name,
            ClassLoader classLoader,
            ManagedConnectionFactory mcf) {
        super(transactionSupport, pooling, subjectSource, transactionManager, name, classLoader);
        this.transactionManager = (RecoverableTransactionManager) ((JtaTransactionManager) transactionManager).getTm();
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
