/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deploy.swapper;

import java.util.Map;

/**
 * A representation of files within a previous built APK, most likely to be a version that is
 * currently installed on a device.
 *
 * <p>
 *
 * <p>Unlike {@link OnHostDexFile}, this representation no longer has the content of the file
 * available. Instead, comparison can only be made with checksum.
 */
public final class OnCacheDexFile extends DexFile {
    private final int dexFileIndex;
    private final DexArchiveDatabase db;

    public OnCacheDexFile(long checksum, String name, int dexFileIndex, DexArchiveDatabase db) {
        super(checksum, name);
        this.dexFileIndex = dexFileIndex;
        this.db = db;
    }

    @Override
    public Map<String, Long> getClasssesChecksum() {
        return db.getClassesChecksum(dexFileIndex);
    }

    @Override
    public byte[] getCode() {
        throw new UnsupportedOperationException(
                "On Cache dex files no longer contains the dex code!");
    }
}
