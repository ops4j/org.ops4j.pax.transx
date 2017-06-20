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
import javax.jms.JMSConsumer;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class JMSConsumerImpl implements JMSConsumer {

    private final JMSContextImpl context;
    private final MessageConsumer consumer;

    JMSConsumerImpl(JMSContextImpl context, MessageConsumer consumer) {
        this.context = context;
        this.consumer = consumer;
    }

    @Override
    public String getMessageSelector() {
        try {
            return consumer.getMessageSelector();
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public MessageListener getMessageListener() throws JMSRuntimeException {
        try {
            return consumer.getMessageListener();
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSRuntimeException {
        try {
            consumer.setMessageListener(new MessageListenerWrapper(listener));
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public Message receive() {
        try {
            return context.setLastMessage(consumer.receive());
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public Message receive(long timeout) {
        try {
            return context.setLastMessage(consumer.receive(timeout));
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public Message receiveNoWait() {
        try {
            return context.setLastMessage(consumer.receiveNoWait());
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            consumer.close();
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public <T> T receiveBody(Class<T> c) {
        try {
            Message message = context.setLastMessage(consumer.receive());
            return getBody(c, message);
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public <T> T receiveBody(Class<T> c, long timeout) {
        try {
            Message message = context.setLastMessage(consumer.receive(timeout));
            return getBody(c, message);
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @Override
    public <T> T receiveBodyNoWait(Class<T> c) {
        try {
            Message message = context.setLastMessage(consumer.receiveNoWait());
            return getBody(c, message);
        } catch (JMSException e) {
            throw Utils.convertToRuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getBody(Class<T> c, Message msg) throws JMSException {
        if (msg == null) {
            return null;
        }
        if (c == String.class && msg instanceof TextMessage) {
            return (T) ((TextMessage) msg).getText();
        }
        if (c == byte[].class && msg instanceof BytesMessage) {
            long l = ((BytesMessage) msg).getBodyLength();
            if (l > Integer.MAX_VALUE) {
                throw new UnsupportedOperationException();
            }
            byte[] data = new byte[(int) l];
            ((BytesMessage) msg).readBytes(data);
            return (T) data;
        }
        if (c == Map.class && msg instanceof MapMessage) {
            Map<String, Object> map = new HashMap<>();
            Enumeration e = ((MapMessage) msg).getMapNames();
            while (e.hasMoreElements()) {
                String k = (String) e.nextElement();
                map.put(k, ((MapMessage) msg).getString(k));
            }
            return (T) map;
        }
        throw new UnsupportedOperationException();
    }

    final class MessageListenerWrapper implements MessageListener {

        final MessageListener wrapped;

        MessageListenerWrapper(MessageListener wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void onMessage(Message message) {
            context.setLastMessage(message);
            wrapped.onMessage(message);
        }
    }

}
