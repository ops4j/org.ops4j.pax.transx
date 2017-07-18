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
import org.ops4j.pax.transx.connection.utils.UserPasswordManagedConnectionFactory;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.util.Set;

public class ManagedConnectionFactoryImpl implements UserPasswordManagedConnectionFactory {

    private final XAConnectionFactory xaConnectionFactory;
    private final ConnectionFactory connectionFactory;
    private ExceptionSorter exceptionSorter;
    private String userName;
    private String password;
    private String clientID;

    public ManagedConnectionFactoryImpl(ConnectionFactory connectionFactory, XAConnectionFactory xaConnectionFactory, ExceptionSorter exceptionSorter) {
        this.connectionFactory = connectionFactory;
        this.xaConnectionFactory = xaConnectionFactory;
        this.exceptionSorter = exceptionSorter;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public XAConnectionFactory getXaConnectionFactory() {
        return xaConnectionFactory;
    }

    /**
     * Return the userName name used to establish the connection.
     *
     * @return the userName name used to establish the connection
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Set the userName name used establish the connection.
     * This value is used if no connection information is supplied by the application
     * when attempting to create a connection.
     *
     * @param user the userName name used to establish the connection; may be null
     */
    public void setUserName(String user) {
        this.userName = user;
    }

    /**
     * Return the password credential used to establish the connection.
     *
     * @return the password credential used to establish the connection
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set the userName password credential establish the connection.
     * This value is used if no connection information is supplied by the application
     * when attempting to create a connection.
     *
     * @param password the password credential used to establish the connection; may be null
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientID() {
        return clientID;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    public ExceptionSorter getExceptionSorter() {
        return exceptionSorter;
    }

    public void setExceptionSorter(ExceptionSorter exceptionSorter) {
        this.exceptionSorter = exceptionSorter;
    }

    @Override
    public Object createConnectionFactory() throws ResourceException {
        return createConnectionFactory(null);
    }

    @Override
    public Object createConnectionFactory(ConnectionManager cm) throws ResourceException {
        return new ConnectionFactoryImpl(this, cm);
    }

    @Override
    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        return new ManagedConnectionImpl(this, subject, (ConnectionRequestInfoImpl) connectionRequestInfo);
    }

    @Override
    public ManagedConnection matchManagedConnections(Set set, Subject subject, ConnectionRequestInfo cri) throws ResourceException {
        for (Object o : set) {
            if (o instanceof ManagedConnectionImpl) {
                ManagedConnectionImpl mc = (ManagedConnectionImpl) o;
                if (mc.getCredentialExtractor().matches(subject, (ConnectionRequestInfoImpl) cri, this)) {
                    return mc;
                }
            }
        }
        return null;
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
