/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsNameResolverTest {

    @Test
    fun `simple`() {
        val resolver = SettingsEnvironmentNameResolver(createSettingsFromJsonString("""
             {
              "environments": [ {
                "namespace": "ndk",
                "environment": "android-ndk",
                "minPlatform": "16"
               },
               // One-per-platform environments
               {
                "namespace": "ndk",
                "environment": "android-ndk-platform-29",
                "platformCode": "Q"
               } ]
             }
        """.trimIndent()).environments)

        assertThat(resolver.resolve("ndk.minPlatform",
            listOf("android-ndk"))!!).isEqualTo("16")
        assertThat(resolver.resolve("ndk.platformCode",
            listOf("android-ndk-platform-29"))!!).isEqualTo("Q")
    }

    @Test
    fun `default namespace name is env`() {
        val resolver = SettingsEnvironmentNameResolver(createSettingsFromJsonString("""
             {
              "environments": [ {
                "namespace": "env",
                "environment": "my-environment",
                "name": "value"
               } ]
             }
        """.trimIndent()).environments)

        assertThat(resolver.resolve("name",
            listOf("my-environment"))!!).isEqualTo("value")
    }

    @Test
    fun `inherit order respected`() {
        val resolver = SettingsEnvironmentNameResolver(createSettingsFromJsonString("""
             {
              "environments": [ {
                "namespace": "ndk",
                "environment": "android-ndk-1",
                "minPlatform": "16"
               },
               {
                "namespace": "ndk",
                "environment": "android-ndk-2",
                "minPlatform": "17"
               }, ]
             }
        """.trimIndent()).environments)

        assertThat(resolver.resolve("ndk.minPlatform",
            listOf("android-ndk-1", "android-ndk-2"))!!)
            .isEqualTo("16")

        assertThat(resolver.resolve("ndk.minPlatform",
            listOf("android-ndk-2", "android-ndk-1"))!!)
            .isEqualTo("17")
    }

    @Test
    fun `environment-to-environment inherit order`() {
        val resolver = SettingsEnvironmentNameResolver(createSettingsFromJsonString("""
             {
              "environments": [ {
                "namespace": "ndk",
                "environment": "android-ndk-1",
                "minPlatform": "16",
                "maxPlatform": "28"
               },
               {
                "namespace": "ndk",
                "environment": "android-ndk-2",
                "inheritEnvironments": ["android-ndk-1"],
                "minPlatform": "17"
               }, ]
             }
        """.trimIndent()).environments)

        assertThat(resolver.resolve("ndk.minPlatform",
            listOf("android-ndk-1"))!!)
            .isEqualTo("16")

        assertThat(resolver.resolve("ndk.minPlatform",
            listOf("android-ndk-2"))!!)
            .isEqualTo("17")

        assertThat(resolver.resolve("ndk.maxPlatform",
            listOf("android-ndk-2"))!!)
            .isEqualTo("28")
    }

    @Test
    fun `environment-to-environment inherit order overridden by group priority`() {
        val resolver = SettingsEnvironmentNameResolver(createSettingsFromJsonString("""
             {
              "environments": [ {
                "namespace": "ndk",
                "environment": "android-ndk-1",
                "groupPriority": 50,
                "minPlatform": "16",
                "maxPlatform": "28"
               },
               {
                "namespace": "ndk",
                "environment": "android-ndk-2",
                "inheritEnvironments": ["android-ndk-1"],
                "minPlatform": "17"
               }, ]
             }
        """.trimIndent()).environments)

        assertThat(resolver.resolve("ndk.minPlatform",
            listOf("android-ndk-2"))!!)
            .isEqualTo("16")
    }

    @Test
    fun `mutually recursive environment names`() {
        val resolver = SettingsEnvironmentNameResolver(createSettingsFromJsonString("""
             {
              "environments": [ {
                "namespace": "ndk",
                "environment": "android-ndk-1",
                "inheritEnvironments": ["android-ndk-2"],
                "groupPriority": 50,
                "minPlatform": "16",
                "maxPlatform": "28"
               },
               {
                "namespace": "ndk",
                "environment": "android-ndk-2",
                "inheritEnvironments": ["android-ndk-1"],
                "minPlatform": "17"
               }, ]
             }
        """.trimIndent()).environments)

        assertThat(resolver.resolve("ndk.minPlatform",
            listOf("android-ndk-2"))!!)
            .isEqualTo("16")
    }
}
