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
package org.ops4j.pax.transx.connector.impl;

import org.ops4j.pax.transx.tm.NamedResource;
import org.ops4j.pax.transx.tm.ResourceFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;

public class RecoverableResourceFactoryImpl implements ResourceFactory {

    private final ManagedConnectionFactory managedConnectionFactory;
    private final String name;

    public RecoverableResourceFactoryImpl(ManagedConnectionFactory managedConnectionFactory, String name) {
        this.managedConnectionFactory = managedConnectionFactory;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public NamedResource create() {
        try {
            ManagedConnection mc = managedConnectionFactory.createManagedConnection(null, null);
            NamedResource xares = new WrapperNamedXAResource(mc.getXAResource(), name);
            return new NamedXAResourceWithConnection(mc, xares);
        } catch (ResourceException e) {
            throw new RuntimeException("Could not get XAResource for recovery for jms: " + name, e);
        }
    }

    @Override
    public void release(NamedResource resource) {
        NamedXAResourceWithConnection named = (NamedXAResourceWithConnection) resource;
        ManagedConnection mc = named.getManagedConnection();
        try {
            mc.destroy();
        } catch (ResourceException e) {
            e.printStackTrace();
        }
    }
}
