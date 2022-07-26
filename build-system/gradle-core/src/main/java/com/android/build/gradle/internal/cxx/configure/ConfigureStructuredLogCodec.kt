/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.build.gradle.internal.cxx.string.StringEncoder

/**
 * Transform a [ConfigureInvalidationState] into an [EncodedConfigureInvalidationState] by encoding
 * strings with [StringEncoder].
 */
fun ConfigureInvalidationState.encode(encoder : StringEncoder) : EncodedConfigureInvalidationState {
    val encoded = EncodedConfigureInvalidationState.newBuilder()
    encoded.forceConfigure = forceConfigure
    encoded.fingerPrintFile = encoder.encode(fingerPrintFile)
    encoded.inputFiles = encoder.encodeList(inputFilesList)
    encoded.requiredOutputFiles = encoder.encodeList(requiredOutputFilesList)
    encoded.optionalOutputFiles = encoder.encodeList(optionalOutputFilesList)
    encoded.hardConfigureFiles = encoder.encodeList(hardConfigureFilesList)
    encoded.fingerPrintFileExisted = fingerPrintFileExisted
    encoded.addedSinceFingerPrintsFiles = encoder.encodeList(addedSinceFingerPrintsFilesList)
    encoded.removedSinceFingerPrintsFiles = encoder.encodeList(removedSinceFingerPrintsFilesList)
    encoded.addedSinceFingerPrintsFiles = encoder.encodeList(addedSinceFingerPrintsFilesList)
    encoded.addAllChangesToFingerPrintFiles(changesToFingerPrintFilesList.map { it.encode(encoder) })
    encoded.unchangedFingerPrintFiles = encoder.encodeList(unchangedFingerPrintFilesList)
    encoded.configureType = configureType
    encoded.addAllSoftConfigureReasons(softConfigureReasonsList.map { it.encode(encoder) })
    encoded.addAllHardConfigureReasons(hardConfigureReasonsList.map { it.encode(encoder) })
    return encoded.build()
}

/**
 * Transform a [EncodedConfigureInvalidationState] into an [ConfigureInvalidationState] by decoding
 * strings with [StringDecoder].
 */
fun EncodedConfigureInvalidationState.decode(decoder : StringDecoder) : ConfigureInvalidationState {
    val decoded = ConfigureInvalidationState.newBuilder()
    decoded.forceConfigure = forceConfigure
    decoded.fingerPrintFile = decoder.decode(fingerPrintFile)
    decoded.addAllInputFiles(decoder.decodeList(inputFiles))
    decoded.addAllRequiredOutputFiles( decoder.decodeList(requiredOutputFiles))
    decoded.addAllOptionalOutputFiles( decoder.decodeList(optionalOutputFiles))
    decoded.addAllHardConfigureFiles(decoder.decodeList(hardConfigureFiles))
    decoded.fingerPrintFileExisted = fingerPrintFileExisted
    decoded.addAllAddedSinceFingerPrintsFiles(decoder.decodeList(addedSinceFingerPrintsFiles))
    decoded.addAllRemovedSinceFingerPrintsFiles(decoder.decodeList(removedSinceFingerPrintsFiles))
    decoded.addAllChangesToFingerPrintFiles(changesToFingerPrintFilesList.map { it.decode(decoder) })
    decoded.addAllUnchangedFingerPrintFiles(decoder.decodeList(unchangedFingerPrintFiles))
    decoded.configureType = configureType
    decoded.addAllSoftConfigureReasons(softConfigureReasonsList.map { it.decode(decoder) })
    decoded.addAllHardConfigureReasons(hardConfigureReasonsList.map { it.decode(decoder) })
    return decoded.build()
}

/**
 * Helper function for calling decode for this [EncodedConfigureInvalidationState]
 */
fun decodeConfigureInvalidationState(
    encoded : EncodedConfigureInvalidationState,
    decoder : StringDecoder
) = encoded.decode(decoder)

/**
 * Transform a [FileChange] into an [EncodedFileChange] by encoding
 * strings with [StringEncoder].
 */
fun ChangedFile.encode(encoder : StringEncoder) : EncodedChangedFile {
    val encoded = EncodedChangedFile.newBuilder()
    encoded.fileName = encoder.encode(fileName)
    encoded.type = type
    return encoded.build()
}

/**
 * Transform a [EncodedFileChange] into an [FileChange] by decoding
 * strings with [StringDecoder].
 */
fun EncodedChangedFile.decode(decoder : StringDecoder) : ChangedFile {
    val decoded = ChangedFile.newBuilder()
    decoded.fileName = decoder.decode(fileName)
    decoded.type = type
    return decoded.build()
}
