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

package com.android.testutils

import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class TestAarGeneratorTest {

    @Test
    fun smokeTestAarGenerator() {
        val aar = generateAarWithContent(
            packageName = "com.example.lib",
            mainJar = "classes jar content".toByteArray(Charsets.UTF_8),
            secondaryJars = mapOf("other" to "otherJarContent".toByteArray(Charsets.UTF_8)),
            resources = mapOf("values/strings.xml" to "stringsXml".toByteArray(Charsets.UTF_8))
        )

        assertThat(readZipEntries(aar))
            .containsExactlyEntriesIn(
                mapOf(
                    "AndroidManifest.xml" to """<manifest package="com.example.lib"></manifest>""",
                    "classes.jar" to "classes jar content",
                    "libs/other.jar" to "otherJarContent",
                    "res/values/strings.xml" to "stringsXml"
                )
            )
    }

    /**
     * Reads all of the entries of a zip as utf-8 strings to allow easy assertions about the content.
     * Returns a map of entry name to content, both as strings.
     */
    private fun readZipEntries(zipBytes: ByteArray): ImmutableMap<String, String> {
        return ImmutableMap.builder<String, String>().also { entries ->
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { aar ->
                while (true) {
                    val entry = aar.nextEntry ?: break
                    entries.put(entry.name, String(aar.readBytes(), Charsets.UTF_8))
                }
            }
        }.build()
    }

}
