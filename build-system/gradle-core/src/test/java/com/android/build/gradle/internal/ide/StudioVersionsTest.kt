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
        }.also {
            assertThat(it).hasMessageThat().startsWith("Unrecognized Android Studio")
            assertThat(it).hasMessageThat().contains("please retry with version 1.1 or newer.")
        }

        // Regression test for b/187470273
        assertFailsWith<InvalidUserDataException> {
            verifyIDEIsNotOld("202.7660.26.42.7322048", MajorMinorVersion(2020, 3, 1))
        }.also {
            assertThat(it).hasMessageThat().startsWith("Unrecognized Android Studio")
            assertThat(it).hasMessageThat().contains("please retry with version 2020.3.1 or newer.")
        }
    }

    @Test
    fun testNewerStudio() {
        verifyIDEIsNotOld("3.3.1.6", MajorMinorVersion(majorVersion = 3, minorVersion = 3))
        // The IDE will send the version in the form 10.x.y for versions 4.0, 4.1 and 4.2
        // See StudioVersions.parseVersion
        verifyIDEIsNotOld("10.4.3.1 Beta 3", MajorMinorVersion(majorVersion = 4, minorVersion = 3))
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
        assertFailsWith<RuntimeException> {
            verifyIDEIsNotOld("3.1.3.6", MajorMinorVersion(majorVersion = 3, minorVersion = 2))
        }.also { exception ->
            assertThat(exception)
                .hasMessageThat()
                .contains("please retry with version 3.2 or newer.")
        }

        assertFailsWith<RuntimeException> {
            verifyIDEIsNotOld("10.4.1.3.6", MajorMinorVersion(majorVersion = 4, minorVersion = 2))
        }.also { exception ->
            assertThat(exception)
                .hasMessageThat()
                .contains("please retry with version 4.2 or newer.")
        }

        assertFailsWith<RuntimeException> {
            verifyIDEIsNotOld("2020.1.1 Beta 5", MajorMinorVersion(yearVersion = 2020, majorVersion = 1, minorVersion = 2))
        }.also { exception ->
            assertThat(exception)
                .hasMessageThat()
                .contains("please retry with version 2020.1.2 or newer.")
        }

    }

    @Test
    fun checkMajorMinorVersionOrdering() {
        val versionsInOrder = listOf<MajorMinorVersion>(
            MajorMinorVersion(majorVersion = 1, minorVersion = 2),
            MajorMinorVersion(majorVersion = 1, minorVersion = 3),
            MajorMinorVersion(majorVersion = 2, minorVersion = 2),
            MajorMinorVersion(majorVersion = 2, minorVersion = 3),
            MajorMinorVersion(yearVersion = 2020, majorVersion = 1, minorVersion = 1),
            MajorMinorVersion(yearVersion = 2020, majorVersion = 1, minorVersion = 2),
            MajorMinorVersion(yearVersion = 2020, majorVersion = 2, minorVersion = 1),
            MajorMinorVersion(yearVersion = 2020, majorVersion = 2, minorVersion = 2),
            MajorMinorVersion(yearVersion = 2021, majorVersion = 1, minorVersion = 1),
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

        // As injected by earlier canaries of Android Studio Arctic Fox 2020.3
        assertThat(parseVersion("10.2020.3 Canary 11")).isEqualTo(MajorMinorVersion(yearVersion=2020, majorVersion = 3, minorVersion = 1))

        assertThat(parseVersion("2020.3.1 Canary 12")).isEqualTo(MajorMinorVersion(yearVersion=2020, majorVersion = 3, minorVersion = 1))
        assertThat(parseVersion("2020.3.2 Canary 12")).isEqualTo(MajorMinorVersion(yearVersion=2020, majorVersion = 3, minorVersion = 2))

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
        assertThat(parseVersion("10")).isNull()
        assertThat(parseVersion("10.1")).isNull()
        assertThat(parseVersion("2020")).isNull()
        assertThat(parseVersion("2020.1")).isNull()
        assertThat(parseVersion("2020.1 ?")).isNull()
        assertThat(parseVersion("10.2021.1 Canary")).isNull()
    }
}
