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
package org.ops4j.pax.transx.log4j2.extra;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

@Plugin(name = "AssertionAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class AssertionAppender extends AbstractAppender {

    private static final Map<String, LogEvent> MESSAGES = new ConcurrentHashMap<>();
    private static volatile boolean capture = false;

    public AssertionAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    @Override
    public void append(LogEvent event) {
        MESSAGES.put(event.getMessage().getFormattedMessage(), event);
    }

    @PluginFactory
    public static AssertionAppender factory(
            @PluginAttribute(value = "name", defaultString = "null") final String name) {

        Bundle bundle = FrameworkUtil.getBundle(AssertionAppender.class);

        return new AssertionAppender(name, null, null, true, Property.EMPTY_ARRAY);
    }

    public static boolean findText(final String... texts) {
        for (Map.Entry<String, LogEvent> entry : MESSAGES.entrySet()) {
            String message = entry.getKey();
            boolean found = true;

            for (String text : texts) {
                found = message.contains(text);
                if (!found) {
                    ThrowableProxy throwable = entry.getValue().getThrownProxy();
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

    public static void clear() {
        MESSAGES.clear();
    }

    public static void startCapture() {
        clear();
        capture = true;
    }

    public static void stopCapture() {
        capture = false;
        clear();
    }

}
