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

import com.android.Version
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

    private val settingsBuilder = SettingsBuilderImpl()
    internal val rootProject = SubProjectBuilderImpl(":")
    internal val subprojects = mutableMapOf<String, SubProjectBuilderImpl>()

    override var buildFileType: BuildFileType = BuildFileType.GROOVY

    override fun settings(action: SettingsBuilder.() -> Unit) {
        action(settingsBuilder)
    }

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

    override fun write(projectDir: File, buildScriptContent: String?, projectRepoScript: String) {
        FileUtils.mkdirs(projectDir)
        rootProject.write(projectDir, buildFileType, buildScriptContent)

        for (project in subprojects.values) {
            val dir = FileUtils.join(projectDir, project.path.replace(':', File.separatorChar))

            FileUtils.mkdirs(dir)
            project.write(dir, buildFileType, null)
        }

        // write settings.gradle
        if (subprojects.isNotEmpty() || includedBuilds.isNotEmpty()  || settingsBuilder.plugins.isNotEmpty()) {
            val file = File(projectDir, "settings.gradle")
            val sb = StringBuilder()

            // setup repositories inside pluginManagement
            sb.append("pluginManagement {\n").append(projectRepoScript).append("\n}\n\n")

            // write customization
            sb.append("plugins {\n")
            for (plugin in settingsBuilder.plugins) {
                if (plugin.isAndroid) {
                    sb.append("id('${plugin.id}') version \"${Version.ANDROID_GRADLE_PLUGIN_VERSION}\"\n")
                } else {
                    sb.append("id('${plugin.id}')\n")
                }
            }
            sb.append("}\n\n")

            for (build in includedBuilds) {
                sb.append("includeBuild(\"${build.name}\")\n")
            }

            if (settingsBuilder.plugins.contains(PluginType.ANDROID_SETTINGS)) {
                val android = settingsBuilder.android
                sb.append("\n")
                sb.append("android {\n")

                android.compileSdk?.let { sb.append("  compileSdk = $it\n") }
                android.minSdk?.let { sb.append("  minSdk = $it\n") }
                android.buildToolsVersion?.let { sb.append("  buildToolsVersion = \"$it\"\n") }
                android.ndkVersion?.let { sb.append("  ndkVersion = \"$it\"\n") }
                android.ndkPath?.let { sb.append("  ndkPath = \"$it\"\n") }

                sb.append("}\n\n")
            }

            for (project in subprojects.keys) {
                sb.append("include '$project'\n")
            }

            file.writeText(sb.toString())
        }

        // write the included builds
        for (build in includedBuilds) {
            build.write(File(projectDir, build.name), buildScriptContent, projectRepoScript)
        }
    }

    override fun containsFullBuildScript(): Boolean {
        return subprojects[":"]?.plugins?.isNotEmpty() ?: false
    }
}

internal class SettingsBuilderImpl: SettingsBuilder {
    override val plugins: MutableList<PluginType> = mutableListOf()
    internal val android = AndroidSettingsBuilderImpl()

    override fun android(action: AndroidSettingsBuilder.() -> Unit) {
        action(android)
    }
}

internal class AndroidSettingsBuilderImpl: AndroidSettingsBuilder {
    override var compileSdk: Int? = null
    override var minSdk: Int? = null
    override var buildToolsVersion: String? = null
    override var ndkVersion: String? = null
    override var ndkPath: String? = null
}
