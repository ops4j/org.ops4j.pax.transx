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
package org.ops4j.pax.transx.connection.utils;

import javax.resource.spi.ConnectionRequestInfo;

public abstract class AbstractConnectionHandle<
        MCF extends AbstractManagedConnectionFactory<MCF, MC, C, CI>,
        MC extends AbstractManagedConnection<MCF, MC, C, CI>,
        C,
        CI extends AbstractConnectionHandle<MCF, MC, C, CI>> {

    protected final MCF mcf;
    protected final ConnectionRequestInfo cri;
    protected final MC mc;

    protected volatile boolean closed = false;

    protected AbstractConnectionHandle(MCF mcf,
                                       ConnectionRequestInfo cri,
                                       MC mc) {
        this.mcf = mcf;
        this.cri = cri;
        this.mc = mc;
    }

    public boolean isClosed() {
        return closed;
    }

    public void cleanup() {
    }

    public void close() {
        if (!closed) {
            synchronized (this) {
                if (!closed) {
                    closed = true;
                    doClose();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void doClose() {
        mc.connectionClosed((CI) this);
    }

    public void connectionError(Exception e) {
        mc.connectionError(e);
    }

    protected abstract <E extends Exception> E wrapException(String msg, Exception e);

    public <E extends Exception> MC getManagedConnection() throws E {
        if (isClosed()) {
            throw this.<E>wrapException("Connection has been closed", null);
        }
        return mc;
    }

    protected interface Runnable<T> {
        void run(T c) throws Exception;
    }

    protected interface Callable<T, R> {
        R call(T c) throws Exception;
    }

    protected <E extends Exception> void execute(Runnable<C> cb) throws E {
        try {
            cb.run(getManagedConnection().getPhysicalConnection());
        } catch (Exception e) {
            connectionError(e);
            throw this.<E>wrapException(null, e);
        }
    }

    protected <E extends Exception, R> R call(Callable<C, R> cb) throws E {
        try {
            return cb.call(getManagedConnection().getPhysicalConnection());
        } catch (Exception e) {
            connectionError(e);
            throw this.<E>wrapException(null, e);
        }
    }
}
