
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
package com.android.build.api.apiTest.buildsrc

import com.android.build.api.apiTest.VariantApiBaseTest

/**
 * Parent class for all buildSrc related tests with common behaviors.
 */
open class BuildSrcScriptApiTest: VariantApiBaseTest(
    TestType.BuildSrc
) {
    protected fun addCommonBuildFile(givenBuilder: GivenBuilder, androidBlock: String? = null) {
        givenBuilder.buildFile =
            """
            plugins {
                    id("com.android.application")
                    kotlin("android")
            }

            apply<ExamplePlugin>()

            android { ${testingElements.addCommonAndroidBuildLogic()}
                ${androidBlock ?: ""}
            }
            """.trimIndent()
    }
}
