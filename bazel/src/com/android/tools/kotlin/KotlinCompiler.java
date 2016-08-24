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
import org.jetbrains.kotlin.cli.common.CLICompiler;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;

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
    protected void compile(List<String> files, String classPath, File outDir) {
        // Extracted from CLICompiler.java:
        // We depend on swing (indirectly through PSI or something), so we want to declare headless mode,
        // to avoid accidentally starting the UI thread
        System.setProperty("java.awt.headless", "true");

        List<String> args = new ArrayList<>(files.size() + 10);
        args.add("-d");
        args.add(outDir.getAbsolutePath());
        args.add("-cp");
        args.add(classPath.replaceAll(":", File.pathSeparator));
        args.addAll(files);
        CLICompiler.doMainNoExit(new K2JVMCompiler(), args.toArray(new String[]{}));
    }
}
