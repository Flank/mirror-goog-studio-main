/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.perflib.heap.hprof;

import java.io.IOException;

public class HprofRootJniGlobal implements HprofDumpRecord {
    public static final byte SUBTAG = 0x01;

    public final long objectId;       // Id
    public final long jniGlobalRefId; // Id

    public HprofRootJniGlobal(long objectId, long jniGlobalRefId) {
        this.objectId = objectId;
        this.jniGlobalRefId = jniGlobalRefId;
    }

    @Override
    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeU1(SUBTAG);
        hprof.writeId(objectId);
        hprof.writeId(jniGlobalRefId);
    }

    @Override
    public int getLength(int idSize) {
        return 1 + 2*idSize;
    }
}
