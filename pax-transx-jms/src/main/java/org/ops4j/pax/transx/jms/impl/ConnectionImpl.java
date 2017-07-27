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


import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import java.util.HashSet;
import java.util.Set;

import static org.ops4j.pax.transx.jms.impl.Utils.forEach;
import static org.ops4j.pax.transx.jms.impl.Utils.unsupported;


public class ConnectionImpl implements TopicConnection, QueueConnection {

    private final ManagedConnectionFactoryImpl mcf;
    private final ConnectionManager cm;
    private final String userName;
    private final String password;
    private final String clientID;

    private final Set<SessionImpl> sessions = new HashSet<>();
    private final Set<TemporaryQueue> tempQueues = new HashSet<>();
    private final Set<TemporaryTopic> tempTopics = new HashSet<>();

    private boolean closed;
    private boolean started;

    public ConnectionImpl(ManagedConnectionFactoryImpl mcf, ConnectionManager cm, String userName, String password, String clientID) {
        this.mcf = mcf;
        this.cm = cm;
        this.userName = userName;
        this.password = password;
        this.clientID = clientID;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Utils.doClose(sessions, SessionImpl::closeSession);
        Utils.doClose(tempQueues, TemporaryQueue::delete);
        Utils.doClose(tempTopics, TemporaryTopic::delete);
    }

    public void start() throws JMSException {
        if (closed) {
            throw new IllegalStateException("The connection is closed");
        }
        synchronized (sessions) {
            if (started) {
                return;
            }
            started = true;
            forEach(sessions, SessionImpl::start);
        }
    }

    public void stop() throws JMSException {
        unsupported("stop");
    }

    public String getClientID() throws JMSException {
        return clientID;
    }

    public void setClientID(String clientID) throws JMSException {
        unsupported("setClientID");
    }

    public ConnectionMetaData getMetaData() throws JMSException {
        ConnectionRequestInfoImpl cri = new ConnectionRequestInfoImpl(false, Session.AUTO_ACKNOWLEDGE, userName, password, clientID);
        try (SessionImpl session = (SessionImpl) cm.allocateConnection(mcf, cri)) {
            session.setConnection(this);
            return session.getManagedConnection().getConnectionMetaData();
        } catch (ResourceException e) {
            throw (JMSException) new JMSException("Unable to retrieve metadata").initCause(e);
        }
    }

    public ExceptionListener getExceptionListener() throws JMSException {
        return unsupported("getExceptionListener");
    }

    public void setExceptionListener(ExceptionListener excptLstnr) throws JMSException {
        unsupported("setExceptionListener");
    }

    public ConnectionConsumer createConnectionConsumer(Destination dest, String msgSel, ServerSessionPool ssp, int maxMsgs) throws JMSException {
        return unsupported("createConnectionConsumer");
    }

    public ConnectionConsumer createConnectionConsumer(Topic topic, String subName, ServerSessionPool ssp, int maxMsgs) throws JMSException {
        return unsupported("createConnectionConsumer");
    }

    public ConnectionConsumer createConnectionConsumer(Queue queue, String subName, ServerSessionPool ssp, int maxMsgs) throws JMSException {
        return unsupported("createConnectionConsumer");
    }

    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subName, String msgSel, ServerSessionPool ssp, int maxMsgs) throws JMSException {
        return unsupported("createDurableConnectionConsumer");
    }

    @Override
    public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return unsupported("createSharedDurableConnectionConsumer");
    }

    @Override
    public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return unsupported("createSharedDurableConnectionConsumer");
    }

    @Override
    public Session createSession() throws JMSException {
        return createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    @Override
    public Session createSession(int sessionMode) throws JMSException {
        return createSession(sessionMode == Session.SESSION_TRANSACTED, sessionMode);
    }

    public TopicSession createTopicSession(boolean transacted, int sessionMode) throws JMSException {
        return createSession(transacted, sessionMode);
    }

    public QueueSession createQueueSession(boolean transacted, int sessionMode) throws JMSException {
        return createSession(transacted, sessionMode);
    }

    public SessionImpl createSession(boolean transacted, int sessionMode) throws JMSException {
        if (closed) {
            throw new IllegalStateException("The connection is closed");
        }
        synchronized (sessions) {
            if (!sessions.isEmpty()) {
                throw new IllegalStateException("Only one session per connection is allowed");
            }
            try {
                ConnectionRequestInfoImpl cri = new ConnectionRequestInfoImpl(transacted, sessionMode, userName, password, clientID);
                SessionImpl session = (SessionImpl) cm.allocateConnection(mcf, cri);
                try {
                    session.setConnection(this);
                    if (started) {
                        session.start();
                    }
                    sessions.add(session);
                    return session;
                } catch (Throwable t) {
                    try {
                        session.close();
                    } catch (Throwable ignored) {
                    }
                    throw t;
                }
            } catch (Exception e) {
                throw Utils.newJMSException(e);
            }
        }
    }

    TemporaryQueue wrapTemporaryQueue(TemporaryQueue queue) {
        tempQueues.add(queue);
        return queue;
    }

    TemporaryTopic wrapTemporaryTopic(TemporaryTopic topic) {
        tempTopics.add(topic);
        return topic;
    }

    void closeSession(SessionImpl session) {
        synchronized (sessions) {
            sessions.remove(session);
        }
    }
}
