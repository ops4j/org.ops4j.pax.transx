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
package org.ops4j.pax.transx.tm.impl;

import org.ops4j.pax.transx.tm.NamedResource;
import org.ops4j.pax.transx.tm.Status;
import org.ops4j.pax.transx.tm.Transaction;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import java.util.function.Consumer;

public abstract class AbstractTransactionManagerWrapper<TM extends javax.transaction.TransactionManager> implements TransactionManager {

    protected final TM tm;
    protected final ThreadLocal<Transaction> transaction = new ThreadLocal<>();

    public AbstractTransactionManagerWrapper(TM tm) {
        this.tm = tm;
    }

    @Override
    public Transaction getTransaction() {
        Transaction tx =  transaction.get();
        if (tx == null) {
            tx = createTransactionWrapper();
            transaction.set(tx);
        }
        return tx;
    }

    @Override
    public Transaction suspend() {
        return null;
    }

    @Override
    public void resume(Transaction transaction) {

    }

    protected Transaction createTransactionWrapper() {
        return new TransactionWrapper();
    }

    protected class TransactionWrapper implements Transaction {

        @Override
        public boolean isActive() {
            return getStatus() == Status.ACTIVE;
        }

        @Override
        public void begin() throws Exception {
            try {
                tm.begin();
            } finally {
                transaction.remove();
            }
        }

        @Override
        public void commit() throws Exception {
            try {
                tm.getTransaction().commit();
            } finally {
                transaction.remove();
            }
        }

        @Override
        public void rollback() throws Exception {
            try {
                tm.getTransaction().rollback();
            } finally {
                transaction.remove();
            }
        }

        @Override
        public void setRollbackOnly() throws Exception {
            tm.setRollbackOnly();
        }

        @Override
        public Status getStatus() {
            try {
                javax.transaction.Transaction tx = tm.getTransaction();
                if (tx != null) {
                    try {
                        return toStatus(tx.getStatus());
                    } catch (SystemException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return Status.NO_TRANSACTION;
                }
            } catch (SystemException e) {
                throw new RuntimeException("Exception caught while getting transaction status", e);
            }
        }

        @Override
        public void enlistResource(NamedResource xares) throws Exception {
            tm.getTransaction().enlistResource(xares);
        }

        @Override
        public void delistResource(NamedResource xares, int flags) throws Exception {
            tm.getTransaction().delistResource(xares, flags);
        }

        @Override
        public void synchronization(Runnable pre, Consumer<Status> post) {
            try {
                tm.getTransaction().registerSynchronization(new Synchronization() {
                    @Override
                    public void beforeCompletion() {
                        if (pre != null) {
                            pre.run();
                        }
                    }
                    @Override
                    public void afterCompletion(int status) {
                        if (post != null) {
                            post.accept(toStatus(status));
                        }
                    }
                });
            } catch (RollbackException e) {
                throw new IllegalStateException("Transaction is marked for rollback", e);
            } catch (SystemException e) {
                throw new RuntimeException("Exception caught while registering synchronization", e);
            }
        }

    }

    static protected Status toStatus(int status) {
        switch (status) {
            case javax.transaction.Status.STATUS_ACTIVE:
                return Status.ACTIVE;
            case javax.transaction.Status.STATUS_MARKED_ROLLBACK:
                return Status.MARKED_ROLLBACK;
            case javax.transaction.Status.STATUS_PREPARED:
                return Status.PREPARED;
            case javax.transaction.Status.STATUS_COMMITTED:
                return Status.COMMITTED;
            case javax.transaction.Status.STATUS_ROLLEDBACK:
                return Status.ROLLED_BACK;
            case javax.transaction.Status.STATUS_PREPARING:
                return Status.PREPARING;
            case javax.transaction.Status.STATUS_COMMITTING:
                return Status.COMMITTING;
            case javax.transaction.Status.STATUS_ROLLING_BACK:
                return Status.ROLLING_BACK;
            default:
                return Status.NO_TRANSACTION;
        }
    }
}
