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

import javax.jms.IllegalStateRuntimeException;
import javax.jms.InvalidClientIDException;
import javax.jms.InvalidClientIDRuntimeException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidDestinationRuntimeException;
import javax.jms.InvalidSelectorException;
import javax.jms.InvalidSelectorRuntimeException;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.JMSSecurityException;
import javax.jms.JMSSecurityRuntimeException;
import javax.jms.MessageFormatException;
import javax.jms.MessageFormatRuntimeException;
import javax.jms.MessageNotWriteableException;
import javax.jms.MessageNotWriteableRuntimeException;
import javax.jms.ResourceAllocationException;
import javax.jms.ResourceAllocationRuntimeException;
import javax.jms.TransactionInProgressException;
import javax.jms.TransactionInProgressRuntimeException;
import javax.jms.TransactionRolledBackException;
import javax.jms.TransactionRolledBackRuntimeException;
import java.util.Set;

final class Utils {

    public static JMSException newJMSException(Throwable t) {
        if (t instanceof JMSException) {
            return (JMSException) t;
        }
        JMSException se = new JMSException(t.getMessage());
        return (JMSException) se.initCause(t);
    }

    public static JMSRuntimeException convertToRuntimeException(JMSException e) {
        if (e instanceof javax.jms.IllegalStateException) {
            return new IllegalStateRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof InvalidClientIDException) {
            return new InvalidClientIDRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof InvalidDestinationException) {
            return new InvalidDestinationRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof InvalidSelectorException) {
            return new InvalidSelectorRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof JMSSecurityException) {
            return new JMSSecurityRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof MessageFormatException) {
            return new MessageFormatRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof MessageNotWriteableException) {
            return new MessageNotWriteableRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof ResourceAllocationException) {
            return new ResourceAllocationRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof TransactionInProgressException) {
            return new TransactionInProgressRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        if (e instanceof TransactionRolledBackException) {
            return new TransactionRolledBackRuntimeException(e.getMessage(), e.getErrorCode(), e);
        }
        return new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e);
    }

    public interface RunnableWithException<E extends Throwable> {
        void run() throws E;
    }

    public interface ProviderWithException<E extends Throwable, R> {
        R call() throws E;
    }

    public interface ConsumerWithException<E extends Throwable, T> {
        void accept(T t) throws E;
    }

    public static <S extends AutoCloseable> void doClose(Set<S> objects) {
        doClose(objects, AutoCloseable::close);
    }

    public static <S> void doClose(Set<S> objects, ConsumerWithException<Exception, S> closer) {
        synchronized (objects) {
            forEachQuietly(objects, closer);
            objects.clear();
        }
    }

    public static <E extends Throwable, T> void forEach(Iterable<T> iterable, ConsumerWithException<E, T> consumer) throws E {
        for (T t : iterable) {
            consumer.accept(t);
        }
    }

    public static <E extends Throwable, T> void forEachQuietly(Iterable<T> iterable, ConsumerWithException<E, T> consumer) {
        for (T t : iterable) {
            try {
                consumer.accept(t);
            } catch (Throwable e) {
                // Ignore
            }
        }
    }

    public static <T> T unsupported(String method) {
        throw new JMSRuntimeException("Illegal call to " + method + " on a managed connection");
    }

    static void debug(String msg) {
        // TODO
    }

    static void debug(String msg, Throwable t) {
        // TODO
    }

    static void trace(String msg) {
        // TODO
    }

    static void trace(String msg, Throwable t) {
        // TODO
    }

    private Utils() { }
}
