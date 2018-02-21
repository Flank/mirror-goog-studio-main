/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.testutils.TestResources
import com.android.testutils.truth.FileSubject
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import com.google.common.collect.ImmutableMap.of as map
import com.google.common.collect.ImmutableSet.of as set

class AutoNamespaceDependenciesTaskTest {
    @get:Rule
    var tempFolder = TemporaryFolder()

    lateinit private var project: Project
    lateinit private var task: AutoNamespaceDependenciesTask
    lateinit private var javacOutput: File

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(tempFolder.newFolder()).build()
        task = project.tasks.create("test", AutoNamespaceDependenciesTask::class.java)
        javacOutput = tempFolder.newFolder("out")
    }

    @Test
    fun oneNonNamespacedNodeWithNoDependencies() {
        val log = MockLogger()

        compileSources(ImmutableList.of(getFile("R.java"), getFile("Test.java")), javacOutput)

        val testClass = FileUtils.join(javacOutput, "com", "example", "mymodule", "Test.class")
        FileSubject.assertThat(testClass).exists()
        val testRClass = FileUtils.join(javacOutput, "com", "example", "mymodule", "R.class")
        FileSubject.assertThat(testRClass).exists()
        val testRStringClass = FileUtils.join(testRClass.parentFile, "R\$string.class")
        FileSubject.assertThat(testRStringClass).exists()

        val testClassesJar = File(tempFolder.newFolder("jars"), ("classes.jar"))
        ZFile(testClassesJar).use {
            it.add("com/example/mymodule/Test.class", testClass.inputStream())
        }

        val rDef = tempFolder.newFile("com.example.mymodule-R-def.txt")
        val symbolTable = SymbolTable.builder()
            .tablePackage("com.example.mymodule")
            .add(symbol("string", "s1"))
            .add(symbol("string", "s2"))
            .add(symbol("string", "s3"))
            .build()
        SymbolIo.writeRDef(symbolTable, rDef.toPath())

        val node = createDependency("libA")

        val dependencies = wrapDependencies(set(node))
        val outputRewrittenClasses = tempFolder.newFolder("output")
        val outputRClasses = tempFolder.newFolder("rClasses")
        val classesJar = tempFolder.newFile("namespaced-classes.jar")
        val rJar = tempFolder.newFile("r.jar")
        val rFiles = getArtifactCollection(map("libA", rDef))
        val jarFiles = getArtifactCollection(map("libA", testClassesJar))
        task.log = log

        task.namespaceDependencies(
                dependencies,
                rFiles,
                jarFiles,
                outputRewrittenClasses,
                outputRClasses,
                classesJar,
                rJar)

        Truth.assertThat(log.warnings).isEmpty()

        val namespacedJar = File(outputRewrittenClasses, "namespaced-libA-classes.jar")
        FileSubject.assertThat(namespacedJar).exists()
        FileSubject.assertThat(File(outputRClasses, "namespaced-libA-R.jar")).exists()
        FileSubject.assertThat(rJar).exists()
        FileSubject.assertThat(classesJar).exists()

        ZFile(namespacedJar).use {
            it.add("com/example/mymodule/R.class", testRClass.inputStream())
            it.add("com/example/mymodule/R\$string.class", testRStringClass.inputStream())
        }

        URLClassLoader(arrayOf(namespacedJar.toURI().toURL()), null).use { classLoader ->
            val testC = classLoader.loadClass("com.example.mymodule.Test")
            val method = testC.getMethod("test")
            val result = method.invoke(null) as Int
            Truth.assertThat(result).isEqualTo(2 * 3 * 5) // original values left
        }
    }

    /**
     * In the non-namespaced world, R.java for each package contains resources from that package as
     * well as resources from its dependencies. In the namespaced world however, R.java files
     * contain only those resources that were defined in that package.
     *
     * In this test we'll transform a graph of non-namespaced libraries, with some of them defining
     * or overriding the 's1' string resource. Once namespaced, for runtime we'll only include those
     * R.class files that come from a library that defined or overrode the string, therefore making
     * sure that those libraries that did not define the resource are now referencing the 's1' from
     * their dependencies.
     *
     * Graph:
     * roots-> libA       libB*
     *        /     \     /    \
     *    libC       libD*      libE
     *     |              \    /
     *    libF*            libG*  <- leaves
     *
     * Nodes above depend on nodes under them, for example:
     * libA dependsOn directly on libC and libD, transitively dependsOn libF and libG.
     *
     * String s1 defined in libG with ID 1, in libF with ID 2, in libD with ID 3, in libB with ID 4
     * to illustrate namespaced and non-namespaced references.
     */
    @Test
    fun fullyNonNamespacedGraph() {
        // IDs to differentiate which package the reference points to.
        val gValue = 1
        val fValue = 2
        val dValue = 3
        val bValue = 4

        val log = MockLogger()

        // Create the Java sources. The R.java files will contain a single 's1' string resource
        // with the given value.
        val sources = ImmutableList.builder<File>()
        sources.addAll(createSources("com.example.libG", gValue)) // new value
        sources.addAll(createSources("com.example.libF", fValue)) // new value
        sources.addAll(createSources("com.example.libE", gValue)) // value from libG
        sources.addAll(createSources("com.example.libD", dValue)) // override
        sources.addAll(createSources("com.example.libC", fValue)) // value from libF
        sources.addAll(createSources("com.example.libB", bValue)) // override
        sources.addAll(createSources("com.example.libA", fValue)) // value from libF

        compileSources(sources.build(), javacOutput)

        // Maps for storing files used for creating inputs for the task.
        val testClasses = HashMap<String, File>()
        val rClasses = HashMap<String, File>()
        val rStringClasses = HashMap<String, File>()
        val classesJars = HashMap<String, File>()
        val rFiles = HashMap<String, File>()

        // Resource tables for generating R-def.txt files.
        val empty = SymbolTable.builder().build()
        val s1 = SymbolTable.builder().add(symbol("string", "s1")).build()

        // Generate R-def.txt files and collect inputs from each dependency.
        setUpDependency(testClasses, rClasses, rStringClasses, classesJars, rFiles, "libG", s1)
        setUpDependency(testClasses, rClasses, rStringClasses, classesJars, rFiles, "libF", s1)
        setUpDependency(testClasses, rClasses, rStringClasses, classesJars, rFiles, "libE", empty)
        setUpDependency(testClasses, rClasses, rStringClasses, classesJars, rFiles, "libD", s1)
        setUpDependency(testClasses, rClasses, rStringClasses, classesJars, rFiles, "libC", empty)
        setUpDependency(testClasses, rClasses, rStringClasses, classesJars, rFiles, "libB", s1)
        setUpDependency(testClasses, rClasses, rStringClasses, classesJars, rFiles, "libA", empty)

        // Create dependencies between the nodes.
        val nodeG = createDependency("libG")
        val nodeF = createDependency("libF")
        val nodeE = createDependency("libE", set(nodeG))
        val nodeD = createDependency("libD", set(nodeG))
        val nodeC = createDependency("libC", set(nodeF))
        val nodeB = createDependency("libB", set(nodeD, nodeE))
        val nodeA = createDependency("libA", set(nodeC, nodeD))

        // Fill task inputs.
        val dependencies = wrapDependencies(set(nodeA, nodeB))
        val outputRewrittenClasses = tempFolder.newFolder("output")
        val outputRClasses = tempFolder.newFolder("rClasses")
        val classesJar = tempFolder.newFile("namespaced-classes.jar")
        val rJar = tempFolder.newFile("r.jar")
        val rFilesArtifact = getArtifactCollection(rFiles)
        val jarFiles = getArtifactCollection(classesJars)
        task.log = log

        // Execute the task.
        task.namespaceDependencies(
                dependencies,
                rFilesArtifact,
                jarFiles,
                outputRewrittenClasses,
                outputRClasses,
                classesJar,
                rJar)

        // Verify warnings about overrides were printed.
        Truth.assertThat(log.warnings).isNotEmpty()

        // Verify that the namespaced classes.jar were generated for each package and collect them.
        val urls = ArrayList<URL>()
        for (c in 'A'..'G') {
            val namespacedJar = File(outputRewrittenClasses, "namespaced-lib$c-classes.jar")
            FileSubject.assertThat(namespacedJar).exists()
            // only add the R classes where the value was declared or overridden, so we can verify
            // that the other libraries are referencing the namespaced values.
            if (c == 'G' || c == 'F' || c == 'D' || c == 'B') {
                ZFile(namespacedJar).use {
                    it.add("com/example/lib$c/R.class", rClasses["lib$c"]!!.inputStream())
                    it.add(
                            "com/example/lib$c/R\$string.class",
                            rStringClasses["lib$c"]!!.inputStream()
                    )
                }
            }
            urls.add(namespacedJar.toURI().toURL())
        }
        // There were 7 libraries, we should have 7 namespaced classes.jar files.
        Truth.assertThat(urls).hasSize(7)

        // Now go through each package and make sure the rewritten references work at runtime and
        // point to the correct values.
        URLClassLoader(urls.toTypedArray(), null).use { classLoader ->
            // Values should remain the same, read from the namespaced reference
            assertReturnValue(classLoader, "com.example.libG", gValue)
            assertReturnValue(classLoader, "com.example.libF", fValue)
            assertReturnValue(classLoader, "com.example.libE", gValue)
            assertReturnValue(classLoader, "com.example.libD", dValue)
            assertReturnValue(classLoader, "com.example.libC", fValue)
            assertReturnValue(classLoader, "com.example.libB", bValue)
            assertReturnValue(classLoader, "com.example.libA", fValue)
        }
    }

    /**
     * Using the given class loader, makes sure the return value of the Test.test() method in the
     * given package returns the expected value.
     */
    private fun assertReturnValue(classLoader: URLClassLoader, packageName: String, value: Int) {
        val testC = classLoader.loadClass("$packageName.Test")
        val method = testC.getMethod("test")
        val result = method.invoke(null) as Int
        Truth.assertThat(result).isEqualTo(value)
    }

    /**
     * Creates the Test.java and R.java files for the given package. The generated R.java file will
     * contain only one 's1' string resource with the given ID.
     */
    private fun createSources(packageName: String, id: Int): ImmutableSet<File> {
        val directory = tempFolder.newFolder(packageName.replace('.', '_'))
        val testJava = File(directory, "Test.java")
        FileUtils.writeToFile(
                testJava,
                "" +
                        "package $packageName;\n" +
                        "public class Test {\n" +
                        "    public static int test() { return R.string.s1; }\n" +
                        "}\n"
        )
        val rJava = File(directory, "R.java")
        FileUtils.writeToFile(
                rJava,
                "" +
                        "package $packageName;" +
                        "public class R {\n" +
                        "    public static class string {\n" +
                        "        public static int s1 = $id;\n" +
                        "    }\n" +
                        "}"
        )
        return set(testJava, rJava)
    }

    /**
     * Generates the R-def.txt file and collects input files from the dependency.
     */
    private fun setUpDependency(
        testClasses: MutableMap<String, File>,
        rClasses: MutableMap<String, File>,
        rStringClasses: MutableMap<String, File>,
        classesJars: MutableMap<String, File>,
        rFiles: MutableMap<String, File>,
        name: String,
        symbolTable: SymbolTable
    ) {
        val testClass = FileUtils.join(javacOutput, "com", "example", name, "Test.class")
        FileSubject.assertThat(testClass).exists()
        testClasses.put(name, testClass)
        val rClass = FileUtils.join(javacOutput, "com", "example", name, "R.class")
        FileSubject.assertThat(rClass).exists()
        rClasses.put(name, rClass)
        val rStringClass = FileUtils.join(rClass.parentFile, "R\$string.class")
        FileSubject.assertThat(rStringClass).exists()
        rStringClasses.put(name, rStringClass)

        val classesJar = File(tempFolder.newFolder(name), ("classes.jar"))
        ZFile(classesJar).use {
            it.add("com/example/$name/Test.class", testClass.inputStream())
        }
        classesJars.put(name, classesJar)

        val rFile = tempFolder.newFile("com.example.$name-R-def.txt")
        SymbolIo.writeRDef(symbolTable.rename( "com.example.$name"), rFile.toPath())
        rFiles.put(name, rFile)
    }

    private fun getFile(name: String): File {
        return TestResources.getFile(AutoNamespaceDependenciesTaskTest::class.java, name)
    }

    private fun wrapDependencies(roots: MutableSet<DependencyResult>): ResolvableDependencies {
        val dependencies = mock(ResolvableDependencies::class.java)
        val resolutionResult = mock(ResolutionResult::class.java)
        val resolvedComponentResult = mock(ResolvedComponentResult::class.java)

        `when`(dependencies.resolutionResult).thenReturn(resolutionResult)
        `when`(resolutionResult.root).thenReturn(resolvedComponentResult)
        `when`(resolvedComponentResult.dependencies).thenReturn(roots)

        return dependencies
    }

    private fun getArtifactCollection(files: Map<String, File>): ArtifactCollection {
        val collection = mock(ArtifactCollection::class.java)
        val artifacts = getArtifacts(files)
        `when`(collection.artifacts).thenReturn(artifacts)
        return collection
    }

    private fun getArtifacts(files: Map<String, File>): ImmutableSet<ResolvedArtifactResult> {
        val builder = ImmutableSet.builder<ResolvedArtifactResult>()
        for (entry in files) {
            val artifact = mock(ResolvedArtifactResult::class.java)
            val artifactIdentifier = mock(ComponentArtifactIdentifier::class.java)
            val identifier = mock(ComponentIdentifier::class.java)
            `when`(identifier.displayName).thenReturn(entry.key)
            `when`(artifactIdentifier.componentIdentifier).thenReturn(identifier)
            `when`(artifact.id).thenReturn(artifactIdentifier)
            `when`(artifact.file).thenReturn(entry.value)
            builder.add(artifact)
        }
        return builder.build()
    }
}