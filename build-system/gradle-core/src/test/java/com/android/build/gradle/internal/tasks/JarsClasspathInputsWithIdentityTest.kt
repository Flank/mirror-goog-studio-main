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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.build.gradle.internal.fixtures.FakeInputChanges
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.work.ChangeType
import org.junit.Test

class JarsClasspathInputsWithIdentityTest {

    @Test
    fun testInitialIdentity() {
        val (inputs, jarsIdentity) = getInitialIdentity("input1.jar", "input2.jar")

        inputs.forEachIndexed { index, file ->
            assertThat(jarsIdentity.jarsInfo[file]).isEqualTo(FileInfo(index.toString(), true))
        }
    }

    @Test
    fun testIdentityLoadedOnce() {
        val mapping =
            FakeObjectFactory.factory.newInstance(JarsClasspathInputsWithIdentity::class.java)
        val changes = FakeInputChanges(false, emptyList())
        assertThat(mapping.getMappingState(changes))
            .isSameInstanceAs(mapping.getMappingState(changes))
    }

    @Test
    fun testIdentityWithAdded() {
        val (inputs, _) = getInitialIdentity("input1.jar", "input2.jar")

        val first = inputs.toList()[0]
        val incrementalIdentity = getNewJarsIdentity(
            listOf(FakeFileChange(file = first, changeType = ChangeType.ADDED)),
            inputs
        )

        val second = inputs.toList()[1]
        assertThat(incrementalIdentity.jarsInfo.getValue(first).hasChanged).isTrue()
        assertThat(incrementalIdentity.jarsInfo.getValue(second).hasChanged).isTrue()

        assertThat(incrementalIdentity.reprocessAll).isTrue()
    }

    @Test
    fun testIdentityWithRemoved() {
        val (inputs, _) = getInitialIdentity("input1.jar", "input2.jar")

        val first = inputs.toList()[0]
        val incrementalIdentity = getNewJarsIdentity(
            listOf(FakeFileChange(file = first, changeType = ChangeType.REMOVED)),
            inputs
        )

        val second = inputs.toList()[1]
        assertThat(incrementalIdentity.jarsInfo.getValue(first).hasChanged).isTrue()
        assertThat(incrementalIdentity.jarsInfo.getValue(second).hasChanged).isTrue()

        assertThat(incrementalIdentity.reprocessAll).isTrue()
    }

    @Test
    fun testIdentityWithModified() {
        val (inputs, _) = getInitialIdentity("input1.jar", "input2.jar")

        val first = inputs.toList()[0]
        val incrementalIdentity = getNewJarsIdentity(
            listOf(FakeFileChange(file = first, changeType = ChangeType.MODIFIED)),
            inputs
        )

        val second = inputs.toList()[1]
        assertThat(incrementalIdentity.jarsInfo.getValue(first).hasChanged).isTrue()
        assertThat(incrementalIdentity.jarsInfo.getValue(second).hasChanged).isFalse()

        assertThat(incrementalIdentity.reprocessAll).isFalse()
    }

    @Test
    fun testIdentityWithNonIncrementalChange() {
        val (inputs, _) = getInitialIdentity("input1.jar", "input2.jar")

        val first = inputs.toList()[0]
        val nonIncrementalIdentity = getNewJarsIdentity(
            listOf(),
            inputs,
            false
        )

        val second = inputs.toList()[1]
        assertThat(nonIncrementalIdentity.jarsInfo.getValue(first).hasChanged).isTrue()
        assertThat(nonIncrementalIdentity.jarsInfo.getValue(second).hasChanged).isTrue()

        assertThat(nonIncrementalIdentity.reprocessAll).isTrue()
    }

    private fun getInitialIdentity(vararg files: String): Pair<ConfigurableFileCollection, JarsIdentityMapping> {
        val mapping =
            FakeObjectFactory.factory.newInstance(JarsClasspathInputsWithIdentity::class.java)

        val inputs = FakeObjectFactory.factory.fileCollection().from(files)
        mapping.inputJars.setFrom(inputs)


        return Pair(
            inputs, mapping.getMappingState(
            FakeInputChanges(
                true, inputs.map {
                    FakeFileChange(file = it, changeType = ChangeType.ADDED)
                }
            )
        ))
    }

    private fun getNewJarsIdentity(
        changed: List<FakeFileChange>,
        inputFiles: FileCollection,
        incremental: Boolean = true
    ): JarsIdentityMapping {
        val mapping =
            FakeObjectFactory.factory.newInstance(JarsClasspathInputsWithIdentity::class.java)

        val inputs = FakeObjectFactory.factory.fileCollection().from(inputFiles)
        mapping.inputJars.setFrom(inputs)

        return mapping.getMappingState(
            FakeInputChanges(incremental, changed)
        )
    }
}