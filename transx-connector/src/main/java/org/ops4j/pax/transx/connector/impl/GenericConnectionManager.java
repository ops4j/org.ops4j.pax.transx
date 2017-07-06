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

import org.ops4j.pax.transx.connector.PoolingAttributes;
import org.ops4j.pax.transx.connector.SubjectSource;
import org.ops4j.pax.transx.tm.NamedResource;
import org.ops4j.pax.transx.tm.ResourceFactory;
import org.ops4j.pax.transx.tm.TransactionManager;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LazyAssociatableConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GenericConnectionManager implements ConnectionManager, LazyAssociatableConnectionManager, PoolingAttributes {

    private static final Logger LOG = Logger.getLogger(GenericConnectionManager.class.getName());

    private final ConnectionInterceptor stack;
    private final ConnectionInterceptor recoveryStack;
    private final PoolingSupport poolingSupport;
    private final TransactionManager transactionManager;
    private final String name;
    private final ManagedConnectionFactory managedConnectionFactory;

    public GenericConnectionManager(
            TransactionSupport transactionSupport,
            PoolingSupport pooling,
            SubjectSource subjectSource,
            TransactionManager transactionManager,
            String name,
            ClassLoader classLoader,
            ManagedConnectionFactory managedConnectionFactory) {

        //Set up the interceptor stack
        MCFConnectionInterceptor tail = new MCFConnectionInterceptor();
        ConnectionInterceptor stack = tail;

        stack = transactionSupport.addXAResourceInsertionInterceptor(stack, name);
        stack = pooling.addPoolingInterceptors(stack);
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "Connection Manager " + name + " installed pool " + stack);
        }

        this.poolingSupport = pooling;
        stack = transactionSupport.addTransactionInterceptors(stack,
                transactionManager);

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

        this.transactionManager = transactionManager;
        this.name = name;
        this.managedConnectionFactory = managedConnectionFactory;
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

    public Duration getBlockingTimeout() {
        return getPooling().getBlockingTimeout();
    }

    public void setBlockingTimeout(Duration blockingTimeout) {
        getPooling().setBlockingTimeout(blockingTimeout);
    }

    public Duration getIdleTimeout() {
        return getPooling().getIdleTimeout();
    }

    public void setIdleTimeout(Duration idleTimeout) {
        getPooling().setIdleTimeout(idleTimeout);
    }

    @Override
    public Duration getValidatingPeriod() {
        return getPooling().getValidatingPeriod();
    }

    @Override
    public void setValidatingPeriod(Duration validatingPeriod) {
        getPooling().setValidatingPeriod(validatingPeriod);
    }

    protected ConnectionInterceptor getStack() {
        return stack;
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
        if (recoveryStack != null) {
            transactionManager.registerResource(new RecoverableResourceFactoryImpl());
        }
    }

    public void doStop() throws Exception {
        stack.destroy();
    }

    public void doFail() {
        stack.destroy();
    }


    private static class NamedXAResourceWithConnectioninfo implements NamedResource {

        private final NamedResource delegate;
        private final ConnectionInfo connectionInfo;

        private NamedXAResourceWithConnectioninfo(NamedResource delegate, ConnectionInfo connectionInfo) {
            this.delegate = delegate;
            this.connectionInfo = connectionInfo;
        }

        public ConnectionInfo getConnectionInfo() {
            return connectionInfo;
        }

        public String getName() {
            return delegate.getName();
        }

        public void commit(Xid xid, boolean b) throws XAException {
            delegate.commit(xid, b);
        }

        public void end(Xid xid, int i) throws XAException {
            delegate.end(xid, i);
        }

        public void forget(Xid xid) throws XAException {
            delegate.forget(xid);
        }

        public int getTransactionTimeout() throws XAException {
            return delegate.getTransactionTimeout();
        }

        public boolean isSameRM(XAResource xaResource) throws XAException {
            return delegate.isSameRM(xaResource);
        }

        public int prepare(Xid xid) throws XAException {
            return delegate.prepare(xid);
        }

        public Xid[] recover(int i) throws XAException {
            return delegate.recover(i);
        }

        public void rollback(Xid xid) throws XAException {
            delegate.rollback(xid);
        }

        public boolean setTransactionTimeout(int i) throws XAException {
            return delegate.setTransactionTimeout(i);
        }

        public void start(Xid xid, int i) throws XAException {
            delegate.start(xid, i);
        }
    }

    private class RecoverableResourceFactoryImpl implements ResourceFactory {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public NamedResource create() {
            try {
                ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, null);

                ConnectionInfo recoveryConnectionInfo = new ConnectionInfo(mci);
                recoveryStack.getConnection(recoveryConnectionInfo);

                // For pooled resources, we may now have a new MCI (not the one constructed above). Make sure we use the correct MCI
                return new NamedXAResourceWithConnectioninfo(recoveryConnectionInfo.getManagedConnectionInfo().getXAResource(), recoveryConnectionInfo);
            } catch (ResourceException e) {
                throw new RuntimeException("Could not get XAResource for recovery for jms: " + name, e);
            }
        }

        @Override
        public void release(NamedResource resource) {
            NamedXAResourceWithConnectioninfo xares = (NamedXAResourceWithConnectioninfo) resource;
            recoveryStack.returnConnection(xares.getConnectionInfo(), ConnectionInterceptor.ConnectionReturnAction.DESTROY);
        }
    }
}
