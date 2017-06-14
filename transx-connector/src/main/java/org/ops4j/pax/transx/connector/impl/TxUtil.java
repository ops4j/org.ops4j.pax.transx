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
package org.ops4j.pax.transx.connector.impl;

import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

final class TxUtil {
    private TxUtil() {
    }

    static Transaction getTransaction(TransactionManager transactionManager) {
        try {
            return transactionManager != null ? transactionManager.getTransaction() : null;
        } catch (SystemException ignored) {
        }
        return null;
    }

    static boolean isActive(Transaction transaction) {
        try {
            if (transaction != null) {
                int status = transaction.getStatus();
                return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
            }
        } catch (SystemException ignored) {
        }
        return false;
    }
}

