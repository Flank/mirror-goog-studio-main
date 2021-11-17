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
package com.android.sdklib.repository

import com.android.sdklib.AndroidVersion
import com.android.sdklib.repository.meta.DetailsTypes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests that assert that the paths we create from AndroidVersion match that which ADRT places in
 * the `path` attribute in package.xml
 */
class DetailsTypePathsTest {

    @Test
    fun testPlatformPath() {
        assertThat(DetailsTypes.getPlatformPath(AndroidVersion(30, null, null, true)))
            .isEqualTo("platforms;android-30")
        assertThat(DetailsTypes.getPlatformPath(AndroidVersion(30, "X", null, true)))
            .isEqualTo("platforms;android-X")
        assertThat(DetailsTypes.getPlatformPath(AndroidVersion(30, null, 2, false)))
            .isEqualTo("platforms;android-30-ext2")
        assertThat(DetailsTypes.getPlatformPath(AndroidVersion(30, null, 2, true)))
            .isEqualTo("platforms;android-30")
        assertThat(DetailsTypes.getPlatformPath(AndroidVersion(30, "X", 2, false)))
            .isEqualTo("platforms;android-X-ext2")
    }

    @Test
    fun testSourcesPath() {
        assertThat(DetailsTypes.getSourcesPath(AndroidVersion(30, null, null, true)))
            .isEqualTo("sources;android-30")
        assertThat(DetailsTypes.getSourcesPath(AndroidVersion(30, "X", null, true)))
            .isEqualTo("sources;android-X")
        assertThat(DetailsTypes.getSourcesPath(AndroidVersion(30, null, 2, false)))
            .isEqualTo("sources;android-30-ext2")
        assertThat(DetailsTypes.getSourcesPath(AndroidVersion(30, null, 2, true)))
            .isEqualTo("sources;android-30")
        assertThat(DetailsTypes.getSourcesPath(AndroidVersion(30, "X", 2, false)))
            .isEqualTo("sources;android-X-ext2")
    }

    @Test
    fun testSysImgPath() {
        assertThat(DetailsTypes.getSysImgPath(null, AndroidVersion(30, null, null, true),
                                              IdDisplay.create("id", "display"), "abi"))
            .isEqualTo("system-images;android-30;id;abi")
        assertThat(DetailsTypes.getSysImgPath(null, AndroidVersion(30, "X", null, true),
                                              IdDisplay.create("id", "display"), "abi"))
            .isEqualTo("system-images;android-X;id;abi")
        assertThat(DetailsTypes.getSysImgPath(null, AndroidVersion(30, null, 2, false),
                                              IdDisplay.create("id", "display"), "abi"))
            .isEqualTo("system-images;android-30-ext2;id;abi")
        assertThat(DetailsTypes.getSysImgPath(null, AndroidVersion(30, null, 2, true),
                                              IdDisplay.create("id", "display"), "abi"))
            .isEqualTo("system-images;android-30;id;abi")
        assertThat(DetailsTypes.getSysImgPath(null, AndroidVersion(30, "X", 2, false),
                                              IdDisplay.create("id", "display"), "abi"))
            .isEqualTo("system-images;android-X-ext2;id;abi")
    }

    @Test
    fun testAddonsPath() {
        assertThat(DetailsTypes.getAddonPath(IdDisplay.create("idVendor", "displayVendor"),
                                             AndroidVersion(30, null, null, true),
                                             IdDisplay.create("idName", "displayName")))
            .isEqualTo("add-ons;addon-idName-idVendor-30")
        assertThat(DetailsTypes.getAddonPath(IdDisplay.create("idVendor", "displayVendor"),
                                             AndroidVersion(30, "X", null, true),
                                             IdDisplay.create("idName", "displayName")))
            .isEqualTo("add-ons;addon-idName-idVendor-X")
        assertThat(DetailsTypes.getAddonPath(IdDisplay.create("idVendor", "displayVendor"),
                                             AndroidVersion(30, null, 2, false),
                                             IdDisplay.create("idName", "displayName")))
            .isEqualTo("add-ons;addon-idName-idVendor-30-ext2")
        assertThat(DetailsTypes.getAddonPath(IdDisplay.create("idVendor", "displayVendor"),
                                             AndroidVersion(30, null, 2, true),
                                             IdDisplay.create("idName", "displayName")))
            .isEqualTo("add-ons;addon-idName-idVendor-30")
        assertThat(DetailsTypes.getAddonPath(IdDisplay.create("idVendor", "displayVendor"),
                                             AndroidVersion(30, "X", 2, false),
                                             IdDisplay.create("idName", "displayName")))
            .isEqualTo("add-ons;addon-idName-idVendor-X-ext2")
    }
}
