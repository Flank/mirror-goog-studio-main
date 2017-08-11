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
package com.android.tools.perflib.heap.ext;

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.Snapshot;

public interface SnapshotPostProcessor {
    /**
     * Perform any post-process operations on the hprof snapshot. The {@link Snapshot} instance is
     * expected to be fully parsed at this point. (e.g. all real classes and instances objects
     * should have been added) However, any computations (e.g. retained size, dominators) based on
     * the heap objects have not taken place.
     */
    void postProcess(@NonNull Snapshot snapshot);
}
