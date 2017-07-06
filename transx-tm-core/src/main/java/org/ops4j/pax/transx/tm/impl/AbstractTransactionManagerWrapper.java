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
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public abstract class AbstractTransactionManagerWrapper<TM extends javax.transaction.TransactionManager> implements TransactionManager {

    final protected TM tm;
    final protected WeakHashMap<javax.transaction.Transaction, TransactionWrapper> transactions = new WeakHashMap<>();

    public AbstractTransactionManagerWrapper(TM tm) {
        this.tm = tm;
    }

    @Override
    public Transaction getTransaction() {
        try {
            javax.transaction.Transaction jtx = tm.getTransaction();
            if (jtx == null) {
                return null;
            }
            return transactions.computeIfAbsent(jtx, this::doCreateTransactionWrapper);
        } catch (SystemException e) {
            throw new RuntimeException("Unable to get transaction", e);
        }
    }

    void disassociate() {
    }

    void associate(Transaction tx) {
    }

    @Override
    public Transaction begin() throws Exception {
        tm.begin();
        disassociate();
        return getTransaction();
    }

    protected TransactionWrapper doCreateTransactionWrapper(javax.transaction.Transaction tx) {
        return new TransactionWrapper(tx);
    }

    protected class TransactionWrapper implements Transaction {

        final javax.transaction.Transaction transaction;
        boolean suspended;

        public TransactionWrapper(javax.transaction.Transaction transaction) {
            this.transaction = Objects.requireNonNull(transaction, "transaction should not be null");
            if (isActive()) {
                synchronization(null, st -> disassociate());
            }
        }

        protected javax.transaction.Transaction getTransaction() throws SystemException {
            return transaction;
        }

        @Override
        public boolean isActive() {
            return getStatus() == Status.ACTIVE;
        }

        @Override
        public void suspend() throws Exception {
            javax.transaction.Transaction tx = tm.suspend();
            if (tx != transaction) {
                throw new IllegalStateException();
            }
            disassociate();
        }

        @Override
        public void resume() throws Exception {
            javax.transaction.Transaction tx = tm.getTransaction();
            if (tx != null) {
                throw new IllegalStateException();
            }
            tm.resume(getTransaction());
            associate(this);
        }

        @Override
        public void commit() throws Exception {
            ensureAssociated();
            try {
                getTransaction().commit();
            } finally {
                disassociate();
            }
        }

        @Override
        public void rollback() throws Exception {
            ensureAssociated();
            try {
                getTransaction().rollback();
            } finally {
                disassociate();
            }
        }

        @Override
        public void setRollbackOnly() throws Exception {
            ensureAssociated();
            getTransaction().setRollbackOnly();
        }

        @Override
        public Status getStatus() {
            try {
                if (suspended) {
                    return Status.SUSPENDED;
                }
                return toStatus(getTransaction().getStatus());
            } catch (SystemException e) {
                throw new RuntimeException("Exception caught while getting transaction status", e);
            }
        }

        @Override
        public void enlistResource(NamedResource xares) throws Exception {
            ensureAssociated();
            getTransaction().enlistResource(xares);
        }

        @Override
        public void delistResource(NamedResource xares, int flags) throws Exception {
            ensureAssociated();
            getTransaction().delistResource(xares, flags);
        }

        @Override
        public void synchronization(Runnable pre, Consumer<Status> post) {
            ensureAssociated();
            try {
                getTransaction().registerSynchronization(new Synchronization() {
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

        private void ensureAssociated() {
            if (suspended) {
                throw new IllegalStateException("Transaction is suspended");
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
