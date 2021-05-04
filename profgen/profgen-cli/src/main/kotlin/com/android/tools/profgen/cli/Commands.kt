/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.profgen.cli

import com.android.tools.profgen.*
import kotlinx.cli.*
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.system.exitProcess

@OptIn(ExperimentalContracts::class)
private inline fun requireOrExit(condition: Boolean, message: () -> String) {
    contract { returns() implies condition }
    if (!condition) {
        System.err.println(message())
        exitProcess(-1)
    }
}

private fun File.assertExists(): File {
    requireOrExit(exists()) { "File not found: $absolutePath" }
    return this
}

private fun Apk(apk: File?, dexFiles: List<File>): Apk {
    return if (apk != null) {
        requireOrExit(dexFiles.isEmpty()) {
            "You cannot specify both --apk and --dex arguments"
        }
        Apk(apk)
    } else {
        requireOrExit(dexFiles.isNotEmpty()) {
            "You must specify an --apk argument or at least one --dex argument"
        }
        Apk(dexFiles.map { DexFile(it) })
    }
}

@ExperimentalCli
class GenerateCommand : Subcommand("generate", "Generate Binary Profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    val apkPath by option(ArgType.String, "apk", "a", "File path to apk")
    val dexPaths by option(ArgType.String, "dex", "d", "File path(s) to dex files in the application").multiple()
    val outPath by option(ArgType.String, "output", "o", "File path to generated binary profile").required()
    val obfPath by option(ArgType.String, "map", "m", "File path to name obfuscation map")
    override fun execute() {
        val hrpFile = File(hrpPath).assertExists()
        val obfFile = obfPath?.let { File(it).assertExists() }
        val apkFile = apkPath?.let { File(it).assertExists() }
        val dexFiles = dexPaths.map { File(it).assertExists() }
        val outFile = File(outPath)

        val hrp = readHumanReadableProfileOrExit(hrpFile)
        val apk = Apk(apkFile, dexFiles)
        val obf = if (obfFile != null) ObfuscationMap(obfFile) else ObfuscationMap.Empty
        val profile = ArtProfile(hrp, obf, apk)
        profile.save(outFile.outputStream(), ArtProfileSerializer.V0_1_0_P)
    }
}

@ExperimentalCli
class ValidateCommand : Subcommand("validate", "Validate Profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    override fun execute() {
        val hrpFile = File(hrpPath).assertExists()
        HumanReadableProfile(hrpFile, StdErrorDiagnostics)
    }
}

@ExperimentalCli
class PrintCommand : Subcommand("print", "Print methods matching profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    val apkPath by option(ArgType.String, "apk", "a", "File path to apk")
    val dexPaths by option(ArgType.String, "dex", "d", "File path(s) to dex files in the application").multiple()
    val obfPath by option(ArgType.String, "map", "m", "File path to name obfuscation map")
    override fun execute() {
        val hrpFile = File(hrpPath).assertExists()
        val obfFile = obfPath?.let { File(it).assertExists() }
        val apkFile = apkPath?.let { File(it).assertExists() }
        val dexFiles = dexPaths.map { File(it).assertExists() }

        val hrp = readHumanReadableProfileOrExit(hrpFile)
        val apk = Apk(apkFile, dexFiles)
        val obf = if (obfFile != null) ObfuscationMap(obfFile) else ObfuscationMap.Empty
        val profile = ArtProfile(hrp, obf, apk)
        profile.print(System.out, obf)
    }
}

private fun readHumanReadableProfileOrExit(hrpFile: File): HumanReadableProfile {
    val hrp = HumanReadableProfile(hrpFile, StdErrorDiagnostics)
    requireOrExit(hrp != null) { "Failed to parse $hrpFile." }
    return hrp
}

private val StdErrorDiagnostics = Diagnostics { System.err.println(it) }
