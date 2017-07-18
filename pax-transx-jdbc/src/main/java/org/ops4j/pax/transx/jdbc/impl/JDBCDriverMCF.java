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
package org.ops4j.pax.transx.jdbc.impl;

import org.ops4j.pax.transx.connection.utils.CredentialExtractor;
import org.ops4j.pax.transx.jdbc.utils.AbstractManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.ResourceAllocationException;
import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * Implementation of a ManagedConnectionFactory that connects to a JDBC database
 * using a generic JDBC Driver.
 */
public class JDBCDriverMCF extends AbstractManagedConnectionFactory {

	private Driver driver;
    private String url;

    // Although we store the log supplied by the container, there is no way to pass
    // it to the actual Driver we are using. The value is not pushed down into the
    // DriverManager to avoid conflicts with the static value in that class.
    private PrintWriter log;


    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo connectionRequestInfo) throws ResourceException {
        CredentialExtractor credentialExtractor = new CredentialExtractor(subject, connectionRequestInfo, this);
        Connection sqlConnection = getPhysicalConnection(credentialExtractor);
        return new ManagedJDBCConnection(this, sqlConnection, credentialExtractor, exceptionSorter);
    }

    protected Connection getPhysicalConnection(CredentialExtractor credentialExtractor) throws ResourceException {
        try {
            if (!driver.acceptsURL(url)) {
                throw new ResourceAdapterInternalException("JDBC Driver cannot handle url: " + url);
            }
        } catch (SQLException e) {
            throw new ResourceAdapterInternalException("JDBC Driver rejected url: " + url);
        }

        Properties info = new Properties();
        String user = credentialExtractor.getUserName();
        if (user != null) {
            info.setProperty("user", user);
        }
        String password = credentialExtractor.getPassword();
        if (password != null) {
            info.setProperty("password", password);
        }
        try {
            return driver.connect(url, info);
        } catch (SQLException e) {
            throw new ResourceAllocationException("Unable to obtain physical connection to " + url, e);
        }
    }

    public PrintWriter getLogWriter() {
        return log;
    }

    public void setLogWriter(PrintWriter log) {
        this.log = log;
    }

    /**
     * Return the name of the Driver class
     *
     * @return the name of the Driver class
     */
    public String getDriver() {
        return driver == null ? null : driver.getClass().getName();
    }

    /**
     * Set the name of the Driver class
     *
     * @param driver the name of the Driver class
     *
     * @throws InvalidPropertyException if the class name is null or empty
     */
    public void setDriver(String driver) throws InvalidPropertyException {
        if (driver == null || driver.length() == 0) {
            throw new InvalidPropertyException("Empty driver class name");
        }
        try {
            Class<Driver> driverClass = (Class<Driver>) loadClass(driver);
            this.driver = driverClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new InvalidPropertyException("Unable to load driver class: " + driver, e);
        } catch (InstantiationException e) {
            throw new InvalidPropertyException("Unable to instantiate driver class: " + driver, e);
        } catch (IllegalAccessException e) {
            throw new InvalidPropertyException("Unable to instantiate driver class: " + driver, e);
        } catch (ClassCastException e) {
            throw new InvalidPropertyException("Class is not a "+ Driver.class.getName() + ": " + driver, e);
        }
    }

    /**
     * Return the JDBC connection URL
     *
     * @return the JDBC connection URL
     */
    public String getConnectionURL() {
        return url;
    }

    /**
     * Set the JDBC connection URL.
     * This URL is passed directly to the Driver and should contain any properties
     * required to configure the connection.
     *
     * @param url the JDBC connection URL
     * @throws InvalidPropertyException if url missing
     */
    public void setConnectionURL(String url) throws InvalidPropertyException {
        if (url == null || url.length() == 0) {
            throw new InvalidPropertyException("Empty connection URL");
        }
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JDBCDriverMCF that = (JDBCDriverMCF) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
	public String toString() {
        return "JDBCDriverMCF[" + userName + "@" + url + "]";
    }

}
