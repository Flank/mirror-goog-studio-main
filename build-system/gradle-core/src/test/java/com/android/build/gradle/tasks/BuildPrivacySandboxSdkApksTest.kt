/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Path

class BuildPrivacySandboxSdkApksTest {

    @Test
    fun testOutputNameChooserCollisionAvoidance() {
        val fs = Jimfs.newFileSystem(Configuration.windows())
        val temp = fs.getPath("temp")
        val inputs = listOf(
                temp.resolve("a").resolve("alpha_1.example"),
                temp.resolve("b").resolve("bravo.example2"),
                temp.resolve("c").resolve("alpha.example2"),
                temp.resolve("d").resolve("alpha"),
        )

        val actions = mutableListOf<Pair<Path, Path>>()
        val outDir = fs.getPath("out")
        BuildPrivacySandboxSdkApks.forEachInputFile(inputs, outDir) { input, output ->
            actions += (input to output)
        }
        assertThat(actions).containsExactly(
                inputs[0] to outDir.resolve("alpha_1"),
                inputs[1] to outDir.resolve("bravo"),
                inputs[2] to outDir.resolve("alpha"),
                inputs[3] to outDir.resolve("alpha_2"),
        ).inOrder()

    }
}
