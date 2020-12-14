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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getDebugGenerateSourcesCommands
import com.android.build.gradle.integration.common.utils.getDebugVariant
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.AndroidProject
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Sanity test for automatic namespacing of dependencies.
 *
 * Verifies that the AARs in the model appear namespaced and that the project builds.
 */
class AutoNamespaceTest {
    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("namespacedApp")
        .create()

    @Test
    fun checkNamespacedApp() {

        // Check model level 3
        val modelContainer =
            project.model().level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
                .fetchAndroidProjects()
        val model = modelContainer.onlyModel

        val libraries = model.getDebugVariant().mainArtifact.dependencies.libraries
        assertThat(libraries).isNotEmpty()
        libraries.forEach { lib ->
            assertThat(lib.resStaticLibrary).exists()
        }

        project.executor().run("assembleDebug")

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        // Sanity check the final APK.
        assertThat(apk).containsClass("Landroid/support/constraint/Guideline;")
        assertThat(apk).containsClass("Landroid/support/constraint/R\$attr;")
    }
}
