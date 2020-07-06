/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.binaries;

import com.android.tools.bazel.model.Package;
import com.android.tools.bazel.model.Workspace;
import com.android.tools.bazel.parser.ast.Argument;
import com.android.tools.bazel.parser.ast.Build;
import com.android.tools.bazel.parser.ast.CallExpression;
import com.android.tools.bazel.parser.ast.CallStatement;
import com.android.tools.bazel.parser.ast.Expression;
import com.android.tools.bazel.parser.ast.ListExpression;
import com.android.tools.bazel.parser.ast.Statement;
import com.android.tools.utils.WorkspaceUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A temporary tool to set the manual arguments of the "unb." rules to match the arguments of their
 * corresponding main rules. Once we move over to the new rules this tool won't be needed anymore.
 */
public class FixUnbundledRules {

    private static final String PROJECT_ID = "unb";

    public static void main(String[] strings) throws Exception {

        Path workspace = null;

        Iterator<String> args = Arrays.asList(strings).iterator();
        while (args.hasNext()) {
            String arg = args.next();
            if (arg.equals("--workspace") && args.hasNext()) {
                workspace = Paths.get(args.next());
            } else {
                System.err.println("Unknown argument: " + arg);
                System.exit(1);
            }
        }
        if (workspace == null) {
            workspace = WorkspaceUtils.findWorkspace();
        }

        run(workspace);
    }

    public static int run(Path workspace) throws IOException {
        Map<String, Set<String>> defined = new HashMap<>();
        defined.put(
                "iml_module",
                new HashSet<>(
                        Arrays.asList(
                                "name",
                                "srcs",
                                "test_srcs",
                                "exclude",
                                "resources",
                                "test_resources",
                                "deps",
                                "exports",
                                "iml_files",
                                "package_prefixes",
                                "runtime_deps",
                                "test_runtime_deps",
                                "test_friends",
                                "visibility")));
        defined.put("java_import", new HashSet<>(Arrays.asList("name", "jars")));

        Workspace w = new Workspace(workspace.toFile(), "");
        w.loadAllPackages(workspace.toFile());

        for (Package p : w.getPackages()) {
            // The following packages cannot be parsed by this tool because they contain
            // unsupported constructs. And that also means that they do not contain rules
            // we maintain. As opposed to iml_to_build, that only attempts to parse BUILD
            // files that correspond to iml files, this tool attempts to parse every
            // package.
            if (p.getName().equals("tools/base/build-system/integration-test")
                    || p.getName().equals("tools/base/deploy/installer")
                    || p.getName()
                            .equals(
                                    "tools/vendor/google3/blaze/java/com/google/devtools/intellij/blaze/plugin/aswb")
                    || p.getName().startsWith("tools/external")
                    || p.getName().startsWith("external")) {
                continue;
            }
            Build buildFile = null;
            try {
                buildFile = p.getBuildFile();
            } catch (Exception e) {
                System.err.println("Error parsing package " + p.getName());
                throw e;
            }
            boolean update = false;
            for (Statement statement : buildFile.getStatements()) {
                if (!(statement instanceof CallStatement)) {
                    continue;
                }
                CallExpression call = ((CallStatement) statement).getCall();
                String literal = call.getLiteralArgument("name");
                if (literal != null && literal.startsWith("\"" + PROJECT_ID + ".")) {
                    String original = literal.substring(PROJECT_ID.length() + 2, literal.length() - 1);
                    CallStatement oCall = buildFile.getCall(original);
                    if (oCall == null) {
                        System.out.println(
                                "Cannot find original rule " + p.getName() + " : " + original);
                        continue;
                    }
                    if (!defined.containsKey(call.getLiteral())) {
                        continue;
                    }
                    Set<String> args = new HashSet<>();
                    for (Argument a : call.getArguments()) {
                        args.add(a.getName());
                    }

                    // For all arguments that are not managed, copy them to the new rule
                    boolean tags = false;
                    for (Argument a : oCall.getCall().getArguments()) {
                        if (defined.get(call.getLiteral()).contains(a.getName())) {
                            // Leave generated fields alone.
                            continue;
                        }
                        if (a.getName().equals("tags")) {
                            ListExpression le = ListExpression.build(Arrays.asList("manual", PROJECT_ID));
                            if (a.getExpression() instanceof ListExpression) {
                                for (Expression e : ((ListExpression) a.getExpression()).getExpressions()) {
                                    le.add(e);
                                }
                            }
                            call.setArgument("tags", le);
                            tags = true;
                        } else {
                            call.setArgument(a.getName(), a.getExpression());
                        }
                        statement.setHidden(false);
                        update = true;
                    }
                    if (!tags) {
                        call.addElementToList("tags", "manual");
                        call.addElementToList("tags", PROJECT_ID);
                    }
                }
            }
            if (update) {
                File tmp = File.createTempFile("BUILD", "test");
                try (FileOutputStream fileOutputStream = new FileOutputStream(tmp);
                        OutputStreamWriter outputStreamWriter =
                                new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
                        PrintWriter writer = new PrintWriter(outputStreamWriter)) {
                    buildFile.write(writer);
                }
                Files.copy(
                        tmp.toPath(),
                        p.findBuildFile().toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            }
        }
        return 0;
    }
}
