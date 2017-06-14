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
package org.ops4j.pax.transx.connector.recovery.narayana;

import org.jboss.tm.XAResourceRecovery;
import org.ops4j.pax.transx.connector.impl.ConnectionInfo;
import org.ops4j.pax.transx.connector.impl.ConnectionInterceptor;
import org.ops4j.pax.transx.connector.impl.ManagedConnectionInfo;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionFactory;
import javax.transaction.xa.XAResource;

public class XAResourceRecoveryImpl implements XAResourceRecovery {

    private final String name;
    private final ConnectionInterceptor recoveryStack;
    private final ManagedConnectionFactory managedConnectionFactory;

    public XAResourceRecoveryImpl(String name, ConnectionInterceptor recoveryStack, ManagedConnectionFactory managedConnectionFactory) {
        this.name = name;
        this.recoveryStack = recoveryStack;
        this.managedConnectionFactory = managedConnectionFactory;
    }

    public String getName() {
        return name;
    }

    @Override
    public XAResource[] getXAResources() {
        try {
            ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, null);

            ConnectionInfo recoveryConnectionInfo = new ConnectionInfo(mci);
            recoveryStack.getConnection(recoveryConnectionInfo);

            // For pooled resources, we may now have a new MCI (not the one constructed above). Make sure we use the correct MCI
            XAResource xaResource = recoveryConnectionInfo.getManagedConnectionInfo().getXAResource();
            return new XAResource[] { xaResource };
        } catch (ResourceException e) {
            throw new RuntimeException("Could not get XAResource for recovery for jms: " + name, e);
        }
    }

}
