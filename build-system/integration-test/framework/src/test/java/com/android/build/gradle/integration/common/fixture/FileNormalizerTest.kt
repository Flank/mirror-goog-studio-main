/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture


import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.testutils.AssumeUtil
import com.google.common.truth.Truth
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.gradle.tooling.model.BuildIdentifier
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class FileNormalizerTest {

    private val normalizer = FileNormalizerImpl(
        buildId = BuildIdentifierImpl(File("/path/to/Project")),
        gradleUserHome = File("/path/to/Gradle"),
        gradleCacheDir = File("/path/to/Gradle/caches/transforms-3/"),
        androidSdkDir = File("/path/to/Sdk"),
        androidPrefsDir = File("/path/to/Home"),
        androidNdkSxSRoot = File("/path/to/ndkSxSRoot"),
        localRepos = listOf(Paths.get("/path/to/localRepo1"), Paths.get("/path/to/localRepo2")),
        defaultNdkSideBySideVersion = DEFAULT_NDK_SIDE_BY_SIDE_VERSION
    )

    private val gson = Gson()

    @Test
    fun `Test Outside Paths`() {
        Truth.assertThat(normalizer.normalize(File("/wrong/path")))
            .isEqualTo("/wrong/path{!}")
    }

    @Test
    fun `Test Project Paths`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Project/foo")))
            .isEqualTo("{PROJECT}/foo{!}")

        Truth.assertThat(normalizer.normalize(File("/wrong/path")))
            .isEqualTo("/wrong/path{!}")
    }

    @Test
    fun `Test Gradle User Home`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Gradle/foo")))
            .isEqualTo("{GRADLE}/foo{!}")
    }

    @Test
    fun `Test Gradle Transform Cache`() {
        Truth.assertThat(normalizer.normalize(
            File("/path/to/Gradle/caches/transforms-3/12345678901234567890123456789012/transformed/foo")
        ))
            .isEqualTo("{GRADLE_CACHE}/{CHECKSUM}/transformed/foo{!}")
    }

    @Test
    fun `Test local repos`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/localRepo1/blah")))
            .isEqualTo("{LOCAL_REPO}/blah{!}")
        Truth.assertThat(normalizer.normalize(File("/path/to/localRepo2/blah")))
            .isEqualTo("{LOCAL_REPO}/blah{!}")
    }

    @Test
    fun `Test android Sdk`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Sdk/foo")))
            .isEqualTo("{ANDROID_SDK}/foo{!}")
    }

    @Test
    fun `Test android Home`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Home/foo")))
            .isEqualTo("{ANDROID_PREFS}/foo{!}")
    }

    @Test
    fun `Test android NDK Root`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/ndkSxSRoot/$DEFAULT_NDK_SIDE_BY_SIDE_VERSION/foo")))
            .isEqualTo("{ANDROID_NDK}/foo{!}")
    }

    @Test
    fun `Test unscrupulouslyReplace(JsonElement)`() {
        AssumeUtil.assumeNotWindows()
        Truth.assertThat(
            normalizer.normalize(
                gson.fromJson(
                    """
                        {
                            gradleUserHome = "/path/to/Gradle",
                            androidSdk = "/path/to/Sdk",
                            androidHome = "/path/to/Home",
                            androidNdkRoot = "/path/to/ndkSxSRoot/$DEFAULT_NDK_SIDE_BY_SIDE_VERSION"
                        }
                    """, JsonObject::class.java
                )
            )
        ).isEqualTo(
            gson.fromJson(
                """
                {
                    gradleUserHome = "{GRADLE}",
                    androidSdk = "{ANDROID_SDK}",
                    androidHome = "{ANDROID_PREFS}",
                    androidNdkRoot = "{ANDROID_NDK}"
                }
                """, JsonObject::class.java
            )
        )
    }

    @Test
    fun `Test unscrupulouslyReplace(JsonElement) - windows`() {
        AssumeUtil.assumeWindows()
        val normalizer = FileNormalizerImpl(
            buildId = BuildIdentifierImpl(File("C:\\path\\to\\Project")),
            gradleUserHome = File("C:\\path\\to\\Gradle"),
            gradleCacheDir = File("C:\\path\\to\\Gradle\\caches\\transforms-3"),
            androidSdkDir = File("C:\\path\\to\\Sdk"),
            androidPrefsDir = File("C:\\path\\to\\Home"),
            androidNdkSxSRoot = File("C:\\path\\to\\ndkSxSRoot"),
            localRepos = listOf(
                Paths.get("C:\\path\\to\\localRepo1"),
                Paths.get("C:\\path\\to\\localRepo2")
            ),
            defaultNdkSideBySideVersion = DEFAULT_NDK_SIDE_BY_SIDE_VERSION
        )

        Truth.assertThat(
            normalizer.normalize(
                gson.fromJson(
                    """
                    {
                        gradleUserHome = "C:\\path\\to\\Gradle",
                        androidSdk = "C:\\path\\to\\Sdk",
                        androidHome = "C:\\path\\to\\Home",
                        androidNdkRoot = "C:\\path\\to\\ndkSxSRoot\\$DEFAULT_NDK_SIDE_BY_SIDE_VERSION"
                    }
                    """, JsonObject::class.java
                )
            )
        ).isEqualTo(
            gson.fromJson(
                """
                {
                    gradleUserHome = "{GRADLE}",
                    androidSdk = "{ANDROID_SDK}",
                    androidHome = "{ANDROID_PREFS}",
                    androidNdkRoot = "{ANDROID_NDK}"
                }
                """, JsonObject::class.java
            )
        )
    }

    private class BuildIdentifierImpl(private val rootDir: File) : BuildIdentifier {
        override fun getRootDir(): File {
            return rootDir
        }
    }
}
