/*
 * Copyright 2021 OPS4J.
 *
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
 */
package org.ops4j.pax.transx.itests;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.logging.PaxLoggingConstants;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ops4j.pax.exam.Constants.START_LEVEL_SYSTEM_BUNDLES;
import static org.ops4j.pax.exam.Constants.START_LEVEL_TEST_BUNDLE;
import static org.ops4j.pax.exam.CoreOptions.bootDelegationPackages;
import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.frameworkProperty;
import static org.ops4j.pax.exam.CoreOptions.frameworkStartLevel;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.linkBundle;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.systemTimeout;
import static org.ops4j.pax.exam.CoreOptions.url;
import static org.ops4j.pax.exam.CoreOptions.workingDirectory;
import static org.ops4j.pax.exam.OptionUtils.combine;

@ExamReactorStrategy(PerClass.class)
public class AbstractControlledTestBase {

    public static final Logger LOG = LoggerFactory.getLogger("org.ops4j.pax.transx.itest");
    public static final String PROBE_SYMBOLIC_NAME = "PaxExam-Probe";

    // location of where pax-logging-api will have output file written according to
    // "org.ops4j.pax.logging.useFileLogFallback" system/context property
    // filename will match test class name with ".log" extension
    protected static final File LOG_DIR = new File("target/logs-default");

    @Rule
    public TestName testName = new TestName();

    @Inject
    protected BundleContext context;

    @Before
    public void beforeEach() {
        LOG.info("========== Running {}.{}() ==========", getClass().getName(), testName.getMethodName());
    }

    @After
    public void afterEach() {
        LOG.info("========== Finished {}.{}() ==========", getClass().getName(), testName.getMethodName());
    }

    protected Option[] baseConfigure() {
        LOG_DIR.mkdirs();

        Properties props = new Properties();
        try (InputStream is = AbstractControlledTestBase.class.getResourceAsStream("/osgi.properties")) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<Option> options = new ArrayList<>();
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
            String k = (String) e.nextElement();
            options.add(CoreOptions.frameworkProperty(k).value(props.getProperty(k)));
            options.add(systemProperty(k).value(props.getProperty(k)));
        }

        Option[] baseOptions = new Option[] {
                // basic options (see https://issues.apache.org/jira/browse/FELIX-6184)
                bootDelegationPackages("sun.*", "com.sun.*", "javax.transaction.xa", "javax.security.*", "jdk.internal.reflect.*", "jdk.internal.reflect"),
                systemPackage("javax.transaction.xa;version=\"1.2\""),

                frameworkStartLevel(START_LEVEL_TEST_BUNDLE),

                workingDirectory("target/paxexam"),
                // needed for PerClass strategy and I had problems running more test classes without cleaning
                // caches (timeout waiting for ProbeInvoker with particular UUID)
                cleanCaches(true),
                systemTimeout(60 * 60 * 1000),

                // set to "4" to see Felix wiring information
                frameworkProperty("felix.log.level").value("0"),

                // added implicitly by pax-exam, if pax.exam.system=test
                // these resources are provided inside org.ops4j.pax.exam:pax-exam-link-mvn jar
                // for example, "link:classpath:META-INF/links/org.ops4j.base.link" = "mvn:org.ops4j.base/ops4j-base/1.5.0"
                url("link:classpath:META-INF/links/org.ops4j.base.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.core.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.extender.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.framework.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.lifecycle.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.tracker.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.exam.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.exam.inject.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
                url("link:classpath:META-INF/links/org.ops4j.pax.extender.service.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),

                linkBundle("org.apache.servicemix.bundles.javax-inject").startLevel(START_LEVEL_SYSTEM_BUNDLES),

                junitBundles(),
                mavenBundle("org.mockito", "mockito-core")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
                mavenBundle("net.bytebuddy", "byte-buddy")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
                mavenBundle("net.bytebuddy", "byte-buddy-agent")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
                mavenBundle("org.objenesis", "objenesis")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),

                mavenBundle("org.ops4j.pax.logging", "pax-logging-api")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
                mavenBundle("org.ops4j.pax.transx", "pax-transx-itests-custom-appender")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1).start(false),
                mavenBundle("org.ops4j.pax.logging", "pax-logging-log4j2")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
                mavenBundle("org.apache.felix", "org.apache.felix.metatype")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
                mavenBundle("org.apache.felix", "org.apache.felix.configadmin")
                        .versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),

                systemProperty("org.ops4j.pax.logging.property.file").value("src/test/resources/log4j2-osgi.properties")
        };

        return combine(defaultLoggingConfig(),
                combine(baseOptions, options.toArray(new Option[options.size()])));
    }

    /**
     * Reasonable defaults for default logging level (actually a threshold), framework logger level and usage
     * of file-based default/fallback logger.
     * @return
     */
    protected Option[] defaultLoggingConfig() {
        String fileName = null;
        try {
            fileName = new File(LOG_DIR, getClass().getSimpleName() + ".log").getCanonicalPath();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return new Option[] {
                // every log with level higher or equal to DEBUG (i.e., not TRACE) will be logged
                frameworkProperty(PaxLoggingConstants.LOGGING_CFG_DEFAULT_LOG_LEVEL).value("DEBUG"),
                // threshold for R7 Compendium 101.8 logging statements (from framework/bundle/service events)
                frameworkProperty(PaxLoggingConstants.LOGGING_CFG_FRAMEWORK_EVENTS_LOG_LEVEL).value("ERROR"),
                // default log will be written to file
                frameworkProperty(PaxLoggingConstants.LOGGING_CFG_USE_FILE_FALLBACK_LOGGER).value(fileName)
        };
    }

    protected Option jcaApiBundle() {
//        return mavenBundle("javax.resource", "javax.resource-api").versionAsInProject();
        return mavenBundle("org.apache.geronimo.specs", "geronimo-j2ee-connector_1.6_spec").versionAsInProject();
    }

}
