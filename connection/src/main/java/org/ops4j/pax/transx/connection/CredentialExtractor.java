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

import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.security.PasswordCredential;
import javax.security.auth.Subject;
import java.util.Arrays;
import java.util.Set;

public class CredentialExtractor {

    private final String userName;
    private final String password;

    public CredentialExtractor(Subject subject, ConnectionRequestInfo connectionRequestInfo, UserPasswordManagedConnectionFactory managedConnectionFactory) throws ResourceAdapterInternalException {
        assert managedConnectionFactory != null;

        if (connectionRequestInfo != null && !(connectionRequestInfo instanceof UserPasswordConnectionRequestInfo)) {
            throw new ResourceAdapterInternalException("ConnectionRequestInfo must be a UserPasswordConnectionRequestInfo, not a " + connectionRequestInfo.getClass().getName());
        }
        if (subject != null) {
            Set<PasswordCredential> credentials = subject.getPrivateCredentials(PasswordCredential.class);
            for (PasswordCredential passwordCredential : credentials) {
                if (managedConnectionFactory.equals(passwordCredential.getManagedConnectionFactory())) {
                    userName = passwordCredential.getUserName();
                    password = new String(passwordCredential.getPassword());
                    return;
                }
            }
            throw new ResourceAdapterInternalException("No credential found for this ManagedConnectionFactory: " + managedConnectionFactory);
        }
        if (connectionRequestInfo != null && ((UserPasswordConnectionRequestInfo) connectionRequestInfo).getUserName() != null) {
            userName = ((UserPasswordConnectionRequestInfo) connectionRequestInfo).getUserName();
            password = ((UserPasswordConnectionRequestInfo) connectionRequestInfo).getPassword();
            return;
        }
        userName = managedConnectionFactory.getUserName();
        password = managedConnectionFactory.getPassword();
    }

    public boolean matches(Subject subject, ConnectionRequestInfo connectionRequestInfo, UserPasswordManagedConnectionFactory managedConnectionFactory) throws ResourceAdapterInternalException {
        assert managedConnectionFactory != null;

        if (subject != null) {
            Set<PasswordCredential> credentials = subject.getPrivateCredentials(PasswordCredential.class);
            for (PasswordCredential passwordCredential : credentials) {
                if (managedConnectionFactory.equals(passwordCredential.getManagedConnectionFactory())) {
                    return (userName == null ? passwordCredential.getUserName() == null : userName.equals(passwordCredential.getUserName())
                        && (password == null ? passwordCredential.getPassword() == null : Arrays.equals(password.toCharArray(), passwordCredential.getPassword())));
                }
            }
            throw new ResourceAdapterInternalException("No credential found for this ManagedConnectionFactory: " + managedConnectionFactory);
        }
        if (connectionRequestInfo != null && ((UserPasswordConnectionRequestInfo) connectionRequestInfo).getUserName() != null) {
            return (userName.equals(((UserPasswordConnectionRequestInfo) connectionRequestInfo).getUserName()))
                && (password == null
                    ? ((UserPasswordConnectionRequestInfo) connectionRequestInfo).getPassword() == null
                    : password.equals(((UserPasswordConnectionRequestInfo) connectionRequestInfo).getPassword()));
        }
        return (userName == null ? managedConnectionFactory.getUserName() == null : userName.equals(managedConnectionFactory.getUserName())
            && (password == null ? managedConnectionFactory.getPassword() == null : password.equals(managedConnectionFactory.getPassword())));
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
}
