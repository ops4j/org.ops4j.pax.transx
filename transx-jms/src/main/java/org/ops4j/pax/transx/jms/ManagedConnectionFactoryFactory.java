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
package org.ops4j.pax.transx.jms;

import org.ops4j.pax.transx.connection.ExceptionSorter;
import org.ops4j.pax.transx.connection.NoExceptionsAreFatalSorter;
import org.ops4j.pax.transx.jms.impl.ManagedConnectionFactoryImpl;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.resource.spi.ManagedConnectionFactory;

public class ManagedConnectionFactoryFactory {

    public static ManagedConnectionFactory create(ConnectionFactory cf, XAConnectionFactory xaCf) {
        return create(cf, xaCf, new NoExceptionsAreFatalSorter());
    }

    public static ManagedConnectionFactory create(ConnectionFactory cf, XAConnectionFactory xaCf, ExceptionSorter exceptionSorter) {
        return new ManagedConnectionFactoryImpl(cf, xaCf, exceptionSorter);
    }

}
