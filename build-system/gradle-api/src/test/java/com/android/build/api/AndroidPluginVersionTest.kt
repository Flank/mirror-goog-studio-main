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

package com.android.build.api

import com.google.common.truth.Truth.assertThat
import nl.jqno.equalsverifier.EqualsVerifier
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class AndroidPluginVersionTest {
    @Test
    fun `test to string`() {
        val stableVersion = AndroidPluginVersion(7, 0, 1)
        assertThat(stableVersion.toString()).isEqualTo("Android Gradle Plugin version 7.0.1")

        val alphaVersion = AndroidPluginVersion(7, 1).alpha(4)
        assertThat(alphaVersion.toString()).isEqualTo("Android Gradle Plugin version 7.1.0-alpha4")

        val devVersion = AndroidPluginVersion(7, 1, 0).dev()
        assertThat(devVersion.toString()).isEqualTo("Android Gradle Plugin version 7.1.0-dev")

    }

    @Test
    fun `check fields`() {
        val version = AndroidPluginVersion(7, 0, 1).rc(2)
        assertThat(version.major).named("major").isEqualTo(7)
        assertThat(version.minor).named("minor").isEqualTo(0)
        assertThat(version.micro).named("micro").isEqualTo(1)
        assertThat(version.previewType).named("previewType").isEqualTo("rc")
        assertThat(version.preview).named("preview").isEqualTo(2)
    }

    @Test
    fun `check sorting`() {
        val versions = listOf(
            AndroidPluginVersion(7, 0).alpha(4),
            AndroidPluginVersion(7, 0).alpha(5),
            AndroidPluginVersion(7, 0).beta(1),
            AndroidPluginVersion(7, 0).beta(2),
            AndroidPluginVersion(7, 0).rc(1),
            AndroidPluginVersion(7, 0).rc(2),
            AndroidPluginVersion(7, 0).dev(),
            AndroidPluginVersion(7, 0),
            // (We've not done this, but let's leave the possibility open)
            AndroidPluginVersion(7, 0, 1).rc(1),
            AndroidPluginVersion(7, 0, 1),
            AndroidPluginVersion(7, 0, 2),
            AndroidPluginVersion(7, 1).alpha(1),
            AndroidPluginVersion(7, 1, 0)
        )

        assertThat(versions.reversed().sorted()).containsExactlyElementsIn(versions).inOrder()
    }

    @Test
    fun `check equals`() {
        EqualsVerifier.forClass(AndroidPluginVersion::class.java).verify()
    }

    @Test
    fun `check stable version validation`() {
        assertThat(assertFailsWith<IllegalArgumentException> { AndroidPluginVersion(-1, 2) })
            .hasMessageThat().isEqualTo("Versions of the Android Gradle Plugin must not be negative")
        assertThat(assertFailsWith<IllegalArgumentException> { AndroidPluginVersion(2, -1) })
            .hasMessageThat().isEqualTo("Versions of the Android Gradle Plugin must not be negative")
        assertThat(assertFailsWith<IllegalArgumentException> { AndroidPluginVersion(2, 2, -1) })
            .hasMessageThat().isEqualTo("Versions of the Android Gradle Plugin must not be negative")
    }


    @Test
    fun `check preview version validation`() {
        val stableVersion = AndroidPluginVersion(7,0)
        assertThat(assertFailsWith<IllegalArgumentException> { stableVersion.alpha(0) })
            .hasMessageThat().isEqualTo("Alpha version must be at least 1")
        assertThat(assertFailsWith<IllegalArgumentException> { stableVersion.beta(0) })
            .hasMessageThat().isEqualTo("Beta version must be at least 1")
        assertThat(assertFailsWith<IllegalArgumentException> { stableVersion.rc(0) })
            .hasMessageThat().isEqualTo("Release candidate version must be at least 1")
    }

    @Test
    fun `check misleading version use check`() {
        val previewVersion = AndroidPluginVersion(7,0).alpha(1)
        assertThat(assertFailsWith<IllegalArgumentException> { previewVersion.alpha(1) })
            .hasMessageThat().isEqualTo("alpha(int) only expected to be called on final versions")
        assertThat(assertFailsWith<IllegalArgumentException> { previewVersion.beta(1) })
            .hasMessageThat().isEqualTo("beta(int) only expected to be called on final versions")
        assertThat(assertFailsWith<IllegalArgumentException> { previewVersion.rc(1) })
            .hasMessageThat().isEqualTo("rc(int) only expected to be called on final versions")
    }
}
