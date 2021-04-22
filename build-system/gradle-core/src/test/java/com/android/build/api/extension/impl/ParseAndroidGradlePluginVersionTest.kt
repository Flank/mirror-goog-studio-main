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

package com.android.build.api.extension.impl

import com.android.build.api.AndroidPluginVersion
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class ParseAndroidGradlePluginVersionTest {

    @get:Rule
    val expect = Expect.create()

    @Test
    fun parseVersion() {
        expect.that(parseAndroidGradlePluginVersion("4.0.0-alpha04")).isEqualTo(AndroidPluginVersion(4, 0).alpha(4))
        expect.that(parseAndroidGradlePluginVersion("4.0.0-beta02")).isEqualTo(AndroidPluginVersion(4, 0).beta(2))
        expect.that(parseAndroidGradlePluginVersion("4.0.0-rc1")).isEqualTo(AndroidPluginVersion(4, 0).rc(1))
        expect.that(parseAndroidGradlePluginVersion("4.0.0-dev")).isEqualTo(AndroidPluginVersion(4, 0).dev())
        expect.that(parseAndroidGradlePluginVersion("4.0.0")).isEqualTo(AndroidPluginVersion(4, 0))
        expect.that(parseAndroidGradlePluginVersion("4.0.1")).isEqualTo(AndroidPluginVersion(4, 0, 1))
        expect.that(parseAndroidGradlePluginVersion("4.1.0")).isEqualTo(AndroidPluginVersion(4, 1, 0))
    }

    @Test
    fun parseInvalidVersion() {
        assertFailsWith<IllegalArgumentException> {
            parseAndroidGradlePluginVersion("4.0.0-foo05")
        }
    }


    @Test
    fun `current android gradle plugin version`() {
        assertThat(CURRENT_AGP_VERSION).isNotNull()
    }
}
