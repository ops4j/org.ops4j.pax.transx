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
package org.ops4j.pax.transx.tm.impl.atomikos;

import com.atomikos.datasource.ResourceException;
import com.atomikos.datasource.xa.XATransactionalResource;
import com.atomikos.icatch.config.Configuration;
import com.atomikos.icatch.jta.J2eeTransactionManager;
import org.ops4j.pax.transx.tm.ResourceFactory;
import org.ops4j.pax.transx.tm.impl.AbstractTransactionManagerWrapper;

import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import java.util.HashMap;
import java.util.Map;

public class TransactionManagerWrapper extends AbstractTransactionManagerWrapper<TransactionManager> {

    Map<String, ResourceFactory> resources = new HashMap<>();
    Map<ResourceFactory, XATransactionalResource> recoverables = new HashMap<>();

    public TransactionManagerWrapper() {
        this(initTransactionManager());
    }

    public TransactionManagerWrapper(TransactionManager tm) {
        super(tm);
    }

    private static TransactionManager initTransactionManager() {
        Configuration.init();
        return new J2eeTransactionManager();
    }

    @Override
    public boolean isLastResourceCommitSupported() {
        return false;
    }

    @Override
    public void registerResource(ResourceFactory resource) {
        XATransactionalResource xatr = new XATransactionalResource(resource.getName()) {
            @Override
            protected XAResource refreshXAConnection() throws ResourceException {
                return resource.create();
            }
        };
        Configuration.addResource(xatr);
        resources.put(resource.getName(), resource);
        recoverables.put(resource, xatr);
    }

    @Override
    public void unregisterResource(String name) {
        ResourceFactory resource = resources.remove(name);
        XATransactionalResource xatr = resource != null ? recoverables.remove(resource) : null;
        Configuration.removeResource(name);
    }

    @Override
    public ResourceFactory getResource(String name) {
        return resources.get(name);
    }

}
