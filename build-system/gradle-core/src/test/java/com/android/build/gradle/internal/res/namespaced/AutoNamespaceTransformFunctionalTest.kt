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

package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.dependency.IdentityTransform
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.createProjectServices
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator.jarWithEmptyClasses
import com.android.testutils.apk.Zip
import com.android.testutils.generateAarWithContent
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

/** Functional tests for automatic resource namespacing */
class AutoNamespaceTransformFunctionalTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val project: Project by lazy {
        ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
    }

    private val configuration: Configuration by lazy {
        project.configurations.create("example")
    }

    @Before
    fun setUpTransforms() {
        val dependencies = project.dependencies
        val projectOptions = createProjectServices().projectOptions
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        dependencies.registerTransform(
            AutoNamespacePreProcessTransform::class.java
        ) { reg: TransformSpec<AutoNamespaceParameters> ->
            reg.from.attribute(ARTIFACT_FORMAT, AndroidArtifacts.ArtifactType.AAR.type)
            reg.to.attribute(ARTIFACT_FORMAT, TYPE_AUTO_NAMESPACE_INTERMEDIATE_PROCESSED_AAR)
            reg.parameters.initialize()
        }

        dependencies.registerTransform(
            AutoNamespacePreProcessTransform::class.java
        ) { reg: TransformSpec<AutoNamespaceParameters> ->
            reg.from.attribute(ARTIFACT_FORMAT, AndroidArtifacts.ArtifactType.JAR.type)
            reg.to.attribute(ARTIFACT_FORMAT, TYPE_AUTO_NAMESPACE_INTERMEDIATE_PROCESSED_AAR)
            reg.parameters.initialize()
        }

        dependencies.registerTransform(
            AutoNamespaceTransform::class.java
        ) { reg: TransformSpec<AutoNamespaceParameters> ->
            reg.from.attribute(ARTIFACT_FORMAT, TYPE_AUTO_NAMESPACE_INTERMEDIATE_PROCESSED_AAR)
            reg.to.attribute(ARTIFACT_FORMAT, AndroidArtifacts.ArtifactType.PROCESSED_AAR.type)
            reg.parameters.initialize()
        }
        // Ignore the interactions with jetifier for this functional test.
        dependencies.registerTransform(
            IdentityTransform::class.java
        ) { reg: TransformSpec<IdentityTransform.Parameters> ->
            reg.from.attribute(ARTIFACT_FORMAT, AndroidArtifacts.ArtifactType.JAR.type)
            reg.to.attribute(ARTIFACT_FORMAT, AndroidArtifacts.ArtifactType.PROCESSED_JAR.type)
            reg.parameters.projectName.set(project.name)
        }
    }

    /** Check that the jar does not interfere with the AAR processing */
    @Test
    fun autoNamespaceDependenciesJarPreserved() {
        val mavenRepo: Path = temporaryFolder.newFolder("mavenRepo").toPath().also {
            val jar = MavenRepoGenerator.Library(
                "com.example:jar:1",
                jarWithEmptyClasses(listOf("com/example/jar/MyClass"))
            )
            MavenRepoGenerator(libraries = listOf(jar)).generate(it)
        }
        project.repositories.add(project.repositories.maven { it.url = mavenRepo.toUri() })
        project.dependencies.add(configuration.name, "com.example:jar:1")

        assertThat(getAars()).hasSize(0)
        val jars = getJars()
        assertThat(jars).hasSize(1)
    }

    /** Sanity test for the AAR processing. */
    @Test
    fun sanityTest() {
        val mavenRepo: Path = temporaryFolder.newFolder("mavenRepo").toPath().also {
            val aar = MavenRepoGenerator.Library(
                "com.example:aar:1",
                "aar",
                generateAarWithContent(
                    "com.example.aar",
                    jarWithEmptyClasses(
                        ImmutableList.of("com/example/aar/MyClass")
                    ),
                    ImmutableMap.of()
                )
            )
            MavenRepoGenerator(libraries = listOf(aar)).generate(it)
        }
        project.repositories.add(project.repositories.maven { it.url = mavenRepo.toUri() })
        project.dependencies.add(configuration.name, "com.example:aar:1")


        assertThat(getJars()).hasSize(0)
        val aars = getAars()
        assertThat(aars).hasSize(1)
        assertThat(aars.first()) {aar ->
            aar.contains("AndroidManifest.xml")
            aar.contains("res.apk")
            aar.containsFileWithContent("R.txt", "")
        }
    }

    /** Check a simple rewriting case */
    @Test
    fun rewriteTest() {
        val mavenRepo: Path = temporaryFolder.newFolder("mavenRepo").toPath().also {
            val foo = MavenRepoGenerator.Library(
                "com.example:foo:1",
                "aar",
                generateAarWithContent(
                    packageName = "com.example.foo",
                    mainJar = jarWithEmptyClasses(ImmutableList.of("com/example/foo/FooClass")),
                    resources = mapOf("values/strings.xml" to """<resources><string name="foo_string">Foo String</string></resources>""".toByteArray())
                )
            )
            val bar = MavenRepoGenerator.Library(
                "com.example:bar:1",
                "aar",
                generateAarWithContent(
                    packageName = "com.example.bar",
                    mainJar = jarWithEmptyClasses(ImmutableList.of("com/example/bar/BarClass")),
                    // Bar string references foo string
                    resources = mapOf("values/strings.xml" to """<resources><string name="bar_string">@string/foo_string</string></resources>""".toByteArray())
                ),
                "com.example:foo:1"
            )
            MavenRepoGenerator(libraries = listOf(foo, bar)).generate(it)
        }
        project.repositories.add(project.repositories.maven { it.url = mavenRepo.toUri() })
        project.dependencies.add(configuration.name, "com.example:bar:1")

        assertThat(getJars()).hasSize(0)
        val aars = getAars()
        assertThat(aars).hasSize(2)
        assertThat(aars.first { it.name.contains("foo") }) { foo ->
            foo.contains("AndroidManifest.xml")
            foo.contains("classes.jar")
            foo.contains("res.apk")
            foo.containsFileWithContent("R.txt", "int string foo_string 0x0")
        }
        assertThat(aars.first { it.name.contains("bar") }) { bar ->
            bar.contains("AndroidManifest.xml")
            bar.contains("classes.jar")
            bar.contains("res.apk")
            // Before namespacing, this would most likely have contained foo_string too.
            // (We don't include the R.txt in the given AARs as it is not used anyway.)
            bar.containsFileWithContent("R.txt", "int string bar_string 0x0")
        }
    }

    /** Check that an AAR that depends on a jar can be namespaced. */
    @Test
    fun aarDependsOnJar() {
        val mavenRepo: Path = temporaryFolder.newFolder("mavenRepo").toPath().also {
            val jar = MavenRepoGenerator.Library(
                "com.example:jar:1",
                jarWithEmptyClasses(listOf("com/example/jar/MyClass"))
            )
            val aar = MavenRepoGenerator.Library(
                "com.example:aar:1",
                "aar",
                generateAarWithContent(
                    packageName = "com.example.aar",
                    mainJar = jarWithEmptyClasses(ImmutableList.of("com/example/aar/AarClass")),
                    resources = mapOf("values/strings.xml" to """<resources><string name="aar_string">Aar String</string></resources>""".toByteArray())
                ),
                "com.example:jar:1"
            )

            MavenRepoGenerator(libraries = listOf(jar, aar)).generate(it)
        }
        project.repositories.add(project.repositories.maven { it.url = mavenRepo.toUri() })
        project.dependencies.add(configuration.name, "com.example:aar:1")

        assertThat(getJars()).hasSize(1)
        val aars = getAars()
        assertThat(aars).hasSize(1)
        assertThat(aars.first()) { foo ->
            foo.contains("AndroidManifest.xml")
            foo.contains("classes.jar")
            foo.contains("res.apk")
            foo.containsFileWithContent("R.txt", "int string aar_string 0x0")
        }
    }

    private fun getArtifactView(type: AndroidArtifacts.ArtifactType): ArtifactView {
        return configuration.incoming.artifactView { config ->
            config.attributes.attribute(ARTIFACT_FORMAT, type.type)
        }
    }

    private fun getFiles(type: AndroidArtifacts.ArtifactType): Set<File> =
        getArtifactView(type).files.files

    private fun getAars() = getFiles(AndroidArtifacts.ArtifactType.PROCESSED_AAR)

    private fun getJars() = getFiles(AndroidArtifacts.ArtifactType.PROCESSED_JAR)

    private fun AutoNamespaceParameters.initialize() {
        projectName.set(project.name)
        createProjectServices(project).initializeAapt2Input(aapt2)
    }

    companion object {
        private const val TYPE_AUTO_NAMESPACE_INTERMEDIATE_PROCESSED_AAR =
            "auto-namespace-intermediate-processed-aar"
    }


}
