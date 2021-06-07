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
@file:Suppress("SpellCheckingInspection") // avoid flagging kotlinc and javac everywhere

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.currentPlatform
import com.android.tools.lint.checks.infrastructure.TestFiles.toBase64gzipJava
import com.android.tools.lint.checks.infrastructure.TestFiles.toBase64gzipKotlin
import com.google.common.base.Joiner
import com.google.common.hash.Hashing
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.BufferedReader
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.Arrays

internal class CompiledSourceFile(
    into: String,
    internal val type: Type,
    /** The test source file for this compiled file. */
    val source: TestFile,
    private val checksum: Long?,
    private val encodedFiles: Array<String>
) : TestFile() {

    init {
        to(into)
    }

    /** The type of test file to create. */
    internal enum class Type {
        BYTECODE_ONLY, SOURCE_AND_BYTECODE, RESOURCE
    }

    val files: List<TestFile >
        get() {
            val files = ArrayList(classFiles)
            if (type == Type.SOURCE_AND_BYTECODE || type == Type.RESOURCE) {
                files.add(source)
            }
            return files
        }

    /**
     * Computes a hash of the source file and the binary contents
     * (SHA256 with source as UTF8 plus bytecode in order)
     */
    @Suppress("UnstableApiUsage")
    private fun computeCheckSum(source: String, binaries: List<ByteArray>): Int {
        val hashFunction = Hashing.sha256()
        val hasher = hashFunction.newHasher()

        hasher.putString(source, Charsets.UTF_8)
        for (bytes in binaries) {
            hasher.putBytes(bytes)
        }
        val hashCode = hasher.hash()
        return hashCode.asInt()
    }

    @Throws(IOException::class)
    override fun createFile(targetDir: File): File {
        val files = files
        val array = files.toTypedArray()
        return if (targetRelativePath.endsWith(DOT_JAR)) {
            JarTestFile(targetRelativePath).files(*array).createFile(targetDir)
        } else {
            for (testFile in array) {
                testFile.createFile(targetDir)
            }

            // This isn't right; there's more than one java.io.File created, but the
            // caller of this method ignores the return value.
            File(targetRelativePath)
        }
    }

    /**
     * Returns true if this compiled file is not complete (e.g. test
     * author is running test without knowing the binary contents yet)
     */
    fun isMissingClasses(): Boolean {
        if (type == Type.RESOURCE) {
            return false
        }
        return encodedFiles.isEmpty() || encodedFiles.size == 1 && encodedFiles[0].isEmpty()
    }

    /**
     * If the file does not already provide a list of encoded files
     * (e.g. test is being written or updated), go and compute it, fail
     * the test and emit the right source to drop into the test. The
     * test infrastructure will invoke this once the rest of the project
     * has been constructed for all the compiled source files that are
     * missing class definitions.
     *
     * Returns true if the file had to be compiled.
     */
    fun compile(projectDir: File, externalJars: List<String>): Boolean {
        if (!isMissingClasses()) {
            // Already done
            return false
        }
        val target = source.targetRelativePath ?: return false
        val (kotlinc, javac: String) = findCompilers(target)

        val classpath = getClassPath(projectDir, externalJars)
        val classesDir = try {
            Files.createTempDirectory("classes").toFile()
        } catch (e: IOException) {
            error("Couldn't create temporary folder for classes")
        }

        val (javaFiles, kotlinFiles) = findSourceFiles(projectDir)
        if (javaFiles.isEmpty() && kotlinFiles.isEmpty()) {
            // This is a resource file
            return false
        }

        // First build with kotlinc; needs access to both Kotlin and Java files
        if (kotlinFiles.isNotEmpty()) {
            val args = ArrayList<String>()
            args.add(kotlinc)
            args.add("-classpath")
            args.add(classpath)
            args.add("-d")
            args.add(classesDir.path)
            kotlinFiles.forEach { args.add(it.path) }
            javaFiles.forEach { args.add(it.path) }
            executeProcess(args)
        }

        // Then build with javac; only pass the Java files (but the -d classes directory
        // contains .class files from the .kt files such that it can resolve references
        // to Kotlin code)
        if (javaFiles.isNotEmpty()) {
            val args = ArrayList<String>()
            args.add(javac)
            args.add("-classpath")
            args.add(classesDir.path + File.pathSeparator + classpath)
            args.add("-d")
            args.add(classesDir.path)
            javaFiles.forEach { args.add(it.path) }
            executeProcess(args)
        }

        val (kotlinTestFile, javaTestFile) = describeTestFiles(classesDir, target)
        fail(
            "Update the test source declaration for ${source.targetRelativePath} with this list " +
                "of encodings:\n\n$kotlinTestFile\n\n$javaTestFile"
        )

        return true
    }

    private fun findSourceFiles(projectDir: File): Pair<List<File>, List<File>> {
        val javaFilter = FilenameFilter { _: File?, name: String -> name.endsWith(DOT_JAVA) }
        val kotlinFilter = FilenameFilter { _: File?, name: String -> name.endsWith(DOT_KT) }

        // Find source files.; we'll need to distinguish between Java and Kotlin
        // since kotlinc must be passed and javac only the java ones
        val src = File(projectDir, "src")
        val javaFiles = findFiles(src, javaFilter)
        val kotlinFiles = findFiles(src, kotlinFilter)
        return Pair(javaFiles, kotlinFiles)
    }

    private fun getClassPath(projectDir: File, extraJars: List<String>): String {
        val jars = findJars(projectDir)
        return Joiner.on(File.pathSeparator).join(jars + extraJars)
    }

    /** Find jar files in the given project directory. */
    private fun findJars(projectDir: File): ArrayList<File> {
        val androidJar = findAndroidJar()
        val jarFilter = FilenameFilter { _: File?, name: String -> name.endsWith(DOT_JAR) }
        val jars = ArrayList<File>()
        jars.add(androidJar)
        jars.addAll(findFiles(File(projectDir, "libs"), jarFilter))
        jars.addAll(findFiles(File(projectDir, "bin/classes"), jarFilter))
        return jars
    }

    /**
     * Locates the android.jar file to compile with as the boot class
     * path.
     */
    private fun findAndroidJar(): File {
        val sdkHome = System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: error(
                "Couldn't find an Android SDK environment to compile with; " +
                    "set \$ANDROID_SDK_ROOT"
            )
        if (!File(sdkHome).isDirectory) {
            fail("$sdkHome is not a directory")
        }

        val platforms = File(sdkHome, "platforms").listFiles { _: File, name: String ->
            name.startsWith("android-") && name.indexOf('.') == -1
        } ?: error("Couldn't find platforms")
        Arrays.sort(platforms) { o1: File, o2: File ->
            val n1 = o1.name
            val n2 = o2.name
            val delta = n1.length - n2.length
            if (delta != 0) {
                return@sort delta
            }
            n1.compareTo(n2)
        }
        val platform = platforms[platforms.size - 1]
        val androidJar = File(platform, "android.jar")
        assertTrue(androidJar.isFile)
        return androidJar
    }

    private fun findOnPath(target: String): String? {
        val path = System.getenv("PATH")?.split(File.pathSeparator) ?: return null
        for (binDir in path) {
            val file = File(binDir + File.separator + target)
            if (file.isFile) { // maybe file.canExecute() too but not sure how .bat files behave
                return file.path
            }
        }
        return null
    }

    private fun findCompilers(target: String): Pair<String, String> {
        val isWindows = currentPlatform() == PLATFORM_WINDOWS
        val kotlinc = System.getenv("LINT_TEST_KOTLINC")
            ?: findOnPath("kotlinc" + if (isWindows) ".bat" else "")
            ?: error(
                "Couldn't find kotlinc to update test file $target " +
                    "with. Point to it with \$LINT_TEST_KOTLINC"
            )

        if (!File(kotlinc).isFile) {
            fail("$kotlinc is not a file")
        }
        if (!File(kotlinc).canExecute()) {
            fail("$kotlinc is not executable")
        }
        val javac: String = System.getenv("LINT_TEST_JAVAC")
            ?: run {
                val javaHome = System.getenv("JAVA_HOME")
                if (javaHome != null) {
                    "$javaHome/bin/javac"
                } else {
                    error(
                        "Couldn't find javac to update test file $target " +
                            "with. Point to it with \$LINT_TEST_JAVAC"
                    )
                }
            }
                .let {
                    if (isWindows && !it.endsWith(".bat")) {
                        "$it.bat"
                    } else {
                        it
                    }
                }
        return Pair(kotlinc, javac)
    }

    /**
     * Creates test sources in Kotlin and Java for what this compiled
     * file should be declared as.
     */
    private fun describeTestFiles(classesDir: File, target: String): Pair<String, String> {
        val kotlin = StringBuilder("Kotlin:\n")
        val java = StringBuilder("Java:\n")

        var indent = 4
        fun StringBuilder.indent(length: Int): StringBuilder {
            for (i in 0 until length) {
                append("    ")
            }
            return this
        }

        // Create the test file declaration, e.g. bytecode("target", ...
        val declaration = StringBuilder()
        declaration.indent(indent)
        declaration.append(if (type == Type.SOURCE_AND_BYTECODE) "compiled" else "bytecode")
        declaration.append("(\n")
        indent++
        declaration.indent(indent)
        declaration.append('"').append(targetRelativePath).append("\",\n")
        declaration.indent(indent)
        if (source.targetRelativePath.endsWith(DOT_JAVA)) {
            declaration.append("java")
        } else {
            declaration.append("kotlin")
        }
        declaration.append("(")
        indent++
        kotlin.append(declaration.toString())
        java.append(declaration.toString())

        // Create the test file source declaration, e.g. kotlin("""source""").
        // This requires some escapes - $ in Kotlin, \ and " in Java
        val lines = source.contents.replace('$', '＄').split("\n")
        kotlin.indent(indent).append("\n").indent(indent).append("\"\"\"\n")
        java.indent(indent).append("\n").indent(indent).append("\"\"\n")

        for (line in lines) {
            if (line.isNotBlank()) {
                kotlin.indent(indent)
                java.indent(indent)
            }
            kotlin.append(line).append('\n')
            java.append("+ \"")
            java.append(
                line
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
            )
            java.append("\\n\"\n")
        }

        kotlin.indent(indent).append("\"\"\"\n")

        indent--
        kotlin.indent(indent).append(").indented(),\n")
        java.indent(indent).append(").indented(),\n")

        val binaryFiles = findFiles(classesDir) { _: File, name: String ->
            name.endsWith(DOT_CLASS) ||
                target.endsWith(DOT_KT) && name.endsWith(".kotlin_module")
        }

        val checksum = computeCheckSum(source.contents, binaryFiles.map { it.readBytes() }.toList())
        val checksumString = "0x" + Integer.toHexString(checksum)
        kotlin.indent(indent).append(checksumString).append(",\n")
        java.indent(indent).append(checksumString).append(",\n")

        var first = true
        for (binaryFile in binaryFiles) {
            val bytes = binaryFile.readBytes()
            if (!isClassForSource(binaryFile, bytes, target)) {
                continue
            }
            if (first) {
                first = false
            } else {
                java.append(",\n")
                kotlin.append(",\n")
            }

            val path = binaryFile.path.substring(classesDir.path.length + 1)
            java.indent(indent).append("\"").append(path).append(":\" +\n")
            java.append(toBase64gzipJava(bytes, indent * 4, true, false))

            indent--
            kotlin.indent(indent).append("\"\"\"\n")
            kotlin.indent(indent).append(path.replace('$', '＄')).append(":\n")
            kotlin.append(toBase64gzipKotlin(bytes, indent * 4, true, false))
            kotlin.indent(indent).append("\"\"\"")
            indent++
        }
        indent--
        java.append("\n").indent(indent).append(")\n")
        kotlin.append("\n").indent(indent).append(")\n")
        return Pair(kotlin.toString(), java.toString())
    }

    /**
     * Returns true if the given [file] with the given [bytes] content
     * looks like it was compiled from the source file pointed to by
     * relative path [target].
     */
    private fun isClassForSource(
        file: File,
        bytes: ByteArray,
        target: String
    ): Boolean {
        if (file.path.endsWith(DOT_CLASS)) {
            // The classes folder will not only contain class files from our source test
            // file, it will contain class files from the rest of the project too. So we'll
            // go through all the class files, load their bytecode, and see if the source
            // file attribute corresponds to our source file.
            val reader = ClassReader(bytes)
            val classNode = ClassNode(Opcodes.ASM7)
            reader.accept(classNode, 0)
            val className = classNode.name
            val pkgEnd = className.lastIndexOf('/')
            val sourcePath =
                if (pkgEnd != -1) {
                    val pkg = className.substring(0, pkgEnd)
                    pkg + "/" + classNode.sourceFile
                } else {
                    classNode.sourceFile
                }
            if (!target.endsWith(sourcePath)) {
                return false
            }
        }
        return true
    }

    /**
     * Returns the list of binary test class files currently included in
     * this compiled source file.
     */
    val classFiles: List<TestFile>
        get() {
            val classFiles = ArrayList<TestFile>()
            for (encoded in encodedFiles) {
                val index = encoded.indexOf(':')
                assertTrue(
                    "Expected encoded binary file to start with a colon " +
                        "separated filename",
                    index != -1
                )
                val path = encoded.substring(0, index).replace('＄', '$').trim()
                val bytes = encoded.substring(index + 1).trim()
                val producer = TestFiles.getByteProducerForBase64gzip(bytes)
                val target =
                    if (targetRelativePath.endsWith(DOT_JAR)) path else "$targetRelativePath/$path"
                val classFile = BinaryTestFile(target, producer)
                classFiles.add(classFile)
            }

            if (checksum != null) {
                val actualChecksum = computeCheckSum(
                    source.contents,
                    classFiles.map {
                        (it as BinaryTestFile).binaryContents
                    }.toList()
                )
                // We only create integer checksums to keep the fingerprints short
                if (checksum.toInt() != actualChecksum) {
                    fail(
                        "The checksum does not match for ${source.targetRelativePath};\n" +
                            "expected " +
                            "0x${Integer.toHexString(checksum.toInt())} but was " +
                            "0x${Integer.toHexString(actualChecksum)}.\n" +
                            "Has the source file been changed without updating the binaries?\n" +
                            "Don't just update the checksum -- delete the binary file arguments and " +
                            "re-run the test first!"
                    )
                }
            }

            return classFiles
        }

    companion object {
        /**
         * Creates all the source and class files for the given (in
         * [compiled]) list of compiled source files, into [targetDir].
         */
        @Throws(IOException::class)
        fun createFiles(
            targetDir: File,
            compiled: List<CompiledSourceFile>
        ) {
            val paths = HashSet<String>()
            for (testFile in compiled) {
                for (encodedFile in testFile.encodedFiles) {
                    val end = encodedFile.indexOf(':')
                    if (end != -1) {
                        val name = encodedFile.substring(0, end)
                        if (!paths.add(name)) {
                            if (name.endsWith(".kotlin_module")) {
                                // Ok redundancy
                                continue
                            }
                            fail("Path $name is defined from more than one compiled test file")
                        }
                    }
                }
            }

            // Targets are allowed to overlap (foo.jar specified repeatedly);
            // this shouldn't wipe out the file each time; it should accumulate.
            val targetMap: MutableMap<String, MutableList<CompiledSourceFile>> = HashMap()
            for (testFile in compiled) {
                val list = targetMap[testFile.targetRelativePath]
                    ?: ArrayList<CompiledSourceFile>()
                        .also { targetMap[testFile.targetRelativePath] = it }
                list.add(testFile)
            }
            for ((target, files) in targetMap) {
                if (target.endsWith(DOT_JAR) && files.size > 1) {
                    // Combine multiple class files into a single jar
                    val allFiles = ArrayList<TestFile>()
                    for (file in files) {
                        allFiles.addAll(file.files)
                    }
                    val array = allFiles.toTypedArray()
                    JarTestFile(target).files(*array).createFile(targetDir)
                } else {
                    for (file in files) {
                        file.createFile(targetDir)
                    }
                }
            }
        }

        private fun executeProcess(args: List<String>) {
            try {
                val process = Runtime.getRuntime().exec(args.toTypedArray())
                val input = BufferedReader(InputStreamReader(process.inputStream))
                val error = BufferedReader(InputStreamReader(process.errorStream))
                val exitVal = process.waitFor()
                if (exitVal != 0) {
                    val sb = StringBuilder()
                    sb.append("Failed to compile test sources\n")
                    sb.append("Command args:\n")
                    for (arg in args) {
                        sb.append("  ").append(arg).append("\n")
                    }
                    sb.append("Standard output:\n")
                    var line: String?
                    while (input.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    sb.append("Error output:\n")
                    while (error.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    input.close()
                    error.close()
                    fail(sb.toString())
                }
            } catch (t: Throwable) {
                val sb = StringBuilder()
                for (arg in args) {
                    sb.append("  ").append(arg).append("\n")
                }
                t.printStackTrace()
                fail("Could not run test compilation:\n$sb")
            }
        }

        private fun findFiles(root: File, filter: FilenameFilter): List<File> {
            val files = ArrayList<File>()
            if (root.isDirectory) {
                addFiles(files, root, filter)
            }
            return files
        }

        private fun addFiles(into: MutableList<File>, root: File, filter: FilenameFilter) {
            if (!root.exists()) {
                return
            }
            if (root.isDirectory) {
                val files = root.listFiles() ?: return
                for (file in files) {
                    addFiles(into, file, filter)
                }
            } else if (filter.accept(root.parentFile, root.name)) {
                into.add(root)
            }
        }
    }
}
