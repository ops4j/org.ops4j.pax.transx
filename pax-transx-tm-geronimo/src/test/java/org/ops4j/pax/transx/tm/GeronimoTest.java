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
package org.ops4j.pax.transx.tm;

import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.ops4j.pax.transx.tm.impl.geronimo.TransactionManagerWrapper;

import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GeronimoTest {

    @Mock
    LastResource xares1;
    @Mock
    NamedResource xares2;

    @Test
    public void testLRC() throws Exception {
        TransactionManager tm = createTm();

        assertTrue(tm.isLastResourceCommitSupported());

        when(xares2.prepare(any(Xid.class))).thenThrow(new XAException());

        tm.begin();
        tm.getTransaction().enlistResource(xares1);
        tm.getTransaction().enlistResource(xares2);
        try {
            tm.getTransaction().commit();
            fail("Expected a RollbackException");
        } catch (javax.transaction.RollbackException e) {
            // expected, ignore
        }

        verify(xares1).start(any(Xid.class), anyInt());
        verify(xares1, never()).prepare(any(Xid.class));
    }

    private TransactionManager createTm() throws XAException {
        return new TransactionManagerWrapper(new GeronimoTransactionManager());
    }
}
