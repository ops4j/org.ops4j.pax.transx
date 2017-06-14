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
package org.ops4j.pax.transx.jdbc;

import org.ops4j.pax.transx.jdbc.impl.ConnectionPoolDataSourceMCF;
import org.ops4j.pax.transx.jdbc.impl.JDBCDriverMCF;
import org.ops4j.pax.transx.jdbc.impl.LocalDataSourceMCF;
import org.ops4j.pax.transx.jdbc.impl.XADataSourceMCF;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionFactory;
import javax.sql.CommonDataSource;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;

public class ManagedConnectionFactoryFactory {

    public static ManagedConnectionFactory create(String driver, String connectionUrl) throws ResourceException {
        JDBCDriverMCF mcf = new JDBCDriverMCF();
        mcf.setDriver(driver);
        mcf.setConnectionURL(connectionUrl);
        return mcf;
    }

    public static ManagedConnectionFactory create(CommonDataSource dataSource) {
        if (dataSource instanceof XADataSource) {
            return create((XADataSource) dataSource);
        }
        else if (dataSource instanceof ConnectionPoolDataSource) {
            return create((ConnectionPoolDataSource) dataSource);
        }
        else if (dataSource instanceof DataSource) {
            return create((DataSource) dataSource);
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    public static ManagedConnectionFactory create(DataSource dataSource) {
        return new LocalDataSourceMCF(dataSource);
    }

    public static ManagedConnectionFactory create(ConnectionPoolDataSource dataSource) {
        return new ConnectionPoolDataSourceMCF(dataSource);
    }

    public static ManagedConnectionFactory create(XADataSource dataSource) {
        return new XADataSourceMCF(dataSource);
    }
}
