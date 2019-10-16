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

import com.android.testutils.TestUtils
import com.android.testutils.truth.DexSubject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.jar.JarFile

class L8DexDesugarLibTaskTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testShrinking() {
        val output = tmp.newFolder().resolve("out")
        val input = tmp.newFolder().toPath()

        val keepRulesFile1 = input.toFile().resolve("keep_rules").also { file ->
            file.bufferedWriter().use {
                it.write("-keep class j$.util.stream.Stream {*;}")
            }
        }
        val keepRulesFile2 = input.toFile().resolve("dir/keep_rules").also { file ->
            file.parentFile.mkdirs()
            file.bufferedWriter().use {
                it.write("-keep class j$.util.Optional {*;}")
            }
        }

        val params = L8DexParams(
            desugarJar,
            output,
            getDesugarLibConfigContent(desugarConfigJar),
            bootClasspath,
            20,
            setOf(keepRulesFile1, keepRulesFile2)
        )
        L8DexRunnable(params).run()

        val dexFile = output.resolve("classes1000.dex")
        DexSubject.assertThatDex(dexFile).containsClass("Lj$/util/stream/Stream;")
        DexSubject.assertThatDex(dexFile).containsClass("Lj$/util/Optional;")
        DexSubject.assertThatDex(dexFile).doesNotContainClasses("Lj$/time/LocalTime;")
    }

    companion object {
        val bootClasspath = TestUtils.getPlatformFile("android.jar")
        val desugarJar = listOf(TestUtils.getDesugarLibJarWithVersion("1.0.1").toFile())
        val desugarConfigJar = TestUtils.getDesugarLibConfigJarWithVersion("0.5.0").toFile()
    }

    private fun getDesugarLibConfigContent(file: File) : String {
        val stringBuilder = StringBuilder()
        JarFile(file).use { jarFile ->
            val jarEntry = jarFile.getJarEntry("META-INF/desugar/d8/desugar.json")
            BufferedReader(InputStreamReader(jarFile.getInputStream(jarEntry)))
                .use { bufferedReader ->
                    var line = bufferedReader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = bufferedReader.readLine()
                    }
                }
        }
        return stringBuilder.toString()
    }
}