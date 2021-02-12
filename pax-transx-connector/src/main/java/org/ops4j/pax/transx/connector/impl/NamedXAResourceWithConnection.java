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
package org.ops4j.pax.transx.connector.impl;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.ops4j.pax.transx.tm.NamedResource;

public class NamedXAResourceWithConnection implements NamedResource {

    private final ManagedConnection mc;
    private final NamedResource delegate;

    NamedXAResourceWithConnection(ManagedConnection mc, NamedResource delegate) throws ResourceException {
        this.mc = mc;
        this.delegate = delegate;
    }

    public ManagedConnection getManagedConnection() {
        return mc;
    }

    public String getName() {
        return delegate.getName();
    }

    public void commit(Xid xid, boolean b) throws XAException {
        delegate.commit(xid, b);
    }

    public void end(Xid xid, int i) throws XAException {
        delegate.end(xid, i);
    }

    public void forget(Xid xid) throws XAException {
        delegate.forget(xid);
    }

    public int getTransactionTimeout() throws XAException {
        return delegate.getTransactionTimeout();
    }

    public boolean isSameRM(XAResource xaResource) throws XAException {
        return delegate.isSameRM(xaResource);
    }

    public int prepare(Xid xid) throws XAException {
        return delegate.prepare(xid);
    }

    public Xid[] recover(int i) throws XAException {
        return delegate.recover(i);
    }

    public void rollback(Xid xid) throws XAException {
        delegate.rollback(xid);
    }

    public boolean setTransactionTimeout(int i) throws XAException {
        return delegate.setTransactionTimeout(i);
    }

    public void start(Xid xid, int i) throws XAException {
        delegate.start(xid, i);
    }
}
