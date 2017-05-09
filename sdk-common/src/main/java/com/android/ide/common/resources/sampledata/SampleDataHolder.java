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
package com.android.ide.common.resources.sampledata;

import com.android.annotations.NonNull;

/** Holder for the sample data cache */
public class SampleDataHolder {
    private final String myName;
    private final long myLastModification;
    private final byte[] myContents;
    private final int myFileSizeMb;

    public SampleDataHolder(
            @NonNull String name,
            long lastModification,
            int contentSizeMb,
            @NonNull byte[] contents) {
        myName = name;
        myLastModification = lastModification;
        myFileSizeMb = contentSizeMb;
        myContents = contents;
    }

    @NonNull
    public String getName() {
        return myName;
    }

    public long getLastModification() {
        return myLastModification;
    }

    @NonNull
    public byte[] getContents() {
        return myContents;
    }

    public int getFileSizeMb() {
        return myFileSizeMb;
    }
}
