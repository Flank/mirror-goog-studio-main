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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.AtomVariantOutputData;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.instantapps.iapk.AtomDependencyProto;
import com.google.wireless.android.instantapps.iapk.AtomMetadataProto;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Task to generate the Atom Metadata files.
 */
@ParallelizableTask
public class GenerateAtomMetadata extends DefaultAndroidTask {

    @TaskAction
    public void taskAction() throws IOException {
        AtomMetadataProto.AtomMetadata.Builder atomMetadataBuilder =
                AtomMetadataProto.AtomMetadata.newBuilder();
        atomMetadataBuilder.setAtomName(getAtomName());
        atomMetadataBuilder.setAtomVersionName(getAtomVersion());

        for (String atomDependency : getAtomDependencies()) {
            AtomDependencyProto.AtomDependency.Builder atomDependencyBuilder =
                    AtomDependencyProto.AtomDependency.newBuilder();
            atomDependencyBuilder.setAtomName(atomDependency);
            atomMetadataBuilder.addAtomDependency(atomDependencyBuilder);
        }

        File atomMetadataFile = new File(getAtomMetadataFolder(), SdkConstants.FN_ATOM_METADATA);

        // Re-create the file.
        atomMetadataFile.delete();
        try (FileOutputStream outputStream = new FileOutputStream(atomMetadataFile)) {
            atomMetadataBuilder.build().writeTo(outputStream);
            outputStream.close();
        }
    }

    @OutputDirectory
    public File getAtomMetadataFolder() {
        return atomMetadataFolder;
    }

    protected void setAtomMetadataFolder(File atomMetadataFolder) {
        this.atomMetadataFolder = atomMetadataFolder;
    }

    @Input
    public String getAtomVersion() {
        return atomVersion;
    }

    public void setAtomVersion(String atomVersion) {
        this.atomVersion = atomVersion;
    }

    @Input
    public String getAtomName() {
        return atomName;
    }

    public void setAtomName(String atomName) {
        this.atomName = atomName;
    }

    @Input
    public Set<String> getAtomDependencies() {
        return atomDependencies;
    }

    public void setAtomDependencies(Set<String> atomDependencies) {
        this.atomDependencies = atomDependencies;
    }

    private File atomMetadataFolder;
    private String atomVersion;
    private String atomName;
    private Set<String> atomDependencies;

    public static class ConfigAction implements TaskConfigAction<GenerateAtomMetadata> {

        public ConfigAction(@NonNull VariantOutputScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("generate", "AtomMetadata");
        }

        @Override
        @NonNull
        public Class<GenerateAtomMetadata> getType() {
            return GenerateAtomMetadata.class;
        }

        @Override
        public void execute(@NonNull GenerateAtomMetadata generateAtomMetadata) {
            AtomVariantOutputData variantOutputData = (AtomVariantOutputData) scope
                    .getVariantOutputData();

            // TODO: Set atom dependencies, with version names.
            generateAtomMetadata.setAtomDependencies(ImmutableSet.of());
            generateAtomMetadata.setAtomName(scope.getGlobalScope().getProject().getName());
            generateAtomMetadata.setAtomVersion(variantOutputData.getVersionName());
            generateAtomMetadata.setAtomMetadataFolder(FileUtils.join(
                    scope.getVariantScope().getBaseBundleDir(),
                    SdkConstants.FD_INSTANTAPP_METADATA));
            generateAtomMetadata.setVariantName(variantOutputData.getFullName());
            variantOutputData.generateAtomMetadataTask = generateAtomMetadata;
        }

        @NonNull
        public VariantOutputScope getScope() {
            return scope;
        }

        public void setScope(@NonNull VariantOutputScope scope) {
            this.scope = scope;
        }

        @NonNull
        private VariantOutputScope scope;
    }
}
