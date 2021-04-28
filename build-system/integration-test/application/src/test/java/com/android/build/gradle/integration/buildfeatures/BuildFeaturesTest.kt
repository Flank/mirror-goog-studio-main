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

package com.android.build.gradle.integration.buildfeatures

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertNotNull

@RunWith(Parameterized::class)
class BuildFeaturesTest(private val buildFeature: String) {

    companion object {
        @Parameterized.Parameters(name = "feature={0}")
        @JvmStatic
        fun data() = arrayOf(
            arrayOf("renderScript"),
            arrayOf("aidl"),
            arrayOf("dataBinding")
        )
    }

    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    val app = MinimalSubProject.app("com.example.test")
        .appendToBuild("""
android {
    buildFeatures {
        $buildFeature = false
    }
}
    """)

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()
        ).create()

    /**
     * Test to ensure the model is property populated when [buildFeature] is disabled.
     */
    @Test
    fun `$buildFeature DisabledTest`() {
        assertNotNull(project)
        project.execute("clean", "lintDebug")
    }
}
