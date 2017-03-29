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

public class HprofControlSettings implements HprofRecord {
    public static final byte TAG = 0x0E;

    // Bit mask flags:
    public static final int ALLOC_TRACES_ON = 0x1;
    public static final int CPU_SAMPLING_ON = 0x2;

    public final int time;
    public final int bitMaskFlags;       // u4
    public final short stackTraceDepth;     // u2

    public HprofControlSettings(int time, int bitMaskFlags, short stackTraceDepth) {
        this.time = time;
        this.bitMaskFlags = bitMaskFlags;
        this.stackTraceDepth = stackTraceDepth;
    }

    @Override
    public void write(HprofOutputStream hprof) throws IOException {
        hprof.writeRecordHeader(TAG, time, 4 + 2);
        hprof.writeU4(bitMaskFlags);
        hprof.writeU2(stackTraceDepth);
    }
}
