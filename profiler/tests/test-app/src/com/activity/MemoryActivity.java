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

package com.activity;

import java.util.*;

public class MemoryActivity extends PerfdTestActivity {

    public static class MemTestEntity {};

    public MemoryActivity() {
        super("MemoryActivity");
        makeSureTestEntityClassLoaded(null);
    }

    // This functions is really doing nothing except making sure
    // MemTestEntity class is loaded by the time we start real testing.
    private MemTestEntity makeSureTestEntityClassLoaded(MemTestEntity o)
    {
        if (o != null) {
            return new MemTestEntity();
        }
        return o;
    }

    List<Object> entities = new ArrayList<Object>();

    public void makeAllocationNoise() {
        List<Object> objs = new ArrayList<Object>();
        final int DataBatchSize = 2000;
        for (int i = 0; i < DataBatchSize; i++) {
            objs.add(new Object());
        }
        System.out.println("MemoryActivity.makeAllocationNoise");
    }

    public void allocate() {
        final int count = Integer.parseInt(System.getProperty("allocation.count"));
        for (int i = 0; i < count; i++) {
            entities.add(new MemTestEntity());
        }
        System.out.println("MemoryActivity.allocate");
        System.out.println("MemoryActivity.size " + entities.size());
    }

    public void free() {
        entities.clear();
        System.out.println("MemoryActivity.free");
        System.out.println("MemoryActivity.size " + entities.size());
    }

    public void gc() {
        System.gc();
        System.out.println("MemoryActivity.gc");
    }
}
