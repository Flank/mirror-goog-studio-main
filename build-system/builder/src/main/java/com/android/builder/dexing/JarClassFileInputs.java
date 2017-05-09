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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apkzlib.zip.ZFile;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Class file input that is backed by a .jar file. All .class files that are part of this input are
 * inside the jar file.
 */
final class JarClassFileInputs implements ClassFileInput {

    /** If we are unable to read .class files from the input. */
    public static final class JarClassFileInputsException extends RuntimeException {

        public JarClassFileInputsException(@NonNull String s, @NonNull IOException e) {
            super(s, e);
        }
    }

    /* Path of the root dir where all the .class files are, or path to the .jar. */
    @NonNull private final Path rootPath;
    /* All files that should be considered as part of this input should satisfy the filter. */
    @NonNull private final Predicate<Path> filter;

    @Nullable private ZFile jarFile = null;

    public JarClassFileInputs(@NonNull Path rootPath, @NonNull Predicate<Path> filter) {
        this.rootPath = rootPath;
        this.filter = filter;
    }

    @NonNull
    @Override
    public Path getRootPath() {
        return rootPath;
    }

    /**
     * Returns an iterator over the {@link com.android.builder.dexing.ClassFileEntry} entries i.e.
     * the class files that should be processed as part of this class file input.
     *
     * @return iterator over the entries to process
     */
    @Override
    @NonNull
    public Iterator<ClassFileEntry> iterator() {
        if (jarFile == null) {
            try {
                jarFile = new ZFile(rootPath.toFile());
            } catch (IOException e) {
                throw new JarClassFileInputsException(
                        "Unable to read jar file " + rootPath.toString(), e);
            }
        }

        return jarFile.entries()
                .stream()
                .filter(
                        entry ->
                                filter.test(Paths.get(entry.getCentralDirectoryHeader().getName())))
                .map(
                        entry -> {
                            try {
                                byte[] content = entry.read();
                                return new ClassFileEntry(
                                        Paths.get(entry.getCentralDirectoryHeader().getName()),
                                        content);
                            } catch (IOException e) {
                                throw new JarClassFileInputsException(
                                        "Unable to read file "
                                                + entry.getCentralDirectoryHeader().getName(),
                                        e);
                            }
                        })
                .iterator();
    }

    @NonNull
    @Override
    public List<ClassFileEntry> allEntries() {
        return Lists.newLinkedList(this);
    }

    @Override
    public void close() throws IOException {
        if (jarFile != null) {
            jarFile.close();
        }
    }
}
