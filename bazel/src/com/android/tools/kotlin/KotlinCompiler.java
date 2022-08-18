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

import static org.jetbrains.kotlin.cli.common.CompilerSystemProperties.KOTLIN_COLORS_ENABLED_PROPERTY;
import static org.jetbrains.kotlin.cli.common.CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY;
import static org.jetbrains.kotlin.cli.jvm.compiler.CompatKt.setupIdeaStandaloneExecution;

import com.android.tools.utils.BazelMultiplexWorker;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import java.io.PrintStream;
import java.util.List;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;

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
        clearJarCache();
        return exit.getCode();
    }

    private static void clearJarCache() {
        // This is adapted from CompileServiceImpl.clearJarCache in the Kotlin compiler daemon.
        // It is needed because CoreJarFileSystem stores a global cache of jar file central
        // directories---and this cache can become stale when it is used across multiple
        // compilations. One would normally expect this sort of cleanup to be handled
        // by K2JVMCompiler directly through the project disposable---but this is not the case.
        // Instead, persistent workers must clear the caches manually. For example, in Gradle, you
        // can see that GradleKotlinCompilerWork.compileWithDaemon invokes daemon.clearJarCache
        // after each compilation job. Side note: it would make more sense (and improve performance)
        // to only clear these caches after *all* jobs from a given build session have finished.
        // However, this does not seem feasible for Bazel persistent workers. And anyway this
        // optimization is not implemented in Gradle either (there is just a to-do comment).
        ZipHandler.clearFileAccessorCache();
        KotlinCoreApplicationEnvironment appEnv =
                KotlinCoreEnvironment.Companion.getApplicationEnvironment();
        if (appEnv != null) {
            VirtualFileSystem jarFileSystem = appEnv.getJarFileSystem();
            if (jarFileSystem instanceof CoreJarFileSystem) {
                ((CoreJarFileSystem) jarFileSystem).clearHandlersCache();
            }
        }
    }
}
