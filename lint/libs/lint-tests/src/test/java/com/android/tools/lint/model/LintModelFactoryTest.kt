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

package com.android.tools.lint.model

import com.android.tools.lint.checks.infrastructure.GradleModelMocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class LintModelFactoryTest {
    @Test
    fun testCaching() {
        val mocker = GradleModelMocker(
            """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion 'android-Q'
                    defaultConfig {
                        minSdkVersion 19
                        targetSdkVersion 'Q'
                    }
                    resourcePrefix 'unit_test_prefix_'
                }

                dependencies {
                    implementation 'com.android.support:appcompat-v7:28.0.0'
                }
                """
        )
        val module1 =
            LintModelFactory().create(mocker.project, mocker.variants, mocker.projectDir)
        assertEquals("unit_test_prefix_", module1.resourcePrefix)

        // Should be cached
        val module2 =
            LintModelFactory().create(mocker.project, mocker.variants, mocker.projectDir)
        assertSame(module1, module2)

        // Different project: should not be cached
        val mocker2 = GradleModelMocker("apply plugin: 'com.android.application'")
        val module3 =
            LintModelFactory().create(mocker2.project, mocker.variants, mocker2.projectDir)
        assertNotSame(module1, module3)
    }
}
