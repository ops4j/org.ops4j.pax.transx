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
package com.atomikos.logging;

public final class LoggerFactory {

	private LoggerFactory() {

	}

	static LoggerFactoryDelegate loggerFactoryDelegate;

	public static Logger createLogger(Class<?> clazz) {
		return loggerFactoryDelegate.createLogger(clazz);
	}

	static void setLoggerFactoryDelegate(LoggerFactoryDelegate loggerFactoryDelegate) {
		LoggerFactory.loggerFactoryDelegate = loggerFactoryDelegate;
	}

	static {
		String cname = null;
		//let's try with
		try {
			Class.forName("org.slf4j.LoggerFactory");
			cname = "com.atomikos.logging.Slf4JLoggerFactoryDelegate";
		} catch (Throwable ignored) {
		}

		try {
			if (cname != null) {
				Class<?> loggerClass = Class.forName(cname.trim(), true, LoggerFactory.class.getClassLoader());
				loggerFactoryDelegate = (LoggerFactoryDelegate) loggerClass.newInstance();
			} else {
				fallbackToDefault();
			}
		} catch (Throwable ex) {
			// ignore - if we get here, some issue prevented the logger class
			// from being loaded.
			// maybe a ClassNotFound or NoClassDefFound or similar. Just use
			// j.u.l
			fallbackToDefault();
		}
		Logger logger = createLogger(LoggerFactory.class);
		logger.logDebug("Using " + loggerFactoryDelegate + " for logging.");
	}

	private static void fallbackToDefault() {
		setLoggerFactoryDelegate(new JULLoggerFactoryDelegate());
	}
}
