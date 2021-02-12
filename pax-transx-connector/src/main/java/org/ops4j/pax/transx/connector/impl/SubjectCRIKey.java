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

import java.util.Objects;
import javax.resource.spi.ConnectionRequestInfo;
import javax.security.auth.Subject;

public class SubjectCRIKey {

    private final Subject subject;
    private final ConnectionRequestInfo cri;
    private final transient int hashcode;

    public SubjectCRIKey(
            final Subject subject,
            final ConnectionRequestInfo cri) {
        this.subject = subject;
        this.cri = cri;
        this.hashcode = Objects.hash(subject, cri);
    }

    public Subject getSubject() {
        return subject;
    }

    public ConnectionRequestInfo getCri() {
        return cri;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubjectCRIKey that = (SubjectCRIKey) o;
        return hashcode == that.hashcode
                && Objects.equals(cri, that.cri)
                && Objects.equals(subject, that.subject);
    }

}
