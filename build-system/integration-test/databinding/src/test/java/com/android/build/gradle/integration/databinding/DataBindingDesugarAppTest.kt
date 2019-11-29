/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_MIN_SDK
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test use of Java 8 language in the application module with data binding.
 *
 * regression test for - http://b.android.com/321693
 */
@RunWith(Parameterized::class)
class DataBindingDesugarAppTest(private val enableGradleWorkers: Boolean) {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    companion object {
        @Parameterized.Parameters(name = "enableGradleWorkers={0}")
        @JvmStatic
        fun getParameters() = arrayOf(java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
    }

    private val projectExecutor: GradleTaskExecutor
        get() = project.executor().with(BooleanOption.ENABLE_GRADLE_WORKERS, enableGradleWorkers)

    @Test
    fun testDatabinding() {
        TestFileUtils.appendToFile(
            project.buildFile,
                """
                |android.compileOptions.sourceCompatibility 1.8
                |android.compileOptions.targetCompatibility 1.8
                |android.buildFeatures.dataBinding true
                |android.defaultConfig.minSdkVersion $SUPPORT_LIB_MIN_SDK
                |dependencies {
                |    compile 'com.android.support:support-v4:$SUPPORT_LIB_VERSION'
                |}
                """.trimMargin("|")
        )

        projectExecutor.run("assembleDebug")
    }
}
