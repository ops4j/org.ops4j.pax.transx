package org.ops4j.pax.transx.jms;

import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.transx.connector.ConnectionManagerFactory;
import org.ops4j.pax.transx.connector.PartitionedPool;
import org.ops4j.pax.transx.connector.XATransactions;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.IllegalStateException;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
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

public class ActiveMQTest {

    public static final String QUEUE = "myqueue";

    int poolMaxSize = 8;
    int poolMinSize = 1;
    int connectionMaxWaitMilliseconds = 5000;
    int connectionMaxIdleMinutes = 15;
    boolean allConnectionsEqual = true;
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
                new XATransactions(true, false),
                new PartitionedPool(poolMaxSize, poolMinSize, connectionMaxWaitMilliseconds, connectionMaxIdleMinutes, allConnectionsEqual, !allConnectionsEqual, false, true, false),
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
