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
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for b/215407138. Ensure that finalizeDsl blocks are run before all checks on the extension objects.
 */
class CompileSdkSetThroughDSLFinalizeBlock {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
            }
        }
    }

    @Test
    fun testCompileSdkVersion() {
        project.buildFile.appendText("""
androidComponents {
    finalizeDsl { extension ->
        println("finalizeDsl { ... }")
        extension.compileSdk = 31
        extension.defaultConfig {
            minSdk = 19
            targetSdk = 31
        }
    }
}
        """.trimIndent())

        val result = project.executor().run("tasks")
        Truth.assertThat(result.failureMessage).isNull()
    }
}
