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
package org.ops4j.pax.transx.jms;

import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.transx.connector.ConnectionManagerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.XAConnectionFactory;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.ops4j.pax.transx.connector.ConnectionManagerFactory.partitionedPool;
import static org.ops4j.pax.transx.connector.ConnectionManagerFactory.xaTransactions;

public class ActiveMQTest {

    public static final String QUEUE = "myqueue";

    GeronimoTransactionManager tm;

    @Before
    public void setUp() throws Exception {
        tm = new GeronimoTransactionManager();
    }

    @Test
    public void testContextWithXaTx() throws Exception {
        ConnectionFactory cf = wrap(createActiveMQXAConnectionFactory());

        assertEquals(0, consumeMessages(cf, QUEUE).size());

        tm.begin();
        try (JMSContext context = cf.createContext()) {
            Queue queue = context.createQueue(QUEUE);
            context.createProducer().send(queue, "Hello");
        }
        tm.rollback();
        assertEquals(0, consumeMessages(cf, QUEUE).size());

        tm.begin();
        try (JMSContext context = cf.createContext()) {
            Queue queue = context.createQueue(QUEUE);
            context.createProducer().send(queue, "Hello");
        }
        tm.commit();
        assertEquals(1, consumeMessages(cf, QUEUE).size());

        try (JMSContext context = cf.createContext()) {
            Queue queue = context.createQueue(QUEUE);
            context.createProducer().send(queue, "Hello");
        }
        assertEquals(1, consumeMessages(cf, QUEUE).size());
    }

    @Test
    public void testSessionWithXaTx() throws Exception {
        ConnectionFactory cf = wrap(createActiveMQXAConnectionFactory());

        assertEquals(0, consumeMessages(cf, QUEUE).size());

        tm.begin();
        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession()) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
            }
        }
        tm.rollback();
        assertEquals(0, consumeMessages(cf, QUEUE).size());

        tm.begin();
        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession()) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
            }
        }
        tm.commit();
        assertEquals(1, consumeMessages(cf, QUEUE).size());

        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession()) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
                try {
                    s.rollback();
                    fail("Should have thrown an exception");
                } catch (JMSException e) {
                    // expected
                }
            }
        }
        assertEquals(1, consumeMessages(cf, QUEUE).size());

        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession(true, Session.SESSION_TRANSACTED)) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
                try {
                    s.rollback();
                    fail("Should have thrown an exception");
                } catch (JMSException e) {
                    // expected
                }
            }
        }
        assertEquals(1, consumeMessages(cf, QUEUE).size());

        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession(false, Session.SESSION_TRANSACTED)) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
                try {
                    s.rollback();
                    fail("Should have thrown an exception");
                } catch (JMSException e) {
                    // expected
                }
            }
        }
        assertEquals(1, consumeMessages(cf, QUEUE).size());
    }

    private List<String> consumeMessages(ConnectionFactory cf, String queue) {
        try (JMSContext ctx = cf.createContext()) {
            return consumeMessages(ctx, ctx.createQueue(queue));
        }
    }

    private List<String> consumeMessages(JMSContext context, Queue queue) {
        List<String> messages = new ArrayList<>();
        try (JMSConsumer consumer = context.createConsumer(queue)) {
            while (true) {
                String msg = consumer.receiveBody(String.class, 100);
                if (msg != null) {
                    messages.add(msg);
                } else {
                    return messages;
                }
            }
        }
    }

   private ConnectionFactory wrap(XAConnectionFactory xaCf) throws ResourceException {
        ManagedConnectionFactory mcf = ManagedConnectionFactoryFactory.create(xaCf);
        ConnectionManager cm = ConnectionManagerFactory.create(
                xaTransactions(true, false),
                partitionedPool(8, 1, 5000, 15, true, false, false, true, false),
                null,
                tm,
                tm,
                mcf,
                "vmbroker",
                null
        );
        return (ConnectionFactory) mcf.createConnectionFactory(cm);
    }

    private XAConnectionFactory createActiveMQXAConnectionFactory() {
        return new ActiveMQXAConnectionFactory("vm://broker?marshal=false&broker.persistent=false");
    }
}
