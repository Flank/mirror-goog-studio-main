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

import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.testutils.MavenRepoGenerator
import com.android.utils.FileUtils
import java.io.File

internal class TestProjectBuilderImpl: TestProjectBuilder, TestProject {

    private val rootProject = SubProjectBuilderImpl(":")
    private val subprojects = mutableMapOf<String, SubProjectBuilderImpl>()

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

    val mavenRepoGenerator: MavenRepoGenerator?
        get() {
            val allLibraries = rootProject.dependencies.externalLibraries +
                    subprojects.values.flatMap { it.dependencies.externalLibraries }
            if (allLibraries.isEmpty()) {
                return null
            }
            return MavenRepoGenerator(allLibraries)
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
        if (subprojects.isNotEmpty()) {
            val file = File(projectDir, "settings.gradle")
            val sb = StringBuilder()

            for (project in subprojects.keys) {
                sb.append("include '$project'\n")
            }

            file.writeText(sb.toString())
        }
    }

    override fun containsFullBuildScript(): Boolean {
        return subprojects[":"]?.plugins?.isNotEmpty() ?: false
    }
}

