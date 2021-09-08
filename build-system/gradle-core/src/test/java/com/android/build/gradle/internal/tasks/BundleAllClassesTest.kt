/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.gradle.internal.feature.BundleAllClasses
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.testutils.truth.PathSubject.assertThat
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BundleAllClassesTest {

    @get:Rule
    val testFolder = TemporaryFolder()

    @Test
    fun testOutputJarIsCreated() {
        val outputJar = testFolder.root.resolve("output.jar")
        object : BundleAllClasses.BundleAllClassesWorkAction() {
            override fun getParameters(): Parameters {
                return object : Parameters() {
                    override val inputDirs =
                        FakeObjectFactory.factory.fileCollection().from(testFolder.newFolder())
                    override val inputJars = FakeObjectFactory.factory.fileCollection()
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val outputJar =
                        FakeObjectFactory.factory.fileProperty().fileValue(outputJar)
                    override val projectPath = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()

        assertThat(outputJar).exists()
    }
}
