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

package com.android.build.api

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.Artifacts
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import org.mockito.Mock

class OperationRequestTest {

    @Mock
    lateinit var artifacts: Artifacts

    @Mock
    lateinit var dirTask: TaskProvider<MyDirTask>

    @Mock
    lateinit var fileTask: TaskProvider<MyFileTask>

    @Mock
    lateinit var fileCombiningTask: TaskProvider<MyFileCombiningTask>

    @Mock
    lateinit var dirCombiningTask: TaskProvider<MyDirCombiningTask>

    abstract class MyDirTask: DefaultTask() {
        abstract val input: DirectoryProperty
        abstract val output: DirectoryProperty
    }

    abstract class MyFileTask: DefaultTask() {
        abstract val input: RegularFileProperty
        abstract val output: RegularFileProperty
    }

    abstract class MyFileCombiningTask: DefaultTask() {
        abstract val input: ListProperty<RegularFile>
        abstract val output: RegularFileProperty
    }

    abstract class MyDirCombiningTask: DefaultTask() {
        abstract val input: ListProperty<Directory>
        abstract val output: DirectoryProperty
    }

    fun testSimple() {

        artifacts
            .use(dirTask)
            .wiredWithDirectories(MyDirTask::input, MyDirTask::output)
            .toTransform(SingleArtifact.APK)

//        artifacts
//            .use2(fileTask)
//            .with(MyFileTask::input)
//            .toAppendTo(ArtifactType.BUNDLE)

        artifacts
            .use(fileTask)
            .wiredWithFiles(MyFileTask::input, MyFileTask::output)
            .toTransform(SingleArtifact.BUNDLE)

        artifacts
            .use(fileTask)
            .wiredWith(MyFileTask::output)
            .toCreate(SingleArtifact.MERGED_MANIFEST)
    }
}
