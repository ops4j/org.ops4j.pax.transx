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
package org.ops4j.pax.transx.connection;

/**
 * Interface that can be used to classify an Exception raised on a physical connection.
 */
public interface ExceptionSorter {

    /**
     * Determine if an Exception is fatal implying that the underlying connection
     * is no longer usable.
     * @param e the Exception to inspect
     * @return true if the Exception implies the connection should no longer be used
     */
    boolean isExceptionFatal(Exception e);

    /**
     * Whether to try to rollback work on the connection if a "fatal" error is encountered.
     * This should only be true if we can't actually determine if the connection is broken, since
     * if it is broken the rollback won't succeed.
     * @return true if a rollback should be attempted
     */
    boolean rollbackOnFatalException();
}
