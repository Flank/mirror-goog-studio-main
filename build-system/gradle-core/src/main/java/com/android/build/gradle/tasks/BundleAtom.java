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

import static com.android.SdkConstants.FD_DEX;
import static com.android.SdkConstants.FD_JAVA_RES;
import static com.android.SdkConstants.FD_NATIVE_LIBS;

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
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
                if (!destLibFile.getParentFile().exists()) {
                    FileUtils.mkdirs(destLibFile.getParentFile());
                }
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
        for (File javaResource : getJavaResources()) {
            if (javaResource.isDirectory()) {
                // Copy the files to the bundle folder.
                for (File javaResFile : FileUtils.getAllFiles(javaResource)) {
                    File destJavaResFile =
                            new File(
                                    javaResBundleFolder,
                                    FileUtils.relativePath(javaResFile, javaResource));
                    if (!destJavaResFile.getParentFile().exists()) {
                        FileUtils.mkdirs(destJavaResFile.getParentFile());
                    }
                    FileUtils.copyFile(javaResFile, destJavaResFile);
                }
            } else {
                // There is a bug somewhere in the proguard build tasks that places .class files as
                // resources. These will be removed here, but this filtering code can and should be
                // removed once that bug is fixed.
                if (javaResource.getName().endsWith(".class")) {
                    continue;
                }

                // Unpack the files to the bundle folder.
                try (ZipFile zipFile = new ZipFile(javaResource)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry zipEntry = entries.nextElement();
                        File destJavaResource =
                                FileUtils.join(javaResBundleFolder, zipEntry.getName());
                        if (zipEntry.isDirectory()) {
                            FileUtils.mkdirs(destJavaResource);
                        } else {
                            if (!destJavaResource.getParentFile().exists()) {
                                FileUtils.mkdirs(destJavaResource.getParentFile());
                            }
                            InputStream zipInputStream = zipFile.getInputStream(zipEntry);
                            byte[] zipInputBuffer = IOUtils.toByteArray(zipInputStream);
                            try (FileOutputStream destOutputStream =
                                    new FileOutputStream(destJavaResource)) {
                                destOutputStream.write(zipInputBuffer, 0, zipInputBuffer.length);
                                destOutputStream.close();
                            }
                            zipInputStream.close();
                        }
                    }
                    zipFile.close();
                }
            }
        }

        // Bundle all the files in the output bundle.
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(new FileOutputStream(getBundleFile()))) {
            // Find all the other files to be bundled.
            for (File file : FileUtils.getAllFiles(bundleFolder)) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    byte[] inputBuffer = IOUtils.toByteArray(fileInputStream);
                    zipOutputStream.putNextEntry(
                            new ZipEntry(
                                    FileUtils.toSystemIndependentPath(
                                            FileUtils.relativePath(file, bundleFolder))));
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
    public Set<File> getJavaResources() {
        return javaResources;
    }

    public void setJavaResources(Set<File> javaResources) {
        this.javaResources = javaResources;
    }

    private File bundleFolder;
    private File bundleFile;
    private Set<File> jniFolders;
    private Set<File> dexFolders;
    private Set<File> javaResources;

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
                    "javaResources",
                    () ->
                            scope.getTransformManager()
                                    .getPipelineOutput(StreamFilter.RESOURCES)
                                    .keySet());
        }

        @NonNull
        private VariantScope scope;
    }
}
