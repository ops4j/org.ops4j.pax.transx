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
package org.ops4j.pax.transx.jms.impl;

import org.ops4j.pax.transx.connection.UserPasswordConnectionRequestInfo;

import java.util.Objects;

public class ConnectionRequestInfoImpl implements UserPasswordConnectionRequestInfo {

    private final boolean transacted;
    private final int acknowledgeMode;
    private final String userName;
    private final String password;
    private final String clientID;

    public ConnectionRequestInfoImpl(boolean transacted, int acknowledgeMode, String userName, String password, String clientID) {
        this.transacted = transacted;
        this.acknowledgeMode = acknowledgeMode;
        this.userName = userName;
        this.password = password;
        this.clientID = clientID;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public int getAcknowledgeMode() {
        return acknowledgeMode;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getClientID() {
        return clientID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionRequestInfoImpl that = (ConnectionRequestInfoImpl) o;
        return transacted == that.transacted &&
               acknowledgeMode == that.acknowledgeMode &&
               Objects.equals(userName, that.userName) &&
               Objects.equals(password, that.password) &&
               Objects.equals(clientID, that.clientID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transacted, acknowledgeMode, userName, password, clientID);
    }
}
