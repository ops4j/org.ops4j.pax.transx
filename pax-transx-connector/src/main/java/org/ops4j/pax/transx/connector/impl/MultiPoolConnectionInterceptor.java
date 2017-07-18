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

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.security.auth.Subject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MultiPoolConnectionInterceptor maps the provided subject and connection request info to a
 * "SinglePool".  This can be used to make sure all matches will succeed, avoiding synchronization
 * slowdowns.
 */
public class MultiPoolConnectionInterceptor implements ConnectionInterceptor, PoolingAttributes {

    private final ConnectionInterceptor next;
    private final PoolingSupport singlePoolFactory;

    private final boolean useSubject;

    private final boolean useCRI;

    private final Map<SubjectCRIKey,PoolingAttributes> pools = new HashMap<>();

    // volatile is not necessary, here, because of synchronization. but maintained for consistency with other Interceptors...
    private volatile boolean destroyed = false;

    public MultiPoolConnectionInterceptor(
            ConnectionInterceptor next,
            PoolingSupport singlePoolFactory,
            boolean useSubject,
            boolean useCRI) {
        this.next = next;
        this.singlePoolFactory = singlePoolFactory;
        this.useSubject = useSubject;
        this.useCRI = useCRI;
    }

    @Override
    public ConnectionInterceptor next() {
        return next;
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
        SubjectCRIKey key = new SubjectCRIKey(
                        useSubject ? mci.getSubject() : null,
                        useCRI ? mci.getConnectionRequestInfo() : null);
        ConnectionInterceptor poolInterceptor;
        synchronized (pools) {
            if (destroyed) {
                throw new ResourceException("ConnectionManaged has been destroyed");
            }
            poolInterceptor = (ConnectionInterceptor) pools.get(key);
            if (poolInterceptor == null) {
                poolInterceptor = singlePoolFactory.addPoolingInterceptors(next);
                pools.put(key, (PoolingAttributes) poolInterceptor);
            }
        }
        poolInterceptor.getConnection(connectionInfo);
        connectionInfo.getManagedConnectionInfo().setPoolInterceptor(poolInterceptor);
    }

    // let underlying pools handle destroyed processing...
    public void returnConnection(
            ConnectionInfo connectionInfo,
            ConnectionReturnAction connectionReturnAction) {
        ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
        ConnectionInterceptor poolInterceptor = mci.getPoolInterceptor();
        poolInterceptor.returnConnection(connectionInfo, connectionReturnAction);
    }

    public void destroy() {
        synchronized (pools) {
            destroyed = true;
            for (PoolingAttributes poolingAttributes : pools.values()) {
                ConnectionInterceptor poolInterceptor = (ConnectionInterceptor) poolingAttributes;
                poolInterceptor.destroy();
            }
            pools.clear();
        }
        next.destroy();
    }
    
    public int getPartitionCount() {
        return pools.size();
    }

    public int getPartitionMaxSize() {
        return singlePoolFactory.getPartitionMaxSize();
    }

    public void setPartitionMaxSize(int maxSize) throws InterruptedException {
        singlePoolFactory.setPartitionMaxSize(maxSize);
        for (PoolingAttributes poolingAttributes : pools.values()) {
            poolingAttributes.setPartitionMaxSize(maxSize);
        }
    }

    public int getPartitionMinSize() {
        return singlePoolFactory.getPartitionMinSize();
    }

    public void setPartitionMinSize(int minSize) {
        singlePoolFactory.setPartitionMinSize(minSize);
        for (PoolingAttributes poolingAttributes : pools.values()) {
            poolingAttributes.setPartitionMinSize(minSize);
        }
    }

    public int getIdleConnectionCount() {
        int count = 0;
        for (PoolingAttributes poolingAttributes : pools.values()) {
            count += poolingAttributes.getIdleConnectionCount();
        }
        return count;
    }

    public int getConnectionCount() {
        int count = 0;
        for (PoolingAttributes poolingAttributes : pools.values()) {
            count += poolingAttributes.getConnectionCount();
        }
        return count;
    }

    public Duration getBlockingTimeout() {
        return singlePoolFactory.getBlockingTimeout();
    }

    public void setBlockingTimeout(Duration timeoutMilliseconds) {
        singlePoolFactory.setBlockingTimeout(timeoutMilliseconds);
        for (PoolingAttributes poolingAttributes : pools.values()) {
            poolingAttributes.setBlockingTimeout(timeoutMilliseconds);
        }
    }

    public Duration getIdleTimeout() {
        return singlePoolFactory.getIdleTimeout();
    }

    public void setIdleTimeout(Duration idleTimeout) {
        singlePoolFactory.setIdleTimeout(idleTimeout);
        for (PoolingAttributes poolingAttributes : pools.values()) {
            poolingAttributes.setIdleTimeout(idleTimeout);
        }
    }

    @Override
    public Duration getValidatingPeriod() {
        return singlePoolFactory.getValidatingPeriod();
    }

    @Override
    public void setValidatingPeriod(Duration validatingPeriod) {
        singlePoolFactory.setValidatingPeriod(validatingPeriod);
        for (PoolingAttributes poolingAttributes : pools.values()) {
            poolingAttributes.setValidatingPeriod(validatingPeriod);
        }
    }

    public void info(StringBuilder s) {
        s.append(getClass().getName()).append("[useSubject=").append(useSubject).append(",useCRI=").append(useCRI).append(",pool count=").append(pools.size()).append("]\n");
        next.info(s);
    }

    static class SubjectCRIKey {
        private final Subject subject;
        private final ConnectionRequestInfo cri;
        private final transient int hashcode;

        public SubjectCRIKey(
                final Subject subject,
                final ConnectionRequestInfo cri) {
            this.subject = subject;
            this.cri = cri;
            this.hashcode = Objects.hash(subject, cri);
        }

        @Override
        public int hashCode() {
            return hashcode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SubjectCRIKey that = (SubjectCRIKey) o;
            return hashcode == that.hashcode
                    && Objects.equals(cri, that.cri)
                    && Objects.equals(subject, that.subject);
        }
        
    }
}
