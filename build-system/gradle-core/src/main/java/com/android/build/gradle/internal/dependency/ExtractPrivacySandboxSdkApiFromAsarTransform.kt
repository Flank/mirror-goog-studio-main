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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask.Companion.privacySandboxSdkStubJarFilename
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import java.util.zip.ZipFile

@DisableCachingByDefault
abstract class ExtractPrivacySandboxSdkApiFromAsarTransform: TransformAction<GenericTransformParameters> {

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val asar = inputArtifact.get().asFile
        val sdkInterfaceDescriptor = transformOutputs.file(privacySandboxSdkStubJarFilename)
        ZipFile(asar).use {
            it.getInputStream(it.getEntry(privacySandboxSdkStubJarFilename)).use { jar ->
                sdkInterfaceDescriptor.writeBytes(jar.readAllBytes())
            }
        }
    }
}
