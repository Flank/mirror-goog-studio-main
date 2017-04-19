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

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;
import java.io.File;
import java.util.Collection;

public class VariantHelper {

    private final Variant variant;
    private final File projectDir;
    private final String outputFileName;

    public VariantHelper(Variant variant, File projectDir, String outputFileName) {
        this.variant = variant;
        this.projectDir = projectDir;
        this.outputFileName = outputFileName;
    }

    public void test() {
        AndroidArtifact artifact = variant.getMainArtifact();
        assertThat(artifact).named("Main Artifact null-check").isNotNull();

        String variantName = variant.getName();
        File build = new File(projectDir,  "build");
        File apk = new File(build, "outputs/apk/" + outputFileName);

        Collection<File> sourceFolders = artifact.getGeneratedSourceFolders();
        assertThat(sourceFolders).named("Gen src Folder count").hasSize(5);

        Collection<AndroidArtifactOutput> outputs = artifact.getOutputs();
        assertThat(outputs).named("artifact output").isNotNull();
        assertThat(outputs).hasSize(1);

        AndroidArtifactOutput output = outputs.iterator().next();

        assertThat(output.getOutputFile()).named(variantName + " output").isEqualTo(apk);
        assertThat(output.getGeneratedManifest())
                .named("Generated manifest for " + variantName)
                .isNotNull();
    }
}
