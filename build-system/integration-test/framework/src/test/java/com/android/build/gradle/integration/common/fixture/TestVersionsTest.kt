/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture

import com.android.sdklib.SdkVersionInfo
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Test to ensure that the test versions don't fall too far behind other versions */
class TestVersionsTest {
    @Test
    fun checkCompileSdkVersion() {
        assertWithMessage("The compile sdk version used for tests should be recent")
            .that(DEFAULT_COMPILE_SDK_VERSION)
            .named("TestVersions DEFAULT_COMPILE_SDK_VERSION")
            .isAtLeast(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API - 1)
    }
}
