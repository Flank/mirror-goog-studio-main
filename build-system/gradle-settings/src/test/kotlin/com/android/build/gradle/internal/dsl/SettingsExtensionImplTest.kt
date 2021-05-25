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

package com.android.build.gradle.internal.dsl

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class SettingsExtensionImplTest {
    private val settings = SettingsExtensionImpl()

    @Test
    fun compileSdk() {
        settings.compileSdk = 12

        assertWithMessage("compileSdk")
            .that(settings.compileSdk)
            .isEqualTo(12)

        assertWithMessage("compileSdkPreview")
            .that(settings.compileSdkPreview)
            .isNull()

        assertWithMessage("hasAddOn")
            .that(settings.hasAddOn)
            .isFalse()

        assertWithMessage("addOnVendor")
            .that(settings.addOnVendor)
            .isNull()
        assertWithMessage("addOnName")
            .that(settings.addOnName)
            .isNull()
        assertWithMessage("addOnApiLevel")
            .that(settings.addOnApiLevel)
            .isNull()
    }

    @Test
    fun compileSdkPreview() {
        settings.compileSdkPreview = "S"

        assertWithMessage("compileSdk")
            .that(settings.compileSdk)
            .isNull()

        assertWithMessage("compileSdkPreview")
            .that(settings.compileSdkPreview)
            .isEqualTo("S")

        assertWithMessage("hasAddOn")
            .that(settings.hasAddOn)
            .isFalse()

        assertWithMessage("addOnVendor")
            .that(settings.addOnVendor)
            .isNull()
        assertWithMessage("addOnName")
            .that(settings.addOnName)
            .isNull()
        assertWithMessage("addOnApiLevel")
            .that(settings.addOnApiLevel)
            .isNull()
    }

    @Test
    fun compileSdkAddon() {
        settings.compileSdkAddon("foo", "bar", 42)

        assertWithMessage("compileSdk")
            .that(settings.compileSdk)
            .isNull()

        assertWithMessage("compileSdkPreview")
            .that(settings.compileSdkPreview)
            .isNull()

        assertWithMessage("hasAddOn")
            .that(settings.hasAddOn)
            .isTrue()

        assertWithMessage("addOnVendor")
            .that(settings.addOnVendor)
            .isEqualTo("foo")
        assertWithMessage("addOnName")
            .that(settings.addOnName)
            .isEqualTo("bar")
        assertWithMessage("addOnApiLevel")
            .that(settings.addOnApiLevel)
            .isEqualTo(42)
    }
}
