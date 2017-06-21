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

import javax.transaction.xa.XAResource;
import java.util.function.IntConsumer;

/**
 * JTA Transaction manager
 */
public interface TransactionManager {

    /**
     * Returns the current transaction, always non null.
     */
    Transaction getTransaction();

    /**
     * Transaction
     */
    interface Transaction {

        /**
         * Check if a transaction is running or not
         */
        boolean isActive();

        /**
         * Enlist the resource
         */
        void enlistResource(XAResource xares) throws Exception;

        /**
         * Delist the resource
         */
        void delistResource(XAResource xares, int flags) throws Exception;

        /**
         * Add a pre-completion job
         */
        void preCompletion(Runnable cb);

        /**
         * Add a post-completion job
         */
        void postCompletion(IntConsumer cb);

    }

}
