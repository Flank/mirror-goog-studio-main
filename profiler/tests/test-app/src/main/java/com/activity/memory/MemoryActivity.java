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

package com.activity.memory;

import com.activity.TransportTestActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused") // Accessed via reflection by perf-test
public final class MemoryActivity extends TransportTestActivity {
    static class MemTestEntity {
        private byte[] values;

        MemTestEntity(int size) {
            values = new byte[size];
            for (int i = 0; i < size; ++i) {
                values[i] = (byte) (i & 0xFF);
            }
        }
    }

    static class MemNoiseEntity {}

    static {
        // Ensure the classes are loaded.
        Class<MemTestEntity> KLASS1 = MemTestEntity.class;
        Class<MemNoiseEntity> KLASS2 = MemNoiseEntity.class;
    }

    public MemoryActivity() {
        super("MemoryActivity");
    }

    ArrayList<Object> entities = new ArrayList<Object>();

    public void makeAllocationNoise() {
        List<Object> objs = new ArrayList<Object>();
        final int DataBatchSize = 2000;
        for (int i = 0; i < DataBatchSize; i++) {
            objs.add(new MemNoiseEntity());
        }
        System.out.println("MemoryActivity.makeAllocationNoise");
    }

    public void allocate() {
        final int count = Integer.parseInt(System.getProperty("allocation.count"));
        final int size = Integer.parseInt(System.getProperty("allocation.size", "1"));
        entities.ensureCapacity(count);
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            entities.add(new MemTestEntity(size));
        }
        long end = System.nanoTime();
        System.out.println("MemoryActivity.allocate");
        System.out.println("allocation_count=" + entities.size());
        // Append newline manually instead of using println() to avoid the occasional situation where stdout is flushed
        // with some other string, causing the number parsing in LiveAllocationTest to fail.
        System.out.print(
                "allocation_timing=" + TimeUnit.NANOSECONDS.toMillis(end - start) + "\r\n");
    }

    public void free() {
        entities.clear();
        System.out.println("MemoryActivity.free");
        System.out.println("free_count=" + entities.size());
    }

    public void gc() {
        Runtime.getRuntime().gc();
        System.out.println("MemoryActivity.gc");
    }
}
