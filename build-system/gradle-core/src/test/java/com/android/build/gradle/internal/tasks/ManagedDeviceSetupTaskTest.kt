/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ManagedDeviceSetupTaskTest {
    private lateinit var mockVersionedSdkLoader: VersionedSdkLoader

    @Before
    fun setup() {
        mockVersionedSdkLoader = mock(VersionedSdkLoader::class.java)
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(false)
    }

    @Test
    fun testGenerateSystemImageErrorOffline() {
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(true)
        val errorMessage = ManagedDeviceSetupTask.generateSystemImageErrorMessage(
            "system-images;android-29;default;x86",
            mockVersionedSdkLoader
        )
        assertThat(errorMessage).isEqualTo(
            """
                system-images;android-29;default;x86 is not available, and could not be downloaded while in offline mode.
            """.trimIndent()
        )
    }
}
