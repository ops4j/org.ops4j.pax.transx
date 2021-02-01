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
package org.ops4j.pax.transx.itests.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AssertionAppender extends AppenderBase<ILoggingEvent> {

    private static final Map<String, ILoggingEvent> MESSAGES = new ConcurrentHashMap<>();
    private static volatile boolean capture = false;

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (capture) {
            MESSAGES.put(eventObject.getFormattedMessage(), eventObject);
        }
    }

    public static boolean findText(final String... texts) {
        for (Map.Entry<String, ILoggingEvent> entry : MESSAGES.entrySet()) {
            String message = entry.getKey();
            boolean found = true;

            for (String text : texts) {
                found = message.contains(text);
                if (!found) {
                    IThrowableProxy throwable = entry.getValue().getThrowableProxy();
                    if (throwable != null && throwable.getMessage() != null) {
                        found = throwable.getMessage().contains(text);
                        if (!found) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }

            if (found) {
                return true;
            }
        }

        return false;
    }

    public static final void clear() {
        MESSAGES.clear();
    }

    public static final void startCapture() {
        clear();
        capture = true;
    }

    public static final void stopCapture() {
        capture = false;
        clear();
    }
}
