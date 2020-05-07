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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Tests for [AarMetadataTask]. */
class AarMetadataTaskTest {

    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testBasic() {
        // first test that there is no AAR metadata file if the feature is not enabled.
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            assertThat(getEntry(AarMetadataTask.aarMetadataEntryPath)).isNull()
        }

        // then test that AAR metadata file is present if the feature is enabled.
        project.executor().with(BooleanOption.ENABLE_AAR_METADATA, true).run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            assertThat(getEntry(AarMetadataTask.aarMetadataEntryPath)).isNotNull()
        }
    }
}
