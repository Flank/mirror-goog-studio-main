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

/**
 * Creates a [TestProject] with the provided configuration action
 */
fun createProject(action: TestProjectBuilder.() -> Unit): TestProject {
    val builder = TestProjectBuilderImpl()
    action(builder)

    return builder
}

/**
 * Creates a [GradleTestProject] with the provided configuration action
 */
fun createGradleProject(action: TestProjectBuilder.() -> Unit): GradleTestProject {
    val builder = TestProjectBuilderImpl()
    action(builder)

    return GradleTestProject.builder().fromTestApp(builder).create()
}

enum class BuildFileType(val extension: String) {
    GROOVY(""), KTS(".kts")
}

interface TestProjectBuilder {
    var buildFileType: BuildFileType

    /**
     * Configures the root project
     */
    fun rootProject(action: SubProjectBuilder.() -> Unit)

    /**
     * Configures the subProject, creating it if needed.
     */
    fun subProject(path: String, action: SubProjectBuilder.() -> Unit)
}

interface SubProjectBuilder {
    val path: String
    var plugins: MutableList<PluginType>

    /**
     * Adds a file to the given location with the given content.
     *
     * This should not be used for build files. Use [appendToBuildFile] instead
     */
    fun addFile(relativePath: String, content: String)

    /**
     * Returns the file at the current location.
     *
     * This will fail if the file has not been added yet. This is a virtual representation of the
     * file that does not yet exist on the disk.
     *
     * This should not be used for build files. Use [appendToBuildFile] instead
     *
     * @return a [SourceFile] that can be used to tweak the content of the file.
     */
    fun fileAt(relativePath: String): SourceFile

    /**
     * Returns the file at the given location or null if the file has not been added yet
     *
     * This should not be used for build files. Use [appendToBuildFile] instead
     */
    fun fileAtOrNull(relativePath: String): SourceFile?

    /**
     * Appends a string to the build file.
     *
     * @param action an action that returns the string to be added. This is triggered only when
     * the project's content if created.
     */
    fun appendToBuildFile(action: () -> String)

    /**
     * Configures the android section of the project.
     *
     * This will fails if no android plugins were added.
     */
    fun android(action: AndroidProjectBuilder.() -> Unit)

    /**
     * Configures dependencies of the project
     */
    fun dependencies(action: DependenciesBuilder.() -> Unit)
}

interface AndroidProjectBuilder {
    var packageName: String
    var applicationId: String?
    var minSdk: Int?
    var minSdkCodename: String?

    fun buildFeatures(action: BuildFeaturesBuilder.() -> Unit)

    fun testFixtures(action: TestFixturesBuilder.() -> Unit)

    fun buildTypes(action: ContainerBuilder<BuildTypeBuilder>.() -> Unit)
    fun productFlavors(action: ContainerBuilder<ProductFlavorBuilder>.() -> Unit)

    fun addFile(relativePath: String, content: String)
}

interface Config {
    var manifest: String
    var dependencies: MutableList<String>
}

interface BuildFeaturesBuilder {
    var aidl: Boolean?
    var buildConfig: Boolean?
    var renderScript: Boolean?
    var resValues: Boolean?
    var shaders: Boolean?
    var androidResources: Boolean?
    var mlModelBinding: Boolean?
}

interface TestFixturesBuilder {
    var enable: Boolean?
}

interface ContainerBuilder<T> {
    fun named(name: String, action: T.() -> Unit)
}

interface BuildTypeBuilder {
    val name: String
    var isDefault: Boolean?
}

interface ProductFlavorBuilder {
    val name: String
    var isDefault: Boolean?
    var dimension: String?
}

interface DependenciesBuilder {

    /**
     * adds a dependency in the implementation scope.
     *
     * The instance being passed as a parameter must be:
     * - a String: for maven coordinates. Should not be quoted.
     * - result of [project] for sub-project dependency
     * - result of [localJar] for on-the-fly created local jars
     * - a [MavenRepoGenerator.Library] for on-the-fly created external AARs.
     */
    fun implementation(dependency: Any)

    /**
     * Creates a [LocalJarBuilder] to be passed to [implementation] (or any other scope)
     */
    fun localJar(action: LocalJarBuilder.() -> Unit) : LocalJarBuilder

    /**
     * Creates a [ProjectDependencyBuilder] to be passed to [implementation] (or any other scope)
     */
    fun project(path: String): ProjectDependencyBuilder
}

interface LocalJarBuilder {
    var name: String
    fun addClass(className: String)
}

interface ProjectDependencyBuilder {
    val path: String
}
