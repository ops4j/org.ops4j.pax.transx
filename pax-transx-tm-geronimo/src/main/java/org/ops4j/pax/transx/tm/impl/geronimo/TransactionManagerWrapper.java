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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.transaction.SystemException;

import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.apache.geronimo.transaction.manager.NamedXAResource;
import org.apache.geronimo.transaction.manager.NamedXAResourceFactory;
import org.apache.geronimo.transaction.manager.WrapperNamedXAResource;
import org.ops4j.pax.transx.tm.LastResource;
import org.ops4j.pax.transx.tm.NamedResource;
import org.ops4j.pax.transx.tm.ResourceFactory;
import org.ops4j.pax.transx.tm.impl.AbstractTransactionManagerWrapper;

public class TransactionManagerWrapper extends AbstractTransactionManagerWrapper<GeronimoTransactionManager> {

    protected final Map<String, ResourceFactory> resources = new HashMap<>();

    public TransactionManagerWrapper(GeronimoTransactionManager geronimoTransactionManager) {
        super(geronimoTransactionManager);
    }

    @Override
    public boolean isLastResourceCommitSupported() {
        return true;
    }

    @Override
    public synchronized void registerResource(ResourceFactory resource) {
        tm.registerNamedXAResourceFactory(new NamedXAResourceFactory() {
            private final Map<NamedXAResource, NamedResource> resources = new IdentityHashMap<>();

            @Override
            public String getName() {
                return resource.getName();
            }

            @Override
            public NamedXAResource getNamedXAResource() throws SystemException {
                NamedResource res = resource.create();
                NamedXAResource nres = new WrapperNamedXAResource(res, res.getName());
                resources.put(nres, res);
                return nres;
            }

            @Override
            public void returnNamedXAResource(NamedXAResource namedXAResource) {
                NamedResource nres = resources.remove(namedXAResource);
                if (nres != null) {
                    resource.release(nres);
                } else {
                    throw new IllegalStateException("Unexpected call to returnNamedXAResource");
                }
            }
        });
        resources.put(resource.getName(), resource);
    }

    @Override
    public synchronized void unregisterResource(String name) {
        resources.remove(name);
        tm.unregisterNamedXAResourceFactory(name);
    }

    @Override
    public synchronized ResourceFactory getResource(String name) {
        return resources.get(name);
    }

    @Override
    protected TransactionWrapper doCreateTransactionWrapper(javax.transaction.Transaction tx) {
        return new GeronimoTransactionWrapper(tx);
    }

    class GeronimoTransactionWrapper extends TransactionWrapper {

        LastResource last;
        private final Map<NamedResource, NamedXAResource> resources = new IdentityHashMap<>();

        GeronimoTransactionWrapper(javax.transaction.Transaction transaction) {
            super(transaction);
        }

        @Override
        public void commit() throws Exception {
            if (last != null) {
                super.enlistResource(last);
            }
            super.commit();
        }

        @Override
        public void rollback() throws Exception {
            if (last != null) {
                super.enlistResource(last);
            }
            super.rollback();
        }

        @Override
        public void enlistResource(NamedResource xares) throws Exception {
            if (xares instanceof LastResource) {
                if (last != null) {
                    throw new IllegalStateException("Can not enlist two LastResource instances");
                }
                last = (LastResource) xares;
            } else {
                NamedXAResource nxares = new WrapperNamedXAResource(xares, xares.getName());
                resources.put(xares, nxares);
                ensureAssociated();
                getTransaction().enlistResource(nxares);
            }
        }

        @Override
        public void delistResource(NamedResource xares, int flags) throws Exception {
            NamedXAResource nxares = resources.remove(xares);
            ensureAssociated();
            getTransaction().delistResource(nxares, flags);
        }
    }

}
