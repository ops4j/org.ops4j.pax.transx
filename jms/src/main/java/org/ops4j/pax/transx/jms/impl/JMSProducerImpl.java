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
import javax.jms.CompletionListener;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class JMSProducerImpl implements JMSProducer {

    private final JMSContextImpl context;
    private final MessageProducer producer;

    private volatile CompletionListener completionListener;

    private String correlationId;
    private String type;
    private Destination replyTo;
    private byte[] correlationIdBytes;

    private long deliveryDelay = Message.DEFAULT_DELIVERY_DELAY;
    private int deliveryMode = DeliveryMode.PERSISTENT;
    private int priority = Message.DEFAULT_PRIORITY;
    private long timeToLive = Message.DEFAULT_TIME_TO_LIVE;
    private boolean disableMessageId;
    private boolean disableTimestamp;

    private final Map<String, Object> messageProperties = new HashMap<>();

    JMSProducerImpl(JMSContextImpl context, MessageProducer producer) {
        this.context = context;
        this.producer = producer;
    }

    //----- Send Methods -----------------------------------------------------//

    @Override
    public JMSProducer send(Destination destination, Message message) {
        try {
            doSend(destination, message);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, byte[] body) {
        try {
            BytesMessage message = context.createBytesMessage();
            message.writeBytes(body);
            doSend(destination, message);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, Map<String, Object> body) {
        try {
            MapMessage message = context.createMapMessage();
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                message.setObject(entry.getKey(), entry.getValue());
            }
            doSend(destination, message);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, Serializable body) {
        try {
            ObjectMessage message = context.createObjectMessage();
            message.setObject(body);
            doSend(destination, message);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, String body) {
        try {
            TextMessage message = context.createTextMessage(body);
            doSend(destination, message);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
        return this;
    }

    private void doSend(Destination destination, Message message) throws JMSException {
        if (message == null) {
            throw new MessageFormatException("Message must not be null");
        }
        for (Map.Entry<String, Object> entry : messageProperties.entrySet()) {
            message.setObjectProperty(entry.getKey(), entry.getValue());
        }
        if (correlationId != null) {
            message.setJMSCorrelationID(correlationId);
        }
        if (correlationIdBytes != null) {
            message.setJMSCorrelationIDAsBytes(correlationIdBytes);
        }
        if (type != null) {
            message.setJMSType(type);
        }
        if (replyTo != null) {
            message.setJMSReplyTo(replyTo);
        }
        producer.setDeliveryMode(deliveryMode);
        producer.setPriority(priority);
        producer.setTimeToLive(timeToLive);
        producer.setDisableMessageID(disableMessageId);
        producer.setDisableMessageTimestamp(disableTimestamp);
        // producer.setDeliveryDelay(deliveryDelay);
        try {
            producer.send(destination, message);
            if (completionListener != null) {
                completionListener.onCompletion(message);
            }
        } catch (Exception e) {
            if (completionListener != null) {
                completionListener.onException(message, e);
            }
        }
    }

    //----- Message Property Methods -----------------------------------------//

    @Override
    public JMSProducer clearProperties() {
        messageProperties.clear();
        return this;
    }

    @Override
    public Set<String> getPropertyNames() {
        return new HashSet<>(messageProperties.keySet());
    }

    @Override
    public boolean propertyExists(String name) {
        return messageProperties.containsKey(name);
    }

    @Override
    public boolean getBooleanProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), Boolean.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public byte getByteProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), Byte.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public double getDoubleProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), Double.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public float getFloatProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), Float.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public int getIntProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), Integer.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public long getLongProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), Long.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public Object getObjectProperty(String name) {
        return messageProperties.get(name);
    }

    @Override
    public short getShortProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), Short.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public String getStringProperty(String name) {
        try {
            return convertPropertyTo(name, messageProperties.get(name), String.class);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSProducer setProperty(String name, boolean value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, byte value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, double value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, float value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, int value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, long value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, Object value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, short value) {
        return setObjectProperty(name, value);
    }

    @Override
    public JMSProducer setProperty(String name, String value) {
        return setObjectProperty(name, value);
    }

    //----- Message Headers --------------------------------------------------//

    @Override
    public String getJMSCorrelationID() {
        return correlationId;
    }

    @Override
    public JMSProducer setJMSCorrelationID(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() {
        return correlationIdBytes;
    }

    @Override
    public JMSProducer setJMSCorrelationIDAsBytes(byte[] correlationIdBytes) {
        this.correlationIdBytes = correlationIdBytes;
        return this;
    }

    @Override
    public Destination getJMSReplyTo() {
        return replyTo;
    }

    @Override
    public JMSProducer setJMSReplyTo(Destination replyTo) {
        this.replyTo = replyTo;
        return this;
    }

    @Override
    public String getJMSType() {
        return type;
    }

    @Override
    public JMSProducer setJMSType(String type) {
        this.type = type;
        return this;
    }

    //----- Producer Send Configuration --------------------------------------//

    @Override
    public CompletionListener getAsync() {
        return completionListener;
    }

    @Override
    public JMSProducer setAsync(CompletionListener completionListener) {
        this.completionListener = completionListener;
        return this;
    }

    @Override
    public long getDeliveryDelay() {
        return deliveryDelay;
    }

    @Override
    public JMSProducer setDeliveryDelay(long deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
        return this;
    }

    @Override
    public int getDeliveryMode() {
        return deliveryMode;
    }

    @Override
    public JMSProducer setDeliveryMode(int deliveryMode) {
        switch (deliveryMode) {
            case DeliveryMode.PERSISTENT:
            case DeliveryMode.NON_PERSISTENT:
                this.deliveryMode = deliveryMode;
                return this;
            default:
                throw new JMSRuntimeException(String.format("Invalid DeliveryMode specified: %d", deliveryMode));
        }
    }

    @Override
    public boolean getDisableMessageID() {
        return disableMessageId;
    }

    @Override
    public JMSProducer setDisableMessageID(boolean disableMessageId) {
        this.disableMessageId = disableMessageId;
        return this;
    }

    @Override
    public boolean getDisableMessageTimestamp() {
        return disableTimestamp;
    }

    @Override
    public JMSProducer setDisableMessageTimestamp(boolean disableTimestamp) {
        this.disableTimestamp = disableTimestamp;
        return this;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public JMSProducer setPriority(int priority) {
        if (priority < 0 || priority > 9) {
            throw new JMSRuntimeException(String.format("Priority value given {%d} is out of range (0..9)", priority));
        }
        this.priority = priority;
        return this;
    }

    @Override
    public long getTimeToLive() {
        return timeToLive;
    }

    @Override
    public JMSProducer setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    //----- Internal support methods -----------------------------------------//

    private JMSProducer setObjectProperty(String name, Object value) {
        messageProperties.put(name, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertPropertyTo(String name, Object value, Class<T> target) throws JMSException {
        if (value == null) {
            if (Boolean.class.equals(target)) {
                return (T) Boolean.FALSE;
            } else if (Float.class.equals(target) || Double.class.equals(target)) {
                throw new NullPointerException("property " + name + " was null");
            } else if (Number.class.isAssignableFrom(target)) {
                throw new NumberFormatException("property " + name + " was null");
            } else {
                return null;
            }
        }
        if (target.isInstance(value)) {
            return target.cast(value);
        } else {
            try {
                return target.getConstructor(String.class).newInstance(value.toString());
            } catch (Exception e) {
                throw new MessageFormatException("Property " + name + " was a "
                        + value.getClass().getName() + " and cannot be read as a " + target.getName());
            }
        }
    }

}
