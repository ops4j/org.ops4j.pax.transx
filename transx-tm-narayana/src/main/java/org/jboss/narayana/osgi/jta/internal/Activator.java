/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.narayana.osgi.jta.internal;

import org.ops4j.pax.transx.tm.impl.AbstractActivator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;

public class Activator extends AbstractActivator {

    public static final String PID = "org.ops4j.pax.transx.tm.narayana";
    public static final String INTERN_PACKAGE = "org.jboss.narayana.osgi.jta.internal";
    public static final String SERVER_CLASS = INTERN_PACKAGE + ".OsgiServer";

    private Object service;

    @Override
    protected String getPid() {
        return PID;
    }

    protected void doStart() throws Exception {
        ClassLoader classLoader = createClassLoader();
        Class<?> osgiServerClass = classLoader.loadClass(SERVER_CLASS);
        service = osgiServerClass.getConstructor(BundleContext.class, Dictionary.class)
                .newInstance(getBundleContext(), getConfiguration());
        service.getClass().getMethod("start").invoke(service);
    }

    protected void doStop() {
        if (service != null) {
            try {
                service.getClass().getMethod("stop").invoke(service);
            } catch (Throwable t) {
                warn("Error stopping service", t);
            } finally {
                service = null;
            }
        }
    }

    ClassLoader createClassLoader() {
        Bundle bundle = getBundleContext().getBundle();
        List<URL> urls = new ArrayList<>();
        // Find our base url
        String name = SERVER_CLASS.replace('.', '/') + ".class";
        URL url = bundle.getResource(name);
        String strUrl = url.toExternalForm();
        if (!strUrl.endsWith(name)) {
            throw new IllegalStateException();
        }
        strUrl = strUrl.substring(0, strUrl.length() - name.length());
        try {
            urls.add(new URL(strUrl));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        // Find all embedded jars
        Collection<String> resources = bundle.adapt(BundleWiring.class)
                .listResources("/", "*.jar", BundleWiring.LISTRESOURCES_LOCAL);
        for (String resource : resources) {
            urls.add(bundle.getResource(resource));
        }
        // Create the classloader
        return new URLClassLoader(urls.toArray(new URL[urls.size()]),
                new ClassLoader(getClass().getClassLoader()) {
                    @Override
                    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                        // We forbid to load the server from the parent
                        if (name.startsWith(INTERN_PACKAGE)) {
                            throw new ClassNotFoundException(name);
                        }
                        return super.loadClass(name, resolve);
                    }
                });
    }

}
