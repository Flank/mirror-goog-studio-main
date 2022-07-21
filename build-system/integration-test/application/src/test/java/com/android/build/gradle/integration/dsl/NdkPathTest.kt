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

package com.android.build.gradle.integration.dsl

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.forEachLine
import com.google.common.truth.Truth
import junit.framework.TestCase.fail
import org.junit.Rule
import org.junit.Test

class NdkPathTest {
    @get:Rule
    val project = createGradleProject {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                ndkPath = "/path/to/ndk"
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun testNdkPathFromSettings() {
        project.buildFile.appendText("println(\"$PREFIX\${android.ndkPath}\")")

        val result = project.executor().run("projects")

        var found = false
        result.stdout.forEachLine {
            if (it.startsWith(PREFIX)) {
                found = true
                val value = it.substring(PREFIX.length)
                Truth.assertThat(value).isEqualTo("/path/to/ndk")
                return@forEachLine
            }
        }

        if (!found) {
            fail("Did not find ndkPath value in stdout")
        }
    }
}

private const val PREFIX = "NDKPATH: "
