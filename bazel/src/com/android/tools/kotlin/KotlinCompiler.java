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

import com.android.tools.utils.JarOutputCompiler;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.CLICompiler;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;


/**
 * A wrapper for the Kotlin compiler.
 */
public class KotlinCompiler extends JarOutputCompiler {

    protected KotlinCompiler() {
        super("kotlinc");
    }

    public static void main(String[] args) throws IOException {
        System.exit(new KotlinCompiler().run(Arrays.asList(args)));
    }

    @Override
    protected boolean compile(List<String> files, String classPath, File outDir) {
        // Extracted from CLICompiler.java:
        // We depend on swing (indirectly through PSI or something), so we want to declare headless mode,
        // to avoid accidentally starting the UI thread
        System.setProperty("java.awt.headless", "true");

        List<String> args = new ArrayList<>(files.size() + 10);
        args.add("-d");
        args.add(outDir.getAbsolutePath());
        args.add("-cp");
        args.add(classPath.replaceAll(":", File.pathSeparator));
        args.addAll(files.stream().map(name -> name.replaceAll(":.*$", "")).collect(Collectors.toList()));
        ExitCode exit = CLICompiler.doMainNoExit(new K2JVMCompiler() {
            @Override
            protected void configureEnvironment(@NotNull CompilerConfiguration configuration,
                    @NotNull K2JVMCompilerArguments arguments) {
                super.configureEnvironment(configuration, arguments);
                for (String file : files) {
                    if (file.contains(":")) {
                        String[] split = file.split(":");
                        configuration.add(CommonConfigurationKeys.CONTENT_ROOTS, new JavaSourceRoot(new File(split[0]), split[1]));
                    }
                }
            }}, args.toArray(new String[]{}));
        return exit == ExitCode.OK;
    }
}
