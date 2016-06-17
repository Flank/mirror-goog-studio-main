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
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.builder.model.AndroidAtom;

import org.apache.commons.io.IOUtils;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A task to process InstantApp resources.
 */
@ParallelizableTask
public class ProcessInstantAppResources extends DefaultAndroidTask {

    @TaskAction
    public void taskAction() throws IOException, BuildException {
        // Find the stream for the manifest in the atom resource package.
        byte[] manifestStream = null;
        try (ZipFile zipFile = new ZipFile(getAtomResourcePackage())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                    InputStream inputStream = zipFile.getInputStream(entry);
                    manifestStream = IOUtils.toByteArray(inputStream);
                    inputStream.close();
                    zipFile.close();
                    break;
                }
            }
            zipFile.close();
        }

        if (manifestStream == null) {
            getLogger().error("AndroidManifest.xml not found in atom resource package.");
            throw new BuildException("AndroidManifest.xml not found in atom resource package.", null);
        }

        // Write the Manifest to the output resource package.
        getOutputResourcePackage().delete();
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(new FileOutputStream(getOutputResourcePackage()))) {
            zipOutputStream.putNextEntry(new ZipEntry(SdkConstants.ANDROID_MANIFEST_XML));
            zipOutputStream.write(manifestStream, 0, manifestStream.length);
            zipOutputStream.closeEntry();
            zipOutputStream.close();
        }
    }

    @InputFile
    public File getAtomResourcePackage() {
        return atomResourcePackage;
    }

    public void setAtomResourcePackage(File atomResourcePackage) {
        this.atomResourcePackage = atomResourcePackage;
    }

    @OutputFile
    public File getOutputResourcePackage() {
        return outputResourcePackage;
    }

    public void setOutputResourcePackage(File outputResourcePackage) {
        this.outputResourcePackage = outputResourcePackage;
    }

    private File atomResourcePackage;
    private File outputResourcePackage;

    public static class ConfigAction implements TaskConfigAction<ProcessInstantAppResources> {

        public ConfigAction(@NonNull VariantOutputScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "InstantAppResources");
        }

        @NonNull
        @Override
        public Class<ProcessInstantAppResources> getType() {
            return ProcessInstantAppResources.class;
        }

        @Override
        public void execute(@NonNull ProcessInstantAppResources processInstantAppResources)
                throws BuildException {
            final GradleVariantConfiguration config =
                    scope.getVariantScope().getVariantConfiguration();

            processInstantAppResources.setVariantName(config.getFullName());
            List<? extends AndroidAtom> atoms =
                scope.getVariantScope().getVariantConfiguration().getCompileAndroidAtoms();
            // TODO: Remove this once we support multiple atoms.
            if (atoms.size() != 1) {
                processInstantAppResources.getLogger().error("Instant apps do not support multiple atoms.");
                throw new BuildException("Instant apps do not support multiple atoms.", null);
            }
            processInstantAppResources.setAtomResourcePackage(
                    atoms.get(0).getResourcePackageFile());
            processInstantAppResources.setOutputResourcePackage(
                    scope.getProcessResourcePackageOutputFile());
        }

        @NonNull
        private VariantOutputScope scope;

    }
}
