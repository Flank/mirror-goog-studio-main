/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.tasks.ir;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to extract the FastDeploy runtime from the gradle-core jar file into a folder to be picked
 * up for co-packaging in the resulting application APK.
 */
public class FastDeployRuntimeExtractorTask extends AndroidVariantTask {

    private File outputFile;

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File file) {
        this.outputFile = file;
    }


    // we could just extract the instant-runtime jar and place it as a stream once we
    // don't have to deal with AppInfo replacement.
    @TaskAction
    public void extract() throws IOException {
        URL fdrJar =
                FastDeployRuntimeExtractorTask.class.getResource(
                        "/instant-run/instant-run-server.jar");
        if (fdrJar == null) {
            throw new RuntimeException("Couldn't find Instant-Run runtime library");
        }
        URLConnection urlConnection = fdrJar.openConnection();
        urlConnection.setUseCaches(false);
        Files.createParentDirs(getOutputFile());

        try (InputStream inputStream = urlConnection.getInputStream();
             JarInputStream jarInputStream = new JarInputStream(inputStream);
             JarOutputStream jarOutputStream = new JarOutputStream(
                     new BufferedOutputStream(new FileOutputStream(getOutputFile())))) {
            ZipEntry entry = jarInputStream.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                // don't extract metadata or classes supposed to be replaced by generated ones.
                if (isValidForPackaging(name)) {
                    ZipEntry copy = new ZipEntry(entry);
                    jarOutputStream.putNextEntry(copy);
                    ByteStreams.copy(jarInputStream, jarOutputStream);
                    jarOutputStream.closeEntry();
                }
                entry = jarInputStream.getNextEntry();
            }
        }
    }

    /**
     * Returns true if the fast deploy runtime jar entry should be packaged in the user's APK.
     *
     * @param name the fast deploy runtime jar entry name.
     * @return true to package it, false otherwise.
     */
    private static boolean isValidForPackaging(String name) {
        // don't extract metadata or classes supposed to be replaced by generated ones.
        return !name.startsWith("META-INF") && !name.endsWith("AppInfo.class");
    }

    public static class ConfigAction implements TaskConfigAction<FastDeployRuntimeExtractorTask> {

        @NonNull
        private final InstantRunVariantScope instantRunVariantScope;

        public ConfigAction(@NonNull InstantRunVariantScope instantRunVariantScope) {
            this.instantRunVariantScope = instantRunVariantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return instantRunVariantScope.getTransformVariantScope().getTaskName(
                    "fastDeploy", "Extractor");
        }

        @NonNull
        @Override
        public Class<FastDeployRuntimeExtractorTask> getType() {
            return FastDeployRuntimeExtractorTask.class;
        }

        @Override
        public void execute(@NonNull FastDeployRuntimeExtractorTask fastDeployRuntimeExtractorTask) {
            fastDeployRuntimeExtractorTask.setVariantName(
                    instantRunVariantScope.getFullVariantName());
            fastDeployRuntimeExtractorTask.setOutputFile(
                    instantRunVariantScope.getIncrementalRuntimeSupportJar());
        }
    }
}
