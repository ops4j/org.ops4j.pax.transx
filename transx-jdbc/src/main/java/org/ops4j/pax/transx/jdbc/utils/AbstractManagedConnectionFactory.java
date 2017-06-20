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
package org.ops4j.pax.transx.jdbc.utils;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.NoExceptionsAreFatalSorter;
import org.ops4j.pax.transx.connection.utils.UserPasswordManagedConnectionFactory;
import org.ops4j.pax.transx.jdbc.impl.AutocommitSpecCompliant;
import org.ops4j.pax.transx.jdbc.impl.TransxDataSource;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ValidatingManagedConnectionFactory;
import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractManagedConnectionFactory implements UserPasswordManagedConnectionFactory, AutocommitSpecCompliant, ValidatingManagedConnectionFactory {

    protected ExceptionSorter exceptionSorter;
    protected String userName;
    protected String password;
    protected boolean commitBeforeAutocommit = false;

    public AbstractManagedConnectionFactory() {
        this.exceptionSorter = NoExceptionsAreFatalSorter.INSTANCE;
    }

    public AbstractManagedConnectionFactory(ExceptionSorter exceptionSorter) {
        this.exceptionSorter = exceptionSorter;
    }

    /**
     * Return the user name used to establish the connection.
     *
     * @return the user name used to establish the connection
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Set the user name used establish the connection.
     * This value is used if no connection information is supplied by the application
     * when attempting to create a connection.
     *
     * @param user the user name used to establish the connection; may be null
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
     * Set the user password credential establish the connection.
     * This value is used if no connection information is supplied by the application
     * when attempting to create a connection.
     *
     * @param password the password credential used to establish the connection; may be null
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Return whether the Driver requires a commit before enabling auto-commit.
     *
     * @return TRUE if the Driver requires a commit before enabling auto-commit.
     */
    public boolean isCommitBeforeAutocommit() {
        return commitBeforeAutocommit;
    }

    /**
     * Set whether the Driver requires a commit before enabling auto-commit.
     * Although the JDBC specification requires any pending work to be committed
     * when auto-commit is enabled, not all drivers respect this. Setting this property
     * to true will cause the connector to explicitly commit the transaction before
     * re-enabling auto-commit; for compliant drivers this may result in two commits.
     *
     * @param commitBeforeAutocommit set TRUE if a commit should be performed before enabling auto-commit
     */
    public void setCommitBeforeAutocommit(boolean commitBeforeAutocommit) {
        this.commitBeforeAutocommit = commitBeforeAutocommit;
    }

    /**
     * Return the name of the ExceptionSorter implementation used to classify Exceptions
     * raised by the Driver.
     *
     * @return the class name of the ExceptionSorter being used
     */
    public String getExceptionSorterClass() {
        return exceptionSorter.getClass().getName();
    }

    /**
     * Set the name of the ExceptionSorter implementation so use.
     *
     * @param className the class name of an ExceptionSorter to use
     *
     * @throws InvalidPropertyException if the class name is null or empty
     */
    public void setExceptionSorterClass(String className) throws InvalidPropertyException {
        if (className == null || className.length() == 0) {
            throw new InvalidPropertyException("Empty class name");
        }
        try {
            Class<ExceptionSorter> clazz = (Class<ExceptionSorter>) loadClass(className);
            exceptionSorter = clazz.newInstance();
        } catch (ClassNotFoundException e) {
            throw new InvalidPropertyException("Unable to load class: " + className, e);
        } catch (IllegalAccessException e) {
            throw new InvalidPropertyException("Unable to instantiate class: " + className, e);
        } catch (InstantiationException e) {
            throw new InvalidPropertyException("Unable to instantiate class: " + className, e);
        } catch (ClassCastException e) {
            throw new InvalidPropertyException("Class is not a "+ ExceptionSorter.class.getName() + ": " + className, e);
        }
    }

    public Object createConnectionFactory() throws ResourceException {
        throw new NotSupportedException("ConnectionManager is required");
    }

    public Object createConnectionFactory(ConnectionManager connectionManager) throws ResourceException {
        return new TransxDataSource(this, connectionManager);
    }

    public ManagedConnection matchManagedConnections(Set set, Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        for (Object o : set) {
            if (o instanceof AbstractManagedConnection) {
                AbstractManagedConnection mc = (AbstractManagedConnection) o;
                if (mc.matches(this, subject, connectionRequestInfo)) {
                    return mc;
                }
            }
        }
        return null;
    }

    @Override
    public Set getInvalidConnections(Set set) throws ResourceException {
        Set newSet = new HashSet();
        for (Object o : set) {
            if (o instanceof AbstractManagedConnection) {
                AbstractManagedConnection mc = (AbstractManagedConnection) o;
                if (!mc.isValid()) {
                    newSet.add(o);
                }
            }
        }
        return newSet;
    }

    protected Class<?> loadClass(String name) throws ClassNotFoundException {
        // first try the TCL, then the classloader that defined us
        ClassLoader cl = getContextClassLoader();
        if (cl != null) {
            try {
                return cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                // ignore this
            }
        }
        return Class.forName(name);
    }

    protected ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
            try {
                return Thread.currentThread().getContextClassLoader();
            } catch (SecurityException e) {
                return null;
            }
        });
    }
}
