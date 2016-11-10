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

package com.android.build.gradle.internal.transforms;

import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;
import org.mockito.Mockito;

/** Utilities for testing the Jack toolchain. */
public class JackTestUtils {

    public enum SourceFile {
        USER_MODEL(
                "UserModel.java",
                "package com.example.jack;\n"
                        + "public class UserModel {\n"
                        + "    private String name;\n"
                        + "    public UserModel(String name) {\n"
                        + "        this.name = name;\n"
                        + "    }\n"
                        + "}\n"),
        ACCOUNT(
                "Account.java",
                "package com.example.jack;\n"
                        + "public class Account {\n"
                        + "    private String accountId;\n"
                        + "}\n"),
        PAYMENT(
                "Payment.java",
                "package com.example.jack;\n"
                        + "public class Payment {\n"
                        + "    private UserModel userModel;\n"
                        + "    private Account account;\n"
                        + "}\n");

        private final String fileName;
        private final String content;

        SourceFile(String fileName, String content) {
            this.fileName = fileName;
            this.content = content;
        }
    }

    /** Compiles the specified source files. */
    public static void compileSources(
            @NonNull Collection<File> sources,
            @NonNull JackProcessOptions options,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull ErrorReporter errorReporter,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull Context context,
            @NonNull File androidJack,
            @NonNull TransformOutputProvider outputProvider)
            throws TransformException, InterruptedException, IOException {
        FileCollection emptyJackPluginsCollection = Mockito.mock(FileCollection.class);
        when(emptyJackPluginsCollection.getFiles()).thenReturn(ImmutableSet.of());
        JackCompileTransform transform =
                new JackCompileTransform(
                        options,
                        () -> buildToolInfo,
                        errorReporter,
                        javaProcessExecutor,
                        emptyJackPluginsCollection,
                        emptyJackPluginsCollection);

        TransformInput androidInput =
                new SimpleJarTransformInput(
                        SimpleJarInput.builder(androidJack)
                                .setContentTypes(TransformManager.CONTENT_JACK)
                                .create());

        TransformInput sourcesInput =
                new SimpleJarTransformInput(
                        sources.stream()
                                .map(f -> SimpleJarInput.builder(f).create())
                                .collect(Collectors.toList()));

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(sourcesInput))
                        .addReferencedInputs(ImmutableList.of(androidInput))
                        .addOutputProvider(outputProvider)
                        .build());
    }

    /** Compiles the specified source files using default transform output provider. */
    public static void compileSources(
            @NonNull Collection<File> sources,
            @NonNull JackProcessOptions options,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull ErrorReporter errorReporter,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull Context context,
            @NonNull File androidJack)
            throws TransformException, InterruptedException, IOException {
        compileSources(
                sources,
                options,
                buildToolInfo,
                errorReporter,
                javaProcessExecutor,
                context,
                androidJack,
                Mockito.mock(TransformOutputProvider.class));
    }

    /** Creates a new source file. */
    public static File fileForClass(@NonNull File baseDir, @NonNull SourceFile file) {
        File sourceFile = FileUtils.join(baseDir, file.fileName);
        if (sourceFile.isFile()) {
            // already exists
            return sourceFile;
        }
        try {
            Files.write(file.content, sourceFile, Charsets.UTF_8);
            return sourceFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JackTestUtils() {
        // empty
    }
}
