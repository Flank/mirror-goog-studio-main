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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.build.gradle.internal.fixtures.FakeFilterShrinkerRulesParameters
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.google.common.truth.Truth.assertThat
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilterShrinkerRulesTransformTest {

    private val slash = File.separator

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    private lateinit var outputs: FakeTransformOutputs

    @Before
    fun setUp() {
        val outputDir = tmp.newFolder()
        outputs = FakeTransformOutputs(outputDir)
    }

    @Test
    fun testSingleRuleFile() {
        val file = createFiles("foo.txt" to "bar").listFiles().first()
        val filterTransform = createTransform(file)
        filterTransform.transform(outputs)

        assertThat(outputs.outputFiles).containsExactly(file)
    }

    @Test
    fun testSingleDirectory() {
        val files = createFiles(
            "lib${slash}proguard.txt" to "bar",
            "lib${slash}META-INF${slash}proguard${slash}pro.txt" to "foo",
            "lib${slash}META-INF${slash}com.android.tools${slash}r8${slash}r8.txt" to "r8",
            "lib${slash}META-INF${slash}com.android.tools${slash}proguard${slash}pg.txt" to "pg"
        )

        val filterTransform = createTransform(files)
        filterTransform.transform(outputs)

        assertThat(outputs.outputFiles).containsExactly(File(files, "lib${slash}META-INF${slash}com.android.tools${slash}r8${slash}r8.txt"))
    }

    @Test
    fun testSingleProguardTxtFile() {
        val files = createFiles(
            "lib${slash}${SdkConstants.FN_PROGUARD_TXT}" to "bar"
        )

        val filterTransform = createTransform(files)
        filterTransform.transform(outputs)

        assertThat(outputs.outputFiles).containsExactly(File(files, "lib${slash}${SdkConstants.FN_PROGUARD_TXT}"))
    }

    @Test
    fun testSingleDirectory_noComAndroidTools() {
        val files = createFiles(
            "lib${slash}META-INF${slash}proguard${slash}pro.txt" to "foo"
        )
        val filterTransform = createTransform(files)
        filterTransform.transform(outputs)

        assertThat(outputs.outputFiles).containsExactly(File(files, "lib${slash}META-INF${slash}proguard${slash}pro.txt"))
    }

    @Test
    fun testMultipleDirectories() {
        val files = createFiles(
            "lib${slash}META-INF${slash}proguard${slash}pro.txt" to "foo",
            "lib2${slash}META-INF${slash}com.android.tools${slash}r8${slash}r8.txt" to "r8",
            "lib2${slash}META-INF${slash}com.android.tools${slash}proguard${slash}pg.txt" to "pg",
            "lib3${slash}proguard.txt" to "pg"
        )

        val filterTransform = createTransform(files)
        filterTransform.transform(outputs)

        assertThat(outputs.outputFiles).containsExactly(
            File(files, "lib${slash}META-INF${slash}proguard${slash}pro.txt"),
            File(files, "lib2${slash}META-INF${slash}com.android.tools${slash}r8${slash}r8.txt"),
            File(files, "lib3${slash}proguard.txt")
        )
    }

    private fun createFiles(vararg entries: Pair<String, String?>): File {
        val filesFolder = tmp.newFolder()
        entries.forEach { entry ->
            val file = File(filesFolder, entry.first)
            file.parentFile.mkdirs()
            file.createNewFile()
            if (entry.second != null) {
                file.outputStream().bufferedWriter().use {
                    it.write(entry.second)
                }
            }
        }
        return filesFolder
    }

    @Test
    fun testConfigDirMatchesVersion() {
        val r8Ver1_5_5 = VersionedCodeShrinker("1.5.5")
        val pgVer6_1_5 = VersionedCodeShrinker("6.1.5")
        val r8Ver1_5_0_alpha = VersionedCodeShrinker("1.5.0-alpha")

        assertThat(configDirMatchesVersion("r8", r8Ver1_5_5)).isTrue()
        assertThat(configDirMatchesVersion("r8", r8Ver1_5_0_alpha)).isTrue()

        assertThat(configDirMatchesVersion("r8-from-1.4.0", r8Ver1_5_5)).isTrue()
        assertThat(configDirMatchesVersion("r8-from-1.4.0", r8Ver1_5_0_alpha)).isTrue()

        assertThat(configDirMatchesVersion("r8-from-1.5.0", r8Ver1_5_5)).isTrue()
        assertThat(configDirMatchesVersion("r8-from-1.5.5", r8Ver1_5_5)).isTrue()
        assertThat(configDirMatchesVersion("r8-from-1.5.0", r8Ver1_5_0_alpha)).isFalse()

        assertThat(configDirMatchesVersion("r8-from-1.6.0", r8Ver1_5_5)).isFalse()
        assertThat(configDirMatchesVersion("r8-from-1.6.0", r8Ver1_5_0_alpha)).isFalse()

        assertThat(configDirMatchesVersion("r8-upto-1.6.0", r8Ver1_5_5)).isTrue()
        assertThat(configDirMatchesVersion("r8-upto-1.6.0", r8Ver1_5_0_alpha)).isTrue()
        assertThat(configDirMatchesVersion("r8-upto-1.5.99", r8Ver1_5_5)).isTrue()
        assertThat(configDirMatchesVersion("r8-upto-1.5.99", r8Ver1_5_0_alpha)).isTrue()

        assertThat(configDirMatchesVersion("r8-upto-1.4.0", r8Ver1_5_5)).isFalse()
        assertThat(configDirMatchesVersion("r8-upto-1.4.0", r8Ver1_5_0_alpha)).isFalse()

        assertThat(configDirMatchesVersion("r8-upto-1.5.0", r8Ver1_5_5)).isFalse()
        assertThat(configDirMatchesVersion("r8-upto-1.5.5", r8Ver1_5_5)).isFalse()
        assertThat(configDirMatchesVersion("r8-upto-1.5.0", r8Ver1_5_0_alpha)).isTrue()

        assertThat(configDirMatchesVersion("r8-from-1.4.0-upto-1.6.0", r8Ver1_5_5)).isTrue()
        assertThat(configDirMatchesVersion("r8-from-1.4.0-upto-1.6.0", r8Ver1_5_0_alpha)).isTrue()

        assertThat(configDirMatchesVersion("proguard", pgVer6_1_5)).isFalse()
        assertThat(configDirMatchesVersion("proguard-from-6.1.0", pgVer6_1_5)).isFalse()
        assertThat(configDirMatchesVersion("proguard-from-6.0.0", pgVer6_1_5)).isFalse()
        assertThat(configDirMatchesVersion("proguard-from-6.5.0", pgVer6_1_5)).isFalse()

        assertThat(configDirMatchesVersion("abc", pgVer6_1_5)).isFalse()
        assertThat(configDirMatchesVersion("abc-from-1.2.3", pgVer6_1_5)).isFalse()
        assertThat(configDirMatchesVersion("-from-1.2.3", pgVer6_1_5)).isFalse()
    }
}

private fun createTransform(inputFile: File): FilterShrinkerRulesTransform {
    return object : FilterShrinkerRulesTransform(){
        override val inputArtifact: Provider<FileSystemLocation>
            get() = FakeGradleProvider(
                FakeGradleRegularFile(
                    inputFile
                )
            )

        override fun getParameters() =
            FakeFilterShrinkerRulesParameters(codeShrinker = VersionedCodeShrinker.create())

    }
}

class FakeTransformOutputs(private val outputDir: File) : TransformOutputs {
    val outputFiles = mutableSetOf<File>()

    override fun file(p0: Any): File {
        val file = File(p0.toString()).takeIf { it.isAbsolute }
            ?: File(outputDir, p0.toString())
        outputFiles.add(file)
        return file
    }
    override fun dir(p0: Any): File {
        val file = File(p0.toString()).takeIf { it.isAbsolute }
            ?: File(outputDir, p0.toString())
        outputFiles.add(file)
        return file
    }
}
