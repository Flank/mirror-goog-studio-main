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

package com.android.projectmodel

import com.android.ide.common.util.PathString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [AndroidSubmodule]
 */
class AndroidSubmoduleTest {

    val project = AndroidSubmodule(name="foo", type=ProjectType.APP)

    @Test
    fun testToString() {
        assertThat(project.toString()).isEqualTo("AndroidSubmodule(name=foo,type=APP)")
    }

    @Test
    fun testWithGeneratedPaths() {
        val paths = listOf("baz", "bam").map{ PathString(it) }
        assertThat(project.withGeneratedPaths(paths)).isEqualTo(project.copy(generatedPaths = paths))
    }

    @Test
    fun testWithNamespacing() {
        assertThat(project.withNamespacing(NamespacingType.REQUIRED)).isEqualTo(project.copy(namespacing = NamespacingType.REQUIRED))
    }

    @Test
    fun testWithType() {
        assertThat(project.withType(ProjectType.LIBRARY)).isEqualTo(project.copy(type = ProjectType.LIBRARY))
    }

    @Test
    fun testWithVariantsGeneratedBy() {
        val configTable = ConfigTable()
        assertThat(
            project.withVariantsGeneratedBy(
                configTable,
                configTable.generateArtifacts()
            )
        ).isEqualTo(
            project.copy(
                configTable = configTable,
                variants = configTable.generateVariants()
            )
        )
    }
}
