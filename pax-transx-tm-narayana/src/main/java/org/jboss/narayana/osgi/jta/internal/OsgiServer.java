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
package org.jboss.narayana.osgi.jta.internal;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import com.arjuna.ats.arjuna.coordinator.TransactionReaper;
import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowser;
import com.arjuna.ats.jbossatx.jta.RecoveryManagerService;
import com.arjuna.ats.jbossatx.jta.TransactionManagerService;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.common.util.propertyservice.PropertiesFactory;
import org.jboss.narayana.osgi.jta.ObjStoreBrowserService;
import org.jboss.tm.XAResourceRecovery;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class OsgiServer implements ServiceTrackerCustomizer<XAResourceRecovery, XAResourceRecovery> {

    List<ServiceRegistration> registrations;
    ServiceTracker<XAResourceRecovery, XAResourceRecovery> resourceRecoveryTracker;
    TransactionManagerService transactionManagerService;
    RecoveryManagerService recoveryManagerService;
    ObjStoreBrowserService objStoreBrowserService;

    private final BundleContext bundleContext;
    private final Dictionary<String, ?> configuration;

    public OsgiServer(BundleContext bundleContext, Dictionary<String, ?> configuration) {
        this.bundleContext = bundleContext;
        this.configuration = configuration;
    }

    public void start() throws Exception {
        ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            doStart();
        } finally {
            Thread.currentThread().setContextClassLoader(ctxLoader);
        }
    }

    public void doStart() throws Exception {
        Properties properties = PropertiesFactory.getDefaultProperties();
        properties.putAll(System.getProperties());
        if (configuration != null) {
            for (Enumeration<String> keyEnum = configuration.keys(); keyEnum.hasMoreElements(); ) {
                String key = keyEnum.nextElement();
                String val = configuration.get(key).toString();
                properties.put(key, val);
            }
        }

        OsgiTransactionManager transactionManager = new OsgiTransactionManager();
        JTAEnvironmentBean jtaEnvironmentBean = jtaPropertyManager.getJTAEnvironmentBean();
        jtaEnvironmentBean.setTransactionManager(transactionManager);
        jtaEnvironmentBean.setUserTransaction(transactionManager);

        RecoveryManagerService rmSvc = new RecoveryManagerService();
        rmSvc.create();
        recoveryManagerService = rmSvc;

        resourceRecoveryTracker = new ServiceTracker<>(bundleContext, XAResourceRecovery.class, this);

        TransactionManagerService tmSvc = new TransactionManagerService();
        tmSvc.setTransactionSynchronizationRegistry(jtaEnvironmentBean.getTransactionSynchronizationRegistry());
        tmSvc.create();
        transactionManagerService = tmSvc;

        ObjStoreBrowser osb = new ObjStoreBrowser();
        osb.setExposeAllRecordsAsMBeans(true);
        objStoreBrowserService = new ObjStoreBrowserImpl(osb);

        resourceRecoveryTracker.open();
        transactionManagerService.start();
        recoveryManagerService.start();
        objStoreBrowserService.start();

        register(TransactionManager.class, transactionManagerService.getTransactionManager());
        register(TransactionSynchronizationRegistry.class, transactionManagerService.getTransactionSynchronizationRegistry());
        register(UserTransaction.class, transactionManagerService.getUserTransaction());
        register(ObjStoreBrowserService.class, objStoreBrowserService);

        // Only modification to this class to register the transx TM
        register(org.ops4j.pax.transx.tm.TransactionManager.class, new TransactionManagerWrapper(transactionManagerService.getTransactionManager()));

        try {
            registrations.add(PlatformTransactionManagerImple.register(
                    bundleContext,
                    (OsgiTransactionManager) transactionManagerService.getTransactionManager()));
        } catch (Throwable t) {
            // Ignore, this is most certainly spring-tx which is not available
        }
    }

    public void stop() {
        ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            doStop();
        } finally {
            Thread.currentThread().setContextClassLoader(ctxLoader);
        }
    }

    protected void doStop() {
        if (registrations != null) {
            for (ServiceRegistration reg : registrations) {
                try {
                    reg.unregister();
                } catch (Throwable t) {
                    warn("Error unregistering service", t);
                }
            }
            registrations = null;
        }
        if (transactionManagerService != null) {
            try {
                try {
                    transactionManagerService.stop();
                } finally {
                    transactionManagerService.destroy();
                }
            } catch (Throwable t) {
                warn("Error stopping transaction manager service", t);
            } finally {
                transactionManagerService = null;
            }
        }
        if (recoveryManagerService != null) {
            try {
                try {
                    recoveryManagerService.stop();
                } finally {
                    recoveryManagerService.destroy();
                }
            } catch (Throwable t) {
                warn("Error stopping recovery manager service", t);
            } finally {
                recoveryManagerService = null;
            }
        }
        if (resourceRecoveryTracker != null) {
            try {
                resourceRecoveryTracker.close();
            } catch (Throwable t) {
                warn("Error stopping resource recovery tracker", t);
            } finally {
                resourceRecoveryTracker = null;
            }
        }
        if (objStoreBrowserService != null) {
            try {
                objStoreBrowserService.stop();
            } catch (Throwable t) {
                warn("Error stopping object browser", t);
            } finally {
                objStoreBrowserService = null;
            }
        }

        TransactionReaper.terminate(false);
        TxControl.disable(true);
        StoreManager.shutdown();
    }

    protected <T> void register(Class<T> clazz, T service) {
        Hashtable<String, String> props = new Hashtable<>();
        props.put("provider", "narayana");
        ServiceRegistration<T> registration = bundleContext.registerService(clazz, service, props);
        if (registrations == null) {
            registrations = new ArrayList<>();
        }
        registrations.add(registration);
    }

    protected void warn(String message, Throwable t) {
        ServiceReference<LogService> ref = bundleContext.getServiceReference(LogService.class);
        if (ref != null) {
            LogService svc = bundleContext.getService(ref);
            svc.log(LogService.LOG_WARNING, message, t);
            bundleContext.ungetService(ref);
        }
    }

    @Override
    public XAResourceRecovery addingService(ServiceReference<XAResourceRecovery> reference) {
        final XAResourceRecovery resourceRecovery = bundleContext.getService(reference);
        recoveryManagerService.addXAResourceRecovery(resourceRecovery);
        return resourceRecovery;
    }

    @Override
    public void modifiedService(ServiceReference<XAResourceRecovery> reference, XAResourceRecovery service) {
    }

    @Override
    public void removedService(ServiceReference<XAResourceRecovery> reference, XAResourceRecovery resourceRecovery) {
        recoveryManagerService.removeXAResourceRecovery(resourceRecovery);
        bundleContext.ungetService(reference);
    }

}
