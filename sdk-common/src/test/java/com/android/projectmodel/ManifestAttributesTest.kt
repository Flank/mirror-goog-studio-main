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

package com.android.projectmodel

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [ManifestAttributes]
 */
class ManifestAttributesTest {
    @Test
    fun toStringDefaultTest() {
        val cfg = ManifestAttributes()
        assertThat(cfg.toString()).isEqualTo("ManifestAttributes()")
    }

    @Test
    fun toStringOverrideTest() {
        val cfg = ManifestAttributes(
                applicationId = "id",
                maxSdkVersion = AndroidVersion.DEFAULT
        )
        assertThat(cfg.toString()).isEqualTo("ManifestAttributes(applicationId=id,maxSdkVersion=API 1)")
    }

    @Test
    fun testOverrideNothing() {
        assertThat(overrideEverything + ManifestAttributes()).isEqualTo(overrideEverything)
        assertThat(overrideApplicationId + ManifestAttributes()).isEqualTo(overrideApplicationId)
        assertThat(ManifestAttributes() + ManifestAttributes()).isEqualTo(ManifestAttributes())
    }

    @Test
    fun testOverideEverything() {
        assertThat(ManifestAttributes() + overrideEverything).isEqualTo(overrideEverything)
        assertThat(overrideEverything + overrideEverything).isEqualTo(overrideEverything)
        assertThat(overrideApplicationId + overrideEverything).isEqualTo(overrideEverything)
    }

    @Test
    fun testOverideSomething() {
        assertThat(ManifestAttributes() + overrideApplicationId).isEqualTo(overrideApplicationId)
        assertThat(overrideEverything + overrideApplicationId).isEqualTo(
                overrideEverything.copy(applicationId = "overriddenApplicationId"))
    }

    @Test
    fun testWithVersion() {
        val version = AndroidVersion(3)
        assertThat(ManifestAttributes().withVersion(version)).isEqualTo(
            ManifestAttributes(
                minSdkVersion = version,
                targetSdkVersion = version,
                apiVersion = version,
                compileSdkVersion = version
            )
        )
    }

    private val overrideApplicationId = ManifestAttributes(
            applicationId = "overriddenApplicationId"
    )

    private val overrideEverything = ManifestAttributes(
            applicationId = "someApplicationId",
            versionCode = 10,
            minSdkVersion = AndroidVersion(1),
            apiVersion = AndroidVersion(2),
            maxSdkVersion = AndroidVersion(3),
            targetSdkVersion = AndroidVersion(4),
            compileSdkVersion = AndroidVersion(5),
            debuggable = true
    )
}