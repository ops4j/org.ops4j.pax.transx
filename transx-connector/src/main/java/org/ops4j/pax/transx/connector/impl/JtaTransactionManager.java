package org.ops4j.pax.transx.connector.impl;

import org.ops4j.pax.transx.connector.TransactionManager;

import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;
import java.util.Objects;
import java.util.function.IntConsumer;

public class JtaTransactionManager implements TransactionManager {

    private final javax.transaction.TransactionManager tm;

    public JtaTransactionManager(javax.transaction.TransactionManager tm) {
        this.tm = Objects.requireNonNull(tm, "The transaction manager must not be null");
    }

    public javax.transaction.TransactionManager getTm() {
        return tm;
    }

    @Override
    public Transaction getTransaction() {
        return new Transaction() {
            @Override
            public boolean isActive() {
                try {
                    javax.transaction.Transaction tx = tm.getTransaction();
                    if (tx != null) {
                        int status = tx.getStatus();
                        return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
                    }
                } catch (SystemException ignored) {
                }
                return false;
            }

            @Override
            public void enlistResource(XAResource xares) throws Exception {
                getTransaction().enlistResource(xares);
            }

            @Override
            public void delistResource(XAResource xares, int flags) throws Exception {
                getTransaction().delistResource(xares, flags);
            }

            @Override
            public void preCompletion(Runnable cb) {
                register(cb, null);
            }

            @Override
            public void postCompletion(IntConsumer cb) {
                register(null, cb);
            }

            private void register(Runnable pre, IntConsumer post) {
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
                                post.accept(status);
                            }
                        }
                    });
                } catch (RollbackException e) {
                    throw new IllegalStateException(e);
                } catch (SystemException e) {
                    throw new RuntimeException(e);
                }
            }

            private javax.transaction.Transaction getTransaction() {
                javax.transaction.Transaction tx;
                try {
                    tx = tm.getTransaction();
                } catch (SystemException e) {
                    throw new RuntimeException(e);
                }
                if (tx == null) {
                    throw new IllegalStateException("Transaction required");
                }
                return tx;
            }
        };
    }

}
