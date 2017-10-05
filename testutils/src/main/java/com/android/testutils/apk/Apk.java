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
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Immutable
public class Apk extends DexAndroidArchive {

    public Apk(@NonNull Path file) throws IOException {
        super(file, "classes.dex", "classes%d.dex");
    }

    public Apk(@NonNull File apk) throws IOException {
        this(apk.toPath());
    }

    @Nullable
    @Override
    public Path getJavaResource(@NonNull String name) throws IOException {
        return getEntry(name);
    }

    /**
     * Returns the total size of all the contents in the APK. This is usually different from the
     * size of the APK file itself (which also contains some meta-data). This method is useful for
     * comparing APK files where the file sizes have changed (probably due to changes in meta-data)
     * but the contents remain the same.
     */
    public long getContentsSize() throws IOException {
        long contentsSize = 0;
        try (ZipFile zipFile = new ZipFile(this.getFile().toFile())) {
            Enumeration entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) entries.nextElement();
                contentsSize += zipEntry.getCompressedSize();
            }
        }
        return contentsSize;
    }

    @Override
    public String toString() {
        return "Apk<" + getFile() + ">";
    }
}
