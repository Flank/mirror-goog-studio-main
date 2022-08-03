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

package com.android.build.gradle.integration.common.fixture.testprojects

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.testutils.MavenRepoGenerator
import com.android.utils.FileUtils
import java.io.File

internal class RootTestProjectBuilderImpl: TestProjectBuilderImpl(":") {

    val mavenRepoGenerator: MavenRepoGenerator?
        get() {
            val allLibraries = accumulateExternalLibraries(this)
            if (allLibraries.isEmpty()) {
                return null
            }
            return MavenRepoGenerator(allLibraries)
        }

    private fun accumulateExternalLibraries(
        build: TestProjectBuilderImpl
    ): List<MavenRepoGenerator.Library> = mutableListOf<MavenRepoGenerator.Library>().also { list ->
        list.addAll(build.includedBuilds.flatMap { accumulateExternalLibraries(it) })
        list.addAll(build.rootProject.dependencies.externalLibraries)
        list.addAll(build.subprojects.values.flatMap { it.dependencies.externalLibraries })
    }
}

internal open class TestProjectBuilderImpl(override val name: String): TestProjectBuilder, TestProject {

    private val _includedBuilds = mutableListOf<TestProjectBuilderImpl>()
    override val includedBuilds: List<TestProjectBuilderImpl>
        get() = _includedBuilds

    val rootProject = SubProjectBuilderImpl(":")
    val subprojects = mutableMapOf<String, SubProjectBuilderImpl>()

    override var buildFileType: BuildFileType = BuildFileType.GROOVY

    override fun rootProject(action: SubProjectBuilder.() -> Unit) {
        action(rootProject)
    }

    override fun subProject(path: String, action: SubProjectBuilder.() -> Unit) {
        if (path == ":") {
            action(rootProject)
        } else {
            val project = subprojects.computeIfAbsent(path) {
                SubProjectBuilderImpl(path)
            }

            action(project)
        }
    }

    override fun includedBuild(name: String, action: TestProjectBuilder.() -> Unit) {
        val build = TestProjectBuilderImpl(name)
        action(build)
        _includedBuilds.add(build)
    }

    // --- TestProject ---

    override fun write(projectDir: File, buildScriptContent: String?) {
        FileUtils.mkdirs(projectDir)
        rootProject.write(projectDir, buildFileType, buildScriptContent)

        for (project in subprojects.values) {
            val dir = FileUtils.join(projectDir, project.path.replace(':', File.separatorChar))

            FileUtils.mkdirs(dir)
            project.write(dir, buildFileType, null)
        }

        // write settings.gradle
        if (subprojects.isNotEmpty() || includedBuilds.isNotEmpty()) {
            val file = File(projectDir, "settings.gradle")
            val sb = StringBuilder()

            projectDir.parentFile.resolve("commonLocalRepo.gradle").takeIf { it.exists() }?.readText()?.let { projectRepoScript ->
                sb.append("pluginManagement {\n").append(projectRepoScript.prependIndent("    ")).append("\n}\n\n")
            }

            for (build in includedBuilds) {
                sb.append("includeBuild(\"${build.name}\")\n")
            }

            for (project in subprojects.keys) {
                sb.append("include '$project'\n")
            }

            file.writeText(sb.toString())
        }

        // write the included builds
        for (build in includedBuilds) {
            build.write(File(projectDir, build.name), buildScriptContent)
        }
    }

    override fun containsFullBuildScript(): Boolean {
        return subprojects[":"]?.plugins?.isNotEmpty() ?: false
    }
}
