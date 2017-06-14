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

package org.ops4j.pax.transx.connector.impl;

import org.ops4j.pax.transx.connector.LocalTransactions;
import org.ops4j.pax.transx.connector.NoTransactions;
import org.ops4j.pax.transx.connector.PartitionedPool;
import org.ops4j.pax.transx.connector.PoolingAttributes;
import org.ops4j.pax.transx.connector.PoolingSupport;
import org.ops4j.pax.transx.connector.SubjectSource;
import org.ops4j.pax.transx.connector.TransactionSupport;
import org.ops4j.pax.transx.connector.XATransactions;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LazyAssociatableConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GenericConnectionManager implements ConnectionManager, LazyAssociatableConnectionManager, PoolingAttributes {

    private static final Logger LOG = Logger.getLogger(GenericConnectionManager.class.getName());

    private final ConnectionInterceptor stack;
    private final ConnectionInterceptor recoveryStack;
    private final PoolingSupport poolingSupport;

    public GenericConnectionManager(
            TransactionSupport transactionSupport,
            PoolingSupport pooling,
            SubjectSource subjectSource,
            TransactionManager transactionManager,
            TransactionSynchronizationRegistry transactionSynchronizationRegistry,
            ManagedConnectionFactory mcf,
            String name,
            ClassLoader classLoader) {

        //check for consistency between attributes
        if (subjectSource == null && pooling instanceof PartitionedPool && ((PartitionedPool) pooling).isPartitionBySubject()) {
            throw new IllegalStateException("To use Subject in pooling, you need a SecurityDomain");
        }

        if (mcf == null) {
            throw new NullPointerException("No ManagedConnectionFactory supplied for " + name);
        }
        if (mcf instanceof javax.resource.spi.TransactionSupport) {
            javax.resource.spi.TransactionSupport txSupport = (javax.resource.spi.TransactionSupport)mcf;
            javax.resource.spi.TransactionSupport.TransactionSupportLevel txSupportLevel = txSupport.getTransactionSupport();
            LOG.info("Runtime TransactionSupport level: " + txSupportLevel);
            if (txSupportLevel != null) {
                if (txSupportLevel == javax.resource.spi.TransactionSupport.TransactionSupportLevel.NoTransaction) {
                    transactionSupport = NoTransactions.INSTANCE;
                } else if (txSupportLevel == javax.resource.spi.TransactionSupport.TransactionSupportLevel.LocalTransaction) {
                    if (transactionSupport != NoTransactions.INSTANCE) {
                        transactionSupport = LocalTransactions.INSTANCE;
                    }
                } else {
                    if (transactionSupport != NoTransactions.INSTANCE && transactionSupport != LocalTransactions.INSTANCE) {
                        transactionSupport = new XATransactions(true, false);
                    }
                }
            }
        } else {
            LOG.info("No runtime TransactionSupport");
        }

        //Set up the interceptor stack
        MCFConnectionInterceptor tail = new MCFConnectionInterceptor();
        ConnectionInterceptor stack = tail;

        stack = transactionSupport.addXAResourceInsertionInterceptor(stack, name);
        stack = pooling.addPoolingInterceptors(stack);
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "Connection Manager " + name + " installed pool " + stack);
        }

        this.poolingSupport = pooling;
        stack = transactionSupport.addTransactionInterceptors(stack, transactionManager, transactionSynchronizationRegistry);

        if (subjectSource != null) {
            stack = new SubjectInterceptor(stack, subjectSource);
        }

        if (transactionSupport.isRecoverable()) {
            this.recoveryStack = new TCCLInterceptor(stack, classLoader);
        } else {
            this.recoveryStack = null;
        }


        stack = new ConnectionHandleInterceptor(stack);
        stack = new TCCLInterceptor(stack, classLoader);
        tail.setStack(stack);
        this.stack = stack;
        if (LOG.isLoggable(Level.FINE)) {
            StringBuilder s = new StringBuilder("ConnectionManager Interceptor stack;\n");
            stack.info(s);
            LOG.log(Level.FINE, s.toString());
        }
    }

    /**
     * in: jms != null, is a deployed jms
     * out: useable connection object.
     */
    public Object allocateConnection(ManagedConnectionFactory managedConnectionFactory,
                                     ConnectionRequestInfo connectionRequestInfo)
            throws ResourceException {
        ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, connectionRequestInfo);
        ConnectionInfo ci = new ConnectionInfo(mci);
        stack.getConnection(ci);
        Object connection = ci.getConnectionProxy();
        if (connection == null) {
            connection = ci.getConnectionHandle();
        } else {
            // connection proxy is used only once so we can be notified
            // by the garbage collector when a connection is abandoned 
            ci.setConnectionProxy(null);
        }
        return connection;
    }

    /**
     * in: non-null connection object, from non-null jms.
     * connection object is not associated with a managed connection
     * out: supplied connection object is assiciated with a non-null ManagedConnection from jms.
     */
    public void associateConnection(Object connection,
                                    ManagedConnectionFactory managedConnectionFactory,
                                    ConnectionRequestInfo connectionRequestInfo)
            throws ResourceException {
        ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, connectionRequestInfo);
        ConnectionInfo ci = new ConnectionInfo(mci);
        ci.setConnectionHandle(connection);
        stack.getConnection(ci);
    }

    public void inactiveConnectionClosed(Object connection, ManagedConnectionFactory managedConnectionFactory) {
        //TODO If we are tracking connections, we need to stop tracking this one.
        //I don't see why we don't get a connectionClosed event for it.
    }

    //statistics

    public int getPartitionCount() {
        return getPooling().getPartitionCount();
    }

    public int getPartitionMaxSize() {
        return getPooling().getPartitionMaxSize();
    }

    public void setPartitionMaxSize(int maxSize) throws InterruptedException {
        getPooling().setPartitionMaxSize(maxSize);
    }

    public int getPartitionMinSize() {
        return getPooling().getPartitionMinSize();
    }

    public void setPartitionMinSize(int minSize) {
        getPooling().setPartitionMinSize(minSize);
    }

    public int getIdleConnectionCount() {
        return getPooling().getIdleConnectionCount();
    }

    public int getConnectionCount() {
        return getPooling().getConnectionCount();
    }

    public int getBlockingTimeoutMilliseconds() {
        return getPooling().getBlockingTimeoutMilliseconds();
    }

    public void setBlockingTimeoutMilliseconds(int timeoutMilliseconds) {
        getPooling().setBlockingTimeoutMilliseconds(timeoutMilliseconds);
    }

    public int getIdleTimeoutMinutes() {
        return getPooling().getIdleTimeoutMinutes();
    }

    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
        getPooling().setIdleTimeoutMinutes(idleTimeoutMinutes);
    }

    protected ConnectionInterceptor getRecoveryStack() {
        return recoveryStack;
    }

    protected boolean getIsRecoverable() {
        return recoveryStack != null;
    }

    //public for persistence of pooling attributes (max, min size, blocking/idle timeouts)
    public PoolingSupport getPooling() {
        return poolingSupport;
    }

    public void doStart() throws Exception {

    }

    public void doStop() throws Exception {
        stack.destroy();
    }

    public void doFail() {
        stack.destroy();
    }

}
