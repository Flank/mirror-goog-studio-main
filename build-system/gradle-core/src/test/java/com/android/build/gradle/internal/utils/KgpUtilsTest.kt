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

package com.android.build.gradle.internal.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KgpUtilsTest {

    @Test
    fun testParseKotlinVersion() {
        // basic version
        var kotlinVersion = parseKotlinVersion("1.2.3")
        assertThat(kotlinVersion).isNotNull()
        assertThat(kotlinVersion).isEqualTo(KotlinVersion(1, 2, 3))

        // kotlin version not available
        kotlinVersion = parseKotlinVersion("unknown")
        assertThat(kotlinVersion).isNull()

        // missing patch
        kotlinVersion = parseKotlinVersion("1.0")
        assertThat(kotlinVersion).isNull()

        // missing patch and added extension
        kotlinVersion = parseKotlinVersion("1.5-IDK")
        assertThat(kotlinVersion).isNull()

        // Check empty is results in null
        kotlinVersion = parseKotlinVersion("")
        assertThat(kotlinVersion).isNull()

        // Should ignore extension
        kotlinVersion = parseKotlinVersion("1.0.0-RC")
        assertThat(kotlinVersion).isNotNull()
        assertThat(kotlinVersion).isEqualTo(KotlinVersion(1, 0, 0))

        // Should ignore extension
        kotlinVersion = parseKotlinVersion("1.5.31-preview")
        assertThat(kotlinVersion).isNotNull()
        assertThat(kotlinVersion).isEqualTo(KotlinVersion(1, 5, 31))

        // should parse zeroes correctly
        kotlinVersion = parseKotlinVersion("0.0.1")
        assertThat(kotlinVersion).isNotNull()
        assertThat(kotlinVersion).isEqualTo(KotlinVersion(0, 0, 1))
    }

}
