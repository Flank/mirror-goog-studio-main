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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CacheableTask
abstract class PrivacySandboxSdkManifestMergerTask: FusedLibraryManifestMergerTask() {

    @get: InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainManifestFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(FusedLibraryManifestMergerWorkAction::class.java) {
            configureParameters(it)
            it.mainAndroidManifest.set(mainManifestFile)
        }
    }

    class CreationAction(creationConfig: PrivacySandboxSdkVariantScope):
        AbstractCreationAction<PrivacySandboxSdkManifestMergerTask>(creationConfig) {

        override val type: Class<PrivacySandboxSdkManifestMergerTask>
            get() = PrivacySandboxSdkManifestMergerTask::class.java

        override fun configure(task: PrivacySandboxSdkManifestMergerTask) {
            super.configure(task)
            task.mainManifestFile.set(
                creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.SANDBOX_MANIFEST)
            )
        }
    }
}
