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

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.currentPlatform
import com.android.resources.ResourceFolderType
import com.android.sdklib.IAndroidTarget
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Document
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import kotlin.math.max
import kotlin.math.min

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
    private val javaSource: String,
    file: File
) : JavaContext(driver, project, null, file) {

    override fun getContents(): String {
        return javaSource
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
    val includeKotlinStdlib = dir.walkBottomUp().any { it.path.endsWith(DOT_KT) }
    val client = object : LintCliClient(CLIENT_UNIT_TESTS) {
        override fun readFile(file: File): CharSequence {
            return sourcesMap[file] ?: super.readFile(file)
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
            val kotlinStdlib = if (includeKotlinStdlib) findKotlinStdlibPath() else emptyList()
            return super.getJavaLibraries(project, includeProvided) + libs + kotlinStdlib
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
    sdkHome: File? = null,
    vararg testFiles: TestFile = emptyArray(),
): Pair<JavaContext, Disposable> {
    val (contexts, disposable) = parse(javaLanguageLevel, kotlinLanguageLevel, library, sdkHome, android, temporaryFolder, *testFiles)
    val first = contexts.firstOrNull { it.file.path.portablePath().endsWith(testFiles[0].targetRelativePath) } ?: contexts.first()
    return Pair(first, disposable)
}

fun parse(
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    library: Boolean = false,
    sdkHome: File? = null,
    android: Boolean = sdkHome != null,
    temporaryFolder: TemporaryFolder,
    vararg testFiles: TestFile
): Pair<List<JavaContext>, Disposable> {
    val dir = temporaryFolder.newFolder()
    val projects = TestLintTask().files(*testFiles).createProjects(dir)
    return parse(projects[0], javaLanguageLevel, kotlinLanguageLevel, library, sdkHome, android)
}

fun parse(
    dir: File,
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    library: Boolean = false,
    sdkHome: File? = null,
    android: Boolean = sdkHome != null,
    sourceOverride: Map<File, String> = emptyMap(),
    extraLibs: List<File> = emptyList()
): Pair<List<JavaContext>, Disposable> {
    val project =
        createTestProjectForFiles(dir, sourceOverride, extraLibs, library, android, javaLanguageLevel, kotlinLanguageLevel, sdkHome)
    val client = project.client as LintCliClient
    val request = LintRequest(client, sourceOverride.keys.toList())
    val driver = LintDriver(TestIssueRegistry(), LintCliClient(LintClient.CLIENT_UNIT_TESTS), request)
    driver.scope = EnumSet.of(Scope.ALL_JAVA_FILES)

    val uastParser = client.getUastParser(project)
    TestCase.assertNotNull(uastParser)
    val contexts = dir.walk().mapNotNull { file ->
        if (file.path.endsWith(DOT_KT) || file.path.endsWith(DOT_JAVA)) {
            val context: JavaContext = JavaTestContext(driver, project, sourceOverride[file] ?: file.readText(), file)
            context.uastParser = uastParser
            context
        } else {
            null
        }
    }.toList()
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

fun List<TestFile>.use(
    temporaryFolder: TemporaryFolder? = null,
    sdkHome: File? = null,
    block: (JavaContext) -> Unit
) {
    var dir: Path? = null
    val folder = temporaryFolder ?: run {
        dir = Files.createTempDirectory("lint-test")
        TemporaryFolder(dir?.toFile()).apply { create() }
    }
    val (context, disposable) = parseFirst(
        null, null, false, sdkHome != null, folder, sdkHome, *this.toTypedArray()
    )
    try {
        block(context)
    } finally {
        dir?.toFile()?.walkBottomUp()?.forEach { it.delete() }
        Disposer.dispose(disposable)
    }
}

/**
 * Converts a String produced on Windows (possibly containing CRLF
 * line separators, containing paths using Windows file and path
 * separators and so on) to the corresponding Unix style string. Unless
 * [indiscriminate] is set to true, this method will attempt to be
 * smart in a few cases such that it understands from context whether
 * a semicolon (for example) is likely to be used in an XML snippet as
 * an entity terminator instead of a path separator, whether a \\ is
 * likely to be used in a path as opposed to a string escape, and so on.
 */
@JvmOverloads
fun String.dos2unix(indiscriminate: Boolean = false): String {
    if (this.none { it == '\r' || it == '\\' || it == ';' }) return this

    if (indiscriminate) {
        return this.replace("\r\n", "\n").replace('\\', '/').replace(';', ':')
    }

    val sb = StringBuilder()
    for (i in indices) {
        when (val c = this[i]) {
            '\r' -> continue
            '\\' -> sb.append('/')
            ';' -> if (isLikelyPathSeparator(this, i)) {
                sb.append(':')
            } else {
                sb.append(';')
            }
            else -> sb.append(c)
        }
    }

    return sb.toString()
}

/** Converts a /-separated path into a platform specific path. */
fun String.platformPath(): String {
    return replace('/', File.separatorChar)
}

/** Converts a platform specific path into a unix/portable path. */
fun String.portablePath(): String {
    return replace(File.separatorChar, '/')
}

/**
 * Given the location of a semicolon in a String, guess
 * whether the semicolon represents a path separator, as in
 * "src\main\java;src\main\kotlin", as opposed to something else such
 * as "this is &quot;some text&quot;" or semicolon usage in an error
 * message text.
 */
private fun isLikelyPathSeparator(s: String, index: Int): Boolean {
    if (index == 0) {
        return false
    }
    // Whitespace surrounding the semicolon tends to indicate text and is definitely
    // not a path separator
    if (s[index - 1].isWhitespace()) {
        return false
    }
    if (index < s.length - 1) {
        val next = s[index + 1]
        if (next.isWhitespace() || next == '"' ||
            // background: url(data:image/png:base64,...
            next == 'b' && s.regionMatches(index + 1, "base64", 0, 6)
        ) {
            return false
        }
    }

    // Look to see if it's an XML entity like &lt; or &quot;
    for (j in index - 1 downTo max(0, index - 6)) {
        val c = s[j]
        if (c == '&') {
            return false
        } else if (!c.isLetterOrDigit() && c != '#') { // entities can look like &quot; or &xA; or &#9029;
            break
        }
    }

    return true
}

/**
 * Runs lint on a tree of sources. This will recursively look for Java
 * and Kotlin files (configurable via the [accept] parameter, and
 * optionally filtered out via the [ignore] parameter which omits dot
 * directories and paths mentioning "test" by default), batch them into
 * groups of at most [bucketSize] files, and then run lint on those
 * files (where the lint task which configures the issues to analyze etc
 * is constructed via the [lintFactory] lambda parameter), and finally
 * asserts that the output is as [expected].
 *
 * This is used to search larger project trees for false positives,
 * where you don't have some easy other way to run your lint check on
 * those project trees.
 *
 * For example, to run lint on a new check called `MyDetector` in the
 * directory tree `/my/src` to see what it finds, from `MyDetectorTest`
 * I can use
 *
 * ```
 * runOnSources(
 *    dir = File("/my/src"),
 *    lintFactory = { TestLintTask.lint().allowMissingSdk().issues(MyDetector.ISSUE) },
 * )
 * ```
 *
 * (Consider passing `verbose = true` too to get progress printed along
 * the way if it's a large source tree.)
 */
@Suppress("LintDocExample")
fun runOnSources(
    dir: File,
    lintFactory: () -> TestLintTask,
    expected: String = "",
    accept: (File) -> Boolean = {
        it.path.endsWith(DOT_KT) || it.path.endsWith(DOT_JAVA) &&
            !it.path.endsWith("module-info.java") && !it.endsWith("package-info.java")
    },
    ignore: (File) -> Boolean = {
        val path = it.path.portablePath()
        path.contains("/.") || path.contains("/test")
    },
    bucketSize: Int = 500,
    absolutePaths: Boolean = currentPlatform() != PLATFORM_WINDOWS,
    testModes: List<TestMode> = listOf(TestMode.DEFAULT),
    verbose: Boolean = false
) {
    val seen = HashSet<String>()
    val root = dir.canonicalFile
    val sourceFiles = root.walkTopDown().filter { !ignore(it) && accept(it) }.sortedBy { it.path }.toList()
    val sb = StringBuilder()
    val buckets = sourceFiles.size / bucketSize
    for (i in 0..buckets) {
        val from = i * bucketSize
        val to = min(sourceFiles.size, (i + 1) * bucketSize)
        val files = sourceFiles.subList(from, to).mapNotNull {
            val source = it.readText()
            val path = it.path
            var keep = true
            if (path.endsWith(DOT_KT) || path.endsWith(DOT_JAVA)) {
                try {
                    val className = ClassName(source, path.substring(path.lastIndexOf('.')))
                    if (className.className != null) {
                        val key = className.packageName + className.className
                        if (!seen.add(key)) {
                            keep = false
                        }
                    }
                } catch (e: Throwable) {
                    keep = false
                }
            }
            if (keep) {
                val srcPath = if (absolutePaths)
                    "src/$path"
                else
                    path.removePrefix(root.path).removePrefix(File.separator).portablePath()

                if (srcPath.endsWith(DOT_KT))
                    kotlin(srcPath, source)
                else if (srcPath.endsWith(DOT_JAVA))
                    java(srcPath, source)
                else if (srcPath.endsWith(DOT_XML))
                    xml(srcPath, source)
                else
                    null
            } else {
                null
            }
        }
        if (files.isEmpty()) {
            break
        }

        var result: TestLintResult? = null
        try {
            if (verbose) {
                println(
                    "Analyzing ${files.size} files, first file is ${files[0].targetRelativePath.removePrefix("src/")}, " +
                        "last is ${files[files.size - 1].targetRelativePath.removePrefix("src/")}"
                )
            }
            result = lintFactory()
                .files(*(files.toTypedArray()))
                .testModes(*testModes.toTypedArray())
                .allowCompilationErrors()
                .allowAbsolutePathsInMessages(absolutePaths)
                .run()

            result.expect("", transformer = { report ->
                if (report != "No warnings.") {
                    val cleaned = if (absolutePaths) {
                        report.removePrefix("src").replace("\nsrc", "\n")
                    } else {
                        report.replace(root.path, "").replace(root.path.portablePath(), "")
                    }
                    sb.append(cleaned).append("\n")
                    if (verbose) {
                        println("Partial:\n$cleaned\n")
                    }
                }
                "" // such that we pass and continue
            })
        } catch (ignore: Throwable) {
            // Gracefully handle parsing errors in some batches
            if (verbose) {
                ignore.printStackTrace()
            }
            result?.cleanup()
        }
    }
    val actual = sb.toString().trim()
    assertEquals(expected, actual)
}
