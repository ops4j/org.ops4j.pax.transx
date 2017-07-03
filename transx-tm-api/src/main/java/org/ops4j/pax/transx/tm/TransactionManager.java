/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ops4j.pax.transx.tm;

/**
 * JTA Transaction manager
 */
public interface TransactionManager {

    /**
     * Check if LRC is supported by this transaction manager
     */
    boolean isLastResourceCommitSupported();

    /**
     * Returns the current transaction, always non null.
     */
    Transaction getTransaction();

    Transaction suspend();

    void resume(Transaction transaction);

    /**
     * Register a resource for recovery
     */
    void registerResource(RecoverableResourceFactory resource);

    void unregisterResource(String name);

    RecoverableResourceFactory getResource(String name);

}
