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

package com.android.build.gradle.internal.transforms;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A Transforms that takes the project/project local streams for native libs and processes and
 * combines them, and put them in the bundle folder under jni/
 *
 * Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the bundle folder.
 */
public class LibraryJniLibsTransform extends Transform {

    @NonNull
    private final String name;
    @NonNull
    private final File jniLibsFolder;
    @NonNull private final Set<ScopeType> scopes;

    private final Pattern pattern = Pattern.compile("lib/[^/]+/[^/]+\\.so");


    public LibraryJniLibsTransform(
            @NonNull String name, @NonNull File jniLibsFolder, @NonNull Set<ScopeType> scopes) {
        this.name = name;
        this.jniLibsFolder = jniLibsFolder;
        this.scopes = scopes;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_NATIVE_LIBS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        return scopes;
    }

    @Override
    public boolean isIncremental() {
        // TODO make incremental
        return false;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(jniLibsFolder);
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        FileUtils.cleanOutputDir(jniLibsFolder);

        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();

        for (TransformInput input : invocation.getReferencedInputs()) {
            for (JarInput jarInput : input.getJarInputs()) {
                executor.execute(() -> {
                    copyFromJar(jarInput.getFile());
                    return null;
                });
            }

            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                executor.execute(() -> {
                    copyFromFolder(directoryInput.getFile());
                    return null;
                });
            }
        }

        executor.waitForTasksWithQuickFail(true);
    }

    private void copyFromFolder(@NonNull File rootDirectory) throws IOException {
        copyFromFolder(rootDirectory, Lists.<String>newArrayListWithCapacity(3));
    }

    private void copyFromFolder(@NonNull File from, @NonNull List<String> pathSegments)
            throws IOException {
        File[] children = from.listFiles(
                (file, name) -> file.isDirectory() || name.endsWith(SdkConstants.DOT_NATIVE_LIBS));

        if (children != null) {
            for (File child : children) {
                pathSegments.add(child.getName());
                if (child.isDirectory()) {
                    copyFromFolder(child, pathSegments);
                } else if (child.isFile()) {
                    if (pattern.matcher(Joiner.on('/').join(pathSegments)).matches()) {
                        // copy the file. However we do want to skip the first segment ('lib') here
                        // since the 'jni' folder is representing the same concept.
                        File to = FileUtils.join(jniLibsFolder, pathSegments.subList(1, 3));
                        FileUtils.mkdirs(to.getParentFile());
                        Files.copy(child, to);
                    }
                }

                pathSegments.remove(pathSegments.size() - 1);
            }
        }
    }

    private void copyFromJar(@NonNull File jarFile) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                String entryPath = entry.getName();

                if (!pattern.matcher(entryPath).matches()) {
                    continue;
                }

                // read the content.
                buffer.reset();
                ByteStreams.copy(zipFile.getInputStream(entry), buffer);

                // get the output file and write to it.
                final File to = computeFile(jniLibsFolder, entryPath);
                FileUtils.mkdirs(to.getParentFile());
                Files.write(buffer.toByteArray(), to);
            }
        }
    }

    /**
     * computes a file path from a root folder and a zip archive path.
     * @param rootFolder the root folder
     * @param path the archive path
     * @return the File
     */
    private static File computeFile(@NonNull File rootFolder, @NonNull String path) {
        // remove the lib/ part of the path and convert.
        path = FileUtils.toSystemDependentPath(path.substring(4));
        return new File(rootFolder, path);
    }
}
