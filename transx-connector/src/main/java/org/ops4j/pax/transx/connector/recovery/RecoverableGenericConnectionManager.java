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

    public RecoverableGenericConnectionManager(TransactionSupport transactionSupport, PoolingSupport pooling, SubjectSource subjectSource, RecoverableTransactionManager transactionManager, TransactionSynchronizationRegistry transactionSynchronizationRegistry, ManagedConnectionFactory mcf, String name, ClassLoader classLoader) {
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
