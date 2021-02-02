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

	static LoggerFactoryDelegate loggerFactoryDelegate = new Slf4JLoggerFactoryDelegate();

	public static final Logger createLogger(Class<?> clazz) {
		return loggerFactoryDelegate.createLogger(clazz);
	}

	static void setLoggerFactoryDelegate(LoggerFactoryDelegate loggerFactoryDelegate) {
		LoggerFactory.loggerFactoryDelegate = loggerFactoryDelegate;
	}

}
