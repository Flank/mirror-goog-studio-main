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

package com.android.build.gradle.internal.ide

import com.google.common.truth.Truth.assertThat
import org.gradle.api.InvalidUserDataException
import org.junit.Test
import kotlin.test.assertFailsWith

class StudioVersionsTest {

    private val oldVersion = MajorMinorVersion(majorVersion = 1, minorVersion = 1)

    @Test
    fun testNotInjected() {
        // Check is lenient when no studio version is injected.
        verifyIDEIsNotOld(null, oldVersion)
    }

    @Test
    fun testInvalidVersionsInjected() {
        assertFailsWith<InvalidUserDataException> {
            verifyIDEIsNotOld("", oldVersion)
        }
    }

    @Test
    fun testNewerStudio() {
        verifyIDEIsNotOld("3.3.1.6", MajorMinorVersion(majorVersion = 3, minorVersion = 2))
        // The IDE will always send the version in the form 10.x.y as of 4.0.
        // See StudioVersions.parseVersion
        verifyIDEIsNotOld("10.3.3.1 Beta 3", MajorMinorVersion(majorVersion = 3, minorVersion = 1))
        verifyIDEIsNotOld("2020.3.1", MajorMinorVersion(2020, 3, 1))
    }

    @Test
    fun testMatchingVersion() {
        verifyIDEIsNotOld("3.2.1.6", MajorMinorVersion(majorVersion = 3, minorVersion = 2))
        verifyIDEIsNotOld("10.4.7.3", MajorMinorVersion(majorVersion = 4, minorVersion = 7))
        verifyIDEIsNotOld("2020.3.1.10", MajorMinorVersion(2020, 3, 1))
    }

    @Test
    fun testTooOldStudioVersion() {
        val exception = assertFailsWith<RuntimeException> {
            verifyIDEIsNotOld("10.3.1.3.6", MajorMinorVersion(majorVersion = 3, minorVersion = 2))
        }

        assertThat(exception)
            .hasMessageThat()
            .contains("please retry with version 3.2 or newer.")

        val secondException = assertFailsWith<RuntimeException> {
            verifyIDEIsNotOld("3.1.3.6", MajorMinorVersion(majorVersion = 3, minorVersion = 2))
        }

        assertThat(secondException)
            .hasMessageThat()
            .contains("please retry with version 3.2 or newer.")
    }

    @Test
    fun checkMajorMinorVersionOrdering() {
        val versionsInOrder = listOf<MajorMinorVersion>(
            MajorMinorVersion(majorVersion = 1, minorVersion = 2),
            MajorMinorVersion(majorVersion = 1, minorVersion = 3),
            MajorMinorVersion(majorVersion = 2, minorVersion = 2),
            MajorMinorVersion(majorVersion = 2, minorVersion = 3)
        )

        for (version in versionsInOrder) {
            assertThat(version).isEquivalentAccordingToCompareTo(version)
        }

        assertThat(versionsInOrder.asReversed().sorted())
            .isEqualTo(versionsInOrder)
    }

    @Test
    fun checkValidVersionParsing() {
        assertThat(parseVersion("3.3.0.6")).isEqualTo(MajorMinorVersion(majorVersion = 3, minorVersion = 3))
        assertThat(parseVersion("3.3.0-beta1")).isEqualTo(MajorMinorVersion(majorVersion = 3, minorVersion = 3))
        assertThat(parseVersion("10.4.1 RC 3")).isEqualTo(MajorMinorVersion(majorVersion = 4, minorVersion = 1))
    }

    @Test
    fun checkInvalidVersionParsing() {
        assertThat(parseVersion("")).isNull()
        assertThat(parseVersion("1")).isNull()
        assertThat(parseVersion("A")).isNull()
        assertThat(parseVersion("-1")).isNull()
        assertThat(parseVersion("A.2")).isNull()
        assertThat(parseVersion("-1.2")).isNull()
        assertThat(parseVersion("1.B")).isNull()
        assertThat(parseVersion("1.-2")).isNull()
    }
}
