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

@file:JvmName("L8Tool")

package com.android.builder.dexing

import com.android.tools.r8.L8
import com.android.tools.r8.L8Command
import com.android.tools.r8.OutputMode
import com.google.common.util.concurrent.MoreExecutors
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

fun runL8(
    inputClasses: Collection<Path>,
    output: Path,
    libConfiguration: String,
    libraries: Collection<Path>,
    minSdkVersion: Int
) {
    val logger: Logger = Logger.getLogger("L8")
    if (logger.isLoggable(Level.FINE)) {
        logger.fine("*** Using L8 to process code ***")
        logger.fine("Program classes: $inputClasses")
        logger.fine("Special library configuration: $libConfiguration")
        logger.fine("Library classes: $libraries")
        logger.fine("Min Api level: $minSdkVersion")
    }

    val l8Command = L8Command.builder()
        .addProgramFiles(inputClasses)
        .setOutput(output, OutputMode.DexIndexed)
        .addSpecialLibraryConfiguration(libConfiguration)
        .addLibraryFiles(libraries)
        .setMinApiLevel(minSdkVersion)
        .build()

    L8.run(l8Command, MoreExecutors.newDirectExecutorService())
}