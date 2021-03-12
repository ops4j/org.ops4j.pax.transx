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
package org.ops4j.pax.transx.tm.impl.narayana;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.junit.Ignore;
import org.junit.Test;

public class ImportClosureTest {

    @Test
    @Ignore("Run only when needed")
    public void showClosureOfImports() throws IOException {
        File[] jars = new File("target/embed").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });

        Map<String, List<String>> imports = new LinkedHashMap<>();
        Map<String, Map<String, Boolean>> optionals = new LinkedHashMap<>();
        Map<String, List<String>> exports = new LinkedHashMap<>();

        if (jars != null) {
            for (File jar : jars) {
                JarFile jf = new JarFile(jar);
                String ip = jf.getManifest().getMainAttributes().getValue("Import-Package");
                String ep = jf.getManifest().getMainAttributes().getValue("Export-Package");

                Clause[] ipClauses = Parser.parseHeader(ip);
                Arrays.sort(ipClauses, Comparator.comparing(Clause::getName));
                Clause[] epClauses = Parser.parseHeader(ep);
                Arrays.sort(epClauses, Comparator.comparing(Clause::getName));

                imports.put(jar.getName(), Arrays.stream(ipClauses).map(Clause::getName).collect(Collectors.toList()));
                Map<String, Boolean> opts = new LinkedHashMap<>();
                for (Clause ipc : ipClauses) {
                    opts.put(ipc.getName(), "optional".equals(ipc.getDirective("resolution")));
                }
                optionals.put(jar.getName(), opts);
                exports.put(jar.getName(), Arrays.stream(epClauses).map(Clause::getName).collect(Collectors.toList()));

                System.out.printf("= %s%n", jar.getName());
                System.out.println("Import-Package");
                int c = 0;
                for (Clause ipc : ipClauses) {
                    System.out.printf(" - %s%s%n", ipc.getName(), opts.get(ipc.getName()) ? " (optional)" : "");
                    c++;
                }
                System.out.println("Export-Package");
                for (Clause epc : epClauses) {
                    System.out.printf(" - %s%n", epc.getName());
                }
            }
        }

        for (List<String> ipClauses : imports.values()) {
            for (List<String> epClauses : exports.values()) {
                ipClauses.removeAll(epClauses);
            }
        }

        System.out.println("External imports:");
        imports.forEach((jar, ipClauses) -> {
            System.out.printf("= %s%n", jar);
            System.out.println("Import-Package");
            for (String ipc : ipClauses) {
                System.out.printf(" - %s%s%n", ipc, optionals.get(jar).get(ipc) ? ";resolution:=optional" : "");
            }
        });
    }

}
