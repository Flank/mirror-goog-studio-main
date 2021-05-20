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

package com.android.sdklib.integration

import com.android.testutils.AssumeUtil
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

@RunWith(Parameterized::class)
class SdkCommandLineToolsPackagingTest(private val platform: AndroidSdkCommandLineToolsPlatform) {

    @Test
    fun checkPackaging() {
        val entries = collectEntries(platform.zipFile)

        val suffix = platform.binarySuffix

        val expectedNonLibEntries = listOfNotNull(
            "cmdline-tools/bin/apkanalyzer$suffix",
            "cmdline-tools/bin/avdmanager$suffix",
            // b/135688047
            // "tools/bin/jobb$suffix",
            "cmdline-tools/bin/lint$suffix",
            "cmdline-tools/bin/profgen$suffix",
            "cmdline-tools/bin/retrace$suffix",
            "cmdline-tools/bin/screenshot2$suffix",
            "cmdline-tools/bin/sdkmanager$suffix",
            "cmdline-tools/NOTICE.txt",
            "cmdline-tools/source.properties",
            "cmdline-tools/lib/README",
            "_codesign/filelist".takeIf { platform == AndroidSdkCommandLineToolsPlatform.MAC }
        )

        assertThat(entries.filter { !(it.startsWith("cmdline-tools/lib/") && it.endsWith(".jar")) })
            .containsExactlyElementsIn(expectedNonLibEntries)
    }

    @Test
    fun sdkManagerSmokeTestOnLinux() {
        AssumeUtil.assumeIsLinux()
    }

    private fun collectEntries(zipFile: Path): Set<String> {
        return ImmutableSet.builder<String>().apply {
            ZipInputStream(Files.newInputStream(zipFile).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    add(entry.name)
                }
            }
        }.build()
    }

    companion object {
        @Suppress("unused")
        @get:JvmStatic
        @get:Parameterized.Parameters
        val parameters = AndroidSdkCommandLineToolsPlatform.values()
    }

}
