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

import java.util.function.Consumer;

/**
 * Transaction
 */
public interface Transaction {

    /**
     * Check if a transaction is running or not
     */
    boolean isActive();

    /**
     * Get the transaction status
     */
    Status getStatus();

    /**
     * Commit the transaction
     */
    void commit() throws Exception;

    /**
     * Rollback the transaction
     */
    void rollback() throws Exception;

    /**
     * Mark the transaction as rollback-only
     */
    void setRollbackOnly() throws Exception;

    /**
     * Suspend this transaction
     */
    void suspend() throws Exception;

    /**
     * Resume this transaction
     */
    void resume() throws Exception;

    /**
     * Enlist the resource
     */
    void enlistResource(NamedResource xares) throws Exception;

    /**
     * Delist the resource
     */
    void delistResource(NamedResource xares, int flags) throws Exception;

    /**
     * Add a pre- and/or post-completion job
     */
    void synchronization(Runnable pre, Consumer<Status> post);

}
