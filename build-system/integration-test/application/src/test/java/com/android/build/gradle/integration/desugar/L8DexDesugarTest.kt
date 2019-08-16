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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.testutils.apk.AndroidArchive.checkValidClassName
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.FileSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

class L8DexDesugarTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestApp(
        MinimalSubProject.app("com.example.test")).create()

    private val desugarClass = "Lj$/util/stream/Stream;"
    private val normalClass = "Lcom/example/test/BuildConfig;"

    @Before
    fun setUp() {
        project.buildFile.appendText(
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 21
            android.compileOptions.javaApiDesugaringEnabled = true
            android.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
            android.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
        """.trimIndent()
        )
    }

    @Test
    fun testNativeMultiDexWithoutKeepRule() {
        project.executor().run("assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        val desugarLibDex: Dex =
            getDexWithDesugarClass(desugarClass, apk.allDexes)
                ?: fail("Failed to find the dex with desugar lib classes")
        assertThat(desugarLibDex).doesNotContainClasses(normalClass)
    }

    @Test
    fun testNativeMultiDexWithoutKeepRuleBundle() {
        project.executor().run("bundleDebug")
        val outputModels = project.model().fetchContainer(AppBundleProjectBuildOutput::class.java)
        val outputAppModel = outputModels.rootBuildModelMap[":"] ?: fail("Failed to get app model")
        val bundleFile = outputAppModel.getOutputByName("debug").bundleFile
        FileSubject.assertThat(bundleFile).exists()
        Zip(bundleFile).use {
            val dex1 = Dex(it.getEntry("base/dex/classes.dex")!!)
            val dex2 = Dex(it.getEntry("base/dex/classes2.dex")!!)
            val desugarLibDex: Dex =
                getDexWithDesugarClass(desugarClass, listOf(dex1, dex2))
                    ?: fail("Failed to find the dex with desugar lib classes")
            assertThat(desugarLibDex).doesNotContainClasses(normalClass)
        }
    }

    private fun getDexWithDesugarClass(desugarClass: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            checkValidClassName(desugarClass)
            it.classes.keys.contains(desugarClass)
        }
}