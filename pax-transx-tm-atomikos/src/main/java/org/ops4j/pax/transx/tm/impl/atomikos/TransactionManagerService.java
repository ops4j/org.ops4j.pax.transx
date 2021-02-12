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
package org.ops4j.pax.transx.tm.impl.atomikos;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import com.atomikos.icatch.config.Configuration;
import com.atomikos.icatch.config.UserTransactionService;
import com.atomikos.icatch.config.UserTransactionServiceImp;
import com.atomikos.icatch.jta.J2eeTransactionManager;
import com.atomikos.icatch.jta.J2eeUserTransaction;
import com.atomikos.icatch.jta.TransactionManagerImp;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;

/**
 */
public class TransactionManagerService {

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

    @SuppressWarnings("unused")
    private final String pid;
    @SuppressWarnings("rawtypes")
    private final Dictionary properties;
    private final BundleContext bundleContext;
    private final UserTransactionService uts;
    private final TransactionManagerImp tm;
    private List<ServiceRegistration<?>> services;

    public TransactionManagerService(String pid, Dictionary<String, ?> properties, BundleContext bundleContext) throws ConfigurationException {
        this.pid = pid;
        this.properties = properties;
        this.bundleContext = bundleContext;
        // Transaction timeout
        int transactionTimeout = getInt(TRANSACTION_TIMEOUT, DEFAULT_TRANSACTION_TIMEOUT);
        if (transactionTimeout <= 0) {
            throw new ConfigurationException(TRANSACTION_TIMEOUT, "The transaction timeout property must be greater than zero.");
        }
        OsgiAssembler.setConfig(properties);
        Configuration.init();

        Configuration.getTransactionService();
        new J2eeUserTransaction();
        uts = new UserTransactionServiceImp();
        uts.init();
        tm = (TransactionManagerImp) TransactionManagerImp.getTransactionManager();
    }

    public void init() throws Exception {
        services = new ArrayList<>();
        services.add(bundleContext.registerService(UserTransaction.class, new J2eeUserTransaction(), null));
        services.add(bundleContext.registerService(TransactionManager.class, new J2eeTransactionManager(), null));
        services.add(bundleContext.registerService(org.ops4j.pax.transx.tm.TransactionManager.class, new TransactionManagerWrapper(tm), null));
    }

    public void destroy() throws Exception {
        for (ServiceRegistration<?> sr : services) {
            try {
                sr.unregister();
            } catch (IllegalStateException e) {
                //This can be safely ignored
            }
        }
        services.clear();
        Configuration.shutdown(false);
   }

    private String getString(String property, String dflt) throws ConfigurationException {
        String value = properties != null ? (String) properties.get(property) : null;
        if (value != null) {
            return value;
        }
        return dflt;
    }

    private int getInt(String property, int dflt) throws ConfigurationException {
        String value = properties != null ? (String) properties.get(property) : null;
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
        String value = properties != null ? (String) properties.get(property) : null;
        if (value != null) {
            try {
                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                throw new ConfigurationException(property, "The property " + property + " should have an boolean value, but the value " + value + " is not a boolean.", e);
            }
        }
        return dflt;
    }

}
