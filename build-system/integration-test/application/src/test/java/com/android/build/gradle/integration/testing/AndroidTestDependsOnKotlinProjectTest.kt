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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.APP
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.JAVALIB
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Regression test for b/187353303. */
class AndroidTestDependsOnKotlinProjectTest {

    @Rule
    @JvmField
    val project = EmptyActivityProjectBuilder()
        .also {
            it.minSdkVersion = 24
        }
        .addJavaLibrary(useKotlin = true)
        .build()

    @Before
    fun setUp() {
        project.getSubproject(APP).buildFile.appendText(
            """

            dependencies {
                androidTestImplementation project(":$JAVALIB")
            }
        """.trimIndent()
        )

        project.getSubproject(JAVALIB).mainSrcDir.let {
            it.mkdirs()
            it.resolve("JavaClass.java").writeText("public class JavaClass {}")
            it.resolve("KotlinClass.kt").writeText("class KotlinClass")
        }
    }

    @Test
    fun testApk() {
        project.executor().run(":app:assembleDebugAndroidTest")
        assertThat(
            project.getSubproject(APP).getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        ).containsClass("LJavaClass;")
        assertThat(
            project.getSubproject(APP).getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        ).containsClass("LKotlinClass;")
    }
}
