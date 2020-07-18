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

package com.android.testutils.apk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.mockito.CheckReturnValue;

@Immutable
public final class Aar extends AndroidArchive {

    @NonNull
    private static String toClassName(@NonNull String name) {
        checkValidClassName(name);
        return name.substring(1, name.length() - 1) + ".class";
    }

    public Aar(@NonNull Path file) throws IOException {
        super(file);
    }

    public Aar(@NonNull File file) throws IOException {
        this(file.toPath());
    }

    @CheckReturnValue
    @Override
    public boolean containsMainClass(@NonNull String name) throws IOException {
        return getEntryAsZip("classes.jar").getEntry(toClassName(name)) != null;
    }

    @CheckReturnValue
    @Override
    public boolean containsSecondaryClass(@NonNull String name) throws IOException {
        String className = toClassName(name);
        for (Path lib : getEntries(Pattern.compile("^/libs/.*\\.jar"))) {
            if (getEntryAsZip(lib.toString()).getEntry(className) != null) {
                return true;
            }
        }
        return false;
    }

    @CheckReturnValue
    @Nullable
    @Override
    public Path getJavaResource(@NonNull String name) throws IOException {
        return getEntryAsZip("classes.jar").getEntry(name);
    }

    /**
     * Returns the contents of the AAR's AndroidManifest.xml entry as a String; returns null if the
     * AAR doesn't have an AndroidManifest.xml entry.
     */
    @CheckReturnValue
    @Nullable
    public String getAndroidManifestContentsAsString() throws IOException {
        Path path = getEntry("AndroidManifest.xml");
        if (path == null) {
            return null;
        }
        return Files.readAllLines(path).stream().collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return "Aar<" + getFile() + ">";
    }
}
