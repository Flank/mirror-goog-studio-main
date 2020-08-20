/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.kotlin;

import static org.jetbrains.kotlin.cli.common.messages.PlainTextMessageRenderer.KOTLIN_COLORS_ENABLED_PROPERTY;

import com.android.tools.utils.BazelWorker;
import com.android.tools.utils.JarOutputCompiler;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.kotlin.cli.common.CLICompiler;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;

/**
 * A wrapper for the Kotlin compiler.
 */
public class KotlinCompiler extends JarOutputCompiler {

    private String moduleName;
    private String jvmTarget = "1.8";
    private List<String> friends = new LinkedList<>();
    private boolean noJdk = false;

    protected KotlinCompiler() {
        super("kotlinc");
        moduleName = "dummy";
    }

    public static void main(String[] args) throws Exception {
        BazelWorker.run(args, compilerArgs -> new KotlinCompiler().run(compilerArgs));
    }

    @Override
    protected List<String> filterOptions(List<String> args) {
        LinkedList<String> filtered = new LinkedList<>();
        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("--module_name") && it.hasNext()) {
                moduleName = it.next();
            } else if (arg.equals("--friend_dir") && it.hasNext()) {
                friends.add(it.next());
            } else if (arg.equals("--jvm-target") && it.hasNext()) {
                jvmTarget = it.next();
            } else if (arg.equals("--no-jdk")) {
                noJdk = true;
            } else {
                filtered.add(arg);
            }
        }
        return filtered;
    }

    @Override
    protected boolean compile(List<String> files, String classPath, File outDir) {
        // Extracted from CLITool.doMain:
        // We depend on swing (indirectly through PSI or something), so we want to declare headless mode,
        // to avoid accidentally starting the UI thread
        System.setProperty("java.awt.headless", "true");
        System.setProperty(KOTLIN_COLORS_ENABLED_PROPERTY, "true");

        List<String> args = new ArrayList<>(files.size() + 16);

        // Disable warnings, mirroring what is done for javac in Bazel.
        args.add("-nowarn");

        args.add("-module-name");
        args.add(moduleName);

        // workaround for https://youtrack.jetbrains.com/issue/KT-37435
        args.add("-Xno-optimized-callable-references");
        // workaround for https://github.com/Kotlin/dokka/issues/1272
        // TODO: remove once everything is moved to Kotlin 1.4
        args.add("-Xno-kotlin-nothing-value-exception");

        args.add("-Xjvm-default=enable");
        if (jvmTarget != null) {
            args.add("-jvm-target");
            args.add(jvmTarget);
        }
        if (noJdk) {
            args.add("-no-jdk");
        }
        if (!friends.isEmpty()) {
            List<String> friendPaths = friends.stream()
                    .map(friend -> new File(friend).getAbsolutePath())
                    .collect(Collectors.toList());
            args.add("-Xfriend-paths=" + String.join(",", friendPaths));
        }

        args.add("-d");
        args.add(outDir.getAbsolutePath());

        args.add("-cp");
        args.add(classPath.replace(":", File.pathSeparator));

        args.addAll(files);

        ExitCode exit = CLICompiler.doMainNoExit(new K2JVMCompiler(), args.toArray(new String[0]));
        if (exit.equals(ExitCode.INTERNAL_ERROR)) {
            // The Kotlin compiler could be in a bad state, e.g. out of memory.
            // Throw an exception to kill the Bazel persistent worker.
            throw new RuntimeException("the Kotlin compiler encountered an internal error");
        }
        ensureManifestFile(outDir);
        return exit == ExitCode.OK;
    }

    private static void ensureManifestFile(File outDir) {
        try {
            File manifest = new File(outDir, "META-INF/MANIFEST.MF");
            if (!manifest.exists()) {
                manifest.getParentFile().mkdirs();
                try(PrintWriter writer = new PrintWriter(manifest)) {
                    writer.println("Manifest-Version: 1.0");
                }
            }
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }
}
