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

package com.android.builder.dexing

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Sanity test to make sure we can invoke L8 successfully
 */
class L8ToolTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testDexGeneration() {
        val output = tmp.newFolder().toPath()
        runL8(
            desugarJar,
            output,
            getFileContentFromJar(desugarConfigJar.toFile()),
            bootClasspath,
            20)
        assertThat(getDexFileCount(output)).isEqualTo(1)
    }

    private fun getDexFileCount(dir: Path): Long =
        Files.list(dir).filter { it.toString().endsWith(".dex") }.count()

    companion object {
        val bootClasspath = listOf(TestUtils.getPlatformFile("android.jar").toPath())
        val desugarJar = listOf(TestUtils.getDesugarLibJarWithVersion("1.0.0"))
        val desugarConfigJar = TestUtils.getDesugarLibConfigJarWithVersion("0.1.0")
    }

    private fun getFileContentFromJar(file: File) : String {
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