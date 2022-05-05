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

import com.android.tools.utils.BazelMultiplexWorker;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;

import java.io.PrintStream;
import java.util.List;

import static org.jetbrains.kotlin.cli.common.CompilerSystemProperties.KOTLIN_COLORS_ENABLED_PROPERTY;
import static org.jetbrains.kotlin.cli.common.CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY;
import static org.jetbrains.kotlin.cli.jvm.compiler.CompatKt.setupIdeaStandaloneExecution;

/** A wrapper for the Kotlin compiler. */
public class KotlinCompiler {

    public static void main(String[] args) throws Exception {
        // Extracted from CLITool.doMain:
        System.setProperty("java.awt.headless", "true");
        KOTLIN_COLORS_ENABLED_PROPERTY.setValue("true");
        setupIdeaStandaloneExecution();

        // In order to support parallel builds in the same process, we prevent
        // the Kotlin environment from being disposed after each job.
        // This matches the behavior of JPS (see JpsKotlinCompilerRunner).
        KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.setValue("true");

        BazelMultiplexWorker.run(args, KotlinCompiler::compile);
    }

    private static int compile(List<String> args, PrintStream out) {
        ExitCode exit = new K2JVMCompiler().exec(out, args.toArray(new String[0]));
        if (exit.equals(ExitCode.INTERNAL_ERROR)) {
            // The Kotlin compiler could be in a bad state, e.g. out of memory.
            // Throw an exception to kill the Bazel persistent worker.
            throw new RuntimeException("the Kotlin compiler encountered an internal error");
        }
        return exit.getCode();
    }
}
