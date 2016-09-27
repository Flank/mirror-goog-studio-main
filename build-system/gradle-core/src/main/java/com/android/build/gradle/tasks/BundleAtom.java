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

import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_DEX;
import static com.android.SdkConstants.FD_JAVA_RES;
import static com.android.SdkConstants.FD_NATIVE_LIBS;
import static com.android.SdkConstants.FD_RES;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Task;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to zip an atom bundle.
 */
public class BundleAtom extends DefaultAndroidTask implements FileSupplier {

    @TaskAction
    public void taskAction() throws IOException {
        // Map of files to be bundled with their internal bundle location.
        ImmutableSet.Builder<ZipFileLocation> fileListBuilder = ImmutableSet.builder();

        // Find all the native libs to be bundled.
        for (File jniFolder : getJniFolders()) {
            for (File lib : FileUtils.find(jniFolder, Pattern.compile("\\.so$"))) {
                fileListBuilder.add(new ZipFileLocation(lib,
                        FD_NATIVE_LIBS + "/" + FileUtils.relativePath(lib, jniFolder)));
            }
        }

        // Find all the dex files to be bundled.
        for (File dexFolder : getDexFolders()) {
            for (File dexFile : FileUtils.find(dexFolder, Pattern.compile("\\.dex$"))) {
                fileListBuilder.add(new ZipFileLocation(dexFile,
                        FD_DEX + "/" + dexFile.getName()));
            }
        }

        // Find all the other files to be bundled.
        for (File file : FileUtils.getAllFiles(getBundleFolder())) {
            fileListBuilder.add(new ZipFileLocation(file,
                    FileUtils.relativePath(file, getBundleFolder())));
        }

        // Bundle all the files in the output bundle.
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(new FileOutputStream(getBundleFile()))) {
            // Ensure all the directories are always created even if they are empty.
            zipOutputStream.putNextEntry(new ZipEntry(FD_JAVA_RES + "/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry(FD_RES + "/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry(FD_NATIVE_LIBS + "/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry(FD_ASSETS + "/"));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry(FD_DEX + "/"));
            zipOutputStream.closeEntry();

            for (ZipFileLocation entry : fileListBuilder.build()) {
                try (FileInputStream fileInputStream = new FileInputStream(entry.file)) {
                    byte[] inputBuffer = IOUtils.toByteArray(fileInputStream);
                    zipOutputStream.putNextEntry(new ZipEntry(entry.path));
                    zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
                    zipOutputStream.closeEntry();
                }
            }
            zipOutputStream.close();
        }
    }

    @InputDirectory
    public File getBundleFolder() {
        return bundleFolder;
    }

    public void setBundleFolder(File bundleFolder) {
        this.bundleFolder = bundleFolder;
    }

    @OutputFile
    public File getBundleFile() {
        return bundleFile;
    }

    public void setBundleFile(File bundleFile) {
        this.bundleFile = bundleFile;
    }

    @InputFiles
    public Set<File> getJniFolders() {
        return jniFolders;
    }

    public void setJniFolders(Set<File> jniFolders) {
        this.jniFolders = jniFolders;
    }

    @InputFiles
    public Set<File> getDexFolders() {
        return dexFolders;
    }

    public void setDexFolders(Set<File> dexFolders) {
        this.dexFolders = dexFolders;
    }

    @InputFiles
    public Set<File> getJavaResFolders() {
        return javaResFolders;
    }

    public void setJavaResFolders(Set<File> javaResFolders) {
        this.javaResFolders = javaResFolders;
    }

    private File bundleFolder;
    private File bundleFile;
    private Set<File> jniFolders;
    private Set<File> dexFolders;
    private Set<File> javaResFolders;

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

    private static class ZipFileLocation {
        private ZipFileLocation(File file, String path) {
            this.file = file;
            this.path = path;
        }

        private File file;
        private String path;
    }

    public static class ConfigAction implements TaskConfigAction<BundleAtom> {

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("bundle");
        }

        @Override
        @NonNull
        public Class<BundleAtom> getType() {
            return BundleAtom.class;
        }

        @Override
        public void execute(@NonNull BundleAtom bundleAtom) {
            bundleAtom.setVariantName(scope.getFullVariantName());

            // TODO: Move the individual outputs out of the bundle directory.
            // TODO: Change this task to be incremental and re-use the IncrementalPackager.
            bundleAtom.setBundleFolder(scope.getBaseBundleDir());

            bundleAtom.setBundleFile(scope.getOutputBundleFile());

            ConventionMappingHelper.map(
                    bundleAtom,
                    "jniFolders",
                    () ->
                            scope.getTransformManager()
                                    .getPipelineOutput(StreamFilter.NATIVE_LIBS)
                                    .keySet());
            ConventionMappingHelper.map(
                    bundleAtom,
                    "dexFolders",
                    () -> scope.getTransformManager().getPipelineOutput(StreamFilter.DEX).keySet());
            ConventionMappingHelper.map(
                    bundleAtom,
                    "javaResFolders",
                    () ->
                            scope.getTransformManager()
                                    .getPipelineOutput(StreamFilter.RESOURCES)
                                    .keySet());
        }

        @NonNull
        private VariantScope scope;
    }
}
