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
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Class file input coming from a directory. All class files will be processed relative to the base
 * directory, {@link #getRootPath()}
 */
final class DirClassFileInputs implements ClassFileInput {

    /** Exception thrown if we are unable to process .class files from this input. */
    public static final class DirClassFileInputsException extends RuntimeException {

        public DirClassFileInputsException(@NonNull String s, @NonNull IOException e) {
            super(s, e);
        }
    }

    /* Path of the root dir where all the .class files are, or path to the .jar. */
    @NonNull private final Path rootPath;
    /* All .class files to process should satisfy this filter. */
    @NonNull private final Predicate<Path> filter;

    public DirClassFileInputs(@NonNull Path rootPath, @NonNull Predicate<Path> relativePath) {
        this.rootPath = rootPath;
        this.filter = relativePath;
    }

    @Override
    @NonNull
    public Path getRootPath() {
        return rootPath;
    }

    @Override
    @NonNull
    public Iterator<ClassFileEntry> iterator() {
        try {
            return Files.walk(rootPath).filter(filter).map(this::createEntryFromPath).iterator();
        } catch (IOException e) {
            throw new DirClassFileInputsException(
                    "Unable to read directory class input " + rootPath.toString(), e);
        }
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }

    @NonNull
    @Override
    public List<ClassFileEntry> allEntries() {
        return Lists.newLinkedList(this);
    }

    @NonNull
    private ClassFileEntry createEntryFromPath(@NonNull Path path) {
        try {
            return new ClassFileEntry(rootPath.relativize(path), Files.readAllBytes(path));
        } catch (IOException e) {
            throw new DirClassFileInputsException("Unable to read file " + path.toString(), e);
        }
    }
}
