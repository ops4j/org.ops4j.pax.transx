/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ops4j.pax.transx.itests;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.util.PathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

public class TestConfiguration {

    private TestConfiguration() {
    }

    public static Option regressionDefaults() {
        Properties props = new Properties();
        try (InputStream is = TestConfiguration.class.getResourceAsStream("/osgi.properties")) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<Option> options = new ArrayList<>();
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
            String k = (String) e.nextElement();
            options.add(CoreOptions.frameworkProperty(k).value(props.getProperty(k)));
            options.add(systemProperty(k).value(props.getProperty(k)));
        }
        return composite(
                // Framework
                composite(options.toArray(new Option[options.size()])),
                // Logging
                mvnBundle("org.slf4j", "slf4j-api"),
                mvnBundle("org.ops4j.pax.transx", "pax-transx-itests-logback"),
                mvnBundle("org.jboss.logging", "jboss-logging"),
                // Set logback configuration via system property.
                // This way, both the driver and the container use the same configuration
                systemProperty("logback.configurationFile").value(
                        "file:" + PathUtils.getBaseDir() + "/src/test/resources/logback.xml"),
                // JUnit
                junitBundles(),
                mvnBundle("org.mockito", "mockito-all"),
                // Config Admin
                mvnBundle("org.apache.felix", "org.apache.felix.configadmin")
        );
    }

    public static MavenArtifactProvisionOption mvnBundle(String groupId, String artifactId) {
        return mavenBundle(groupId, artifactId).versionAsInProject();
    }

}
