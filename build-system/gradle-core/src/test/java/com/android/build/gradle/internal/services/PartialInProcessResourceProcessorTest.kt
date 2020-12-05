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

package com.android.build.gradle.internal.services

import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptConvertConfig
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.NoErrorsOrWarningsLogger
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.Mockito.verifyNoMoreInteractions

class PartialInProcessResourceProcessorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun compileWithJvm() {
        val from = temporaryFolder.newFolder().resolve("res/values/strings.xml")
        from.parentFile.mkdirs()
        from.writeText("""<resources><string name="my_string">my string</string></resources>""")
        val to = temporaryFolder.newFolder()

        val aapt2: Aapt2 = mock()
        val processor =
            PartialInProcessResourceProcessor(
                aapt2
            )
        val logger = NoErrorsOrWarningsLogger()
        processor.compile(CompileResourceRequest(inputFile = from, outputDirectory = to), logger)

        verifyNoMoreInteractions(aapt2)
        assertThat(to.resolve(
            Aapt2RenamingConventions.compilationRename(
                from
            )
        )).exists()
    }

    @Test
    fun compileWithAapt2() {
        val from = temporaryFolder.newFolder().resolve("res/drawables/crunch_me.png")
        val to = temporaryFolder.newFolder()
        val aapt2: Aapt2 = mock()
        val processor =
            PartialInProcessResourceProcessor(
                aapt2
            )
        val logger = NoErrorsOrWarningsLogger()
        val request = CompileResourceRequest(inputFile = from, outputDirectory = to, isPngCrunching = true)
        processor.compile(request, logger)
        Mockito.verify(aapt2).compile(eq(request), eq(logger))
    }


    @Test
    fun link() {
        val aapt2: Aapt2 = mock()
        val processor =
            PartialInProcessResourceProcessor(
                aapt2
            )
        val logger = NoErrorsOrWarningsLogger()
        val request = AaptPackageConfig(
            androidJarPath = "",
            manifestFile = temporaryFolder.newFile("AndroidManifest.xml"),
            resourceOutputApk = temporaryFolder.newFolder().resolve("res.ap_"),
            options = AaptOptions(),
            variantType = VariantTypeImpl.BASE_APK
        )
        processor.link(request, logger)
        Mockito.verify(aapt2).link(eq(request), eq(logger))
    }

    @Test
    fun convert() {
        val aapt2: Aapt2 = mock()
        val processor =
            PartialInProcessResourceProcessor(
                aapt2
            )
        val logger = NoErrorsOrWarningsLogger()
        val request = AaptConvertConfig(inputFile = temporaryFolder.newFile("in.ap_"), outputFile = temporaryFolder.newFolder().resolve("out.ap_"))
        processor.convert(request, logger)
        Mockito.verify(aapt2).convert(eq(request), eq(logger))
    }
}
