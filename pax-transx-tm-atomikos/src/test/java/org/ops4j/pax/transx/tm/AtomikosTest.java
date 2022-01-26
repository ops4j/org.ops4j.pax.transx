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
package org.ops4j.pax.transx.tm;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.ops4j.pax.transx.tm.impl.atomikos.TransactionManagerWrapper;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AtomikosTest {

    @Mock
    LastResource xares1;
    @Mock
    NamedResource xares2;
    @Spy
    ResourceFactory rf1 = new ResourceFactory() {
        @Override
        public String getName() {
            return "rf1";
        }

        @Override
        public NamedResource create() {
            return xares1;
        }

        @Override
        public void release(NamedResource resource) {
        }
    };
    @Spy
    ResourceFactory rf2 = new ResourceFactory() {
        @Override
        public String getName() {
            return "rf2";
        }

        @Override
        public NamedResource create() {
            return xares2;
        }

        @Override
        public void release(NamedResource resource) {
        }
    };

    @Before
    public void setUp() throws URISyntaxException, IOException, XAException {
        Path basedir = Paths.get(getClass().getClassLoader().getResource("foo").toURI()).getParent().getParent().getParent();
        Path dir = basedir.resolve("target/data/atomikos");
        Files.createDirectories(dir);
        System.setProperty("com.atomikos.icatch.log_base_dir", dir.toString());

        when(xares1.isSameRM(xares1)).thenReturn(true);
        when(xares2.isSameRM(xares2)).thenReturn(true);
    }

    @Test
    public void testLRC() throws Exception {
        TransactionManager tm = createTm();
        tm.registerResource(rf1);
        tm.registerResource(rf2);

        assertFalse(tm.isLastResourceCommitSupported());

        tm.begin();
        tm.getTransaction().enlistResource(xares1);
        tm.getTransaction().enlistResource(xares2);
        tm.getTransaction().commit();

        verify(xares1).start(any(Xid.class), anyInt());
        verify(xares2).start(any(Xid.class), anyInt());
        verify(xares1).prepare(any(Xid.class));
        verify(xares2).prepare(any(Xid.class));
        verify(xares1).commit(any(Xid.class), anyBoolean());
        verify(xares2).commit(any(Xid.class), anyBoolean());
        verify(xares1).end(any(Xid.class), anyInt());
        verify(xares2).end(any(Xid.class), anyInt());
    }

    private TransactionManager createTm() throws XAException {
        return new TransactionManagerWrapper();
    }
}
