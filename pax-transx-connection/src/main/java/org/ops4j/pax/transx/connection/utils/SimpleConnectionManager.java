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
package org.ops4j.pax.transx.connection.utils;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;

/**
 * A simple implementation of a ConnectionManager. An Application Server will
 * have a better implementation with pooling and security etc.
 */
public class SimpleConnectionManager implements ConnectionManager, ConnectionEventListener {

    private static final Logger LOG = Logger.getLogger(SimpleConnectionManager.class.getName());

    /**
     * @see javax.resource.spi.ConnectionManager#allocateConnection(javax.resource.spi.ManagedConnectionFactory,
     * javax.resource.spi.ConnectionRequestInfo)
     */
    public Object allocateConnection(ManagedConnectionFactory connectionFactory, ConnectionRequestInfo info) throws ResourceException {
        ManagedConnection connection = connectionFactory.createManagedConnection(null, info);
        connection.addConnectionEventListener(this);
        return connection.getConnection(null, info);
    }

    /**
     * @see javax.resource.spi.ConnectionEventListener#connectionClosed(javax.resource.spi.ConnectionEvent)
     */
    public void connectionClosed(ConnectionEvent event) {
        try {
            ((ManagedConnection) event.getSource()).cleanup();
        } catch (ResourceException e) {
            LOG.log(Level.WARNING, "Error occured during the cleanup of a managed connection: ", e);
        }
        try {
            ((ManagedConnection) event.getSource()).destroy();
        } catch (ResourceException e) {
            LOG.log(Level.WARNING, "Error occured during the destruction of a managed connection: ", e);
        }
    }

    /**
     * @see javax.resource.spi.ConnectionEventListener#localTransactionStarted(javax.resource.spi.ConnectionEvent)
     */
    public void localTransactionStarted(ConnectionEvent event) {
    }

    /**
     * @see javax.resource.spi.ConnectionEventListener#localTransactionCommitted(javax.resource.spi.ConnectionEvent)
     */
    public void localTransactionCommitted(ConnectionEvent event) {
    }

    /**
     * @see javax.resource.spi.ConnectionEventListener#localTransactionRolledback(javax.resource.spi.ConnectionEvent)
     */
    public void localTransactionRolledback(ConnectionEvent event) {
    }

    /**
     * @see javax.resource.spi.ConnectionEventListener#connectionErrorOccurred(javax.resource.spi.ConnectionEvent)
     */
    public void connectionErrorOccurred(ConnectionEvent event) {
        LOG.log(Level.WARNING, "Managed connection experienced an error: ", event.getException());
        try {
            ((ManagedConnection) event.getSource()).cleanup();
        } catch (ResourceException e) {
            LOG.log(Level.WARNING, "Error occured during the cleanup of a managed connection: ", e);
        }
        try {
            ((ManagedConnection) event.getSource()).destroy();
        } catch (ResourceException e) {
            LOG.log(Level.WARNING, "Error occured during the destruction of a managed connection: ", e);
        }
    }

}
