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
package org.ops4j.pax.transx.jdbc.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Wrappers {

    private static final Set<Class<?>> CLASSES_TO_WRAP = new HashSet<>(Arrays.asList(
            Statement.class,
            PreparedStatement.class,
            CallableStatement.class,
            DatabaseMetaData.class,
            ResultSet.class
    ));

    private static final Object UNHANLED = new Object();

    private Wrappers() { }

    @SuppressWarnings("unchecked")
    static <H> H wrap(Class<H> clazz, ConnectionHandle c, H h) {
        return (H) wrap(clazz, c, h, Arrays.asList(wrapperIh(h), statementIh(c, h), getConnectionIh(c)));
    }

    private static Object wrap(Class<?> clazz, ConnectionHandle c, Object h, List<InvocationHandler> subHandlers) {
        if (h == null) {
            return null;
        }
        InvocationHandler ih = (proxy, method, args) -> {
            try {
                for (InvocationHandler sih : subHandlers) {
                    Object o = sih.invoke(proxy, method, args);
                    if (o != UNHANLED) {
                        return o;
                    }
                }
                Object result = method.invoke(h, args);
                if (CLASSES_TO_WRAP.contains(method.getReturnType())) {
                    result = wrap(method.getReturnType(), c, result,
                                  Arrays.asList(wrapperIh(result), statementIh(c, result), getConnectionIh(c)));
                }
                return result;
            } catch (InvocationTargetException e) {
                Throwable t = e.getCause();
                if (t instanceof Exception) {
                    c.connectionError((Exception) t);
                }
                throw e;
            }
        };
        return Proxy.newProxyInstance(h.getClass().getClassLoader(), new Class[] { clazz }, ih);
    }

    private static InvocationHandler getConnectionIh(ConnectionHandle c) {
        return (proxy, method, args) -> {
            if (method.getReturnType() == Connection.class
                    && method.getName().equals("getConnection")) {
                return c;
            }
            return UNHANLED;
        };
    }

    private static InvocationHandler statementIh(ConnectionHandle c, Object h) {
        return (proxy, method, args) -> {
            if (Statement.class.isAssignableFrom(method.getDeclaringClass())
                    && ResultSet.class == method.getReturnType()) {
                Object ret = method.invoke(h, args);
                InvocationHandler getStatementIh = (proxy2, method2, args2) -> {
                    if (ResultSet.class.isAssignableFrom(method2.getDeclaringClass())
                            && method2.getName().equals("getStatement")) {
                        return proxy;
                    }
                    return UNHANLED;
                };
                return Wrappers.wrap(method.getReturnType(), c, ret, Arrays.asList(wrapperIh(ret), getStatementIh));
            }
            return UNHANLED;
        };
    }

    private static InvocationHandler wrapperIh(Object h) {
        return (proxy, method, args) -> {
            if (method.getDeclaringClass() == Wrapper.class) {
                switch (method.getName()) {
                    case "unwrap":
                        if (((Class) args[0]).isInstance(h)) {
                            return ((Class) args[0]).cast(h);
                        } else {
                            return method.invoke(h, args);
                        }
                    case "isWrapperFor":
                        if (((Class) args[0]).isInstance(h)) {
                            return true;
                        } else {
                            return method.invoke(h, args);
                        }
                }
            }
            return UNHANLED;
        };

    }

}
