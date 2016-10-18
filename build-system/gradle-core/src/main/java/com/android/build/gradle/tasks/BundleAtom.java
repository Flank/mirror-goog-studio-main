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

    private static void deleteDirectoryContents(@NonNull File directory) throws IOException {
        if (!directory.exists()) {
            FileUtils.mkdirs(directory);
        } else if (!directory.isDirectory()) {
            FileUtils.delete(directory);
            FileUtils.mkdirs(directory);
        } else {
            FileUtils.deleteDirectoryContents(directory);
        }
    }

    @TaskAction
    public void taskAction() throws IOException {
        File bundleFolder = getBundleFolder();

        // Copy all the native libs to be bundled.
        File libBundleFolder = new File(bundleFolder, FD_NATIVE_LIBS);
        deleteDirectoryContents(libBundleFolder);
        for (File jniFolder : getJniFolders()) {
            for (File lib : FileUtils.find(jniFolder, Pattern.compile("\\.so$"))) {
                File destLibFile =
                        new File(libBundleFolder, FileUtils.relativePath(lib, jniFolder));
                FileUtils.copyFile(lib, destLibFile);
            }
        }

        // Copy all the dex files to be bundled.
        File dexBundleFolder = new File(bundleFolder, FD_DEX);
        deleteDirectoryContents(dexBundleFolder);
        for (File dexFolder : getDexFolders()) {
            for (File dexFile : FileUtils.find(dexFolder, Pattern.compile("\\.dex$"))) {
                File destDexFile = new File(dexBundleFolder, dexFile.getName());
                FileUtils.copyFile(dexFile, destDexFile);
            }
        }

        // Copy all the java resource files to be bundled.
        File javaResBundleFolder = new File(bundleFolder, FD_JAVA_RES);
        deleteDirectoryContents(javaResBundleFolder);
        for (File javaResFolder : getJavaResFolders()) {
            for (File javaResFile : FileUtils.getAllFiles(javaResFolder)) {
                File destJavaResFile =
                        new File(
                                javaResBundleFolder,
                                FileUtils.relativePath(javaResFile, javaResBundleFolder));
                FileUtils.copyFile(javaResFile, destJavaResFile);
            }
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

            // Find all the other files to be bundled.
            for (File file : FileUtils.getAllFiles(bundleFolder)) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    byte[] inputBuffer = IOUtils.toByteArray(fileInputStream);
                    zipOutputStream.putNextEntry(
                            new ZipEntry(FileUtils.relativePath(file, bundleFolder)));
                    zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
                    zipOutputStream.closeEntry();
                }
            }
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
