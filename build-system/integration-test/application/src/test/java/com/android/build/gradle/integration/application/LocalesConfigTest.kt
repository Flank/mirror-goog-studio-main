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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for Issue 226200249
 */
class LocalesConfigTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(MinimalSubProject.app("com.example.app"))
            .create()

    @Before
    fun before() {
        TestFileUtils.searchAndReplace(
            project.file("src/main/AndroidManifest.xml"),
            "<application />",
            "<application android:localeConfig=\"@xml/locales_config\"/>"
        )
        val localesConfigFile = project.file("src/main/res/xml/locales_config.xml")
        localesConfigFile.parentFile.mkdirs()
        localesConfigFile.writeText(
            """
                <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
                    <locale android:name="en-US"/>
                    <locale android:name="zh-TW"/>
                    <locale android:name="pt"/>
                    <locale android:name="fr"/>
                    <locale android:name="zh-Hans-SG"/>
                </locale-config>
            """.trimIndent()
        )
    }

    @Test
    fun testLocalesConfig() {
        project.executor().run("bundleDebug")
    }
}
