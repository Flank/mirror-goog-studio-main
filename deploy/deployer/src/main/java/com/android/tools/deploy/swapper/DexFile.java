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

/** The DexFile class that represents a classesXX.dex file within an APK. */
public abstract class DexFile {
    private final long checksum;
    private final String name;

    public DexFile(long checksum, String name) {
        this.checksum = checksum;
        this.name = name;
    }

    public long getChecksum() {
        return checksum;
    }

    /** Comparison based solely on checksum. Might give false negatives but not false positives. */
    public boolean differs(DexFile other) {
        return this.checksum != other.checksum;
    }

    public abstract Map<String, Long> getClasssesChecksum();

    public abstract byte[] getCode();

    /**
     * Saves all the checksum within a dex file to a cache.
     *
     * @return An index of the dex file within the table of all dex files.
     */
    int cache(DexArchiveDatabase db) {
        int prevIndex = db.getDexFileIndex(this.checksum);
        if (prevIndex != -1) {
            return prevIndex;
        }
        int index = db.addDexFile(this.checksum, name);
        db.fillEntriesChecksum(index, getClasssesChecksum());
        return index;
    }
}
