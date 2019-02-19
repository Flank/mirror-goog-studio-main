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

package com.android.build.gradle.integration.nativebuild

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.testutils.AssumeUtil
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

class VulkanTest {
    @get:Rule
    val project = GradleTestProject.builder()
        .setCmakeVersion("3.10.4819442")
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .setWithCmakeDirInLocalProp(true)
        .fromTestProject("vulkan").create()

    @Test
    fun assembleDebug() {
        project.executor().run("assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk).containsFile("lib/x86/libvktuts.so")
            assertThat(apk).containsFile("lib/x86_64/libvktuts.so")
            assertThat(apk).containsFile("lib/armeabi-v7a/libvktuts.so")
            assertThat(apk).containsFile("lib/arm64-v8a/libvktuts.so")
            assertThat(apk).containsFile("assets/shaders/tri.vert.spv")
            assertThat(apk).containsFile("assets/shaders/tri.frag.spv")
        }
    }
}