/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.transx.tm.impl.atomikos;

import com.atomikos.icatch.provider.ConfigProperties;
import com.atomikos.icatch.provider.imp.AssemblerImp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Properties;

public class OsgiAssembler extends AssemblerImp {

    static Dictionary<String, ?> properties;

    public static void setConfig(Dictionary<String, ?> properties) {
        OsgiAssembler.properties = properties;
    }

    @Override
    public ConfigProperties initializeProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/transactions-defaults.properties")) {
            props.load(is);
        } catch (IOException e) {
            // Ignore
        }
        if (properties != null) {
            for (Enumeration<String> ke = properties.keys(); ke.hasMoreElements();) {
                String key = ke.nextElement();
                props.put(key, properties.get(key));
            }
        }
        return new ConfigProperties(props);
    }

}
