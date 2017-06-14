package org.ops4j.pax.transx.connector;

import org.ops4j.pax.transx.connector.impl.GenericConnectionManager;

import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

public class ConnectionManagerFactory {

    public static ConnectionManager create(
            TransactionSupport transactionSupport,
            PoolingSupport pooling,
            SubjectSource subjectSource,
            TransactionManager transactionManager,
            TransactionSynchronizationRegistry transactionSynchronizationRegistry,
            ManagedConnectionFactory mcf,
            String name,
            ClassLoader classLoader) {
        return new GenericConnectionManager(transactionSupport, pooling, subjectSource, transactionManager, transactionSynchronizationRegistry, mcf, name, classLoader);
    }
}
