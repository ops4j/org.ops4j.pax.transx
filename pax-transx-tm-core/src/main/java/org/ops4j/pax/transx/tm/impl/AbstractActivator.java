/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
