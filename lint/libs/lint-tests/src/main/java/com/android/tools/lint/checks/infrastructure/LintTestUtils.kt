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

@file:JvmName("LintTestUtils")

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.sdklib.IAndroidTarget
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.openapi.Disposable
import com.intellij.pom.java.LanguageLevel
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Document
import java.io.File
import java.util.EnumSet

// Misc utilities to help writing lint tests

/**
 * Ensures that the comparator for the given comparable [list], the
 * comparison is transitive.
 *
 * (Note: This is inefficient (n^3) so is only meant for tests to do
 * some basic validation of comparison operators. As an example, the
 * ApiClassTest takes 100 randomly chosen elements from the large list
 * and checks those, with a different seed each time.)
 *
 * TODO: See
 *     https://r8-review.googlesource.com/c/r8/+/60142/1/src/main/java/com/android/tools/r8/utils/ListUtils.java#225
 *     for an O(n^2) implementation
 */
fun <T : Comparable<T>> checkTransitiveComparator(list: List<T>) {
    // TODO: Consider caching the comparisons of all the pairs in the list
    for (i in list.indices) {
        for (j in list.indices) {
            for (k in list.indices) {
                val x = list[i]
                val y = list[j]
                val z = list[k]
                val a = Integer.signum(x.compareTo(y))
                val b = Integer.signum(y.compareTo(z))
                val c = Integer.signum(x.compareTo(z))

                if (a != -Integer.signum(y.compareTo(x))) {
                    fail("x.compareTo(y) != -y.compareTo(x) for x=$x and y=$y")
                }
                if (b != -Integer.signum(z.compareTo(y))) {
                    fail("x.compareTo(y) != -y.compareTo(z) for x=$y and y=$z")
                }
                if (a != 0 && b != 0) {
                    if (a == b && c != a) {
                        if (!(x > y && y > z && x > z)) {
                            fail("Not true that when x > y and y > z, then x > z for x = $x, y = $y, z = $z\n")
                        } else {
                            assertFalse(x < y && y < x && x < z)
                            fail("Not true that when x < y and y < z, then x < z for x = $x, y = $y, z = $z\n")
                        }
                    }
                } else if (a == b) {
                    if (c != 0) {
                        fail("\nEquality not transitive: Not true that x == y and y == z, then x = y for x = $x, y = $y, z = $z\n")
                    }
                } else if (a != 0) {
                    if (c != a) {
                        if (!(x < y && y == z && x < z)) {
                            fail("Not true that when x < y and y == z, then x < z for x = $x, y = $y, z = $z\n")
                        } else {
                            assertFalse(x > y && y == z && x > z)
                            fail("Not true that when x > y and y == z, then x > z for x = $x, y = $y, z = $z\n")
                        }
                    }
                } else if (b != 0) {
                    if (c != b) {
                        fail("\nEither\n  x == y && y < z => x < z\nor\n  x == y && y > z => x > z\nis not true for x = $x, y = $y, z = $z")
                        if (!(x == y && y < z && x < z)) {
                            fail("Not true that when x == y and y < z, then x < z for x = $x, y = $y, z = $z\n")
                        } else {
                            assertFalse(x == y && y > z && x > z)
                            fail("Not true that when x == y and y > z, then x > z for x = $x, y = $y, z = $z\n")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Checks transitivity for a given [comparator] over the given [list].
 */
fun <T> checkTransitiveComparator(list: List<T>, comparator: Comparator<T>) {
    class Wrapper(val item: T) : Comparable<Wrapper> {
        override fun toString(): String = item.toString()
        override fun compareTo(other: Wrapper): Int {
            return comparator.compare(this.item, other.item)
        }
    }
    checkTransitiveComparator(list.map { Wrapper(it) })
}

private class JavaTestContext(
    driver: LintDriver,
    project: Project,
    private val mJavaSource: String,
    file: File
) : JavaContext(driver, project, null, file) {

    override fun getContents(): String {
        return mJavaSource
    }
}

private class XmlTestContext(
    driver: LintDriver,
    project: Project,
    private val xmlSource: String,
    file: File,
    type: ResourceFolderType,
    document: Document
) : XmlContext(driver, project, null, file, type, xmlSource, document) {
    override fun getContents(): String {
        return xmlSource
    }
}

fun createXmlContext(@Language("XML") xml: String, relativePath: File): XmlContext {
    val dir = File(System.getProperty("java.io.tmpdir"))
    val fullPath = File(dir, relativePath.path)
    val project = createTestProjectForFiles(dir, mutableMapOf(fullPath to xml))
    val client = project.client

    val request = LintRequest(client, listOf(fullPath))
    val driver = LintDriver(TestIssueRegistry(), LintCliClient(LintClient.CLIENT_UNIT_TESTS), request)
    driver.scope = Scope.JAVA_FILE_SCOPE
    val folderType = ResourceFolderType.getFolderType(relativePath.parentFile.name)
    val document = client.getXmlDocument(fullPath, xml)
    return XmlTestContext(driver, project, xml, fullPath, folderType!!, document!!)
}

private fun createTestProjectForFiles(
    dir: File,
    sourcesMap: Map<File, String>,
    libs: List<File> = emptyList(),
    library: Boolean = false,
    android: Boolean = true,
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    sdkHome: File? = null
): Project {
    sourcesMap.forEach { (fullPath, source) ->
        fullPath.parentFile.mkdirs()
        fullPath.writeText(source)
    }

    val client = object : LintCliClient(CLIENT_UNIT_TESTS) {
        override fun readFile(file: File): CharSequence {
            return if (file in sourcesMap) {
                sourcesMap[file] as CharSequence
            } else super.readFile(file)
        }

        override fun getCompileTarget(project: Project): IAndroidTarget? {
            val targets = getTargets()
            for (i in targets.indices.reversed()) {
                val target = targets[i]
                if (target.isPlatform) {
                    return target
                }
            }

            return super.getCompileTarget(project)
        }

        override fun getSdkHome(): File? {
            return sdkHome
        }

        override fun getJavaLanguageLevel(project: Project): LanguageLevel {
            if (javaLanguageLevel != null) {
                return javaLanguageLevel
            }
            return super.getJavaLanguageLevel(project)
        }

        override fun getKotlinLanguageLevel(project: Project): LanguageVersionSettings {
            if (kotlinLanguageLevel != null) {
                return kotlinLanguageLevel
            }
            return super.getKotlinLanguageLevel(project)
        }

        override fun getJavaLibraries(
            project: Project,
            includeProvided: Boolean
        ): List<File> {
            return libs + findKotlinStdlibPath().map { File(it) }
        }

        override fun getJavaSourceFolders(project: Project): List<File> {
            // Include the top-level dir as a source root, so Java references are resolved.
            return super.getJavaSourceFolders(project) + dir
        }

        override fun createProject(dir: File, referenceDir: File): Project {
            val clone = super.createProject(dir, referenceDir)
            val p =
                object : TestLintClient.TestProject(this, dir, referenceDir, null, null) {
                    override fun isLibrary(): Boolean {
                        return library
                    }

                    override fun isAndroidProject(): Boolean {
                        return android
                    }
                }
            clone.buildTargetHash?.let { p.buildTargetHash = it }
            clone.ideaProject?.let { p.ideaProject = it }
            return p
        }
    }

    val project = client.getProject(dir, dir)
    client.initializeProjects(listOf(project))
    return project
}

fun parseFirst(
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    library: Boolean = false,
    android: Boolean = true,
    temporaryFolder: TemporaryFolder,
    vararg testFiles: TestFile = emptyArray(),
    sdkHome: File? = null
): Pair<JavaContext, Disposable> {
    val result = parse(javaLanguageLevel, kotlinLanguageLevel, library, android, sdkHome, temporaryFolder, *testFiles)
    return Pair(result.first.first(), result.second)
}

fun parse(
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    library: Boolean = false,
    android: Boolean = true,
    sdkHome: File? = null,
    temporaryFolder: TemporaryFolder,
    vararg testFiles: TestFile
): Pair<List<JavaContext>, Disposable> {
    val dir = temporaryFolder.newFolder("src")

    val libs: List<File> = if (testFiles.size > 1) {
        val projects = TestLintTask().files(*testFiles).createProjects(dir)
        testFiles.filter { it.targetRelativePath.endsWith(SdkConstants.DOT_JAR) }
            .map { File(projects[0], it.targetRelativePath) }
    } else {
        emptyList()
    }

    val sources = testFiles
        .filter { !it.targetRelativePath.endsWith(SdkConstants.DOT_JAR) }
        .associate { Pair(File(dir, it.targetRelativePath), it.contents) }

    val project =
        createTestProjectForFiles(dir, sources, libs, library, android, javaLanguageLevel, kotlinLanguageLevel, sdkHome)
    val client = project.client as LintCliClient
    val request = LintRequest(client, sources.keys.toList())
    val driver = LintDriver(TestIssueRegistry(), LintCliClient(LintClient.CLIENT_UNIT_TESTS), request)
    driver.scope = EnumSet.of(Scope.ALL_JAVA_FILES)

    val uastParser = client.getUastParser(project)
    TestCase.assertNotNull(uastParser)
    val contexts = sources.map { (fullPath, source) ->
        val context = JavaTestContext(driver, project, source, fullPath)
        context.uastParser = uastParser
        context
    }
    uastParser.prepare(contexts)
    contexts.forEach { context ->
        val uFile = uastParser.parse(context)
        context.uastFile = uFile
        assert(uFile != null)
        context.setJavaFile(uFile!!.sourcePsi)
    }

    val disposable = Disposable {
        client.disposeProjects(listOf(project))
    }
    return Pair(contexts, disposable)
}
