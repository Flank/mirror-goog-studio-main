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
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.builder.internal.packaging.ApkCreatorType
import com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER
import com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR
import com.android.tools.build.apkzlib.zip.CompressionMethod
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files

@RunWith(FilterableParameterized::class)
class NoCompressTest(apkCreatorType: ApkCreatorType) {

    companion object {
        @Parameterized.Parameters(name = "apkCreatorType_{0}")
        @JvmStatic
        fun params() = listOf(APK_Z_FILE_CREATOR, APK_FLINGER)
    }

    private val content = ByteArray(1000)

    @get:Rule
    var expect = Expect.create()

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.example.test")
                .appendToBuild(
                    "android.aaptOptions.noCompress = " +
                            "['.no', '.Test', 'end', '.a(b)c', 'space name.txt', '.KoŃcówka']")
                .appendToBuild("android.defaultConfig.versionCode 1")
                .withFile("src/main/resources/jres.yes", content)
                .withFile("src/main/resources/jres.no", content)
                .withFile("src/main/resources/jres.variantApiNo", content)
                .withFile("src/main/resources/jres.jpg", content)
                .withFile("src/main/resources/jres.tflite", content)
                .withFile("src/main/assets/a.yes", content)
                .withFile("src/main/assets/a.no", content)
                .withFile("src/main/assets/a.variantApiNo", content)
                .withFile("src/main/assets/a_matching.Test", content)
                .withFile("src/main/assets/a_lower.test", content)
                .withFile("src/main/assets/a_upper.TEST", content)
                .withFile("src/main/assets/a_space name.txt", content)
                .withFile("src/main/assets/a_pl_matching.KoŃcówka", content)
                .withFile("src/main/assets/a_pl_upper.KOŃCÓWKA", content)
                .withFile("src/main/assets/a_pl_lower.końcówka", content)
                .withFile("src/main/assets/a_weird_chars.a(b)c", content)
                .withFile("src/main/assets/a_not_weird_chars.abc", content)
                .withFile("src/main/assets/a_not_weird_chars2.ac", content)
                .withFile("src/main/assets/a_not_weird_chars3.aa(b)c", content)
                .withFile("src/main/assets/a.jpg", content)
                .withFile("src/main/assets/a.tflite", content)
                .withFile("src/main/assets/a.webp", content)
                .withFile("src/main/res/raw/r_yes.yes", content)
                .withFile("src/main/res/raw/r_no.no", content)
                .withFile("src/main/res/raw/r_matching.Test", content)
                .withFile("src/main/res/raw/r_upper.TEST", content)
                .withFile("src/main/res/raw/r_lower.test", content)
                .withFile("src/main/res/raw/r_end_.noKeep", content)
                .withFile("src/main/res/raw/r_pl_matching.KoŃcówka", content)
                .withFile("src/main/res/raw/r_pl_upper.KOŃCÓWKA", content)
                .withFile("src/main/res/raw/r_pl_lower.końcówka", content)
                .withFile("src/main/res/raw/r_weird_chars.a(b)c", content)
                .withFile("src/main/res/raw/r_not_weird_chars.abc", content)
                .withFile("src/main/res/raw/r_not_weird_chars2.ac", content)
                .withFile("src/main/res/raw/r_not_weird_chars3.aa(b)c", content)
                .withFile("src/main/res/raw/r_jpg.jpg", content)
        ).setApkCreatorType(apkCreatorType)
        .create()

    @Test
    fun noCompressIsAccepted() {
        project.execute(":assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).exists()
        verifyCompression(apk.file.toFile())
    }

    @Test
    fun bundleNoCompressTest() {
        project.executor().run(":makeApkFromBundleForDebug")

        val extracted = temporaryFolder.newFile("base-master.apk")

        FileUtils.createZipFilesystem(project.getIntermediateFile("apks_from_bundle", "debug", "bundle.apks").toPath()).use { apks ->
            extracted.outputStream().buffered().use {
                Files.copy(apks.getPath("splits/base-master.apk"), it)
            }
        }
        verifyCompression(extracted)
    }

    private fun verifyCompression(apk: File) {
        ZFile.openReadOnly(apk).use { zf ->
            zf.expectCompressionMethodOf("jres.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("jres.no").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("jres.jpg").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("jres.tflite").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a.no").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_matching.Test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_lower.test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_upper.TEST").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_space name.txt").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_pl_matching.KoŃcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_pl_upper.KOŃCÓWKA").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_pl_lower.końcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_weird_chars.a(b)c").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_not_weird_chars.abc").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a_not_weird_chars2.ac").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a_not_weird_chars3.aa(b)c").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a.jpg").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a.tflite").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a.webp").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_yes.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_no.no").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_matching.Test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_upper.TEST").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_lower.test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_end_.noKeep").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_pl_matching.KoŃcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_pl_upper.KOŃCÓWKA").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_pl_lower.końcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_weird_chars.a(b)c").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_not_weird_chars.abc").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_not_weird_chars2.ac").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_not_weird_chars3.aa(b)c").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_jpg.jpg").isEqualTo(CompressionMethod.STORE)
        }
    }

    private fun ZFile.expectCompressionMethodOf(path: String) =
        expect.that(get(path)?.centralDirectoryHeader?.compressionInfoWithWait?.method)
            .named("Compression method of $path")
}
