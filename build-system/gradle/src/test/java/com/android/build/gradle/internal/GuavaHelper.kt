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

package com.android.build.gradle.internal

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap

/**
 * Creates a multimap for the given value pairs.
 */
fun multimapOf(vararg pairs: Pair<String, String>): ListMultimap<String, String> {
    val map = ArrayListMultimap.create<String, String>()
    for (pair in pairs) {
        map.put(pair.first, pair.second)
    }

    return map
}

/**
 * Creates a multimap for the given value.
 *
 * The first value is the key, and all subsequent values are values associated with the key.
 */
fun multimapWithSingleKeyOf(
        key: String, vararg values: String): ListMultimap<String, String> {
    val map = ArrayListMultimap.create<String, String>()
    for (value in values) {
        map.put(key, value)
    }
    return map
}
