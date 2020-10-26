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
import java.util.List;
import org.jetbrains.kotlin.cli.common.CLICompiler;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;

/**
 * A wrapper for the Kotlin compiler.
 */
public class KotlinCompiler extends JarOutputCompiler {

    protected KotlinCompiler() {
        super("kotlinc");
    }

    public static void main(String[] args) throws Exception {
        BazelWorker.run(args, compilerArgs -> new KotlinCompiler().run(compilerArgs));
    }

    @Override
    protected boolean compile(List<String> forwardedArgs, String classPath, File outDir) {
        // Extracted from CLITool.doMain:
        System.setProperty("java.awt.headless", "true");
        System.setProperty(KOTLIN_COLORS_ENABLED_PROPERTY, "true");

        List<String> args = new ArrayList<>(forwardedArgs.size() + 16);

        args.add("-d");
        args.add(outDir.getAbsolutePath());

        args.add("-cp");
        args.add(classPath.replace(":", File.pathSeparator));

        args.addAll(forwardedArgs);

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
