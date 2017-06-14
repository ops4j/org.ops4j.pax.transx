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
package org.ops4j.pax.transx.connector.impl;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnection;
import java.util.logging.Level;
import java.util.logging.Logger;


public class MCFConnectionInterceptor implements ConnectionInterceptor {

    protected static final Logger LOG = Logger.getLogger(MCFConnectionInterceptor.class.getName());

    private ConnectionInterceptor stack;

    public MCFConnectionInterceptor() {
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
        if (mci.getManagedConnection() != null) {
            return;
        }

        try {
            ManagedConnection mc = mci.getManagedConnectionFactory().createManagedConnection(
                    mci.getSubject(),
                    mci.getConnectionRequestInfo());
            mci.setManagedConnection(mc);
            ConnectionEventListenerImpl listener = new ConnectionEventListenerImpl(stack, mci);
            mci.setConnectionEventListener(listener);
            mc.addConnectionEventListener(listener);
        } catch (ResourceException re) {
            LOG.log(Level.SEVERE, "Error occurred creating ManagedConnection for " + connectionInfo, re);
            throw re;
        }
    }

    public void returnConnection(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
        ManagedConnection mc = mci.getManagedConnection();
        try {
            mc.destroy();
        } catch (ResourceException e) {
            //LOG and forget
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            //LOG and forget
        }
    }

    public void destroy() {
        // MCF is the "tail" of the stack. So, we're all done...
    }

    public void setStack(ConnectionInterceptor stack) {
        this.stack = stack;
    }

    public void info(StringBuilder s) {
        s.append(getClass().getName()).append("[stack=").append(stack).append("]\n");
        s.append("<end>");
    }

}
