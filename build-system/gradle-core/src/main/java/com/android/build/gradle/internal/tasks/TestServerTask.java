/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.testing.api.TestServer;
import com.android.utils.StringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/** Task sending APKs out to a {@link TestServer} */
public class TestServerTask extends AndroidVariantTask {

    private FileCollection testApks;

    @Nullable private FileCollection testedApks;

    TestServer testServer;

    @TaskAction
    public void sendToServer() {

        List<File> testedApkFiles =
                testedApks != null
                        ? ExistingBuildElements.from(InternalArtifactType.APK, testedApks)
                                .stream()
                                .map(BuildOutput::getOutputFile)
                                .collect(Collectors.toList())
                        : ImmutableList.of();

        if (testedApkFiles.size() > 1) {
            throw new RuntimeException("Cannot handle split APKs");
        }
        File testedApkFile = testedApkFiles.isEmpty() ? null : testedApkFiles.get(0);
        List<File> testApkFiles =
                ExistingBuildElements.from(InternalArtifactType.APK, testApks)
                        .stream()
                        .map(BuildOutput::getOutputFile)
                        .collect(Collectors.toList());
        if (testApkFiles.size() > 1) {
            throw new RuntimeException("Cannot handle split APKs in test APKs");
        }
        testServer.uploadApks(getVariantName(), testApkFiles.get(0), testedApkFile);
    }

    @InputFiles
    public FileCollection getTestApks() {
        return testApks;
    }

    @InputFiles
    @Optional
    @Nullable
    public FileCollection getTestedApks() {
        return testedApks;
    }

    @NonNull
    @Override
    @Input
    public String getVariantName() {
        return Preconditions.checkNotNull(super.getVariantName(),
                "Test server task must have a variant name.");
    }

    public TestServer getTestServer() {
        return testServer;
    }

    public void setTestServer(TestServer testServer) {
        this.testServer = testServer;
    }

    public void setTestApks(FileCollection testApks) {
        this.testApks = testApks;
    }

    public void setTestedApks(@Nullable FileCollection testedApks) {
        this.testedApks = testedApks;
    }

    /** Configuration Action for a TestServerTask. */
    public static class TestServerTaskConfigAction implements TaskConfigAction<TestServerTask> {

        private final VariantScope scope;

        private final TestServer testServer;

        public TestServerTaskConfigAction(VariantScope scope, TestServer testServer) {
            this.scope = scope;
            this.testServer = testServer;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getVariantConfiguration().hasFlavors()
                    ? scope.getTaskName(testServer.getName() + "Upload")
                    : testServer.getName() + ("Upload");
        }

        @NonNull
        @Override
        public Class<TestServerTask> getType() {
            return TestServerTask.class;
        }

        @Override
        public void execute(@NonNull TestServerTask serverTask) {

            final BaseVariantData testedVariantData = scope.getTestedVariantData();

            final String variantName = scope.getVariantConfiguration().getFullName();
            serverTask.setDescription(
                    "Uploads APKs for Build \'"
                            + variantName
                            + "\' to Test Server \'"
                            + StringHelper.capitalize(testServer.getName())
                            + "\'.");
            serverTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            serverTask.setVariantName(variantName);

            serverTask.setTestServer(testServer);

            if (testedVariantData != null
                    && testedVariantData.getScope().hasOutput(InternalArtifactType.APK)) {
                serverTask.setTestedApks(
                        testedVariantData.getScope().getOutput(InternalArtifactType.APK));
            }

            serverTask.setTestApks(scope.getOutput(InternalArtifactType.APK));

            if (!testServer.isConfigured()) {
                serverTask.setEnabled(false);
            }
        }
    }
}
