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
package com.android.tools.apk.analyzer.internal;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.ArchiveNode;

public class ApkFileByFileEntry extends ApkDiffEntry {
    private final long myDiffSize;

    public ApkFileByFileEntry(
            @NonNull String name,
            ArchiveNode oldFile,
            ArchiveNode newFile,
            long oldSize,
            long newSize,
            long diffSize) {
        super(name, oldFile, newFile, oldSize, newSize);
        myDiffSize = diffSize;
    }

    @Override
    public long getSize() {
        return myDiffSize;
    }
}
