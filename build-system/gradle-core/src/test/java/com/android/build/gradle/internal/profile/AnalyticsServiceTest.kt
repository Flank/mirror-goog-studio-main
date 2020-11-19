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

package com.android.build.gradle.internal.profile

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.builder.profile.NameAnonymizer
import com.android.builder.profile.NameAnonymizerSerializer
import com.android.builder.profile.Recorder
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.Base64

class AnalyticsServiceTest {

    @get:Rule
    var outputDir = TemporaryFolder()

    private lateinit var analyticsService: AnalyticsService
    private lateinit var mProfileDir: File

    private val projectPath = "projectPath"
    private val variantName = "variantName"

    @Before
    fun setUp() {
        mProfileDir = outputDir.newFolder("profile_proto")
        analyticsService = createAnalyticsServiceInstance()
    }

    @Test
    fun testRecordsOrder() {
        analyticsService.recordBlock(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
            null,
            projectPath,
            variantName,
            Recorder.VoidBlock {
                analyticsService.recordBlock(
                    GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
                    null,
                    projectPath,
                    variantName,
                    Recorder.VoidBlock {  }
                )
            }
        )
        analyticsService.close()

        val profile = loadProfile()
        Truth.assertThat(profile.spanList).hasSize(2)
        val parent = profile.getSpan(1)
        val child = profile.getSpan(0)
        Truth.assertThat(child.id).isGreaterThan(parent.id)
        Truth.assertThat(child.parentId).isEqualTo(parent.id)
    }

    private fun loadProfile(): GradleBuildProfile {
        val rawProto = mProfileDir.listFiles().first { it.extension == "rawproto" }.toPath()
        return GradleBuildProfile.parseFrom(Files.readAllBytes(rawProto))
    }

    private fun createAnalyticsServiceInstance(): AnalyticsService {
        return object : AnalyticsService() {
            override val provider: ProviderFactory
                get() = FakeProviderFactory.factory

            override fun getParameters(): Params {
                return object: Params {
                    override val profile: Property<String>
                        get() = getProfile()
                    override val anonymizer: Property<String>
                        get() = FakeGradleProperty(NameAnonymizerSerializer().toJson(NameAnonymizer()))
                    override val projects: MapProperty<String, ProjectData>
                        get() = getProjects()
                    override val enableProfileJson: Property<Boolean>
                        get() = FakeGradleProperty(true)
                    override val profileDir: Property<File?>
                        get() = FakeObjectFactory.factory.property(File::class.java).value(mProfileDir)
                    override val taskMetadata: MapProperty<String, TaskMetadata>
                        get() = getTaskMetaData()
                    override val rootProjectPath: Property<String>
                        get() = FakeGradleProperty("/path")
                }
            }

            private fun getProfile(): Property<String> {
                val profile = GradleBuildProfile.newBuilder().build().toByteArray()
                return FakeGradleProperty(Base64.getEncoder().encodeToString(profile))
            }

            private fun getProjects(): MapProperty<String, ProjectData> {
                val map: MutableMap<String, ProjectData> = mutableMapOf()
                val customProject = ProjectData(GradleBuildProject.newBuilder().setId(1L))
                customProject.variantBuilders[variantName] = GradleBuildVariant.newBuilder().setId(2L)
                map[projectPath] = customProject
                return FakeObjectFactory.factory
                    .mapProperty(String::class.java, ProjectData::class.java)
                    .value(map)
            }

            private fun getTaskMetaData(): MapProperty<String, TaskMetadata> {
                return FakeObjectFactory.factory.mapProperty(
                    String::class.java, TaskMetadata::class.java)
            }
        }
    }
}
