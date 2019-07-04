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

package com.android.build.gradle.internal.cxx.process

import java.io.File
import java.util.*
import kotlin.jvm.internal.Intrinsics
import kotlin.reflect.KClass

/**
 * Test utility class that starts Java.executableExtension and calls back into the main function
 * of the given [KClass].
 */
class JvmProcessBuilder(clazz : KClass<*>) {
    private val entryPoint = clazz.java.canonicalName
    private val mainClassPath : String = clazz.classPath()
    private val isJar = mainClassPath.endsWith(".jar")
    private var classPath = mainClassPath

    init {
        // Will always need Kotlin intrinsics
        dependsOn(Intrinsics::class)
    }

    /**
     * Supply an additional class that should be available when the process executes.
     */
    fun dependsOn(clazz : KClass<*>) : JvmProcessBuilder {
        if (!isJar) {
            classPath = clazz.classPath() + os.classPathSeparator + classPath
        }
        return this
    }

    /**
     * The new process should use the current process's class path.
     */
    fun dependsOnCurrentClassPath() : JvmProcessBuilder {
        classPath = System.getProperty("java.class.path") + os.classPathSeparator + classPath
        return this
    }

    /**
     * Return a[ProcessBuilder] that can be used to launch
     */
    fun toProcessBuilder(vararg processArgs: String) : ProcessBuilder {
        val args = ArrayList<String>()
        args.add(javaExe().path)

        args.add("-classpath")
        args.add(classPath)
        args.add(entryPoint)
        args.addAll(processArgs)

        return ProcessBuilder(args)
    }

    companion object {
        private enum class Os(
            val executableExtension: String,
            val classPathSeparator: String) {
            WINDOWS(".exe", ";"),
            LINUX("", ":"),
            DARWIN("", ":")
        }

        private val osName = System.getProperty("os.name")

        private val os = when {
            osName.startsWith("Win") -> Companion.Os.WINDOWS
            osName.startsWith("Mac") -> Companion.Os.DARWIN
            else -> Companion.Os.LINUX
        }

        private fun <T : Any> KClass<T>.classPath(): String {
            val codeSourceLocation = java.protectionDomain.codeSource.location
            if (codeSourceLocation != null) {
                return jarUrlToFile(
                    codeSourceLocation.toString()
                )
            }
            throw RuntimeException("Could not access code source location")
        }

        private fun tryJarUrlToFile(path: String): File? {
            if (path.startsWith("jar:")) {
                val index = path.indexOf("!/")
                if (index == -1) {
                    return null
                }
                return File(path.substring(4, index))
            }
            if (path.startsWith("file:")) {
                return File(path.substring(5))
            }
            return null
        }

        private fun jarUrlToFile(url: String): String {
            val result = tryJarUrlToFile(
                url
            )
                ?: throw RuntimeException("Invalid URL $url")
            val full = result.absolutePath
            require(File(full).exists())
            return full
        }

        private fun javaExeFolder() =
            File(System.getProperties().getProperty("java.home"), "bin")

        private fun javaExeBase() = "java" + os.executableExtension

        private fun javaExe(): File {
            val java = File(
                javaExeFolder(),
                javaExeBase()
            )
            if (!java.isFile) {
                throw RuntimeException("Expected to find java at $java but didn't")
            }
            return java
        }
    }
}
