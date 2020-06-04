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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.internal.dependency.DexingNoClasspathTransform
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for https://issuetracker.google.com/144916156. Make sure we are using
 * dexing artifact transforms when there are external transforms that are not applied to the
 * current variant.
 */
class DexingArtifactTransformWithTransformApiTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder()
        .fromTestProject("transformVariantApiTest")
        .create()

    @Test
    fun testArtifactTransformsUsedForDebugVariant() {
        project.executor().run("assembleDebug")
        project.buildResult.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner)
                .contains(DexingNoClasspathTransform::class.java.simpleName)
        }
    }

    @Test
    fun testArtifactTransformsNotUsedForReleaseVariant() {
        // b/149978740
        project.executor().withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF).run("assembleRelease")
        project.buildResult.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner)
                .doesNotContain(DexingNoClasspathTransform::class.java.simpleName)
        }
    }
}