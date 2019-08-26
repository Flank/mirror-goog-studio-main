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

package com.android.build.api.artifact.impl

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.junit.Test

class MultipleDirectoryArtifactTest : AbstractMultipleArtifactTest<Directory>(
{ objectFactory -> objectFactory.listProperty(Directory::class.java)},
{ directory, name -> directory.dir(name) },
{ tasks, name -> tasks.register(name, SingleDirectoryArtifactTest.DirectoryProducerTask::class.java)}) {

    @Test
    fun testReplace() {
        abstract class MultipleDirectoryProducerTask: MultipleProducerTask<Directory>()
        super.testReplace { tasks, taskName ->
            tasks.register(taskName, MultipleDirectoryProducerTask::class.java)
        }
    }

    @Test
    fun testAddAndReplace() {
        abstract class MultipleFileProducerTask: MultipleProducerTask<Directory>()
        super.testAddAndReplace() { tasks, taskName ->
            tasks.register(taskName, MultipleFileProducerTask::class.java)
        }
    }

    @Test
    fun testTransform() {
        abstract class MultipleFileProducerTask: MultipleProducerTask<Directory>()
        super.testTransform() { tasks, taskName ->
            tasks.register(taskName, MultipleFileProducerTask::class.java)
        }
    }
}