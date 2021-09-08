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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.dependency.KEEP_RULES_FILE_NAME
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Testing [CopyDexOutput] worker action which copies input dex files and keep rules to output. */
class CopyDexOutputTest {

    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    @Test
    fun testDexCopyOnly() {
        val inputA = tmp.newFolder().also {
            it.resolve("classes.dex").createNewFile()
            it.resolve("do_not_copy").createNewFile()
        }
        val inputB = tmp.newFolder().also {dir ->
            dir.resolve("subdir").also {
                it.mkdir()
                it.resolve("classes.dex").createNewFile()
            }
        }
        val output = tmp.newFolder()
        object: CopyDexOutput() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val inputDirs = FakeConfigurableFileCollection(inputA, inputB)
                    override val outputDexDir = FakeObjectFactory.factory.directoryProperty().fileValue(output)
                    override val outputKeepRules = FakeObjectFactory.factory.directoryProperty()
                    override val projectPath = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()
        assertThat(output.list()).asList().containsExactly("classes_ext_0.dex", "classes_ext_1.dex")
    }

    @Test
    fun testDexAndKeepRulesCopyOnly() {
        val inputA = tmp.newFolder().also {
            it.resolve("classes.dex").createNewFile()
            it.resolve("do_not_copy").createNewFile()
        }
        val inputB = tmp.newFolder().also { dir ->
            dir.resolve(KEEP_RULES_FILE_NAME).createNewFile()
            dir.resolve("classes.dex").createNewFile()
        }
        val outputDex = tmp.newFolder()
        val outputKeepRules = tmp.newFolder()
        object: CopyDexOutput() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val inputDirs = FakeConfigurableFileCollection(inputA, inputB)
                    override val outputDexDir = FakeObjectFactory.factory.directoryProperty().fileValue(outputDex)
                    override val outputKeepRules = FakeObjectFactory.factory.directoryProperty().fileValue(outputKeepRules)
                    override val projectPath = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        assertThat(outputDex.list()).asList().containsExactly("classes_ext_0.dex", "classes_ext_1.dex")
        assertThat(outputKeepRules.list()).asList().containsExactly("core_lib_keep_rules_0.txt")
    }
}
