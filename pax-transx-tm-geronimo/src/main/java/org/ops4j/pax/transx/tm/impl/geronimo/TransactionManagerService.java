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
package org.ops4j.pax.transx.tm.impl.geronimo;

import org.apache.geronimo.transaction.log.HOWLLog;
import org.apache.geronimo.transaction.log.UnrecoverableLog;
import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;
import org.apache.geronimo.transaction.manager.TransactionLog;
import org.apache.geronimo.transaction.manager.XidFactory;
import org.apache.geronimo.transaction.manager.XidFactoryImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import java.io.File;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

/**
 */
public class TransactionManagerService {

    public static final String PROPERTY_PREFIX = "org.apache.geronimo.tm.";

    public static final String TRANSACTION_TIMEOUT = "timeout";
    public static final String RECOVERABLE = "recoverable";
    public static final String TMID = "tmid";
    public static final String HOWL_BUFFER_CLASS_NAME = "howl.bufferClassName";
    public static final String HOWL_BUFFER_SIZE = "howl.bufferSize";
    public static final String HOWL_CHECKSUM_ENABLED = "howl.checksumEnabled";
    public static final String HOWL_ADLER32_CHECKSUM = "howl.adler32Checksum";
    public static final String HOWL_FLUSH_SLEEP_TIME = "howl.flushSleepTime";
    public static final String HOWL_LOG_FILE_EXT = "howl.logFileExt";
    public static final String HOWL_LOG_FILE_NAME = "howl.logFileName";
    public static final String HOWL_MAX_BLOCKS_PER_FILE = "howl.maxBlocksPerFile";
    public static final String HOWL_MAX_LOG_FILES = "howl.maxLogFiles";
    public static final String HOWL_MAX_BUFFERS = "howl.maxBuffers";
    public static final String HOWL_MIN_BUFFERS = "howl.minBuffers";
    public static final String HOWL_THREADS_WAITING_FORCE_THRESHOLD = "howl.threadsWaitingForceThreshold";
    public static final String HOWL_LOG_FILE_DIR = "howl.logFileDir";
    public static final String HOWL_FLUSH_PARTIAL_BUFFERS = "flushPartialBuffers";

    public static final int DEFAULT_TRANSACTION_TIMEOUT = 600; // 600 seconds -> 10 minutes
    public static final boolean DEFAULT_RECOVERABLE = false;   // not recoverable by default

    private static final String PLATFORM_TRANSACTION_MANAGER_CLASS = "org.springframework.transaction.PlatformTransactionManager";

    private final Dictionary<String, ?> properties;
    private final BundleContext bundleContext;
    private boolean useSpring;
    private GeronimoTransactionManager transactionManager;
    private TransactionLog transactionLog;
    private ServiceRegistration<?> serviceRegistration;
    private ServiceRegistration<?> wrapperRegistration;

    public TransactionManagerService(String pid, Dictionary<String, ?> properties, BundleContext bundleContext) throws ConfigurationException {
        this.properties = properties;
        this.bundleContext = bundleContext;
        // Transaction timeout
        int transactionTimeout = getInt(TRANSACTION_TIMEOUT, DEFAULT_TRANSACTION_TIMEOUT);
        if (transactionTimeout <= 0) {
            throw new ConfigurationException(TRANSACTION_TIMEOUT, "The transaction timeout property must be greater than zero.");
        }

        final String tmid = getString(TMID, pid);
        // the max length of the factory should be 64
        XidFactory xidFactory = new XidFactoryImpl(tmid.substring(0, Math.min(tmid.length(), 64)).getBytes());
        // Transaction log
        if (getBool(RECOVERABLE, DEFAULT_RECOVERABLE)) {
            String bufferClassName = getString(HOWL_BUFFER_CLASS_NAME, "org.objectweb.howl.log.BlockLogBuffer");
            int bufferSizeKBytes = getInt(HOWL_BUFFER_SIZE, 4);
            if (bufferSizeKBytes < 1 || bufferSizeKBytes > 32) {
                throw new ConfigurationException(HOWL_BUFFER_SIZE, "The buffer size must be between one and thirty-two.");
            }
            boolean checksumEnabled = getBool(HOWL_CHECKSUM_ENABLED, true);
            boolean adler32Checksum = getBool(HOWL_ADLER32_CHECKSUM, true);
            int flushSleepTimeMilliseconds = getInt(HOWL_FLUSH_SLEEP_TIME, 50);
            String logFileExt = getString(HOWL_LOG_FILE_EXT, "log");
            String logFileName = getString(HOWL_LOG_FILE_NAME, "transaction");
            int maxBlocksPerFile = getInt(HOWL_MAX_BLOCKS_PER_FILE, -1);
            int maxLogFiles = getInt(HOWL_MAX_LOG_FILES, 2);
            int minBuffers = getInt(HOWL_MIN_BUFFERS, 4);
            if (minBuffers < 0) {
                throw new ConfigurationException(HOWL_MIN_BUFFERS, "The minimum number of buffers must be greater than zero.");
            }
            int maxBuffers = getInt(HOWL_MAX_BUFFERS, 0);
            if (maxBuffers > 0 && minBuffers < maxBuffers) {
                throw new ConfigurationException(HOWL_MAX_BUFFERS, "The maximum number of buffers must be greater than the minimum number of buffers.");
            }
            int threadsWaitingForceThreshold = getInt(HOWL_THREADS_WAITING_FORCE_THRESHOLD, -1);
            boolean flushPartialBuffers = getBool(HOWL_FLUSH_PARTIAL_BUFFERS, true);
            String logFileDir = getString(HOWL_LOG_FILE_DIR, null);
            if (logFileDir == null || logFileDir.length() == 0 || !new File(logFileDir).isAbsolute()) {
                throw new ConfigurationException(HOWL_LOG_FILE_DIR, "The log file directory must be set to an absolute directory.");
            }
            try {
                transactionLog = new HOWLLog(bufferClassName,
                                             bufferSizeKBytes,
                                             checksumEnabled,
                                             adler32Checksum,
                                             flushSleepTimeMilliseconds,
                                             logFileDir,
                                             logFileExt,
                                             logFileName,
                                             maxBlocksPerFile,
                                             maxBuffers,
                                             maxLogFiles,
                                             minBuffers,
                                             threadsWaitingForceThreshold,
                                             flushPartialBuffers,
                                             xidFactory,
                                             null);
                ((HOWLLog) transactionLog).doStart();
            } catch (Exception e) {
                // This should not really happen as we've checked properties earlier
                throw new ConfigurationException(null, e.getMessage(), e);
            }
        } else {
            transactionLog =  new UnrecoverableLog();
        }
        // Create transaction manager
        try {
            try {
                transactionManager = new SpringTransactionManagerCreator().create(transactionTimeout, xidFactory, transactionLog);
                useSpring = true;
            } catch (NoClassDefFoundError e) {
                transactionManager = new GeronimoTransactionManager(transactionTimeout, xidFactory, transactionLog);
            }
        } catch (XAException e) {
            throw new RuntimeException("An exception occurred during transaction recovery.", e);
        }
    }

    public void init() throws Exception {
        List<String> clazzes = new ArrayList<>();
        clazzes.add(TransactionManager.class.getName());
        clazzes.add(TransactionSynchronizationRegistry.class.getName());
        clazzes.add(UserTransaction.class.getName());
        clazzes.add(RecoverableTransactionManager.class.getName());
        if (useSpring) {
            clazzes.add(PLATFORM_TRANSACTION_MANAGER_CLASS);
        }
        String[] ifar = clazzes.toArray(new String[clazzes.size()]);
        serviceRegistration = bundleContext.registerService(ifar, transactionManager, null);
        wrapperRegistration = bundleContext.registerService(org.ops4j.pax.transx.tm.TransactionManager.class,
                new TransactionManagerWrapper(transactionManager), null);
    }

    public void destroy() throws Exception {
        if (wrapperRegistration != null) {
            try {
                wrapperRegistration.unregister();
            } catch (IllegalStateException e) {
                //This can be safely ignored
            }
        }
        if (serviceRegistration != null) {
            try {
                serviceRegistration.unregister();
            } catch (IllegalStateException e) {
                //This can be safely ignored
            }
        }
      
        if (transactionLog instanceof HOWLLog) {
            ((HOWLLog) transactionLog).doStop();
        }
    }

    private String getString(String property, String dflt) throws ConfigurationException {
        String value = getRawString(property);
        if (value != null) {
            return value;
        }
        return dflt;
    }

    private int getInt(String property, int dflt) throws ConfigurationException {
        String value = getRawString(property);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                throw new ConfigurationException(property, "The property " + property + " should have an integer value, but the value " + value + " is not an integer.", e);
            }
        }
        return dflt;
    }

    private boolean getBool(String property, boolean dflt) throws ConfigurationException {
        String value = getRawString(property);
        if (value != null) {
            try {
                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                throw new ConfigurationException(property, "The property " + property + " should have an boolean value, but the value " + value + " is not a boolean.", e);
            }
        }
        return dflt;
    }

    private String getRawString(String property) {
        String name = PROPERTY_PREFIX + property;
        String value = properties != null ? (String) properties.get(name) : null;
        if (value == null && bundleContext != null) {
            value = bundleContext.getProperty(name);
        }
        return value;
    }

    /**
     * We use an inner static class to decouple this class from the spring-tx classes
     * in order to not have NoClassDefFoundError if those are not present.
     */
    public static class SpringTransactionManagerCreator {

        public GeronimoTransactionManager create(int defaultTransactionTimeoutSeconds, XidFactory xidFactory, TransactionLog transactionLog) throws XAException {
            return new GeronimoPlatformTransactionManager(defaultTransactionTimeoutSeconds, xidFactory, transactionLog);
        }

    }
}
