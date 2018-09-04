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
 * Test cases for [SourceSet].
 */
class SourceSetTest {
    val sourceSetMap = mapOf(
        AndroidPathType.MANIFEST to listOf(PathString("Manifest.xml")),
        AndroidPathType.JAVA to listOf("src1", "src2").map { PathString(it) },
        AndroidPathType.RES to listOf("res1", "res2").map { PathString(it) },
        AndroidPathType.C to listOf("C1", "C2").map { PathString(it) }
    )
    val sourceSet = SourceSet(sourceSetMap)

    @Test
    fun builderTest() {
        val builtByBuilter = SourceSet.Builder()
            .add(AndroidPathType.MANIFEST, PathString("Manifest.xml"))
            .add(AndroidPathType.JAVA, PathString("src1"), PathString("src2"))
            .add(AndroidPathType.RES, listOf(PathString("res1"), PathString("res2")))
            .addIfNotNull(AndroidPathType.C, PathString("C1"))
            .addIfNotNull(AndroidPathType.C, null)
            .addIfNotNull(AndroidPathType.C, PathString("C2"))
            .build()

        assertThat(builtByBuilter).isEqualTo(sourceSet);
    }

    @Test
    fun getterTest() {
        assertThat(sourceSet.manifests).containsExactly(PathString("Manifest.xml"))
        assertThat(sourceSet.javaDirectories).containsExactly(
            PathString("src1"),
            PathString("src2")
        )
        assertThat(sourceSet.resDirectories).containsExactly(
            PathString("res1"),
            PathString("res2")
        )
        assertThat(sourceSet.assetsDirectories).isEmpty()
        assertThat(sourceSet[AndroidPathType.C]).containsExactly(
            PathString("C1"),
            PathString("C2")
        )
        assertThat(sourceSet[AndroidPathType.RENDERSCRIPT]).isEmpty()
    }

    @Test
    fun asMapTest() {
        assertThat(sourceSet.asMap).isEqualTo(sourceSetMap)
    }
}