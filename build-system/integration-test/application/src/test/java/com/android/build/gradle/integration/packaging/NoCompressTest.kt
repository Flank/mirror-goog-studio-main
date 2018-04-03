/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.packaging

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.tools.build.apkzlib.zip.CompressionMethod
import com.android.tools.build.apkzlib.zip.ZFile
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import java.io.File

class NoCompressTest {

    private val content = ByteArray(1000)

    @get:Rule
    var expect = Expect.create()

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.example.test")
                .appendToBuild("android.aaptOptions.noCompress = ['.no']")
                .withFile(TestSourceFile("src/main/resources", "jres.yes", content))
                .withFile(TestSourceFile("src/main/resources", "jres.no", content))
                .withFile(TestSourceFile("src/main/assets", "a.yes", content))
                .withFile(TestSourceFile("src/main/assets", "a.no", content))
                .withFile(TestSourceFile("src/main/res/raw", "r_yes.yes", content))
                .withFile(TestSourceFile("src/main/res/raw", "r_no.no", content))
        ).create()

    @Test
    fun noCompressIsAccepted() {
        project.execute(":assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).exists()
        verifyCompression(apk.file.toFile())
    }

    private fun verifyCompression(apk: File) {
        ZFile(apk).use { zf ->
            zf.expectCompressionMethodOf("jres.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("jres.no").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a.no").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_yes.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_no.no").isEqualTo(CompressionMethod.STORE)
        }
    }

    private fun ZFile.expectCompressionMethodOf(path: String) =
        expect.that(get(path)?.centralDirectoryHeader?.compressionInfoWithWait?.method)
            .named("Compression method of $path")

}
