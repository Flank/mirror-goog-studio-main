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

package com.android.build.gradle.integration.common.utils;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildInformation;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Collection;

public class VariantHelper {

    private final Variant variant;
    private final VariantBuildInformation variantOutput;
    private final File projectDir;
    private final String outputFileName;

    public VariantHelper(
            Variant variant,
            VariantBuildInformation variantOutput,
            File projectDir,
            String outputFileName) {
        this.variant = variant;
        this.variantOutput = variantOutput;
        this.projectDir = projectDir;
        this.outputFileName = outputFileName;
    }

    public void test() {
        AndroidArtifact artifact = variant.getMainArtifact();
        assertThat(artifact).named("Main Artifact null-check").isNotNull();

        String variantName = variant.getName();
        assertThat(variantName).isEqualTo(variantOutput.getVariantName());
        File build = new File(projectDir,  "build");
        File apk = new File(build, "outputs/apk/" + outputFileName);

        Collection<File> sourceFolders = artifact.getGeneratedSourceFolders();

        if (FileUtils.join(
                        build,
                        "intermediates",
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            assertThat(sourceFolders).named("Gen src Folder count").hasSize(3);
        } else {
            assertThat(sourceFolders).named("Gen src Folder count").hasSize(4);
        }

        Collection<String> outputs = ProjectBuildOutputUtils.getOutputFiles(variantOutput);
        assertThat(outputs).named("artifact output").isNotNull();
        assertThat(outputs).hasSize(1);

        File output = new File(ProjectBuildOutputUtils.getSingleOutputFile(variantOutput));

        assertThat(output).named(variantName + " output").isEqualTo(apk);
    }
}
