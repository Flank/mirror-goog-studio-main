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

public class HprofRootNativeStack implements HprofDumpRecord {
    public static final byte SUBTAG = 0x04;

    public final long objectId;           // Id
    public final int threadSerialNumber;  // u4

    public HprofRootNativeStack(long objectId, int threadSerialNumber) {
        this.objectId = objectId;
        this.threadSerialNumber = threadSerialNumber;
    }

    @Override
    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeU1(SUBTAG);
        hprof.writeId(objectId);
        hprof.writeU4(threadSerialNumber);
    }

    @Override
    public int getLength(int idSize) {
        return 1 + idSize + 4;
    }
}
