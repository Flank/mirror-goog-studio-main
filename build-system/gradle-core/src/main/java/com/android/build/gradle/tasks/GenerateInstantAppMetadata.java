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
import com.android.build.gradle.internal.variant.InstantAppVariantOutputData;
import com.android.utils.FileUtils;
import com.google.wireless.android.instantapps.iapk.InstantAppMetadataProto;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Task to generate the atom metadata for the instant app.
 */
@ParallelizableTask
public class GenerateInstantAppMetadata extends DefaultAndroidTask {

    @TaskAction
    public void taskAction() throws IOException {
        InstantAppMetadataProto.InstantAppMetadata.Builder instantAppMetadataBuilder =
                InstantAppMetadataProto.InstantAppMetadata.newBuilder();
        instantAppMetadataBuilder.setSubstrateApiVersion(getSubstrateApiVersion());

        File instantAppMetadataFile =
                new File(getInstantAppMetadataFolder(), SdkConstants.FN_INSTANTAPP_METADATA);

        // Re-create the file.
        instantAppMetadataFile.delete();
        try (FileOutputStream outputStream = new FileOutputStream(instantAppMetadataFile)) {
            instantAppMetadataBuilder.build().writeTo(outputStream);
            outputStream.close();
        }
    }

    @OutputDirectory
    public File getInstantAppMetadataFolder() {
        return instantAppMetadataFolder;
    }

    public void setInstantAppMetadataFolder(File instantAppMetadataFolder) {
        this.instantAppMetadataFolder = instantAppMetadataFolder;
    }

    @Input
    public int getSubstrateApiVersion() {
        return substrateApiVersion;
    }

    public void setSubstrateApiVersion(int substrateApiVersion) {
        this.substrateApiVersion = substrateApiVersion;
    }

    private File instantAppMetadataFolder;

    private int substrateApiVersion;

    public static class ConfigAction implements TaskConfigAction<GenerateInstantAppMetadata> {

        public ConfigAction(@NonNull VariantOutputScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("generate", "InstantAppMetadata");
        }

        @Override
        @NonNull
        public Class<GenerateInstantAppMetadata> getType() {
            return GenerateInstantAppMetadata.class;
        }

        @Override
        public void execute(@NonNull GenerateInstantAppMetadata generateInstantAppMetadata) {
            final InstantAppVariantOutputData variantOutputData =
                    (InstantAppVariantOutputData) scope.getVariantOutputData();

            generateInstantAppMetadata.setInstantAppMetadataFolder(FileUtils.join(
                    scope.getGlobalScope().getIntermediatesDir(),
                    SdkConstants.FD_INSTANTAPP_METADATA,
                    scope.getVariantScope().getDirName()));
            generateInstantAppMetadata.setVariantName(variantOutputData.getFullName());
            // TODO: Set the substrate-api-version from the gradle file.
            generateInstantAppMetadata.setSubstrateApiVersion(5);
            variantOutputData.generateInstantAppMetadataTask = generateInstantAppMetadata;
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
