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

@file:JvmName("Commands")

package com.android.tools.app.inspection

import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.ArtifactCoordinate
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand
import com.android.tools.app.inspection.AppInspection.DisposeInspectorCommand
import com.android.tools.app.inspection.AppInspection.LaunchMetadata
import com.android.tools.app.inspection.AppInspection.RawCommand
import com.android.tools.idea.protobuf.ByteString

fun createLibraryInspector(
    inspectorId: String,
    dexPath: String,
    minLibrary: ArtifactCoordinate
): AppInspectionCommand {
    val metadata = LaunchMetadata.newBuilder().setMinLibrary(minLibrary).build()
    return createInspector(inspectorId, dexPath, metadata)
}

@JvmOverloads
fun createInspector(
    inspectorId: String,
    dexPath: String,
    launchMetadata: LaunchMetadata? = null
): AppInspectionCommand = appInspectionCommand(inspectorId) {
    createInspectorCommand = CreateInspectorCommand.newBuilder().apply {
        this.dexPath = dexPath
        launchMetadata?.let { this.launchMetadata = it }
    }.build()
}

fun disposeInspector(inspectorId: String): AppInspectionCommand =
    appInspectionCommand(inspectorId) {
        disposeInspectorCommand = DisposeInspectorCommand.getDefaultInstance()
    }

fun rawCommandInspector(inspectorId: String, commandData: ByteArray): AppInspectionCommand =
    appInspectionCommand(inspectorId) {
        rawInspectorCommand = RawCommand.newBuilder()
            .setContent(ByteString.copyFrom(commandData)).build()
    }

private fun appInspectionCommand(
    inspectorId: String,
    initializer: AppInspectionCommand.Builder.() -> Unit
): AppInspectionCommand = AppInspectionCommand.newBuilder().apply {
    this.inspectorId = inspectorId
    initializer()
}.build()
