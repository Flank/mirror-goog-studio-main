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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.testutils.TestUtils
import com.android.testutils.truth.DexSubject
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class L8DexDesugarLibTaskTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testShrinking() {
        val output = tmp.newFolder().resolve("out")
        val input = tmp.newFolder().toPath()

        val keepRulesFile1 = input.toFile().resolve("keep_rules").also { file ->
            file.bufferedWriter().use {
                it.write("-keep class j$.util.stream.Stream {*;}")
            }
        }
        val keepRulesFile2 = input.toFile().resolve("dir/keep_rules").also { file ->
            file.parentFile.mkdirs()
            file.bufferedWriter().use {
                it.write("-keep class j$.util.Optional {*;}")
            }
        }

        object: L8DexWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val desugarLibJar = FakeConfigurableFileCollection(desugarJar)
                    override val desugarLibDex =
                        FakeObjectFactory.factory.directoryProperty().fileValue(output)
                    override val libConfiguration = FakeGradleProperty(desugarConfig)
                    override val androidJar =
                        FakeObjectFactory.factory.fileProperty().fileValue(bootClasspath)
                    override val minSdkVersion = FakeGradleProperty(20)
                    override val keepRulesFiles =
                        FakeConfigurableFileCollection(setOf(keepRulesFile1, keepRulesFile2))
                    override val keepRulesConfigurations =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val debuggable = FakeGradleProperty(true)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()


        val dexFile = output.resolve("classes1000.dex")
        DexSubject.assertThatDex(dexFile).containsClass("Lj$/util/stream/Stream;")
        DexSubject.assertThatDex(dexFile).containsClass("Lj$/util/Optional;")
        // check unused API classes are removed from the from desugar lib dex.
        DexSubject.assertThatDex(dexFile).doesNotContainClasses("Lj$/time/LocalTime;")
    }

    companion object {
        val bootClasspath = TestUtils.resolvePlatformPath("android.jar").toFile()
        val desugarJar = listOf(TestUtils.getDesugarLibJar().toFile())
        val desugarConfig = TestUtils.getDesugarLibConfigContent()
    }
}
