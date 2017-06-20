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
package org.ops4j.pax.transx.connector.impl;

import org.ops4j.pax.transx.connector.PoolingAttributes;

import java.time.Duration;

public class SinglePool implements PoolingSupport {

    private int maxSize;
    private int minSize;
    private Duration blockingTimeout;
    private Duration idleTimeout;
    private boolean backgroundValidation;
    private Duration validatingPeriod;
    private boolean validateOnMatch;
    private boolean matchOne;
    private boolean matchAll;
    private boolean selectOneAssumeMatch;

    private transient PoolingAttributes pool;

    public SinglePool(int maxSize, int minSize, Duration blockingTimeout, Duration idleTimeout, boolean backgroundValidation, Duration validatingPeriod, boolean validateOnMatch, boolean matchOne, boolean matchAll, boolean selectOneAssumeMatch) {
        this.maxSize = maxSize;
        this.minSize = minSize;
        this.blockingTimeout = blockingTimeout;
        this.idleTimeout = idleTimeout;
        this.backgroundValidation = backgroundValidation;
        this.validatingPeriod = validatingPeriod;
        this.validateOnMatch = validateOnMatch;
        this.matchOne = matchOne;
        this.matchAll = matchAll;
        this.selectOneAssumeMatch = selectOneAssumeMatch;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getMinSize() {
        return minSize;
    }

    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    public Duration getBlockingTimeout() {
        return blockingTimeout;
    }

    public void setBlockingTimeout(Duration blockingTimeout) {
        this.blockingTimeout = blockingTimeout;
        if (pool != null) {
            pool.setBlockingTimeout(blockingTimeout);
        }
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
        if (pool != null) {
            pool.setIdleTimeout(idleTimeout);
        }
    }

    public boolean isBackgroundValidation() {
        return backgroundValidation;
    }

    public void setBackgroundValidation(boolean backgroundValidation) {
        this.backgroundValidation = backgroundValidation;
    }

    public Duration getValidatingPeriod() {
        return validatingPeriod;
    }

    public void setValidatingPeriod(Duration validatingPeriod) {
        this.validatingPeriod = validatingPeriod;
        if (pool != null) {
            pool.setValidatingPeriod(validatingPeriod);
        }
    }

    public boolean isValidateOnMatch() {
        return validateOnMatch;
    }

    public void setValidateOnMatch(boolean validateOnMatch) {
        this.validateOnMatch = validateOnMatch;
    }

    public boolean isMatchOne() {
        return matchOne;
    }

    public void setMatchOne(boolean matchOne) {
        this.matchOne = matchOne;
    }

    public boolean isMatchAll() {
        return matchAll;
    }

    public void setMatchAll(boolean matchAll) {
        this.matchAll = matchAll;
    }

    public boolean isSelectOneAssumeMatch() {
        return selectOneAssumeMatch;
    }

    public void setSelectOneAssumeMatch(boolean selectOneAssumeMatch) {
        this.selectOneAssumeMatch = selectOneAssumeMatch;
    }

    public ConnectionInterceptor addPoolingInterceptors(ConnectionInterceptor tail) {
        if (isMatchAll()) {
            SinglePoolMatchAllConnectionInterceptor pool = new SinglePoolMatchAllConnectionInterceptor(tail,
                    getMaxSize(),
                    getMinSize(),
                    getBlockingTimeout(),
                    getIdleTimeout(),
                    isBackgroundValidation(),
                    getValidatingPeriod(),
                    isValidateOnMatch());
            this.pool = pool;
            return pool;
        } else {
            SinglePoolConnectionInterceptor pool = new SinglePoolConnectionInterceptor(tail,
                    getMaxSize(),
                    getMinSize(),
                    getBlockingTimeout(),
                    getIdleTimeout(),
                    isBackgroundValidation(),
                    getValidatingPeriod(),
                    isValidateOnMatch(),
                    isSelectOneAssumeMatch());
            this.pool = pool;
            return pool;
        }
    }

    public int getPartitionCount() {
        return 1;
    }

    public int getPartitionMaxSize() {
        return maxSize;
    }

    public void setPartitionMaxSize(int maxSize) throws InterruptedException {
        if (pool != null) {
            pool.setPartitionMaxSize(maxSize);
        }
        this.maxSize = maxSize;
    }

    public int getPartitionMinSize() {
        return minSize;
    }

    public void setPartitionMinSize(int minSize) {
        if (pool != null) {
            pool.setPartitionMinSize(minSize);
        }
        this.minSize = minSize;
    }

    public int getIdleConnectionCount() {
        return pool == null ? 0 : pool.getIdleConnectionCount();
    }

    public int getConnectionCount() {
        return pool == null ? 0 : pool.getConnectionCount();
    }
}
