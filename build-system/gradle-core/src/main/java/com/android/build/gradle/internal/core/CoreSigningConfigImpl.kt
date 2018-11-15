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

package com.android.build.gradle.internal.core

import com.android.build.gradle.internal.dsl.CoreSigningConfig
import com.android.builder.model.SigningConfig
import com.android.builder.signing.DefaultSigningConfig
import com.google.common.base.MoreObjects
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * A serializable SigningConfig with added annotations to be used with @Nested in a Gradle task and
 * in Gradle workers.
 */
class CoreSigningConfigImpl @Inject constructor(name: String) : DefaultSigningConfig(name),
    CoreSigningConfig, Serializable {

    fun initWith(that: SigningConfig): CoreSigningConfigImpl {
        storeFile = that.storeFile
        storePassword = that.storePassword
        keyAlias = that.keyAlias
        keyPassword = that.keyPassword
        isV1SigningEnabled = that.isV1SigningEnabled
        isV2SigningEnabled = that.isV2SigningEnabled
        storeType = that.storeType
        return this
    }

    @Input
    override fun getStorePassword(): String? = super.getStorePassword()

    @Input
    override fun getKeyPassword(): String? = super.getKeyPassword()

    @Input
    override fun getStoreType(): String? = super.getStoreType()

    @InputFile
    @Optional
    override fun getStoreFile(): File? = super.getStoreFile()

    @Input
    override fun getKeyAlias(): String? = super.getKeyAlias()

    @Input
    override fun isV1SigningEnabled(): Boolean = super.isV1SigningEnabled()

    @Input
    override fun isV2SigningEnabled(): Boolean = super.isV2SigningEnabled()

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("name", mName)
            .add("storeFile", if (storeFile != null) storeFile!!.absolutePath else "null")
            .add("storePassword", storePassword)
            .add("keyAlias", keyAlias)
            .add("keyPassword", keyPassword)
            .add("storeType", storeFile)
            .add("v1SigningEnabled", isV1SigningEnabled)
            .add("v2SigningEnabled", isV2SigningEnabled)
            .toString()
    }
}