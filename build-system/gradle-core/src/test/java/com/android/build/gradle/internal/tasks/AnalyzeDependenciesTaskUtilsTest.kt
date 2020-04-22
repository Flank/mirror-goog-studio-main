/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.internal.dependency.getClassesInJar
import com.android.build.gradle.internal.dependency.getResourcesFromExplodedAarToFile
import com.android.build.gradle.internal.dependency.writePathsToFile
import com.android.build.gradle.internal.fixtures.FakeArtifactCollection
import com.android.build.gradle.internal.fixtures.FakeComponentIdentifier
import com.android.build.gradle.internal.fixtures.FakeResolvedArtifactResult
import com.android.build.gradle.internal.transforms.testdata.*
import com.android.build.gradle.tasks.AnalyzeDependenciesTask.VariantClassesHolder
import com.android.build.gradle.tasks.AnalyzeDependenciesTask.VariantDependenciesHolder
import com.android.build.gradle.tasks.ClassFinder
import com.android.build.gradle.tasks.DependencyUsageFinder
import com.android.build.gradle.tasks.ResourcesFinder
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Incubating
import org.gradle.api.artifacts.Dependency
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnalyzeDependenciesTaskUtilsTest {

    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    @Test
    fun testClassHolder() {
        val classesDir = tmp.root.toPath().resolve("classesDir")
        TestInputsGenerator.pathWithClasses(
            classesDir,
            ImmutableList.of<Class<*>>(Animal::class.java))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(classesDir.toFile(), FakeComponentIdentifier("foo"))))
        val fileCollection = classesArtifacts.artifactFiles

        val variantClassHolder = VariantClassesHolder(fileCollection)

        val publicClasses = variantClassHolder.getPublicClasses()
        val allClasses = variantClassHolder.getUsedClasses()

        assertThat(allClasses).containsExactly(
            "com/android/build/gradle/internal/transforms/testdata/Animal.class",
            "java/lang/Object.class",
            "com/android/build/gradle/internal/transforms/testdata/CarbonForm.class")
        assertThat(publicClasses).containsExactly(
            "com/android/build/gradle/internal/transforms/testdata/Animal.class")
    }

    @Test
    fun testDependenciesHolder() {
        val dependency1 = FakeDependency("com/library", "Dependency", "1")
        val dependency2 = FakeDependency("com/example", "AnotherDependency", "2")

        val all = listOf<Dependency>(dependency1, dependency2)
        val api = listOf<Dependency>(dependency1)
        val dependenciesHolder = VariantDependenciesHolder(all, api)

        assertThat(dependenciesHolder.buildDependencyId(dependency1))
                .isEqualTo("com/library:Dependency:1")
        assertThat(dependenciesHolder.all)
                .containsExactly("com/library:Dependency:1", "com/example:AnotherDependency:2")
        assertThat(dependenciesHolder.api).containsExactly("com/library:Dependency:1")
    }

    @Test
    fun testClassFinder() {
        val fooPackage = "foo"
        val barPackage = "bar"

        val class1 = Cat::class.java
        val class2 = Dog::class.java
        val class3 = Toy::class.java

        val fooJar = tmp.root.toPath().resolve("foo.jar")
        val barJar = tmp.root.toPath().resolve("bar.jar")

        TestInputsGenerator.pathWithClasses(
                fooJar,
                ImmutableList.of<Class<*>>(class1, class2))
        TestInputsGenerator.pathWithClasses(
                barJar,
                ImmutableList.of<Class<*>>(class3))

        val fooDependencySources = tmp.newFolder("dependency-sources-foo")
        val barDependencySources = tmp.newFolder("dependency-sources-bar")

        writePathsToFile(File(fooDependencySources, "classes.txt"), getClassesInJar(fooJar))
        writePathsToFile(File(barDependencySources, "classes.txt"), getClassesInJar(barJar))

        val classesArtifacts = FakeArtifactCollection(mutableSetOf(
                FakeResolvedArtifactResult(fooDependencySources, FakeComponentIdentifier(fooPackage)),
                FakeResolvedArtifactResult(barDependencySources, FakeComponentIdentifier(barPackage))))

        val finder = ClassFinder(classesArtifacts)

        assertThat(fooPackage).isEqualTo(finder.find(getClassName(class1)))
        assertThat(fooPackage).isEqualTo(finder.find(getClassName(class2)))
        assertThat(barPackage).isEqualTo(finder.find(getClassName(class3)))
        assertThat(finder.findClassesInDependency(fooPackage))
                .containsExactly(getClassName(class1), getClassName(class2))
        assertThat(finder.findClassesInDependency(barPackage))
                .containsExactly(getClassName(class3))
    }

    @Test
    fun testResourceFinder() {
        val fooPackage = "foo"
        val barPackage = "bar"
        val fooProject = tmp.newFolder(fooPackage)
        val barProject = tmp.newFolder(barPackage)
        val fooDependencySources = tmp.newFolder("dependency-sources-foo")
        addResourceTestDirectoryTo(fooProject)
        val barDependencySources = tmp.newFolder("dependency-sources-bar")
        //Add bar resource which will depend on a resource from foo.
        val barMainActivty = """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                            xmlns:app="http://schemas.android.com/apk/res-auto">
                <TextView
                    android:id="@+id/text_box"
                    android:text="@string/app_name"
                    android:layout_height="wrap_content"
                    android:layout_width="match_parent"/>
            </RelativeLayout>
        """.trimIndent()
        val barRes = File(barProject, SdkConstants.FD_RES)
        val barLayout = File(barRes, SdkConstants.FD_RES_LAYOUT)
        barRes.mkdir()
        barLayout.mkdir()
        val barActivityFile = File(barLayout, "bar_activity.xml")
        barActivityFile.writeText(barMainActivty)

        val fooResSymbols = getResourcesFromExplodedAarToFile(fooProject)
        val barResSymbols = getResourcesFromExplodedAarToFile(barProject)
        writePathsToFile(
                File(fooDependencySources, "resources_symbols${SdkConstants.DOT_TXT}"),
                fooResSymbols)
        writePathsToFile(
                File(barDependencySources, "resources_symbols${SdkConstants.DOT_TXT}"),
                barResSymbols)

        val artifacts = FakeArtifactCollection(mutableSetOf(
                FakeResolvedArtifactResult(fooDependencySources,
                        FakeComponentIdentifier(fooPackage)),
                FakeResolvedArtifactResult(barDependencySources,
                        FakeComponentIdentifier(barPackage))
        ))

        val finder = ResourcesFinder(artifacts)

        assertThat(finder.findUsedDependencies())
                .containsExactlyElementsIn(listOf(fooPackage, barPackage))
        assertThat(finder.findUnUsedDependencies().size).isEqualTo(0)
        assertThat(finder.find("string:app_name:-1").size).isEqualTo(2)
    }

    @Test
    fun testDependencyUsage() {
        // Create some classes
        val class1 = Animal::class.java
        val class2 = Toy::class.java
        val class3 = Cat::class.java
        val class4 = Dog::class.java

        // Create some dependencies
        val dependency1 = FakeDependency("com/library", "Dependency", "1")
        val dependency2 = FakeDependency("com/example", "AnotherDependency", "2")
        val dependency3 = FakeDependency("com/test", "NewDependency", "3")

        val jarA = tmp.root.toPath().resolve("A.jar")
        val jarB = tmp.root.toPath().resolve("B.jar")
        val jarC = tmp.root.toPath().resolve("C.jar")

        // Add the classes to the dependencies
        TestInputsGenerator.pathWithClasses(jarA, ImmutableList.of<Class<*>>(class1))
        TestInputsGenerator.pathWithClasses(jarB, ImmutableList.of<Class<*>>(class2))
        TestInputsGenerator.pathWithClasses(jarC, ImmutableList.of<Class<*>>(class3, class4))

        // Add dep1 and dep2 as direct dependencies of test project. Dep1 is transitive
        val all = listOf<Dependency>(dependency2, dependency3)
        val api = listOf<Dependency>()
        val variantDependencies = VariantDependenciesHolder(all, api)

        val id1 = variantDependencies.buildDependencyId(dependency1)!!
        val id2 = variantDependencies.buildDependencyId(dependency2)!!
        val id3 = variantDependencies.buildDependencyId(dependency3)!!

        val dependency1Sources = tmp.newFolder("dependencySources-aar1")
        val dependency2Sources = tmp.newFolder("dependencySources-aar2")
        val dependency3Sources = tmp.newFolder("dependencySources-aar3")

        writePathsToFile(File(dependency1Sources, "classes.txt"), getClassesInJar(jarA))
        writePathsToFile(File(dependency2Sources, "classes.txt"), getClassesInJar(jarB))
        writePathsToFile(File(dependency3Sources, "classes.txt"), getClassesInJar(jarC))

        // Create an ArtifactCollection
        val externalArtifactCollection = FakeArtifactCollection(mutableSetOf(
                FakeResolvedArtifactResult(dependency1Sources, FakeComponentIdentifier(id1)),
                FakeResolvedArtifactResult(dependency2Sources, FakeComponentIdentifier(id2)),
                FakeResolvedArtifactResult(dependency3Sources, FakeComponentIdentifier(id3))))


        // Create some test classes
        val class6 = Giraffe::class.java
        val class7 = NewClass::class.java

        val classesDir = tmp.root.toPath().resolve("projectClasses")
        TestInputsGenerator.pathWithClasses(classesDir, ImmutableList.of<Class<*>>(class6, class7))

        val projectClassesArtifact = FakeArtifactCollection(
            mutableSetOf(
                FakeResolvedArtifactResult(
                    classesDir.toFile(),
                    FakeComponentIdentifier("project"))))

        val variantClasses = VariantClassesHolder(projectClassesArtifact.artifactFiles)

        val classFinder = ClassFinder(externalArtifactCollection)
        val dependencyUsageFinder = DependencyUsageFinder(
            classFinder,
            variantClasses,
            variantDependencies)

        assertThat(dependencyUsageFinder.usedDirectDependencies)
            .containsExactly("com/example:AnotherDependency:2")
        assertThat(dependencyUsageFinder.requiredDependencies)
            .containsExactly("com/example:AnotherDependency:2", "com/library:Dependency:1")
        assertThat(dependencyUsageFinder.unusedDirectDependencies)
            .containsExactly("com/test:NewDependency:3")
    }

    @Test
    fun testDependencyGraph() {
        // Create some classes
        val class1 = CarbonForm::class.java
        val class2 = Animal::class.java
        val class3 = Cat::class.java

        // Create some dependencies
        val dependency1 = FakeDependency("com/library", "Dependency", "1")
        val dependency2 = FakeDependency("com/example", "AnotherDependency", "2")
        val dependency3 = FakeDependency("com/test", "NewDependency", "3")

        val all = listOf<Dependency>(dependency1, dependency3)
        val api = listOf<Dependency>()
        val variantDependencies = VariantDependenciesHolder(all, api)

        val jarA = tmp.root.toPath().resolve("A.jar")
        val jarB = tmp.root.toPath().resolve("B.jar")
        val jarC = tmp.root.toPath().resolve("C.jar")

        // Add the classes to the dependencies
        TestInputsGenerator.pathWithClasses(jarA, ImmutableList.of<Class<*>>(class1))
        TestInputsGenerator.pathWithClasses(jarB, ImmutableList.of<Class<*>>(class2))
        TestInputsGenerator.pathWithClasses(jarC, ImmutableList.of<Class<*>>(class3))

        val id1 = variantDependencies.buildDependencyId(dependency1)!!
        val id2 = variantDependencies.buildDependencyId(dependency2)!!
        val id3 = variantDependencies.buildDependencyId(dependency3)!!

        val dependency1Sources = tmp.newFolder("dependencySources-aar1")
        val dependency2Sources = tmp.newFolder("dependencySources-aar2")
        val dependency3Sources = tmp.newFolder("dependencySources-aar3")

        writePathsToFile(File(dependency1Sources, "classes.txt"), getClassesInJar(jarA))
        writePathsToFile(File(dependency2Sources, "classes.txt"), getClassesInJar(jarB))
        writePathsToFile(File(dependency3Sources, "classes.txt"), getClassesInJar(jarC))

        // Create an ArtifactCollection
        val externalArtifactCollection = FakeArtifactCollection(mutableSetOf(
            FakeResolvedArtifactResult(dependency1Sources, FakeComponentIdentifier(id1)),
            FakeResolvedArtifactResult(dependency2Sources, FakeComponentIdentifier(id2)),
            FakeResolvedArtifactResult(dependency3Sources, FakeComponentIdentifier(id3))))

        // Create some test classes
        val class6 = Giraffe::class.java
        val class7 = Toy::class.java

        val classesDir = tmp.root.toPath().resolve("projectClasses")
        TestInputsGenerator.pathWithClasses(classesDir, ImmutableList.of<Class<*>>(class6, class7))

        val projectClassesArtifact = FakeArtifactCollection(
            mutableSetOf(
                FakeResolvedArtifactResult(
                    classesDir.toFile(),
                    FakeComponentIdentifier("project"))))

        val variantClasses = VariantClassesHolder(projectClassesArtifact.artifactFiles)
        val classFinder = ClassFinder(externalArtifactCollection)
        val dependencyUsageFinder = DependencyUsageFinder(
            classFinder,
            variantClasses,
            variantDependencies)

        /*              dependency1 (CarbonForm.class) ----
                                   |                       |
                                   |             dependency2 (Animal.class)
                                   |                       |
                                   |              dependency3 (Cat.class)
                                   |                       |
                                    ---------- ------------
                                              |
                                 app (Giraffe.class, Toy.class)

              App does not use dependency3, but uses transitive dependency2
         */

        assertThat(dependencyUsageFinder.usedDirectDependencies)
            .containsExactly("com/library:Dependency:1")
        assertThat(dependencyUsageFinder.requiredDependencies)
            .containsExactly("com/library:Dependency:1", "com/example:AnotherDependency:2")
        assertThat(dependencyUsageFinder.unusedDirectDependencies)
            .containsExactly("com/test:NewDependency:3")
        // TODO: Add tests for the new dependency graph
    }

    private fun addResourceTestDirectoryTo(parentDirectory: File): File {
        val resDir = File(parentDirectory, SdkConstants.FD_RES).also { it.mkdir() }
        val layoutDir = File(resDir, SdkConstants.FD_RES_LAYOUT).also { it.mkdir() }
        val valuesDir = File(resDir, SdkConstants.FD_RES_VALUES).also { it.mkdir() }

        val layoutMainActivity = File(layoutDir, "activity_main.xml")
        layoutMainActivity.writeText(
                """
                    <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                                    xmlns:app="http://schemas.android.com/apk/res-auto">
                        <ImageView android:src="@drawable/foo"/>
                    </RelativeLayout>"""
        )
        val values = File(valuesDir, "values.xml")
        values.writeText(
                """
                    <resources>
                        <string name="app_name">My app</string>
                        <string name="desc">It does something</string>
                    </resources>"""
        )
        val valuesStyles = File(valuesDir, "style.xml")
        valuesStyles.writeText(
                """
                    <resources>
                        <attr name="myAttr" format="color" />
                        <declare-styleable name="ds">
                            <attr name="android:name" />
                            <attr name="android:color" />
                            <attr name="myAttr" />
                        </declare-styleable>
                    </resources>"""
        )
        val valuesStylesReversed = File(valuesDir, "stylesReversed.xml")
        valuesStylesReversed.writeText(
                """
                    <resources>
                            <declare-styleable name="ds2">
                                <attr name="myAttr2" />
                                <attr name="maybeAttr" />
                            </declare-styleable>
                        <attr name="myAttr2" format="color" />
                        </resources>"""
        )
        return resDir
    }

    private fun getClassName(cls: Class<*>): String
            = cls.name.replace(".", "/").plus(SdkConstants.DOT_CLASS)

    private class FakeDependency(
        private val fakeGroup: String,
        private val fakeName: String,
        private val fakeVersion: String
    ) : Dependency {
        override fun getGroup() = fakeGroup

        override fun getName() = fakeName

        override fun getVersion() = fakeVersion

        override fun contentEquals(var1: Dependency): Boolean {
            return (var1.group == fakeGroup && name == fakeName && version == fakeVersion)
        }

        override fun copy(): Dependency {
            return FakeDependency(fakeName, fakeVersion, fakeGroup)
        }

        @Incubating
        override fun getReason(): String? {
            return null
        }

        @Incubating
        override fun because(var1: String?) {
        }
    }
}