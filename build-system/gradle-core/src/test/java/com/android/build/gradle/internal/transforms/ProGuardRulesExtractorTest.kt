/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms

import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProGuardRulesExtractorTest {

    @JvmField
    @Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun testExtractMetaInfRulesLines_noMetaInf() {

        val jarFile = createZip("bar.txt" to "hello")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).isEmpty()
    }

    @Test
    fun testExtractMetaInfRulesLines_noProguard() {

        val jarFile = createZip("META-INF/bar.txt" to "hello")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).isEmpty()
    }

    @Test
    fun testExtractMetaInfRulesLines_noRules() {

        val jarFile = createZip("META-INF/proguard" to null)

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).isEmpty()
    }

    @Test
    fun testExtractMetaInfRulesLines_singleRule() {

        val jarFile = createZip("META-INF/proguard/bar.txt" to "hello")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).containsExactly("hello")
    }

    @Test
    fun testExtractMetaInfRulesLines_lowerCaseName() {

        val jarFile = createZip("meta-inf/proguard/bar.txt" to "hello")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).containsExactly("hello")
    }

    @Test
    fun testExtractMetaInfRulesLines_slashStartingName() {

        val jarFile = createZip("/META-INF/proguard/bar.txt" to "hello")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).containsExactly("hello")
    }

    @Test
    fun testExtractMetaInfRulesLines_singleMultilineRule() {

        val jarFile = createZip("META-INF/proguard/bar.txt" to "hello\nhi")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).containsExactly("hello\nhi")
    }

    @Test
    fun testExtractMetaInfRulesLines_singleMultilineRuleEmptyLines() {

        val jarFile = createZip("META-INF/proguard/bar.txt" to "hello\n\n\nhi\n\n")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).containsExactly("hello\n\n\nhi\n\n")
    }

    @Test
    fun testExtractMetaInfRulesLines_twoRules() {

        val jarFile = createZip(
            "META-INF/proguard/bar.txt" to "hello",
            "META-INF/proguard/foo.pro" to "goodbye")

        val rules = extractRulesText(jarFile)

        Truth.assertThat(rules).containsExactly("hello", "goodbye")
    }

    private fun extractRulesText(f : File) : List<String> {
        return ProGuardRulesExtractor.extractRulesTexts(f).values.toList()
    }

    private fun createZip(vararg entries: Pair<String, String?>): File {
        val zipFile = tmp.newFile()
        ZipOutputStream(FileOutputStream(zipFile)).use {
            for (entry in entries) {
                it.putNextEntry(ZipEntry(entry.first))
                if (entry.second != null) {
                    it.write(entry.second!!.toByteArray())
                }
                it.closeEntry()
            }
        }
        return zipFile
    }
}