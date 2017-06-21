/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.builder.testing.MockableJarGenerator;
import com.android.builder.utils.FileCache;
import com.android.sdklib.IAndroidTarget;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Task for generating a mockable android.jar
 */
public class MockableAndroidJarTask extends DefaultTask {

    private File androidJar;
    private File outputFile;
    @Nullable private FileCache fileCache;

    /**
     * Whether the generated jar should return default values from all methods or throw exceptions.
     */
    private boolean returnDefaultValues;

    @TaskAction
    public void createMockableJar() throws IOException, ExecutionException {
        File outputFile = getOutputFile();
        if (outputFile.exists()) {
            // Modules share the mockable jar, all the "inputs" are reflected in the filename,
            // e.g. mockable-android-22.default-values.jar. If we ever change the generator logic,
            // it will be reflected in the name as well.
            //
            // This is not how Gradle understands tasks with overlapping outputs - it will run
            // all instances of this task, because the output was not created by this instance. We
            // need to return here manually because of that behavior.
            return;
        }

        if (fileCache != null) {
            fileCache.createFile(outputFile, getCacheInputs(), this::doCreateMockableJar);
        } else {
            doCreateMockableJar();
        }
    }

    @Input
    public boolean getReturnDefaultValues() {
        return returnDefaultValues;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    @InputFile
    public File getAndroidJar() {
        return androidJar;
    }

    private void doCreateMockableJar() throws IOException {
        MockableJarGenerator generator = new MockableJarGenerator(getReturnDefaultValues());
        getLogger()
                .info(
                        String.format(
                                "Creating %s from %s.",
                                outputFile.getAbsolutePath(), androidJar.getAbsolutePath()));
        generator.createMockableJar(getAndroidJar(), outputFile);
    }

    /**
     * Builds the cache key. All the relevant information should already be in the file name, see
     * {@link GlobalScope#getMockableAndroidJarFile()}.
     */
    private FileCache.Inputs getCacheInputs() throws IOException {
        return new FileCache.Inputs.Builder(FileCache.Command.GENERATE_MOCKABLE_JAR)
                .putString("fileName", outputFile.getName())
                .putFilePathLengthTimestamp("platformJar", androidJar)
                .build();
    }

    public static class ConfigAction implements TaskConfigAction<MockableAndroidJarTask> {

        @NonNull
        private final GlobalScope scope;
        @NonNull
        private final File mockableJar;

        public ConfigAction(
                @NonNull GlobalScope scope,
                @NonNull File mockableJar) {
            this.scope = scope;
            this.mockableJar = mockableJar;
        }

        @NonNull
        @Override
        public String getName() {
            return "mockableAndroidJar";
        }

        @NonNull
        @Override
        public Class<MockableAndroidJarTask> getType() {
            return MockableAndroidJarTask.class;
        }

        @Override
        public void execute(@NonNull final MockableAndroidJarTask task) {
            task.setGroup(TaskManager.BUILD_GROUP);
            task.setDescription(
                    "Creates a version of android.jar that's suitable for unit tests.");
            task.returnDefaultValues =
                    scope.getExtension().getTestOptions().getUnitTests().isReturnDefaultValues();

            task.androidJar =
                    new File(
                            scope.getAndroidBuilder()
                                    .getTarget()
                                    .getPath(IAndroidTarget.ANDROID_JAR));

            task.outputFile = mockableJar;
            task.fileCache = scope.getBuildCache();
        }
    }
}
