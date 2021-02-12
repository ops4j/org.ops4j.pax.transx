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
package org.ops4j.pax.transx.jms.impl;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.resource.spi.ConnectionRequestInfo;

import org.ops4j.pax.transx.connection.utils.AbstractConnectionHandle;

import static org.ops4j.pax.transx.jms.impl.Utils.unsupported;


public class SessionImpl extends AbstractConnectionHandle<ManagedConnectionFactoryImpl, ManagedConnectionImpl, Session, SessionImpl> implements TopicSession, QueueSession {

    private ConnectionImpl con;

    private final Set<AutoCloseable> closeables = new HashSet<>();

    public SessionImpl(ManagedConnectionFactoryImpl mcf, ConnectionRequestInfo cri, ManagedConnectionImpl mc) {
        super(mcf, cri, mc);
        this.con = null;
    }

    ConnectionRequestInfoImpl cri() {
        return (ConnectionRequestInfoImpl) cri;
    }

    public void setConnection(ConnectionImpl con) {
        this.con = con;
    }

    @Override
    protected void doClose() {
        con.closeSession(this);
        try {
            mc.stop();
        } catch (Throwable t) {
            // TODO: Log
        }
        Utils.doClose(closeables);
        mc.connectionClosed(this);
    }

    public void cleanup() {
    }

    void start() throws JMSException {
        if (mc != null) {
            mc.start();
        }
    }

    private QueueSession getQueueSessionInternal(Session s) throws JMSException {
        if (!(s instanceof QueueSession)) {
            throw new InvalidDestinationException("Attempting to use QueueSession methods on: " + this);
        }
        return (QueueSession) s;
    }

    private TopicSession getTopicSessionInternal(Session s) throws JMSException {
        if (!(s instanceof TopicSession)) {
            throw new InvalidDestinationException("Attempting to use TopicSession methods on: " + this);
        }
        return (TopicSession) s;
    }

    @Override
    public QueueReceiver createReceiver(Queue queue) throws JMSException {
        return call(session -> wrap(QueueReceiver.class, getQueueSessionInternal(session).createReceiver(queue)));
    }

    @Override
    public QueueReceiver createReceiver(Queue queue, String messageSelector) throws JMSException {
        return call(session -> wrap(QueueReceiver.class, getQueueSessionInternal(session).createReceiver(queue, messageSelector)));
    }

    @Override
    public QueueSender createSender(Queue queue) throws JMSException {
        return call(session -> wrap(QueueSender.class, getQueueSessionInternal(session).createSender(queue)));
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
        return call(session -> wrap(TopicSubscriber.class, getTopicSessionInternal(session).createSubscriber(topic)));
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) throws JMSException {
        return call(session -> wrap(TopicSubscriber.class, getTopicSessionInternal(session).createSubscriber(topic, messageSelector, noLocal)));
    }

    @Override
    public TopicPublisher createPublisher(Topic topic) throws JMSException {
        return call(session -> wrap(TopicPublisher.class, getTopicSessionInternal(session).createPublisher(topic)));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createConsumer(destination)));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createConsumer(destination, messageSelector)));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createConsumer(destination, messageSelector, noLocal)));
    }

    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException {
        return call(session -> wrap(MessageProducer.class, session.createProducer(destination)));
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
        return call(session -> wrap(TopicSubscriber.class, session.createDurableSubscriber(topic, name)));
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        return call(session -> wrap(TopicSubscriber.class, session.createDurableSubscriber(topic, name, messageSelector, noLocal)));
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createSharedConsumer(topic, sharedSubscriptionName)));
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createSharedConsumer(topic, sharedSubscriptionName, messageSelector)));
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createDurableConsumer(topic, name)));
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createDurableConsumer(topic, name, messageSelector, noLocal)));
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createSharedDurableConsumer(topic, name)));
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
        return call(session -> wrap(MessageConsumer.class, session.createSharedDurableConsumer(topic, name, messageSelector)));
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        return call(session -> con.wrapTemporaryQueue(session.createTemporaryQueue()));
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        return call(session -> con.wrapTemporaryTopic(session.createTemporaryTopic()));
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        return call(Session::createBytesMessage);
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        return call(Session::createMapMessage);
    }

    @Override
    public Message createMessage() throws JMSException {
        return call(Session::createMessage);
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        return call(Session::createObjectMessage);
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
        return call(session -> session.createObjectMessage(object));
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        return call(Session::createStreamMessage);
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        return call(Session::createTextMessage);
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        return call(session -> session.createTextMessage(text));
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return call(session -> session.createQueue(queueName));
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return call(session -> session.createTopic(topicName));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        return call(session -> session.createBrowser(queue));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
        return call(session -> session.createBrowser(queue, messageSelector));
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        execute(session -> session.unsubscribe(name));
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return unsupported("getMessageListener");
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        unsupported("setMessageListener");
    }

    @Override
    public void run() {
        unsupported("run");
    }

    @Override
    public void recover() throws JMSException {
        execute(session -> {
            if (cri().isTransacted()) {
                throw new IllegalStateException("Session is transacted");
            }
            session.recover();
        });
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        return call(session -> cri().getAcknowledgeMode());
    }

    @Override
    public boolean getTransacted() throws JMSException {
        return call(session -> cri().isTransacted());
    }

    @Override
    public void commit() throws JMSException {
        execute(session -> {
            if (!cri().isTransacted()) {
                throw new IllegalStateException("Session is not transacted");
            }
            session.commit();
        });
    }

    @Override
    public void rollback() throws JMSException {
        execute(session -> {
            if (!cri().isTransacted()) {
                throw new IllegalStateException("Session is not transacted");
            }
            session.rollback();
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <E extends Exception> E wrapException(String msg, Exception e) {
        if (msg == null && e instanceof JMSException) {
            return (E) e;
        }
        if (msg == null && e != null && e.getCause() instanceof JMSException) {
            return (E) e.getCause();
        }
        return (E) new JMSException(msg).initCause(e);
    }

    private <T extends AutoCloseable> T wrap(Class<T> clazz, T closeable) {
        closeables.add(closeable);
        return clazz.cast(Proxy.newProxyInstance(closeable.getClass().getClassLoader(), new Class[]{ clazz },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())
                            && method.getParameterCount() == 0
                            && method.getReturnType() == void.class) {
                        closeables.remove(closeable);
                    }
                    return method.invoke(closeable, args);
                }));
    }

}
