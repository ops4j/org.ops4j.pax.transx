/**
 * Copyright (C) 2000-2020 Atomikos <info@atomikos.com>
 *
 * LICENSE CONDITIONS
 *
 * See http://www.atomikos.com/Main/WhichLicenseApplies for details.
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
