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

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.CompileOptions
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.testutils.MavenRepoGenerator

/**
 * Creates a [TestProject] with the provided configuration action
 */
fun createProject(action: TestProjectBuilder.() -> Unit): TestProject {
    val builder = RootTestProjectBuilderImpl()
    action(builder)

    return builder
}

/**
 * Creates a [GradleTestProject] with the provided configuration action
 */
fun createGradleProject(action: TestProjectBuilder.() -> Unit): GradleTestProject {
    return createGradleProjectBuilder(action).create()
}

/**
 * Creates a [GradleTestProjectBuilder] with the provided configuration action
 */
fun createGradleProjectBuilder(action: TestProjectBuilder.() -> Unit): GradleTestProjectBuilder {
    val builder = RootTestProjectBuilderImpl()
    action(builder)

    return GradleTestProject
        .builder()
        .fromTestApp(builder)
        .withAdditionalMavenRepo(builder.mavenRepoGenerator)
}

enum class BuildFileType(val extension: String) {
    GROOVY(""), KTS(".kts")
}

interface TestProjectBuilder {
    val name: String

    var buildFileType: BuildFileType

    fun settings(action: SettingsBuilder.() -> Unit)

    /**
     * Configures the root project
     */
    fun rootProject(action: SubProjectBuilder.() -> Unit)

    /**
     * Configures the subProject, creating it if needed.
     */
    fun subProject(path: String, action: SubProjectBuilder.() -> Unit)

    fun includedBuild(name: String, action: TestProjectBuilder.() -> Unit)

    val includedBuilds: List<TestProjectBuilder>
}

interface SubProjectBuilder {
    val path: String
    var group: String?
    var version: String?
    val plugins: MutableList<PluginType>

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
     * Configures the androidcomponents section of the project.
     *
     * This will fails if no android plugins were added.
     */
    fun androidComponents(action: AndroidComponentsBuilder.() -> Unit)

    /**
     * Configures dependencies of the project
     */
    fun dependencies(action: DependenciesBuilder.() -> Unit)

    /**
     * Wraps a library binary with a module
     */
    fun wrap(library: ByteArray, fileName: String)
}

interface AndroidProjectBuilder {
    var packageName: String
    var applicationId: String?
    var buildToolsRevision: String?
    var namespace: String?
    var compileSdk: Int?
    var minSdk: Int?
    var minSdkPreview: String?
    var targetProjectPath: String?
    var renderscriptTargetApi: Int?
    var renderscriptSupportModeEnabled: Boolean?

    val dynamicFeatures: MutableSet<String>

    /**
     * Sets up a default compile Sdk Value
     */
    fun defaultCompileSdk()

    fun aarMetadata(action: AarMetadataBuilder.() -> Unit)

    fun buildFeatures(action: BuildFeaturesBuilder.() -> Unit)

    fun testFixtures(action: TestFixturesBuilder.() -> Unit)

    fun buildTypes(action: ContainerBuilder<BuildTypeBuilder>.() -> Unit)
    fun productFlavors(action: ContainerBuilder<ProductFlavorBuilder>.() -> Unit)
    fun prefab(action: ContainerBuilder<PrefabBuilder>.() -> Unit)
    fun sourceSets(action: ContainerBuilder<SourceSetsBuilder>.() -> Unit)

    fun addFile(relativePath: String, content: String)

    fun androidResources(action: AndroidResources.() -> Unit)

    fun compileOptions(action: CompileOptions.() -> Unit)
}

interface AndroidComponentsBuilder {
    fun disableVariant(variantName: String)
    fun disableAndroidTest(variantName: String)
    fun disableUnitTest(variantName: String)
    fun registerSourceType(name: String)
}

interface Config {
    var manifest: String
    var dependencies: MutableList<String>
}

interface AarMetadataBuilder {
    var minAgpVersion: String?
    var minCompileSdk: Int?
    var minCompileSdkExtension: Int?
}

interface BuildFeaturesBuilder {
    var aidl: Boolean?
    var buildConfig: Boolean?
    var prefab: Boolean?
    var prefabPublishing: Boolean?
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
    var testCoverageEnabled: Boolean?

    fun ndk(action: NdkBuilder.() -> Unit)
    var ndk: NdkBuilder?

    fun resValue(type: String, name: String, value: String)
    val resValues: List<Triple<String, String, String>>
}

interface PrefabBuilder {
    val name: String
    var headers: String?
}

interface ProductFlavorBuilder {
    val name: String
    var isDefault: Boolean?
    var dimension: String?
}

interface NdkBuilder {
    var abiFilters: List<String>
}

interface DependenciesBuilder {

    fun clear()

    /**
     * adds a dependency in the implementation scope.
     *
     * The instance being passed as a parameter must be:
     * - a String (should not be quoted) or result of [externalLibrary]: for maven coordinates.
     * - result of [project] for sub-project dependency
     * - result of [localJar] for on-the-fly created local jars
     * - a [MavenRepoGenerator.Library] for on-the-fly created external AARs.
     */
    fun implementation(dependency: Any)

    /**
     * adds a dependency in the api scope.
     *
     * See [implementation] for details
     */
    fun api(dependency: Any)

    /** Adds a dependency to the runtimeOnly configuration. See [implementation] for details. */
    fun runtimeOnly(dependency: Any)

    /**
     * adds a dependency in the testImplementation scope.
     *
     * See [implementation] for details
     */
    fun testImplementation(dependency: Any)

    /**
     * adds a dependency in the androidTestImplementation scope.
     *
     * See [implementation] for details
     */
    fun androidTestImplementation(dependency: Any)

    /**
     * adds a dependency
     */
    fun include(dependency: Any)

    /**
     * adds a dependency in the lintPublish scope.
     *
     * See [implementation] for details
     */
    fun lintPublish(dependency: Any)

    /**
     * adds a dependency in the lintCheck scope.
     *
     * See [implementation] for details
     */
    fun lintChecks(dependency: Any)

    /**
     * Creates a [LocalJarBuilder] to be passed to [implementation] or any other scope
     */
    fun localJar(action: LocalJarBuilder.() -> Unit) : LocalJarBuilder

    /**
     * Creates a [ProjectDependencyBuilder] to be passed to [implementation] or any other scope
     *
     * @param path the project path
     * @param testFixtures whether the dependency is on the test fixtures of the project.
     */
    fun project(
        path: String,
        testFixtures: Boolean = false,
        configuration: String? = null): ProjectDependencyBuilder

    /**
     * Creates a [ExternalDependencyBuilder] to be passed to [implementation] or any other scope
     *
     * @param coordinate the external library coordinate
     * @param testFixtures whether the dependency is on the test fixtures of the library.
     */
    fun externalLibrary(coordinate: String, testFixtures: Boolean = false): ExternalDependencyBuilder

    /**
     * Adds a dependency in the coreLibraryDesugaring scope.
     */
    fun coreLibraryDesugaring(dependency: Any)
}

interface LocalJarBuilder {
    var name: String
    fun addClass(className: String)
}

interface ProjectDependencyBuilder {
    val path: String
    val testFixtures: Boolean

    /**
     * the configuration that is targeted on the publishing project. This is legacy
     * for the case that pre-dates variant-aware publishing.
     */
    val configuration: String?
}

interface ExternalDependencyBuilder {
    val coordinate: String
    val testFixtures: Boolean
}

interface AndroidSettingsBuilder {
    var compileSdk: Int?
    var minSdk: Int?
}

interface SettingsBuilder {
    val plugins: MutableList<PluginType>

    /**
     * Configures the android section of the project.
     *
     * This will fails if no android plugins were added.
     */
    fun android(action: AndroidSettingsBuilder.() -> Unit)
}

interface SourceSetsBuilder {
    val name: String
    var manifestSrcFile: String?
    var javaSrcDirs: List<String>
    var resSrcDirs: List<String>
    var resourcesSrcDirs: List<String>
}
