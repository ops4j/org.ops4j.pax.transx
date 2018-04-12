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

import org.ops4j.pax.transx.tm.impl.AbstractActivator;

import java.util.logging.Level;
import java.util.logging.Logger;

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