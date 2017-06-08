/**
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

import org.ops4j.pax.transx.connector.impl.ConnectionInterceptor;
import org.ops4j.pax.transx.connector.impl.LocalXAResourceInsertionInterceptor;
import org.ops4j.pax.transx.connector.impl.TransactionCachingInterceptor;
import org.ops4j.pax.transx.connector.impl.TransactionEnlistingInterceptor;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

/**
 *
 *
 * */
public class LocalTransactions implements TransactionSupport {

    public static final TransactionSupport INSTANCE = new LocalTransactions();

    private LocalTransactions() {
    }

    public ConnectionInterceptor addXAResourceInsertionInterceptor(ConnectionInterceptor stack, String name) {
        return new LocalXAResourceInsertionInterceptor(stack, name);
    }

    public ConnectionInterceptor addTransactionInterceptors(ConnectionInterceptor stack, TransactionManager transactionManager, TransactionSynchronizationRegistry tsr) {
        stack = new TransactionEnlistingInterceptor(stack, transactionManager);
        return new TransactionCachingInterceptor(stack, transactionManager, tsr);
    }
    
    public boolean isRecoverable() {
        return false;
    }
}
