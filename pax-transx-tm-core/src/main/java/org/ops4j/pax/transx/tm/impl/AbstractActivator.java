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
package org.ops4j.pax.transx.tm.impl;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractActivator implements BundleActivator {

    private BundleContext bundleContext;

    private ExecutorService executor = new ThreadPoolExecutor(0, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());
    private AtomicBoolean scheduled = new AtomicBoolean();

    private long schedulerStopTimeout = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS);

    private ServiceRegistration managedServiceRegistration;
    private Dictionary<String, ?> configuration;

    @Override
    public void start(BundleContext context) throws Exception {
        bundleContext = context;
        scheduled.set(true);
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, getPid());
        managedServiceRegistration = bundleContext.registerService(ManagedService.class, this::updated, props);
        scheduled.set(false);
        reconfigure();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        scheduled.set(true);
        if (managedServiceRegistration != null) {
            managedServiceRegistration.unregister();
        }
        executor.shutdown();
        executor.awaitTermination(schedulerStopTimeout, TimeUnit.MILLISECONDS);
        doStop();
    }

    private void updated(Dictionary<String, ?> properties) {
        if (!equals(this.configuration, properties)) {
            this.configuration = properties;
            reconfigure();
        }
    }

    private boolean equals(Dictionary<String, ?> d1, Dictionary<String, ?> d2) {
        if (d1 == d2) {
            return true;
        } else if (d1 == null ^ d2 == null) {
            return false;
        } else if (d1.size() != d2.size()) {
            return false;
        } else {
            for (Enumeration<String> e1 = d1.keys(); e1.hasMoreElements();) {
                String key = e1.nextElement();
                Object v1 = d1.get(key);
                Object v2 = d2.get(key);
                if (v1 != v2 && (v2 == null || !v2.equals(v1))) {
                    return false;
                }
            }
            return true;
        }
    }

    private void reconfigure() {
        if (scheduled.compareAndSet(false, true)) {
            executor.submit(this::run);
        }
    }

    private void run() {
        scheduled.set(false);
        doStop();
        try {
            doStart();
        } catch (Exception e) {
            warn("Error starting service", e);
            doStop();
        }
    }

   protected abstract String getPid();

    protected abstract void doStart() throws Exception;

    protected abstract void doStop();

    protected BundleContext getBundleContext() {
        return bundleContext;
    }

    protected Dictionary<String, ?> getConfiguration() {
        return configuration;
    }

    protected void warn(String message, Throwable t) {
        ServiceReference<LogService> ref = bundleContext.getServiceReference(LogService.class);
        if (ref != null) {
            LogService svc = bundleContext.getService(ref);
            svc.log(LogService.LOG_WARNING, message, t);
            bundleContext.ungetService(ref);
        }
    }

}
