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

import com.android.tools.profgen.Apk
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.ArtProfileSerializer
import com.android.tools.profgen.Diagnostics
import com.android.tools.profgen.HumanReadableProfile
import com.android.tools.profgen.ObfuscationMap
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import java.io.File
import kotlin.system.exitProcess

@ExperimentalCli
class BinCommand : Subcommand("bin", "Generate Binary Profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    val apkPath by option(ArgType.String, "apk", "a", "File path to apk").required()
    val outPath by option(ArgType.String, "output", "o", "File path to generated binary profile").required()
    val obfPath by option(ArgType.String, "map", "m", "File path to name obfuscation map")
    override fun execute() {
        val hrpFile = File(hrpPath)
        require(hrpFile.exists()) { "File not found: $hrpPath" }

        val apkFile = File(apkPath)
        require(apkFile.exists()) { "File not found: $apkPath" }

        val obfFile = obfPath?.let { File(it) }
        require(obfFile?.exists() != false) { "File not found: $obfPath" }


        val outFile = File(outPath)
        require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

        val hrp = readHumanReadableProfileOrExit(hrpFile)
        val apk = Apk(apkFile)
        val obf = if (obfFile != null) ObfuscationMap(obfFile) else ObfuscationMap.Empty
        val profile = ArtProfile(hrp, obf, apk)
        profile.save(outFile.outputStream(), ArtProfileSerializer.V0_1_0_P)
    }
}

@ExperimentalCli
class ValidateCommand : Subcommand("validate", "Validate Profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    override fun execute() {
        val hrpFile = File(hrpPath)
        require(hrpFile.exists()) { "File not found: $hrpPath" }
        HumanReadableProfile(hrpFile, StdErrorDiagnostics)
    }
}

@ExperimentalCli
class PrintCommand : Subcommand("print", "Print methods matching profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    val apkPath by option(ArgType.String, "apk", "a", "File path to apk").required()
    val outPath by option(ArgType.String, "output", "o", "File path to generated binary profile").required()
    val obfPath by option(ArgType.String, "map", "m", "File path to name obfuscation map")
    override fun execute() {
        val hrpFile = File(hrpPath)
        require(hrpFile.exists()) { "File not found: $hrpPath" }

        val apkFile = File(apkPath)
        require(apkFile.exists()) { "File not found: $apkPath" }

        val obfFile = obfPath?.let { File(it) }
        require(obfFile?.exists() != false) { "File not found: $obfPath" }


        val outFile = File(outPath)
        require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

        val hrp = readHumanReadableProfileOrExit(hrpFile)
        val apk = Apk(apkFile)
        val obf = if (obfFile != null) ObfuscationMap(obfFile) else ObfuscationMap.Empty
        val profile = ArtProfile(hrp, obf, apk)
        profile.print(System.out, obf)
    }
}

private fun readHumanReadableProfileOrExit(hrpFile: File): HumanReadableProfile {
    val hrp = HumanReadableProfile(hrpFile, StdErrorDiagnostics)
    if (hrp == null) {
        System.err.println("Failed to parse $hrpFile.")
        exitProcess(-1)
    }
    return hrp
}

private val StdErrorDiagnostics = Diagnostics { System.err.println(it) }
