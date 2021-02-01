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
package org.ops4j.pax.transx.connection;

/**
 * Implementation of a generic @{link ExceptionSorter} that indicates that
 * all Exceptions are fatal.
 *
 * @version $Revision: 805 $ $Date: 2010-11-11 15:06:35 -0800 (Thu, 11 Nov 2010) $
 */
public class AllExceptionsAreFatalSorter implements ExceptionSorter {

    /**
     * Always returns true.
     *
     * @param e the Exception to inspect
     * @return true indicating all Exceptions are fatal
     */
    public boolean isExceptionFatal(Exception e) {
        return true;
    }

    /**
     * we can't tell if the connection is valid but we will close it.... rollback so nothing is accidentally committed
     * @return true
     */
    public boolean rollbackOnFatalException() {
        return true;
    }

}
