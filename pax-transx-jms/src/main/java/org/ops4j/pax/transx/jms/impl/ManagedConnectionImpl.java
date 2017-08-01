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

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.utils.AbstractManagedConnection;
import org.ops4j.pax.transx.connection.utils.CredentialExtractor;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.resource.ResourceException;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.security.auth.Subject;

import static org.ops4j.pax.transx.jms.impl.Utils.trace;

public class ManagedConnectionImpl extends AbstractManagedConnection<ManagedConnectionFactoryImpl, ManagedConnectionImpl, Session, SessionImpl> implements ManagedConnection {

    private final XAConnection xaConnection;
    private final XASession xaSession;
    private final Session xaSessionSession;
    private final Connection connection;
    private final Session session;

    public ManagedConnectionImpl(ManagedConnectionFactoryImpl mcf,
                                 Subject subject,
                                 ExceptionSorter exceptionSorter,
                                 ConnectionRequestInfoImpl cri) throws ResourceException {
        super(mcf, new CredentialExtractor(subject, cri, mcf), exceptionSorter);
        this.cri = cri;

        try {
            boolean transacted = cri != null && cri.isTransacted();
            int acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
            String userName = credentialExtractor.getUserName();
            String password = credentialExtractor.getPassword();
            if (userName != null && password != null) {
                connection = mcf.getConnectionFactory().createConnection(userName, password);
                xaConnection = mcf.getXaConnectionFactory().createXAConnection(userName, password);
            } else {
                connection = mcf.getConnectionFactory().createConnection();
                xaConnection = mcf.getXaConnectionFactory().createXAConnection();
            }
            connection.setExceptionListener(this::onException);
            xaConnection.setExceptionListener(this::onException);
            session = connection.createSession(transacted, acknowledgeMode);
            xaSession = xaConnection.createXASession();
            xaSessionSession = xaSession.getSession();
            xaResource = xaSession.getXAResource();
        } catch (JMSException e) {
            throw new ResourceException(e.getMessage(), e);
        }
    }

    @Override
    public Session getPhysicalConnection() {
        return getSession();
    }

    private Session getSession() {
        return inXaTransaction ? xaSessionSession : session;
    }

    ConnectionMetaData getConnectionMetaData() throws JMSException {
        return connection.getMetaData();
    }

    private void onException(final JMSException exception) {
        safe(() -> connection.setExceptionListener(null), "Unable to unset exception listener");
        safe(() -> xaConnection.setExceptionListener(null), "Unable to unset exception listener");
        unfilteredConnectionError(exception);
    }

    void start() throws JMSException {
        if (xaConnection != null) {
            xaConnection.start();
        }
        if (connection != null) {
            connection.start();
        }
    }

    void stop() throws JMSException {
        if (xaConnection != null) {
            xaConnection.stop();
        }
        if (connection != null) {
            connection.stop();
        }
    }

    @Override
    protected void closePhysicalConnection() throws ResourceException {
        try {
            try {
                connection.close();
            } finally {
                xaConnection.close();
            }
        } catch (JMSException e) {
            throw new ResourceException("Could not properly close the connection", e);
        }
    }

    @Override
    public void cleanup() throws ResourceException {
        super.cleanup();

        safe(connection::stop, "Error stopping connection");
        safe(xaConnection::stop, "Error stopping xaConnection");

        inXaTransaction = false;
    }

    protected boolean isValid() {
        try {
            session.createMessage();
            xaSession.createMessage();
            connection.getMetaData();
            return true;
        } catch (JMSException e) {
            return false;
        }
    }

    @Override
    public LocalTransaction getLocalTransaction() throws ResourceException {
        return new LocalTransaction() {
            @Override
            public void begin() throws ResourceException {
            }

            @Override
            public void commit() throws ResourceException {
                try {
                    if (getSession().getTransacted()) {
                        getSession().commit();
                    }
                } catch (JMSException e) {
                    throw new ResourceException("Could not commit LocalTransaction", e);
                }
            }

            @Override
            public void rollback() throws ResourceException {
                try {
                    if (getSession().getTransacted()) {
                        getSession().rollback();
                    }
                } catch (JMSException ex) {
                    throw new ResourceException("Could not rollback LocalTransaction", ex);
                }
            }
        };
    }

    static private void safe(Utils.RunnableWithException<JMSException> cb, String msg) {
        try {
            cb.run();
        } catch (JMSException e) {
            trace(msg, e);
        }
    }

}
