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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ObjectArrays;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

/** Task to compile the atoms final R classes. */
public class JavaCompileAtomResClass extends DefaultAndroidTask {

    @TaskAction
    public void compile() throws InterruptedException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();

        for (AtomConfig.AtomInfo atomInfo : atomConfigTask.getAtomInfoCollection()) {
            executor.execute(
                    () -> {
                        List<String> srcFiles =
                                FileUtils.find(
                                                atomInfo.getSourceOutputDir(),
                                                Pattern.compile("\\.java$"))
                                        .stream()
                                        .map(File::getAbsolutePath)
                                        .collect(Collectors.toList());
                        if (srcFiles.isEmpty()) {
                            return null;
                        }

                        String[] firstArgs = {
                            "-source",
                            "1.7",
                            "-target",
                            "1.7",
                            "-d",
                            atomInfo.getJavaClassDir().getAbsolutePath()
                        };
                        String[] arguments =
                                ObjectArrays.concat(
                                        firstArgs, srcFiles.toArray(new String[0]), String.class);

                        int result =
                                compiler.run(
                                        null,
                                        ByteStreams.nullOutputStream(),
                                        ByteStreams.nullOutputStream(),
                                        arguments);

                        if (result != 0) {
                            throw new GradleException(
                                    String.format(
                                            "Failed to compile atom %1$s R.java classes.",
                                            atomInfo.getName()));
                        }
                        return null;
                    });
        }
        executor.waitForTasksWithQuickFail(true);
    }

    @InputFiles
    @NonNull
    public Collection<File> getSrcDirsCollection() {
        return atomConfigTask.getSourceOutputDirsCollection();
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getOutDirsCollection() {
        return atomConfigTask.getJavaClassDirsCollection();
    }

    private AtomConfig atomConfigTask;

    public static class ConfigAction implements TaskConfigAction<JavaCompileAtomResClass> {
        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("compileAll", "AtomsResClassesWithJavac");
        }

        @NonNull
        @Override
        public Class<JavaCompileAtomResClass> getType() {
            return JavaCompileAtomResClass.class;
        }

        @Override
        public void execute(@NonNull JavaCompileAtomResClass javaCompileAtomResClass) {
            javaCompileAtomResClass.setVariantName(scope.getVariantConfiguration().getFullName());
            javaCompileAtomResClass.atomConfigTask = scope.getVariantData().atomConfigTask;
        }

        @NonNull private VariantScope scope;
    }
}
