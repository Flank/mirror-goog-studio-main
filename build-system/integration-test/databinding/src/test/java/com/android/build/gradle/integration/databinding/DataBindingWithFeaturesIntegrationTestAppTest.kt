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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.IOException

/**
 * This is a smoke test that ensures that the integration tests of DataBinding with Features
 * compiles fine. The actual test is run as a post-submit step.
 */
@RunWith(JUnit4::class)
class DataBindingWithFeaturesIntegrationTestAppTest {
    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .fromDataBindingIntegrationTest("InstantApp")
        .addGradleProperties(
                BooleanOption.ENABLE_DATA_BINDING_V2.propertyName + "=true"
        )
        .addGradleProperties(
                BooleanOption.ENABLE_EXPERIMENTAL_FEATURE_DATABINDING.propertyName
                        + "=true"
        )
        .create()

    @Before
    fun clean() {
        project.execute("clean")
    }

    @Test
    fun instantapp() {
        project.execute(":instantapp:assemble")
    }

    @Test
    fun app() {
        project.execute(":app:assemble")
    }
}
