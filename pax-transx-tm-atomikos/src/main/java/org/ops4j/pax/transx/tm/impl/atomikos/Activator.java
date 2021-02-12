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
package org.ops4j.pax.transx.tm.impl.atomikos;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.ops4j.pax.transx.tm.impl.AbstractActivator;

/**
 */
public class Activator extends AbstractActivator {

    public static final String PID = "org.ops4j.pax.transx.tm.atomikos";

    private static final Logger LOG = Logger.getLogger(PID);

    private TransactionManagerService manager;

    @Override
    protected String getPid() {
        return PID;
    }

    @Override
    protected void doStart() throws Exception {
        manager = new TransactionManagerService(PID, getConfiguration(), getBundleContext());
        manager.init();
    }

    @Override
    protected void doStop() {
        if (manager != null) {
            try {
                manager.destroy();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "An exception occurred stopping the transaction manager.", e);
            } finally {
                manager = null;
            }
        }
    }

}