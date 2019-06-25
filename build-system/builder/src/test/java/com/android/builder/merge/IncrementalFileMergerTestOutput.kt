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

package com.android.builder.merge

import com.google.common.truth.Truth.assertThat

/**
 * Output that keeps track of all requested operations. Used for tests.
 */
internal class IncrementalFileMergerTestOutput : IncrementalFileMergerOutput {
    var open: Boolean = false
    val removed: MutableSet<String> = mutableSetOf()
    val created: MutableList<CreateParams> = mutableListOf()
    val updated: MutableList<UpdateParams> = mutableListOf()

    override fun open() {
        assertThat(open).isFalse()
        open = true
    }

    override fun close() {
        assertThat(open).isTrue()
        open = false
    }

    override fun remove(path: String) {
        assertThat(open).isTrue()
        removed.add(path)
    }

    override fun create(
        path: String,
        inputs: List<IncrementalFileMergerInput>,
        compress: Boolean
    ) {
        assertThat(open).isTrue()
        assertThat(created.any { it.path == path }).isFalse()
        created.add(CreateParams(path, inputs.toList(), compress))
    }

    override fun update(
        path: String,
        prevInputNames: List<String>,
        inputs: List<IncrementalFileMergerInput>,
        compress: Boolean
    ) {
        assertThat(open).isTrue()
        assertThat(created.any { it.path == path }).isFalse()
        updated.add(UpdateParams(path, prevInputNames.toList(), inputs.toList(), compress))
    }
}

data class CreateParams(
    val path: String,
    val inputs: List<IncrementalFileMergerInput>,
    val compress: Boolean)

data class UpdateParams(
    val path: String,
    val prevInputNames: List<String>,
    val inputs: List<IncrementalFileMergerInput>,
    val compress: Boolean)

