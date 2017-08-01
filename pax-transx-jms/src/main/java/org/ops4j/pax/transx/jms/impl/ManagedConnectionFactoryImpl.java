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
import org.ops4j.pax.transx.connection.utils.AbstractManagedConnectionFactory;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.security.auth.Subject;
import java.io.PrintWriter;

public class ManagedConnectionFactoryImpl
        extends AbstractManagedConnectionFactory<SessionImpl> {

    private final XAConnectionFactory xaConnectionFactory;
    private final ConnectionFactory connectionFactory;
    private String clientID;

    public ManagedConnectionFactoryImpl(ConnectionFactory connectionFactory, XAConnectionFactory xaConnectionFactory, ExceptionSorter exceptionSorter) {
        assert connectionFactory != null;
        assert xaConnectionFactory != null;
        assert exceptionSorter != null;
        this.connectionFactory = connectionFactory;
        this.xaConnectionFactory = xaConnectionFactory;
        this.exceptionSorter = exceptionSorter;
    }

    @Override
    public TransactionSupportLevel getTransactionSupport() {
        return TransactionSupportLevel.XATransaction;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public XAConnectionFactory getXaConnectionFactory() {
        return xaConnectionFactory;
    }

    @Override
    public SessionImpl createConnectionHandle(ConnectionRequestInfo cri, AbstractManagedConnection mc) {
        return new SessionImpl(this, cri, mc);
    }

    public String getClientID() {
        return clientID;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    @Override
    public Object createConnectionFactory(ConnectionManager cm) throws ResourceException {
        return new TransxConnectionFactory(this, cm);
    }

    @Override
    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        return new ManagedConnectionImpl(this, subject, exceptionSorter, (ConnectionRequestInfoImpl) connectionRequestInfo);
    }

    @Override
    public void setLogWriter(PrintWriter out) throws ResourceException {
    }

    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        return null;
    }

    public Integer getUseTryLock() {
        return null;
    }
}
