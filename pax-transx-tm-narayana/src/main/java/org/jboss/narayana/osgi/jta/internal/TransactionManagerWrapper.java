/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.narayana.osgi.jta.internal;

import com.arjuna.ats.jbossatx.jta.RecoveryManagerService;
import org.jboss.tm.XAResourceRecovery;
import org.ops4j.pax.transx.tm.LastResource;
import org.ops4j.pax.transx.tm.NamedResource;
import org.ops4j.pax.transx.tm.ResourceFactory;
import org.ops4j.pax.transx.tm.impl.AbstractTransactionManagerWrapper;

import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class TransactionManagerWrapper extends AbstractTransactionManagerWrapper<TransactionManager> {

    final RecoveryManagerService recoveryManagerService;
    final Map<String, ResourceFactory> resources = new HashMap<>();
    final Map<ResourceFactory, XAResourceRecovery> recoverables = new HashMap<>();

    public TransactionManagerWrapper(TransactionManager narayanaTransactionManager) {
        super(narayanaTransactionManager);
        recoveryManagerService = new RecoveryManagerService();
        recoveryManagerService.create();
        recoveryManagerService.start();
    }

    @Override
    public boolean isLastResourceCommitSupported() {
        return true;
    }

    @Override
    public void registerResource(ResourceFactory resource) {
        XAResourceRecovery rr = () -> new XAResource[] {
                new XAResource() {
                    NamedResource xares = resource.create();
                    @Override
                    public void commit(Xid xid, boolean b) throws XAException {
                        xares.commit(xid, b);
                    }
                    @Override
                    public void end(Xid xid, int i) throws XAException {
                        xares.end(xid, i);
                        if ((i & XAResource.TMENDRSCAN) != 0) {
                            resource.release(xares);
                        }
                    }
                    @Override
                    public void forget(Xid xid) throws XAException {
                        xares.forget(xid);
                    }
                    @Override
                    public int getTransactionTimeout() throws XAException {
                        return xares.getTransactionTimeout();
                    }
                    @Override
                    public boolean isSameRM(XAResource xaResource) throws XAException {
                        return xares.isSameRM(xaResource);
                    }
                    @Override
                    public int prepare(Xid xid) throws XAException {
                        return xares.prepare(xid);
                    }
                    @Override
                    public Xid[] recover(int i) throws XAException {
                        return xares.recover(i);
                    }
                    @Override
                    public void rollback(Xid xid) throws XAException {
                        xares.rollback(xid);
                    }

                    @Override
                    public boolean setTransactionTimeout(int i) throws XAException {
                        return xares.setTransactionTimeout(i);
                    }

                    @Override
                    public void start(Xid xid, int i) throws XAException {
                        xares.start(xid, i);
                    }
                }
        };
        recoveryManagerService.addXAResourceRecovery(rr);
        recoverables.put(resource, rr);
        resources.put(resource.getName(), resource);
    }

    @Override
    public void unregisterResource(String name) {
        ResourceFactory resource = resources.remove(name);
        XAResourceRecovery rr = resource != null ? recoverables.remove(resource) : null;
        if (rr != null) {
            recoveryManagerService.removeXAResourceRecovery(rr);
        }
    }

    @Override
    public ResourceFactory getResource(String name) {
        return resources.get(name);
    }

    @Override
    protected TransactionWrapper doCreateTransactionWrapper(javax.transaction.Transaction tx) {
        return new NarayanaTransactionWrapper(tx);
    }

    class NarayanaTransactionWrapper extends TransactionWrapper {

        NamedResource original;
        NamedResource wrapped;

        public NarayanaTransactionWrapper(javax.transaction.Transaction transaction) {
            super(transaction);
        }

        @Override
        public void enlistResource(NamedResource xares) throws Exception {
            if (xares instanceof LastResource) {
                if (wrapped != null) {
                    throw new IllegalStateException();
                }
                original = xares;
                wrapped = (NamedResource) Proxy.newProxyInstance(
                        xares.getClass().getClassLoader(),
                        new Class[]{org.jboss.tm.LastResource.class, LastResource.class},
                        (proxy, method, args) -> method.invoke(xares, args));

                super.enlistResource(wrapped);
            } else {
                super.enlistResource(xares);
            }
        }

        @Override
        public void delistResource(NamedResource xares, int flags) throws Exception {
            if (xares == original) {
                xares = wrapped;
            }
            super.delistResource(xares, flags);
        }
    }
}
