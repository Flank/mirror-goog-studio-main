/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.work.FileChange
import org.gradle.work.InputChanges

class FakeInputChanges(
    val incremental: Boolean = false,
    val inputChanges: List<FileChange> = emptyList()
) : InputChanges {

    override fun isIncremental(): Boolean {
        return incremental
    }

    override fun getFileChanges(parameter: FileCollection): MutableIterable<FileChange> {
        return inputChanges.toMutableList()
    }

    override fun getFileChanges(parameter: Provider<out FileSystemLocation>)
            : MutableIterable<FileChange> {
        return inputChanges.toMutableList()
    }
}