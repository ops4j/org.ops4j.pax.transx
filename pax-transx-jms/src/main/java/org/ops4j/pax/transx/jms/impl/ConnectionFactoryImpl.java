/*
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
 *
 */
package org.ops4j.pax.transx.jms.impl;

import org.ops4j.pax.transx.connection.utils.SimpleConnectionManager;

import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TopicConnectionFactory;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;

public class ConnectionFactoryImpl implements TopicConnectionFactory, QueueConnectionFactory, AutoCloseable {

    private final ManagedConnectionFactoryImpl mcf;
    private final ConnectionManager cm;

    public ConnectionFactoryImpl(ManagedConnectionFactoryImpl mcf, ConnectionManager cm) {
        this.mcf = mcf;
        this.cm = cm != null ? cm : new SimpleConnectionManager();
    }

    public void close() throws Exception {
        if (cm instanceof AutoCloseable) {
            ((AutoCloseable) cm).close();
        }
    }

    @Override
    public ConnectionImpl createConnection() throws JMSException {
        return createConnection(null, null);
    }

    @Override
    public ConnectionImpl createConnection(String user, String password) throws JMSException {
        return new ConnectionImpl(mcf, cm, user, password, mcf.getClientID());
    }

    @Override
    public ConnectionImpl createTopicConnection() throws JMSException {
        return createConnection();
    }

    @Override
    public ConnectionImpl createTopicConnection(String userName, String password) throws JMSException {
        return createConnection(userName, password);
    }

    @Override
    public ConnectionImpl createQueueConnection() throws JMSException {
        return createConnection();
    }

    @Override
    public ConnectionImpl createQueueConnection(String userName, String password) throws JMSException {
        return createConnection(userName, password);
    }

    @Override
    public JMSContext createContext() {
        return createContext(null, null, Session.AUTO_ACKNOWLEDGE);
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        return createContext(null, null, sessionMode);
    }

    @Override
    public JMSContext createContext(String user, String password) {
        return createContext(user, password, Session.AUTO_ACKNOWLEDGE);
    }

    @Override
    public JMSContext createContext(String user, String password, int sessionMode) {
        try {
            return new JMSContextImpl(createConnection(user, password), sessionMode);
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

}
