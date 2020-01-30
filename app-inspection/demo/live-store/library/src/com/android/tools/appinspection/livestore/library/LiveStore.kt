/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.appinspection.livestore.library

/**
 * A simple hashmap based key-value store created to demonstrate how an inspector agent can intercept state changes.
 *
 * Currently, it only works with string key and value pairs.
 */
class LiveStore {
    private val store = mutableMapOf<String, String>()

    /**
     * Returns the value at [key]. Null if it doesn't exist.
     */
    fun get(key: String) = store[key]

    /**
     * Sets the provided value at [key].
     */
    fun set(key: String, value: String) = store.set(key, value)
}

operator fun LiveStore.get(key: String) = get(key)

operator fun LiveStore.set(key: String, value: String) = set(key, value)