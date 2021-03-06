/*
 * Copyright 2021 OPS4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.transx.tm.impl.geronimo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;

import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.apache.geronimo.transaction.manager.TransactionLog;
import org.apache.geronimo.transaction.manager.TransactionManagerMonitor;
import org.apache.geronimo.transaction.manager.XidFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 */
public class GeronimoPlatformTransactionManager extends GeronimoTransactionManager implements PlatformTransactionManager {

    private final PlatformTransactionManager platformTransactionManager;
    private final Map<Transaction, SuspendedResourcesHolder> suspendedResources = new ConcurrentHashMap<>();

    public GeronimoPlatformTransactionManager() throws XAException {
        platformTransactionManager = new JtaTransactionManager(this, this);
        registerTransactionAssociationListener();
    }

    public GeronimoPlatformTransactionManager(int defaultTransactionTimeoutSeconds) throws XAException {
        super(defaultTransactionTimeoutSeconds);
        platformTransactionManager = new JtaTransactionManager(this, this);
        registerTransactionAssociationListener();
    }

    public GeronimoPlatformTransactionManager(int defaultTransactionTimeoutSeconds, TransactionLog transactionLog) throws XAException {
        super(defaultTransactionTimeoutSeconds, transactionLog);
        platformTransactionManager = new JtaTransactionManager(this, this);
        registerTransactionAssociationListener();
    }

    public GeronimoPlatformTransactionManager(int defaultTransactionTimeoutSeconds, XidFactory xidFactory, TransactionLog transactionLog) throws XAException {
        super(defaultTransactionTimeoutSeconds, xidFactory, transactionLog);
        platformTransactionManager = new JtaTransactionManager(this, this);
        registerTransactionAssociationListener();
    }

    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        return platformTransactionManager.getTransaction(definition);
    }

    public void commit(TransactionStatus status) throws TransactionException {
        platformTransactionManager.commit(status);
    }

    public void rollback(TransactionStatus status) throws TransactionException {
        platformTransactionManager.rollback(status);
    }

    protected void registerTransactionAssociationListener() {
        addTransactionAssociationListener(new TransactionManagerMonitor() {
            public void threadAssociated(Transaction transaction) {
                try {
                    if (transaction.getStatus() == Status.STATUS_ACTIVE) {
                        SuspendedResourcesHolder holder = suspendedResources.remove(transaction);
                        if (holder != null && holder.getSuspendedSynchronizations() != null) {
                            TransactionSynchronizationManager.setActualTransactionActive(true);
                            TransactionSynchronizationManager.setCurrentTransactionReadOnly(holder.isReadOnly());
                            TransactionSynchronizationManager.setCurrentTransactionName(holder.getName());
                            TransactionSynchronizationManager.initSynchronization();
                            for (TransactionSynchronization synchronization : holder.getSuspendedSynchronizations()) {
                                synchronization.resume();
                                TransactionSynchronizationManager.registerSynchronization(synchronization);
                            }
                        }
                    }
                } catch (SystemException e) {
                    // Ignore
                }
            }
            public void threadUnassociated(Transaction transaction) {
                try {
                    if (transaction.getStatus() == Status.STATUS_ACTIVE) {
                        if (TransactionSynchronizationManager.isSynchronizationActive()) {
                            List<TransactionSynchronization> suspendedSynchronizations = TransactionSynchronizationManager.getSynchronizations();
                            for (TransactionSynchronization synchronization : suspendedSynchronizations) {
                                synchronization.suspend();
                            }
                            TransactionSynchronizationManager.clearSynchronization();
                            String name = TransactionSynchronizationManager.getCurrentTransactionName();
                            TransactionSynchronizationManager.setCurrentTransactionName(null);
                            boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                            TransactionSynchronizationManager.setActualTransactionActive(false);
                            SuspendedResourcesHolder holder = new SuspendedResourcesHolder(null, suspendedSynchronizations, name, readOnly);
                            suspendedResources.put(transaction, holder);
                        }
                    }
                } catch (SystemException e) {
                    // Ignore
                }
            }
        });
    }

    /**
     * Holder for suspended resources.
     * Used internally by <code>suspend</code> and <code>resume</code>.
     */
    private static class SuspendedResourcesHolder {

        private final Object suspendedResources;

        private final List<TransactionSynchronization> suspendedSynchronizations;

        private final String name;

        private final boolean readOnly;

        SuspendedResourcesHolder(
                Object suspendedResources, List<TransactionSynchronization> suspendedSynchronizations, String name, boolean readOnly) {

            this.suspendedResources = suspendedResources;
            this.suspendedSynchronizations = suspendedSynchronizations;
            this.name = name;
            this.readOnly = readOnly;
        }

        @SuppressWarnings("unused")
        public Object getSuspendedResources() {
            return suspendedResources;
        }

        public List<TransactionSynchronization> getSuspendedSynchronizations() {
            return suspendedSynchronizations;
        }

        public String getName() {
            return name;
        }

        public boolean isReadOnly() {
            return readOnly;
        }
    }

}
