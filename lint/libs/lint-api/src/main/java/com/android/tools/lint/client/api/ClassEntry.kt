/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.lint.client.api

import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_SRCJAR
import com.google.common.collect.Maps
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.math.min

/**
 * A class, present either as a .class file on disk, or inside a .jar
 * file.
 */
class ClassEntry(
    val file: File,
    val jarFile: File?,
    val binDir: File,
    val bytes: ByteArray
) : Comparable<ClassEntry> {
    fun path(): String {
        return if (jarFile != null) {
            jarFile.path + ':' + file.path
        } else {
            file.path
        }
    }

    override fun compareTo(other: ClassEntry): Int {
        val p1 = file.path
        val p2 = other.file.path
        val m1 = p1.length
        val m2 = p2.length
        if (m1 == m2 && p1 == p2) {
            return 0
        }
        val m = min(m1, m2)
        for (i in 0 until m) {
            val c1 = p1[i]
            val c2 = p2[i]
            if (c1 != c2) {
                return when {
                    c1 == '.' -> -1
                    c2 == '.' -> 1
                    else -> c1 - c2
                }
            }
        }
        return if (m == m1) -1 else 1
    }

    override fun toString(): String {
        return file.path
    }

    fun visit(client: LintClient, flags: Int = 0): ClassNode? {
        return visit(client, jarFile ?: file, file.path, bytes, ClassNode(), flags) as? ClassNode
    }

    fun visit(client: LintClient, visitor: ClassVisitor, flags: Int = 0): ClassVisitor? {
        return visit(client, jarFile ?: file, file.path, bytes, visitor, flags)
    }

    /**
     * Visitor skimming classes and initializing a map of super classes
     */
    private class SuperclassVisitor constructor(private val map: MutableMap<String, String>) : ClassVisitor(ASM9) {
        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            // Record super class in the map (but don't waste space on java.lang.Object)
            if (superName != null && "java/lang/Object" != superName) {
                map[name] = superName
            }
        }
    }

    companion object {
        /**
         * Creates a list of class entries from the given class path.
         *
         * @param client the client to report errors to and to use to
         *     read files
         * @param classPath the class path (directories and jar files)
         *     to scan
         * @return the list of class entries, never null.
         */
        fun fromClassPath(
            client: LintClient,
            classPath: List<File>
        ): List<ClassEntry> {
            return if (classPath.isNotEmpty()) {
                val libraryEntries: MutableList<ClassEntry> = ArrayList(64)
                addEntries(client, libraryEntries, classPath)
                libraryEntries.sort()
                libraryEntries
            } else {
                emptyList()
            }
        }

        /**
         * Creates a list of class entries from the given class path and
         * specific set of files within it.
         *
         * @param client the client to report errors to and to use to
         *     read files
         * @param classFiles the specific set of class files to look for
         * @param classFolders the list of class folders to look in (to
         *     determine the package root)
         * @return the list of class entries, never null.
         */
        fun fromClassFiles(
            client: LintClient,
            classFiles: List<File>,
            classFolders: List<File>
        ): List<ClassEntry> {
            val entries: MutableList<ClassEntry> = ArrayList(classFiles.size)
            if (classFolders.isNotEmpty()) {
                for (file in classFiles) {
                    val path = file.path
                    if (file.isFile && path.endsWith(DOT_CLASS)) {
                        try {
                            val bytes = client.readBytes(file)
                            for (dir in classFolders) {
                                if (path.startsWith(dir.path)) {
                                    entries.add(ClassEntry(file, null, dir, bytes))
                                    break
                                }
                            }
                        } catch (e: IOException) {
                            client.log(e, null)
                        }
                    }
                }
                if (entries.isNotEmpty()) {
                    entries.sort()
                }
            }
            return entries
        }

        /**
         * Given a classpath, add all the class files found within the
         * directories and inside jar files
         */
        private fun addEntries(
            client: LintClient,
            entries: MutableList<ClassEntry>,
            classPath: List<File>
        ) {
            for (classPathEntry in classPath) {
                val name = classPathEntry.name
                if (name.endsWith(DOT_JAR)) {
                    if (!classPathEntry.exists()) {
                        continue
                    }
                    try {
                        ZipFile(classPathEntry).use { jar ->
                            val enumeration = jar.entries()
                            while (enumeration.hasMoreElements()) {
                                val entry = enumeration.nextElement()
                                if (entry.name.endsWith(DOT_CLASS)) {
                                    try {
                                        jar.getInputStream(entry).use { stream ->
                                            val bytes = stream.readBytes()
                                            val file = File(entry.name)
                                            entries.add(ClassEntry(file, classPathEntry, classPathEntry, bytes))
                                        }
                                    } catch (e: Throwable) {
                                        client.log(e, null)
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        client.log(e, "Could not read jar file contents from %1\$s", classPathEntry)
                    }
                } else if (classPathEntry.isDirectory) {
                    val classFiles: MutableList<File> = ArrayList()
                    addClassFiles(classPathEntry, classFiles)
                    for (file in classFiles) {
                        try {
                            val bytes = client.readBytes(file)
                            entries.add(ClassEntry(file, null, classPathEntry, bytes))
                        } catch (e: IOException) {
                            client.log(e, null)
                        }
                    }
                } else if (!name.endsWith(DOT_SRCJAR)) {
                    client.log(null, "Ignoring class path entry %1\$s", classPathEntry)
                }
            }
        }

        /**
         * Adds in all the .class files found recursively in the given
         * directory
         */
        private fun addClassFiles(dir: File, classFiles: MutableList<File>) {
            // Process the resource folder
            val files = dir.listFiles() ?: return
            if (files.isNotEmpty()) {
                for (file in files) {
                    if (file.isFile && file.name.endsWith(DOT_CLASS)) {
                        classFiles.add(file)
                    } else if (file.isDirectory) {
                        // Recurse
                        addClassFiles(file, classFiles)
                    }
                }
            }
        }

        /**
         * Creates a super class map (from class to its super class) for
         * the given set of entries
         *
         * @param client the client to report errors to and to use to
         *     access files
         * @param libraryEntries the set of library entries to consult
         * @param classEntries the set of class entries to consult
         * @return a map from name to super class internal names
         */
        fun createSuperClassMap(
            client: LintClient,
            libraryEntries: List<ClassEntry>,
            classEntries: List<ClassEntry>
        ): Map<String, String> {
            val size = libraryEntries.size + classEntries.size
            val map: MutableMap<String, String> = Maps.newHashMapWithExpectedSize(size)
            val visitor = SuperclassVisitor(map)
            addSuperClasses(client, visitor, libraryEntries)
            addSuperClasses(client, visitor, classEntries)
            return map
        }

        /**
         * Creates a super class map (from class to its super class) for
         * the given set of entries
         *
         * @param client the client to report errors to and to use to
         *     access files
         * @param entries the set of library entries to consult
         * @return a map from name to super class internal names
         */
        fun createSuperClassMap(
            client: LintClient,
            entries: List<ClassEntry>
        ): Map<String, String> {
            val map: MutableMap<String, String> = Maps.newHashMapWithExpectedSize(entries.size)
            val visitor = SuperclassVisitor(map)
            addSuperClasses(client, visitor, entries)
            return map
        }

        /**
         * Adds in all the super classes found for the given class
         * entries into the given map
         */
        private fun addSuperClasses(
            client: LintClient,
            visitor: SuperclassVisitor,
            entries: List<ClassEntry>
        ) {
            val flags = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
            for (entry in entries) {
                entry.visit(client, visitor, flags)
            }
        }

        /**
         * Visits the given [bytes] array with the given [visitor]
         * using the specified ASM [flags], and if there's a problem
         * reports the problem to the given [client] referencing the
         * given class file [file] (which could be a jar file, in which
         * case the specific class file inside the jar file is given by
         * [relative]). If the file is inside a `.jar` file, the path
         * should be the relative file within the path (because it will
         * specially be interpreted to see if it's a multi release jar
         * file).
         */
        fun visit(
            client: LintClient,
            file: File,
            relative: String?,
            bytes: ByteArray,
            visitor: ClassVisitor,
            flags: Int = 0
        ): ClassVisitor? {
            return try {
                val reader = ClassReader(bytes)
                reader.accept(visitor, flags)
                visitor
            } catch (t: Throwable) {
                // Unsupported class file format? If it's coming from a multi-release file (see https://openjdk.java.net/jeps/238)
                // we don't want to complain
                val message = t.message ?: t.toString()
                if (relative != null &&
                    t is IllegalArgumentException && message.startsWith("Unsupported class file") &&
                    (relative.startsWith("META-INF/versions/") || relative.startsWith("META-INF\\versions\\"))
                ) {
                    return null
                }

                client.log(
                    null,
                    "Error processing %1\$s: broken class file? (%2\$s)",
                    file.path + if (relative != null) ":$relative" else "",
                    message
                )
                null
            }
        }
    }
}
