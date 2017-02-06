/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.merge;

import com.android.annotations.NonNull;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Algorithm to merge streams. See {@link StreamMergeAlgorithms} for some commonly-used algorithms.
 */
public interface StreamMergeAlgorithm {

    /**
     * Merges streams in {@code from} to the stream {@code to}.
     *
     * @param path the OS-independent path being merged
     * @param from the source streams; must contains at least one element
     * @param to the destination file
     */
    void merge(@NonNull String path, @NonNull List<InputStream> from, @NonNull OutputStream to);
}
