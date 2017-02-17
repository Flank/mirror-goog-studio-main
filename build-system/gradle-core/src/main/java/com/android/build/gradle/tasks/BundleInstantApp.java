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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.variant.InstantAppVariantData;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Task;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Task to zip an instantApp */
public class BundleInstantApp extends DefaultAndroidTask implements FileSupplier {

    @TaskAction
    public void taskAction() throws IOException {
        // Bundle all the files in the output bundle.
        try (ZipOutputStream zipOutputStream =
                new ZipOutputStream(new FileOutputStream(getBundleFile()))) {
            for (File file : getFinalOutputFilesCollection()) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    byte[] inputBuffer = IOUtils.toByteArray(fileInputStream);
                    zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
                    zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
                    zipOutputStream.closeEntry();
                }
            }
        }
    }

    @InputFiles
    @NonNull
    public Collection<File> getFinalOutputFilesCollection() {
        return atomConfigTask.getFinalOutputFilesCollection();
    }

    @OutputFile
    @NonNull
    public File getBundleFile() {
        return bundleFile;
    }

    private File bundleFile;
    private AtomConfig atomConfigTask;

    // ----- FileSupplierTask -----

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    @Override
    public File get() {
        return getBundleFile();
    }

    public static class ConfigAction implements TaskConfigAction<BundleInstantApp> {
        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("package", "InstantApp");
        }

        @NonNull
        @Override
        public Class<BundleInstantApp> getType() {
            return BundleInstantApp.class;
        }

        @Override
        public void execute(@NonNull BundleInstantApp bundleInstantApp) {
            bundleInstantApp.setVariantName(scope.getFullVariantName());
            bundleInstantApp.atomConfigTask = scope.getVariantData().atomConfigTask;
            bundleInstantApp.bundleFile = scope.getInstantAppPackage();

            InstantAppVariantData instantAppVariantData =
                    (InstantAppVariantData) scope.getVariantData();
            instantAppVariantData.bundleInstantAppTask = bundleInstantApp;
        }
    }
}
