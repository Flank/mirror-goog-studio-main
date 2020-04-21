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

package com.android.build.gradle.internal.res.shrinker.obfuscation

import com.google.common.base.Charsets
import com.google.common.io.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProguardMappingsRecorderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `test resolve original class name`() {
        val obfuscatedClasses = ProguardMappingsRecorder(createMappingsFile())
            .extractObfuscatedResourceClasses()

        assertEquals("androidx.loader.content.Panel",
            obfuscatedClasses.resolveOriginalClass("a.k.b.b"))
        assertEquals("androidx.recyclerview.R\$dimen",
            obfuscatedClasses.resolveOriginalClass("a.l.a"))
        assertEquals("com.NotObfuscated",
            obfuscatedClasses.resolveOriginalClass("com.NotObfuscated"))
    }

    @Test
    fun `test resolve original fields and methods`() {
        val obfuscatedClasses = ProguardMappingsRecorder(createMappingsFile())
            .extractObfuscatedResourceClasses()

        assertEquals(ClassAndMethod("androidx.recyclerview.R\$dimen", "margin1"),
            obfuscatedClasses.resolveOriginalMethod(ClassAndMethod("a.l.a", "d")))

        assertEquals(ClassAndMethod("androidx.loader.content.Loader", "dump"),
            obfuscatedClasses.resolveOriginalMethod(ClassAndMethod("a.k.b.a", "d")))
        assertEquals(ClassAndMethod("androidx.loader.content.Loader", "abandon"),
            obfuscatedClasses.resolveOriginalMethod(ClassAndMethod("a.k.b.a", "a")))

        assertEquals(ClassAndMethod("androidx.loader.content.Panel", "start"),
            obfuscatedClasses.resolveOriginalMethod(ClassAndMethod("a.k.b.b", "start")))

        val notObfuscatedMethod = ClassAndMethod("com.NotObfuscated", "method")
        assertEquals(notObfuscatedMethod,
            obfuscatedClasses.resolveOriginalMethod(notObfuscatedMethod))
    }

    private fun createMappingsFile(): Path {
        val tempFile = temporaryFolder.newFile()
        Files.asCharSink(tempFile, Charsets.UTF_8).write("""
            # compiler: R8
            # compiler_version: 2.1.12-dev
            # min_api: 24
            # compiler_hash: ba61baaed5cd2da8a79c616ed701190bfa7bcc1b
            # pg_map_id: 15e7330
            # common_typos_disable
            androidx.recyclerview.R${'$'}dimen -> a.l.a:
                int fastscroll_margin -> b
                int fastscroll_minimum_range -> c
                int fastscroll_default_thickness -> a
                int margin1 -> d
            androidx.loader.content.Panel -> a.k.b.b:
            androidx.loader.content.Loader -> a.k.b.a:
                void abandon() -> a
                boolean cancelLoad() -> b
                java.lang.String dataToString(java.lang.Object) -> c
                void dump(java.lang.String,java.io.FileDescriptor,java.io.PrintWriter,java.lang.String[]) -> d
                void reset() -> e
                void startLoading() -> f
                void stopLoading() -> g
                
        """.trimIndent())
        return tempFile.toPath()
    }
}
