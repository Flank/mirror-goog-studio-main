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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.tasks.LibraryAarJarsTask
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.Zip
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.Locale
import java.util.function.Predicate
import java.util.zip.Deflater

class LibraryAarJarsTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private fun makeFolders(): Map<String, File> {
        return mapOf(
            "local/folder" to temporaryFolder.newFolder("local", "folder"),
            "main/folder" to temporaryFolder.newFolder("main", "folder"),
            "resource/folder" to temporaryFolder.newFolder("resource", "folder"),
            "excludes/folder" to temporaryFolder.newFolder("excludes", "folder"),
            "localOutput" to temporaryFolder.newFolder("localOutput")
        )
    }

    private fun makeFiles(folders: Map<String, File>): Map<String, File> {
        // Add 2 jars to each different folder and output jar
        val jarPaths = listOf(
            "local/empty1.jar",
            "local/empty2.jar",
            "main/empty1.jar",
            "main/empty2.jar",
            "resource/empty1.jar",
            "resource/empty2.jar")

        val excludeJarPaths = listOf(
            "excludes/empty1.jar",
            "excludes/empty2.jar"
        )

        val outputFile = temporaryFolder.newFile("classes.jar")

        val files = mutableMapOf<String, File>()

        for ((iteration, i) in jarPaths.withIndex()) {
            val file = temporaryFolder.newFile(i)
            TestInputsGenerator.writeJarWithEmptyEntries(
                file.toPath(),
                listOf("Class$iteration.class", "class$iteration.txt")
            )

            files[i] = file
        }

        // Add excludes
        for ((iteration, i) in excludeJarPaths.withIndex()) {
            val file = temporaryFolder.newFile(i)
            TestInputsGenerator.writeJarWithEmptyEntries(
                file.toPath(),
                listOf("ExcludeClass$iteration.class", "excludeClass$iteration.txt")
            )
            files[i] = file
        }

        // Add folder inputs
        TestInputsGenerator.dirWithEmptyClasses(
            folders["local/folder"]!!.toPath(),
            setOf("LocalClass1", "LocalClass2")
        )

        TestInputsGenerator.dirWithEmptyClasses(
            folders["main/folder"]!!.toPath(),
            setOf("MainClass1", "MainClass2")
        )

        TestInputsGenerator.dirWithEmptyClasses(
            folders["resource/folder"]!!.toPath(),
            setOf("ResourceClass1", "ResourceClass2")
        )

        TestInputsGenerator.dirWithEmptyClasses(
            folders["excludes/folder"]!!.toPath(),
            setOf("ExcludeClass1", "ExcludeClass2")
        )


        files["classes.jar"] = outputFile

        return files
    }

    @Test
    fun testInputMerge() {
        // Create folders
        val folders = makeFolders()

        // Create jars
        val jars = makeFiles(folders)

        val localJars = mutableSetOf(
            jars["local/empty1.jar"]!!,
            jars["local/empty2.jar"]!!,
            folders["local/folder"]!!)

        val mainJars = mutableSetOf(
            jars["main/empty1.jar"]!!,
            jars["main/empty2.jar"]!!,
            jars["excludes/empty1.jar"]!!,
            jars["excludes/empty2.jar"]!!,
            folders["main/folder"]!!)

        val mainResources = mutableSetOf(
            jars["resource/empty1.jar"]!!,
            jars["resource/empty2.jar"]!!,
            jars["excludes/empty1.jar"]!!,
            jars["excludes/empty2.jar"]!!,
            folders["resource/folder"]!!)

        // Exclude excludes from final jar
        val excludes = Predicate { archivePath: String ->
            !archivePath.toLowerCase(Locale.US).startsWith("exclude")
        }

        LibraryAarJarsTask.mergeInputs(
            localJars,
            folders["localOutput"]!!,
            mainJars,
            mainResources,
            jars["classes.jar"]!!,
            excludes,
            null,
            JarCreatorType.JAR_FLINGER,
            Deflater.BEST_SPEED
        )

        assertThat(Zip(jars["classes.jar"]!!).entries.map(Path::toString)).containsExactly(
            // main classes
            "/Class2.class",
            "/Class3.class",
            // resource classes
            "/class4.txt",
            "/Class4.class",
            "/class5.txt",
            "/Class5.class",
            // folder classes
            "/ResourceClass1.class",
            "/ResourceClass2.class",
            "/MainClass1.class",
            "/MainClass2.class"
        )

        assertThat(folders["localOutput"]!!.listFiles().map(File::getName)).containsExactly(
            // local classes
            "empty1.jar", "otherclasses.jar", "empty2.jar"
        )
    }
}
