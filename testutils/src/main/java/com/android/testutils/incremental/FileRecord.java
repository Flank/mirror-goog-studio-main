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

package com.android.testutils.incremental;

import com.android.annotations.concurrency.Immutable;
import com.android.utils.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Record of a file for verify if a File has changed.
 */
@Immutable
public class FileRecord {
    private final File file;

    private final String hash;

    public static FileRecord of(File file) throws IOException {
        return new FileRecord(file);
    }

    private FileRecord(File file) throws IOException {
        this.file = file;
        this.hash = FileUtils.sha1(file);
    }

    public File getFile() {
        return file;
    }

    public String getHash() {
        return hash;
    }

    public String toString() {
        return file.toString();
    }
}
