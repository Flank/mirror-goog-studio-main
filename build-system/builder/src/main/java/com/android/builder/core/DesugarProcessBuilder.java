/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessEnvBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A builder to create an information necessary to run Desugar in a separate JVM process. */
public final class DesugarProcessBuilder extends ProcessEnvBuilder<DesugarProcessBuilder> {
    private static final String DESUGAR_MAIN = "com.google.devtools.build.android.desugar.Desugar";

    private final boolean verbose;
    private final Path input;
    private final List<Path> classpath;
    private final Path output;
    private final List<Path> bootClasspath;
    private final int minSdkVersion;

    public DesugarProcessBuilder(
            boolean verbose,
            @NonNull Path input,
            @NonNull List<Path> classpath,
            @NonNull Path output,
            @NonNull List<Path> bootClasspath,
            int minSdkVersion) {
        this.verbose = verbose;
        this.input = input;
        this.classpath = classpath;
        this.output = output;
        this.bootClasspath = bootClasspath;
        this.minSdkVersion = minSdkVersion;
    }

    @NonNull
    public JavaProcessInfo build() throws ProcessException, IOException {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.addEnvironments(mEnvironment);

        builder.setClasspath(getExtractedDesugarDeployJar().toAbsolutePath().toString());
        builder.setMain(DESUGAR_MAIN);
        builder.addJvmArg("-Xmx64M");

        if (verbose) {
            builder.addArgs("--verbose");
        }

        builder.addArgs("--input", input.toString());
        builder.addArgs("--output", output.toString());
        classpath.forEach(c -> builder.addArgs("--classpath_entry", c.toString()));
        bootClasspath.forEach(b -> builder.addArgs("--bootclasspath_entry", b.toString()));

        // Disable min sdk version param until we have a new DX supporting default and static
        // interface methods merged. Current one will blow up.
        // builder.addArgs("--min_sdk_version", Integer.toString(minSdkVersion));

        return builder.createJavaProcess();
    }

    @NonNull
    private Path getExtractedDesugarDeployJar() throws IOException {
        // TODO - extract to the same location per plugin version
        Path tmp = Files.createTempDirectory("").resolve("desugar_deploy.jar");
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("desugar_deploy.jar")) {
            Files.copy(in, tmp);
            tmp.toFile().deleteOnExit();
        }
        return tmp;
    }
}
