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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.truth.MoreTruth
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for the Jetifier feature.
 */
class JetifierTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("jetifier")
        .create()

    @Test
    fun testJetifierDisabled() {
        // Build the project with Jetifier disabled
        project.executor().run("assembleDebug")
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)
        val dex = apk.mainDexFile.get()

        // 1. Check that the old support library is not yet replaced with a new one
        MoreTruth.assertThat(dex).containsClass("Landroid/support/v7/preference/Preference;")
        MoreTruth.assertThat(dex).doesNotContainClasses("Landroidx/preference/Preference;")

        // 2. Check that the library to refactor is not yet refactored
        val classToRefactor =
            MoreTruth.assertThat(dex).containsClass("Lcom/example/androidlib/MyPreference;")
        classToRefactor.that().hasSuperclass("Landroid/support/v7/preference/Preference;")
    }
}
