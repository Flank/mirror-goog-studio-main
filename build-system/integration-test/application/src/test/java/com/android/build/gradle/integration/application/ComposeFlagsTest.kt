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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertNotNull

class ComposeFlagsTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    private val withCompose = MinimalSubProject.app("com.example.with")
        .appendToBuild("""
android {
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "+"
    }
}
        """.trimIndent())

    private val withoutCompose = MinimalSubProject.app("com.example.without")

    private val explicitWithoutCompose = MinimalSubProject.app("com.example.explicit")
        .appendToBuild("""
android {
    buildFeatures {
        compose false
    }
}
        """.trimIndent())

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":with", withCompose)
                .subproject(":without", withoutCompose)
                .subproject(":explicitWithout", explicitWithoutCompose)
                .build()
        ).create()

    @Test
    fun verifyFlagInModel() {
        assertNotNull(project)
        val withModel = project.model().fetchAndroidProjects().onlyModelMap[":with"]
        assertNotNull(withModel)
        assertThat(withModel.flags.booleanFlagMap[
                AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE]).isTrue()
        val withoutModel = project.model().fetchAndroidProjects().onlyModelMap[":without"]
        assertNotNull(withoutModel)
        assertThat(withoutModel.flags.booleanFlagMap[
                AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE]).isFalse()
        val explicitWithoutModel =
            project.model().fetchAndroidProjects().onlyModelMap[":explicitWithout"]
        assertNotNull(explicitWithoutModel)
        assertThat(explicitWithoutModel.flags.booleanFlagMap[
                AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE]).isFalse()
    }
}
