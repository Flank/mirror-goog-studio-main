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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [AndroidModel]
 */
class AndroidModelTest {
    @Test
    fun testEmptyToString() {
        assertThat(AndroidModel().toString()).isEqualTo("AndroidModel()")
    }

    @Test
    fun testEquals() {
        assertThat(AndroidModel()).isEqualTo(AndroidModel())
    }

    @Test
    fun testSingleSubmoduleToString() {
        assertThat(
            AndroidModel(
              submodules = listOf(
                AndroidSubmodule(
                        name = "project",
                        type = ProjectType.APP
                    )
                )
            ).toString()
        )
            .isEqualTo("AndroidModel(submodules=[AndroidSubmodule(name=project,type=APP)])")
    }

    /**
     * Tests the [AndroidModel.getSubmodule] method.
     */
    @Test
    fun testGetSubmodule() {
        val p1 = AndroidSubmodule(name="p1", type=ProjectType.APP)
        val p2 = AndroidSubmodule(name="p2", type=ProjectType.APP)
        val p3 = AndroidSubmodule(name="p3", type=ProjectType.APP)
        val model = AndroidModel(listOf(p1, p2, p3))

        assertThat(model.getSubmodule("p1")).isEqualTo(p1)
        assertThat(model.getSubmodule("p2")).isEqualTo(p2)
        assertThat(model.getSubmodule("p3")).isEqualTo(p3)
        assertThat(model.getSubmodule("missing key")).isNull()
    }
}
