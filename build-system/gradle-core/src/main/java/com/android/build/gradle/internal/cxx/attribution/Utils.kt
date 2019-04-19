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

package com.android.build.gradle.internal.cxx.attribution

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxBuildModel
import com.android.build.gradle.internal.cxx.model.getCxxBuildModel
import com.android.build.gradle.internal.cxx.services.completeModelAbis
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Appends current timestamp and build ID to the given ninja log. */
@Throws(IOException::class)
internal fun appendTimestampAndBuildIdToNinjaLog(cxxAbiModel: CxxAbiModel): File {
    val ninjaLogFile = cxxAbiModel.ninjaLogFile
    val contentToWrite = StringBuilder()
    if (!ninjaLogFile.exists()) {
        // Magic words required by ninja. Every ninja log file must start with this.
        contentToWrite.appendln("# ninja log v5")
    }
    contentToWrite.appendln(
        "# ${Clock.systemUTC().millis()} ${getCxxBuildModel().buildId}"
    )
    return ninjaLogFile.apply { appendText(contentToWrite.toString()) }
}

/**
 * Collects entries in the given ninja logs related to the given `buildId` to
 * `$projectRoot/.cxx/attribution/ninja_build_log_<timestamp>.zip`.
 */
internal fun collectNinjaLogs(cxxBuildSessionService: CxxBuildModel) {
    if (cxxBuildSessionService.completeModelAbis().isEmpty()) {
        return
    }
    // This is not the most natural thing but it works since all CxxModuleModel should point to the
    // same buildAttributionFolder that is under the source root. In future, this
    // buildAttributionFolder property should go under a new CxxProjectModel and by that time this
    // code can be refactored to do it in a more natural way.
    val buildAttributionFolder =
        cxxBuildSessionService.completeModelAbis().first().variant.module.buildAttributionFolder
    val zipFile =
        buildAttributionFolder.resolve("ninja_build_log_${Clock.systemUTC().millis()}.zip")
    zipFile.parentFile.mkdirs()
    ZipOutputStream(FileOutputStream(zipFile)).use { zipOs ->
        for (abiModel in cxxBuildSessionService.completeModelAbis()) {
            val ninjaLogFile = abiModel.ninjaLogFile
            if (!ninjaLogFile.exists()) continue
            val modulePath = abiModel.variant.module.gradleModulePathName
                .replace(':', '/')
                .removePrefix("/")
            val variantName = abiModel.variant.variantName
            val abiName = abiModel.abi.tag
            zipOs.putNextEntry(
                ZipEntry("$modulePath/$variantName/$abiName")
            )
            ninjaLogFile.useLines { lines ->
                lines
                    .dropWhile { !it.endsWith(" ${cxxBuildSessionService.buildId}") }
                    .forEach { zipOs.write("$it\n".toByteArray(StandardCharsets.UTF_8)) }
            }
            zipOs.closeEntry()
        }
    }
}
