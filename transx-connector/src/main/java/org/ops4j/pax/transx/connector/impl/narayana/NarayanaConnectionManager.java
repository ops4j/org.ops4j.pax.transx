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
package org.ops4j.pax.transx.connector.impl.narayana;

import org.jboss.tm.XAResourceRecovery;
import org.ops4j.pax.transx.connector.RecoverableConnectionManager;
import org.ops4j.pax.transx.connector.SubjectSource;
import org.ops4j.pax.transx.connector.impl.GenericConnectionManager;
import org.ops4j.pax.transx.connector.impl.PoolingSupport;
import org.ops4j.pax.transx.connector.TransactionManager;
import org.ops4j.pax.transx.connector.impl.TransactionSupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

import javax.resource.spi.ManagedConnectionFactory;

public class NarayanaConnectionManager extends GenericConnectionManager implements RecoverableConnectionManager {

    private final TransactionManager transactionManager;
    private final ManagedConnectionFactory managedConnectionFactory;
    private final String name;
    private ServiceRegistration<XAResourceRecovery> registration;

    public static class TMCheck {

        public static boolean isNarayana(TransactionManager transactionManager) {
            try {
                // Check if the transaction manager comes from Narayana
                // and if we can load the classes needed for recovery
                return transactionManager.getClass().getName().contains("narayana")
                        && TMCheck.class.getClassLoader().loadClass("org.jboss.tm.XAResourceRecovery") != null;
            } catch (Throwable t) {
                return false;
            }
        }

    }

    public NarayanaConnectionManager(
            TransactionSupport transactionSupport,
            PoolingSupport pooling,
            SubjectSource subjectSource,
            TransactionManager transactionManager,
            String name,
            ClassLoader classLoader,
            ManagedConnectionFactory mcf) {
        super(transactionSupport, pooling, subjectSource, transactionManager, name, classLoader);
        this.transactionManager = transactionManager;
        this.managedConnectionFactory = mcf;
        this.name = name;
    }

    public void doRecovery() {
        if (!getIsRecoverable()) {
            return;
        }
        XAResourceRecovery recovery = new XAResourceRecoveryImpl(name, getRecoveryStack(), managedConnectionFactory);
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        registration = bundleContext.registerService(XAResourceRecovery.class, recovery, null);
    }

    public void doStop() throws Exception {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        super.doStop();
    }

    public void doFail() {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        super.doFail();
    }
}
