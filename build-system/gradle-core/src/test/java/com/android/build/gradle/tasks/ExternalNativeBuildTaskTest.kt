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

package com.android.build.gradle.tasks

import com.google.common.truth.Truth
import org.junit.Test
import java.lang.reflect.Modifier

class ExternalNativeBuildTaskTest {

    @Test
    fun `ensure objFolder and soFolder`() {
        val objFolder =
            ExternalNativeBuildTask::class.java.methods.single { it.name == "getObjFolder" }
        Truth.assertThat(Modifier.isPublic(objFolder.modifiers)).isTrue()

        val soFolder =
            ExternalNativeBuildTask::class.java.methods.single { it.name == "getSoFolder" }
        Truth.assertThat(Modifier.isPublic(soFolder.modifiers)).isTrue()
    }
}