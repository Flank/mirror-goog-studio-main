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

package com.android.build.gradle.internal.signing

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.SigningConfigUtils
import com.android.build.gradle.options.SigningOptions
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.Serializable

/**
 * Encapsulates different ways to get the signing config information.
 *
 * This class is designed to be used by tasks that are interested in the actual signing config
 * information, not the ways to get that information (i.e., *how* to get the info is internal to
 * this class).
 *
 * Those tasks should then annotate this object with `@Nested`, so that if the signing config
 * information has changed, the tasks will be re-executed with the updated info.
 */
class SigningConfigProvider(

    /** When not null, the signing config information can be obtained directly in memory. */
    @get:Nested
    @get:Optional
    val signingConfigData: SigningConfigData?,

    /** When not null, the signing config information can be obtained from a file. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val signingConfigFileCollection: FileCollection?
) {

    init {
        check((signingConfigData != null).xor(signingConfigFileCollection != null))
    }

    /** Resolves this provider to get the signing config information. */
    fun resolve(): SigningConfigData? {
        return convertToParams().resolve()
    }

    /** Converts this provider to [SigningConfigProviderParams] to be used by Gradle workers. */
    fun convertToParams(): SigningConfigProviderParams {
        return SigningConfigProviderParams(
            signingConfigData,
            signingConfigFileCollection?.let {
                SigningConfigUtils.getSigningConfigFile(it.singleFile)
            }
        )
    }

    companion object {

        @JvmStatic
        fun create(variantScope: VariantScope): SigningConfigProvider {
            val signingOptions =
                SigningOptions.readSigningOptions(variantScope.globalScope.projectOptions)
            // If the signing config information is passed from the IDE, tasks can get this
            // information directly without having to read from a file. This helps avoid writing the
            // signing config information to disk (see bug 137210434).
            return if (signingOptions != null
                    && signingOptions.v1Enabled != null && signingOptions.v2Enabled != null) {
                val signingConfig = SigningConfigData(
                    name = "SigningConfigReceivedFromIDE",
                    storeType = signingOptions.storeType, // The IDE may or may not send storeType
                    storeFile = File(signingOptions.storeFile),
                    storePassword = signingOptions.storePassword,
                    keyAlias = signingOptions.keyAlias,
                    keyPassword = signingOptions.keyPassword,
                    v1SigningEnabled = signingOptions.v1Enabled!!,
                    v2SigningEnabled = signingOptions.v2Enabled!!
                )
                SigningConfigProvider(signingConfig, null)
            } else {
                SigningConfigProvider(null, variantScope.signingConfigFileCollection)
            }
        }
    }
}

/**
 * Similar to [SigningConfigProvider], but uses a [File] instead of a [FileCollection] to be used
 * by Gradle workers.
 */
class SigningConfigProviderParams(
    private val signingConfigData: SigningConfigData?,
    private val signingConfigFile: File?
) : Serializable {

    init {
        check((signingConfigData != null).xor(signingConfigFile != null))
    }

    /** Resolves this provider to get the signing config information. */
    fun resolve(): SigningConfigData? {
        return signingConfigData?: SigningConfigUtils.load(signingConfigFile!!)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}