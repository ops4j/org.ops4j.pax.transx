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
package org.ops4j.pax.transx.jdbc.utils;

import org.ops4j.pax.transx.connection.utils.UserPasswordManagedConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LazyAssociatableConnectionManager;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractConnectionHandle<C, CI extends AbstractConnectionHandle<C, CI>> {

    protected final LazyAssociatableConnectionManager cm;
    protected final UserPasswordManagedConnectionFactory mcf;
    protected final ConnectionRequestInfo cri;

    protected final AtomicBoolean closed = new AtomicBoolean(false);
    protected AbstractManagedConnection<C, CI> mc;

    protected AbstractConnectionHandle(LazyAssociatableConnectionManager cm,
                                       UserPasswordManagedConnectionFactory mcf,
                                       ConnectionRequestInfo cri) {
        this.cm = cm;
        this.mcf = mcf;
        this.cri = cri;
    }

    public void setAssociation(AbstractManagedConnection<C, CI> mc) {
        this.mc = mc;
    }

    public AbstractManagedConnection<C, CI> getAssociation() {
        return mc;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            doClose();
        }
    }

    @SuppressWarnings("unchecked")
    protected void doClose() {
        if (mc != null) {
            mc.connectionClosed((CI) this);
        }
    }

    public void connectionError(Exception e) {
        if (mc != null) {
            mc.connectionError(e);
        }
    }

    protected abstract <E extends Exception> E wrapException(String msg, Exception e);

    public <E extends Exception> AbstractManagedConnection<C, CI> getManagedConnection() throws E {
        if (isClosed()) {
            throw this.<E>wrapException("Connection has been closed", null);
        }
        if (mc == null && cm != null) {
            try {
                cm.associateConnection(this, mcf, cri);
            } catch (ResourceException e) {
                throw this.<E>wrapException("Failed lazy association with ManagedConnection", e);
            }
            if (mc == null) {
                throw this.<E>wrapException("Failed lazy association with ManagedConnection", null);
            }
        }
        assert mc != null;
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
