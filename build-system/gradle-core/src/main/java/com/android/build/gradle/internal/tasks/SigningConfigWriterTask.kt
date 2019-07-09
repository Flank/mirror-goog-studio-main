/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.google.common.hash.Hashing
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Task that writes the SigningConfig information to a file.
 */
@CacheableTask
abstract class SigningConfigWriterTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val validatedSigningOutput: DirectoryProperty

    @get:Nested
    @get:Optional
    var signingConfigData: SigningConfigData? = null
        internal set

    public override fun doTaskAction() {
        SigningConfigUtils.save(
            outputDirectory.get().asFile,
            signingConfigData?.toSigningConfig()
        )
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<SigningConfigWriterTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("signingConfigWriter")
        override val type: Class<SigningConfigWriterTask>
            get() = SigningConfigWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out SigningConfigWriterTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.SIGNING_CONFIG,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                SigningConfigWriterTask::outputDirectory
            )
        }

        override fun configure(task: SigningConfigWriterTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.VALIDATE_SIGNING_CONFIG,
                task.validatedSigningOutput
            )

            // convert to a serializable signing config. Objects from DSL are not serializable.
            val signingConfig = variantScope.variantConfiguration.signingConfig?.let {
                SigningConfig(it.name).initWith(it)
            }
            task.signingConfigData = signingConfig?.let { SigningConfigData.fromSigningConfig(it) }
        }
    }
}

/**
 * A copy of the [SigningConfig] object, which contains the properties that the
 * [SigningConfigWriterTask] is interested in, together with their input annotations.
 *
 * Note that this is the recommended treatment for @Nested objects: Tasks should not share a common
 * object for input/output specification because different tasks may be interested in different
 * subsets of the properties as well as different types of input normalization for each property.
 * Therefore, each task should take a snapshot of the properties and annotate them privately.
 */
class SigningConfigData(
    @get:Input
    val name: String,

    @get:Input
    @get:Optional
    val storeType: String?,

    // When the SigningConfig object is written to disk, only the store file's path is written,
    // while the store file's contents are ignored. Therefore, only the store file's path is
    // considered an input.
    @get:Input
    @get:Optional
    val storeFilePath: String?,

    // Don't set the password as @Input because we don't want it to be written to disk. (Although
    // Gradle typically hashes stuff before storing it to disk, it's better to be prudent.) Instead,
    // we set the password's hash as @Input (see below).
    @get:Internal
    val storePassword: String?,

    @get:Input
    @get:Optional
    val keyAlias: String?,

    // Don't set the password as @Input because we don't want it to be written to disk. (Although
    // Gradle typically hashes stuff before storing it to disk, it's better to be prudent.) Instead,
    // we set the password's hash as @Input (see below).
    @get:Internal
    val keyPassword: String?,

    @get:Input
    val v1SigningEnabled: Boolean,

    @get:Input
    val v2SigningEnabled: Boolean
) {

    @Input
    @Optional
    fun getStorePasswordHash(): String? =
        storePassword?.let { Hashing.sha256().hashUnencodedChars(it).toString() }

    @Input
    @Optional
    fun getKeyPasswordHash(): String? =
        keyPassword?.let { Hashing.sha256().hashUnencodedChars(it).toString() }

    fun toSigningConfig(): SigningConfig {
        val signingConfig = SigningConfig(name)
        signingConfig.storeType = storeType
        signingConfig.storeFile = storeFilePath?.let { File(it) }
        signingConfig.storePassword = storePassword
        signingConfig.keyAlias = keyAlias
        signingConfig.keyPassword = keyPassword
        signingConfig.isV1SigningEnabled = v1SigningEnabled
        signingConfig.isV2SigningEnabled = v2SigningEnabled
        return signingConfig
    }

    companion object {

        fun fromSigningConfig(signingConfig: SigningConfig): SigningConfigData {
            return SigningConfigData(
                name = signingConfig.name,
                storeType = signingConfig.storeType,
                storeFilePath = signingConfig.storeFile?.path,
                storePassword = signingConfig.storePassword,
                keyAlias = signingConfig.keyAlias,
                keyPassword = signingConfig.keyPassword,
                v1SigningEnabled = signingConfig.isV1SigningEnabled,
                v2SigningEnabled = signingConfig.isV2SigningEnabled
            )
        }
    }
}