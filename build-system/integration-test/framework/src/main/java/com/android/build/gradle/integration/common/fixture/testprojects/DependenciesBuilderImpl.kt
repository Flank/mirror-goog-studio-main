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

package com.android.build.gradle.integration.common.fixture.testprojects

import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import java.io.File

class DependenciesBuilderImpl() : DependenciesBuilder {
    private val implementation = mutableListOf<Any>()

    val externalLibraries: List<MavenRepoGenerator.Library>
        get() = implementation.filterIsInstance(MavenRepoGenerator.Library::class.java)

    override fun implementation(dependency: Any) {
        implementation.add(dependency)
    }

    override fun localJar(action: LocalJarBuilder.() -> Unit): LocalJarBuilder =
            LocalJarBuilderImpl().also { action(it) }

    override fun project(path: String): ProjectDependencyBuilder =
            ProjectDependencyBuilderImpl(path)

    fun writeBuildFile(sb: StringBuilder, projectDir: File) {
        sb.append("\ndependencies {\n")
        for (dependency in implementation) {
            when (dependency) {
                is String -> sb.append("implementation '$dependency'\n")
                is ProjectDependencyBuilder -> sb.append("implementation project('${dependency.path}')\n")
                is MavenRepoGenerator.Library -> sb.append("implementation '${dependency.mavenCoordinate}'\n")
                is LocalJarBuilderImpl -> {
                    val path = createLocalJar(dependency, projectDir)
                    sb.append("implementation files('$path')\n")
                }
                else -> throw RuntimeException("unsupported dependency type: ${dependency.javaClass}")
            }
        }

        sb.append("}\n")
    }

    private fun createLocalJar(
        localJarSpec : LocalJarBuilderImpl,
        projectDir: File,
    ): String {
        val libsFolder = File(projectDir, "libs")
        FileUtils.mkdirs(libsFolder)
        val jarFile = File(libsFolder, localJarSpec.name)
        TestInputsGenerator.jarWithEmptyClasses(jarFile.toPath(), localJarSpec.classNames)

        return "libs/${localJarSpec.name}"
    }
}

private class LocalJarBuilderImpl(
    override var name: String = "foo.jar"
): LocalJarBuilder {
    val classNames = mutableSetOf<String>()

    override fun addClass(className: String) {
        classNames.add(className)
    }
}

private class ProjectDependencyBuilderImpl(override val path: String): ProjectDependencyBuilder
