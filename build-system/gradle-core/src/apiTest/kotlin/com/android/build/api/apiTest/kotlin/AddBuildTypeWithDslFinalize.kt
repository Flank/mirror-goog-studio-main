/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

class AddBuildTypeWithDslFinalize: VariantApiBaseTest(TestType.Script) {

    @Test
    fun addBuildTypeUsingDslFinalize() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleExtra"))
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
                    |plugins {
                    |    id("com.android.application")
                    |    kotlin("android")
                    |    kotlin("android.extensions")
                    |}

                    |android {
                    |    ${testingElements.addCommonAndroidBuildLogic()}
                    |}

                    |androidComponents.finalizeDsl { extension ->
                    |    extension.buildTypes.create("extra").let {
                    |       it.isJniDebuggable = true
                    |    }
                    |}
                    """.trimMargin()
                testingElements.addManifest( this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                    """
                This recipe shows how a build script can add a build type programmatically
                using the finalizeDsl androidComponents {} API.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Task :app:assembleExtra")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
