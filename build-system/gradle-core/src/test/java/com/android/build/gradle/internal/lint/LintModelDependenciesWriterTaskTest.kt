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

package com.android.build.gradle.internal.lint


import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.runAfterEvaluate
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import org.gradle.api.Project
import org.gradle.api.services.BuildServiceRegistration
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.stream.Collectors
import java.util.stream.Collectors.joining

/** Smoke test for the LintModelDependenciesWriterTask */
class LintModelDependenciesWriterTaskTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun doTaskAction() {
        val project = TestProjects.builder(temporaryFolder.newFolder("project").toPath())
            .withPlugin(TestProjects.Plugin.APP)
            .build()

        val plugin = project.plugins.getPlugin(AppPlugin::class.java)
        plugin.extension.compileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        val mavenRepo: Path = temporaryFolder.newFolder("mavenRepo").toPath().also {
            val jar = MavenRepoGenerator.Library(
                "com.example:jar:1",
                TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/jar/MyClass"))
            )
            MavenRepoGenerator(libraries = listOf(jar)).generate(it)
        }
        project.repositories.add(project.repositories.maven { it.url = mavenRepo.toUri() })
        project.dependencies.add("implementation", "com.example:jar:1")
        plugin.runAfterEvaluate()

        val task = project.tasks.getByName("generateDebugDependenciesForLint") as LintModelDependenciesWriterTask
        configureAnalyticsService(project)
        task.taskAction()

        val outputDirectory = task.outputDirectory.get().asFile
        assertThat(outputDirectory).isDirectory()
        assertThat(outputDirectory.list()?.toList())
            .containsExactly("debug-mainArtifact-dependencies.xml", "debug-mainArtifact-libraries.xml")
        assertThat(outputDirectory.resolve("debug-mainArtifact-libraries.xml").readText()).contains("com.example:jar:1")
        assertThat(outputDirectory.resolve("debug-mainArtifact-dependencies.xml").readText()).contains("com.example:jar:1")
    }

    private fun configureAnalyticsService(project: Project) {
        val serviceRegistration = project.gradle
            .sharedServices
            .registrations
            .getByName(
                getBuildServiceName(AnalyticsService::class.java)
            ) as BuildServiceRegistration<AnalyticsService, AnalyticsService.Params>
        serviceRegistration.parameters.let {
            val profile = GradleBuildProfile.newBuilder().build().toByteArray()
            it.profile.set(Base64.getEncoder().encodeToString(profile))
            it.projects.set(mutableMapOf())
            it.enableProfileJson.set(true)
            it.taskMetadata.set(mutableMapOf())
        }
    }
}
