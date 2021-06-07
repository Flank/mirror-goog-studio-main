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

package com.android.tools.lint.client.api

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.tools.lint.checks.infrastructure.TestFiles.compiled
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.readUrlData
import com.google.common.io.ByteStreams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.text.NumberFormat
import java.util.jar.JarFile

@Suppress("LintDocExample")
class LintJarVerifierTest {
    // (Note that there are also some indirect tests of this class from CustomRuleTest)

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @Test
    fun testDeletedMethod() {
        val projects = lint().files(
            kotlin(
                """
                package com.android.tools.lint.client.api
                import com.android.resources.ResourceType
                import org.jetbrains.uast.UExpression

                class ResourceReference(
                    val node: UExpression,
                    val `package`: String,
                    val type: ResourceType,
                    val name: String
                    // This was added in 4.1 without @JvmOverloads:
                    // , val heuristic: Boolean = false
                )
                """
            ).indented(),
            kotlin(
                """
                // Just a stub
                package com.android.resources
                enum class ResourceType { STRING }
                """
            ).indented(),
            kotlin(
                """
                // Just a stub
                package org.jetbrains.uast
                class UExpression
                """
            ).indented(),
            compiled(
                "lint.jar",
                kotlin(
                    """
                    package test.pkg

                    import com.android.tools.lint.client.api.ResourceReference
                    import com.android.resources.ResourceType
                    import org.jetbrains.uast.UExpression

                    // Not implementing real lint APIs here, just
                    // accessing an old API which was deleted a while
                    // back (added a default parameter in 4.1 and forgot to
                    // add @JvmOverloads at the time)
                    class MyDetector {
                        fun test(expression: UExpression) {
                            val reference = ResourceReference(expression, "test.pkg",
                                ResourceType.STRING, "app_name")
                        }
                    }
                    """
                ).indented(),
                0xecc2e397,
                """
                test/pkg/MyDetector.class:
                H4sIAAAAAAAAAJ1UW08TQRT+ZntluZUiVxURityUhSJeKDHxnpqCpiCJ4cFM
                tyMM3e42O9NG3vgt/gN9wWhiiI/+KOOZUi5BEoxtcubMN+c7Z+abM/vr97cf
                ALJYYejVQmmnVtl2VveeCS1cHYQJMIbULm9wx+P+tvO6tEt4AhGGqAlnGJ8q
                BOG2syt0KeTSV06dU5a3zz/WQqGUDPzc9CZFnQvivh9ormlZOWuBXqt7Xo7B
                FiesJJIMI5VAe9J3dhtVR/pahD73nLyvQ0ohXZWAzdDn7gi30srxhoe8SlsP
                GSanCue3nTuDrJsk27S3DnSg00Y7uhgW3aBKWyuHgSw7Ogg85VB57bieFDTw
                mnSKQgX10BVF8UGEwndFAimGpNFijqRLIs0wdjZN2CKoE+rGXo1YVxji6xvF
                /NpLhkzhckauA/0YaEMfBqker9Xe+3TUJIYpz4r0pX7EULn0Lv5W4F9KX6zb
                NVy30YMRhrbwWA2GpcJ/iJgz7bQjFV1n4YIupOXRy07GEJk62tWEjShuMfQU
                Wu2zKjQvc80pxqo2ItTvzJioMRZhpSYGY6ivWYWg8gLD8uF+v20NWraVOty3
                raRxLNu4g9bg4X7WmmdPYj8/xQl91ZWKDFvz0Ww8FaMxbjJkGdVB5+kp5ir0
                XKJPgzKp1F2QvlirV0si3OAlj5B0IXC5t8lDaeYtMFOs+1pWRd5vSCUJOmnw
                x6cPiN7NelPOF9JwhlqczSPGmUAswCJpzM+ii4shTuM9mnHyzOkHZtNtX9H9
                HT3vZtO9XzCUvnqAG6ufm4T7ZNNEj6OT/l3kd1JHduMB4aNE76fTjuImRVIi
                jGG8WWgASWRo5SH5CRIXCSO/uSWyprYDgwKxmQNMnlaKN8H2ZvaOo4BWTgvL
                TbuEHI15WpuitektRPKYyWM2j9u4czyby1OBeXKxsAWm6EO3uIWEwrjCXYUJ
                hVjTSSpk/gB0T2uoCwUAAA==
                """,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGIWIGYCYgYlBi0GAA1qZtQYAAAA
                """
            )
        ).createProjects(temporaryFolder.newFolder())

        assertEquals(1, projects.size)
        val jar = File(projects[0], "lint.jar")
        assertTrue(jar.isFile)
        val verifier = LintJarVerifier()
        assertFalse(verifier.isCompatible(jar))
        assertEquals(
            "com.android.tools.lint.client.api.ResourceReference: ResourceReference(org.jetbrains.uast.UExpression,java.lang.String,com.android.resources.ResourceType,java.lang.String)",
            verifier.describeFirstIncompatibleReference()
        )
    }

    @Test
    fun checkMavenJars() {
        // This test downloads ALL the AAR files on maven.google.com and verifies
        // any lint.jar files found within!
        //
        // (As a special optimization we walk backwards from the most recent version
        // to earlier versions and if an AAR does not contain a lint.jar we assume
        // that none of the earlier versions do either)

        if (System.getenv("INCLUDE_EXPENSIVE_LINT_TESTS") == null) {
            println("Skipping ${this.javaClass.simpleName}.checkMavenJars: Network intensive")
            return
        }

        // Using java.io.tempdir here instead of TemporaryFolder() because
        // we want to not have the directory cleared between test runs; it takes
        // a long time to download everything the first time, but after that the
        // test can be rerun with everything cached such that it's only measuring
        // the verification overhead
        val cacheDir = File(System.getProperty("java.io.tmpdir"))
        cacheDir.mkdirs()
        val client = object : TestLintClient() {
            @Throws(IOException::class)
            override fun openConnection(url: URL, timeout: Int): URLConnection? {
                val connection = url.openConnection()
                if (timeout > 0) {
                    connection.connectTimeout = timeout
                    connection.readTimeout = timeout
                }
                return connection
            }
        }
        val repository: GoogleMavenRepository = object : GoogleMavenRepository(cacheDir.toPath()) {
            public override fun readUrlData(url: String, timeout: Int): ByteArray? =
                com.android.tools.lint.detector.api.readUrlData(client, url, timeout)

            public override fun error(throwable: Throwable, message: String?) =
                client.log(throwable, message)
        }

        var jarCount = 0
        var jarSizes = 0L
        var touchedNetwork = false
        var distinctLibraries = 0
        var incompatible = 0
        var lastIsIncompatible = 0
        var apiCount = 0

        val start = System.currentTimeMillis()
        for (group in repository.getGroups().sorted()) {
            // Note that as of today, only AndroidX libraries ship with lint.jars so
            // you can run this test much faster by skipping any group that doesn't start with "androidx."
            for (artifact in repository.getArtifacts(group).sorted()) {
                var first = true
                val versions = repository.getVersions(group, artifact).sorted().reversed()
                for (version in versions) {
                    val key = "$group:$artifact:$version"
                    val groupPath = group.replace('.', '/')
                    val url =
                        "https://dl.google.com/android/maven2/$groupPath/$artifact/$version/$artifact-$version.aar"
                    val aarTarget = File(cacheDir, "$key.aar")
                    val lintTarget = File(cacheDir, "$key-lint.jar")
                    if (lintTarget.isFile) {
                        if (lintTarget.length() == 0L) {
                            // No lint jar for this library/version combination
                            continue
                        }
                    } else {
                        // Don't try again:
                        lintTarget.createNewFile()

                        if (aarTarget.isFile && aarTarget.length() == 0L) {
                            // Not an AAR file or couldn't download
                            continue
                        }

                        if (!aarTarget.isFile) {
                            // Download and create it
                            aarTarget.createNewFile() // or don't try again if it doesn't exist
                            try {
                                touchedNetwork = true
                                val bytes = readUrlData(client, url, 30 * 1000) ?: continue
                                println("Read $url")
                                aarTarget.writeBytes(bytes)

                                // Try to extract lint.jar
                                var lintJarBytes: ByteArray? = null
                                JarFile(aarTarget).use { jarFile ->
                                    val lintJar = jarFile.getJarEntry("lint.jar")
                                    if (lintJar != null) {
                                        jarFile.getInputStream(lintJar).use { stream ->
                                            lintJarBytes = ByteStreams.toByteArray(stream)
                                        }
                                    }
                                }
                                if (lintJarBytes == null) {
                                    // No lint jar: stop checking these versions
                                    lintTarget.createNewFile()

                                    // Also write empty files for all older files so we don't keep trying
                                    for (v in versions) {
                                        val versionKey = "$group:$artifact:$v"
                                        val olderLintJar = File(cacheDir, "$versionKey-lint.jar")
                                        if (!olderLintJar.isFile) {
                                            olderLintJar.createNewFile()
                                        }
                                    }
                                    break
                                }
                                lintTarget.writeBytes(lintJarBytes!!)
                            } catch (ignore: Throwable) {
                                // Couldn't read URL -- probably not an aar but a jar
                                continue
                            }
                        }
                    }
                    jarCount++
                    jarSizes += lintTarget.length()
                    if (first) {
                        first = false
                        distinctLibraries++
                    }
                    val verifier = LintJarVerifier()
                    val compatible = verifier.isCompatible(lintTarget)
                    if (!compatible) {
                        incompatible++
                        if (version == versions.first()) {
                            lastIsIncompatible++
                        }
                        println("$key: Not compatible\n    (${verifier.describeFirstIncompatibleReference()})")
                    }
                    apiCount += verifier.apiCount
                }
            }
        }
        val end = System.currentTimeMillis()
        val time = end - start
        println()
        println(
            "Checking compatibility for $jarCount jar files from $distinctLibraries distinct libraries:\n" +
                "Total jar files combined size is ${jarSizes / 1024}K\n" +
                "Verified ${NumberFormat.getIntegerInstance().format(apiCount)} API elements, and found " +
                "$incompatible incompatible libraries ($lastIsIncompatible in the most recent version)\n" +
                "Total time was ${time}ms (${time / 1000}s)"
        )
        if (touchedNetwork) {
            println("HOWEVER, the test had to download artifacts from maven.google.com")
            println("Run the test again (with the jars cached locally) to check the performance")
            println("of the lint verifier.")
        }

        // Make sure that all the maven.google.com libraries are compatible with this current version of lint
        assertEquals(0, lastIsIncompatible)
    }
}
