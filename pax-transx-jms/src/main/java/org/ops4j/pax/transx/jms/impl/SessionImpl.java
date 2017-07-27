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
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.transaction.xa.XAResource;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.ops4j.pax.transx.jms.impl.Utils.unsupported;


public class SessionImpl implements TopicSession, QueueSession {

    private final ConnectionRequestInfoImpl cri;
    private ManagedConnectionImpl mc;
    private ConnectionImpl con;

    private final Set<AutoCloseable> closeables = new HashSet<>();

    public SessionImpl(ManagedConnectionImpl mc, ConnectionRequestInfoImpl cri) {
        this.mc = mc;
        this.cri = cri;
        this.con = null;
    }

    public void setConnection(ConnectionImpl con) {
        this.con = con;
    }

    void setManagedConnection(ManagedConnectionImpl managedConnection) {
        if (mc != null) {
            mc.removeHandle(this);
        }
        mc = managedConnection;
    }

    ManagedConnectionImpl getManagedConnection() {
        return mc;
    }

    public void start() throws JMSException {
        if (mc != null) {
            mc.start();
        }
    }

    public void closeSession() {
        if (mc != null) {
            try {
                mc.stop();
            } catch (Throwable t) {
                // TODO: Log
            }
            Utils.doClose(closeables);
            mc.removeHandle(this);
            ConnectionEvent ev = new ConnectionEvent(mc, ConnectionEvent.CONNECTION_CLOSED);
            ev.setConnectionHandle(this);
            mc.sendEvent(ev);
            mc = null;
        }
    }

    private QueueSession getQueueSessionInternal() throws JMSException {
        Session s = getSessionInternal();
        if (!(s instanceof QueueSession)) {
            throw new InvalidDestinationException("Attempting to use QueueSession methods on: " + this);
        }
        return (QueueSession) s;
    }

    private TopicSession getTopicSessionInternal() throws JMSException {
        Session s = getSessionInternal();
        if (!(s instanceof TopicSession)) {
            throw new InvalidDestinationException("Attempting to use TopicSession methods on: " + this);
        }
        return (TopicSession) s;
    }

    private Session getSessionInternal() throws JMSException {
        if (mc == null) {
            throw new IllegalStateException("The session is closed");
        }
        return mc.getSession();
    }

    private XAResource getXAResourceInternal() throws JMSException {
        if (mc == null) {
            throw new IllegalStateException("The session is closed");
        }
        try {
            return mc.getXAResource();
        } catch (ResourceException e) {
            throw (JMSException) new JMSException("Unable to get XAResource").initCause(e);
        }
    }

    @Override
    public QueueReceiver createReceiver(Queue queue) throws JMSException {
        return call(() -> wrap(QueueReceiver.class, getQueueSessionInternal().createReceiver(queue)));
    }

    @Override
    public QueueReceiver createReceiver(Queue queue, String messageSelector) throws JMSException {
        return call(() -> wrap(QueueReceiver.class, getQueueSessionInternal().createReceiver(queue, messageSelector)));
    }

    @Override
    public QueueSender createSender(Queue queue) throws JMSException {
        return call(() -> wrap(QueueSender.class, getQueueSessionInternal().createSender(queue)));
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
        return call(() -> wrap(TopicSubscriber.class, getTopicSessionInternal().createSubscriber(topic)));
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) throws JMSException {
        return call(() -> wrap(TopicSubscriber.class, getTopicSessionInternal().createSubscriber(topic, messageSelector, noLocal)));
    }

    @Override
    public TopicPublisher createPublisher(Topic topic) throws JMSException {
        return call(() -> wrap(TopicPublisher.class, getTopicSessionInternal().createPublisher(topic)));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createConsumer(destination)));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createConsumer(destination, messageSelector)));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createConsumer(destination, messageSelector, noLocal)));
    }

    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException {
        return call(() -> wrap(MessageProducer.class, getSessionInternal().createProducer(destination)));
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
        return call(() -> wrap(TopicSubscriber.class, getSessionInternal().createDurableSubscriber(topic, name)));
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        return call(() -> wrap(TopicSubscriber.class, getSessionInternal().createDurableSubscriber(topic, name, messageSelector, noLocal)));
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createSharedConsumer(topic, sharedSubscriptionName)));
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createSharedConsumer(topic, sharedSubscriptionName, messageSelector)));
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createDurableConsumer(topic, name)));
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createDurableConsumer(topic, name, messageSelector, noLocal)));
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createSharedDurableConsumer(topic, name)));
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
        return call(() -> wrap(MessageConsumer.class, getSessionInternal().createSharedDurableConsumer(topic, name, messageSelector)));
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        return call(() -> con.wrapTemporaryQueue(getSessionInternal().createTemporaryQueue()));
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        return call(() -> con.wrapTemporaryTopic(getSessionInternal().createTemporaryTopic()));
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        return call(() -> getSessionInternal().createBytesMessage());
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        return call(() -> getSessionInternal().createMapMessage());
    }

    @Override
    public Message createMessage() throws JMSException {
        return call(() -> getSessionInternal().createMessage());
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        return call(() -> getSessionInternal().createObjectMessage());
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
        return call(() -> getSessionInternal().createObjectMessage(object));
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        return call(() -> getSessionInternal().createStreamMessage());
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        return call(() -> getSessionInternal().createTextMessage());
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        return call(() -> getSessionInternal().createTextMessage(text));
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return call(() -> getSessionInternal().createQueue(queueName));
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return call(() -> getSessionInternal().createTopic(topicName));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        return call(() -> getSessionInternal().createBrowser(queue));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
        return call(() -> getSessionInternal().createBrowser(queue, messageSelector));
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        execute(() -> getSessionInternal().unsubscribe(name));
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
        execute(() -> {
            Session session = getSessionInternal();
            if (cri.isTransacted()) {
                throw new IllegalStateException("Session is transacted");
            }
            session.recover();
        });
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        return call(() -> {
            getSessionInternal();
            return cri.getAcknowledgeMode();
        });
    }

    @Override
    public boolean getTransacted() throws JMSException {
        return call(() -> {
            getSessionInternal();
            return cri.isTransacted();
        });
    }

    @Override
    public void commit() throws JMSException {
        execute(() -> {
            Session session = getSessionInternal();
            if (!cri.isTransacted()) {
                throw new IllegalStateException("Session is not transacted");
            }
            session.commit();
        });
    }

    @Override
    public void rollback() throws JMSException {
        execute(() -> {
            Session session = getSessionInternal();
            if (!cri.isTransacted()) {
                throw new IllegalStateException("Session is not transacted");
            }
            session.rollback();
        });
    }

    @Override
    public void close() throws JMSException {
        con.closeSession(this);
        closeSession();
    }

    private <E extends Exception> void execute(Utils.RunnableWithException<E> cb) throws JMSException {
        mc.tryLock();
        try {
            cb.run();
        } catch (Exception e) {
            connectionError(e);
            throw Utils.newJMSException(e);
        } finally {
            mc.unlock();
        }
    }

    private <R> R call(Utils.ProviderWithException<JMSException, R> cb) throws JMSException {
        mc.tryLock();
        try {
            return cb.call();
        } catch (Exception e) {
            connectionError(e);
            throw Utils.newJMSException(e);
        } finally {
            mc.unlock();
        }
    }

    private void connectionError(Exception e) {
        // TODO: ?
    }

    @SuppressWarnings("unchecked")
    private <T extends AutoCloseable> T wrap(Class<T> clazz, T closeable) {
        closeables.add(closeable);
        return (T) Proxy.newProxyInstance(closeable.getClass().getClassLoader(), new Class[]{ clazz },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())
                            && method.getParameterCount() == 0
                            && method.getReturnType() == void.class) {
                        closeables.remove(closeable);
                    }
                    return method.invoke(closeable, args);
                });
    }

    void destroy() {
        mc = null;
    }
}
