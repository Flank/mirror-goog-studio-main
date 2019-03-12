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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.utils.FileUtils.mkdirs
import com.google.common.io.Files
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.slf4j.LoggerFactory
import java.io.File

abstract class DexingTransform : TransformAction<DexingTransform.Parameters> {

    interface Parameters: TransformParameters {
        @get:Input
        val minSdkVersion: Property<Int>
        @get:Input
        val debuggable: Property<Boolean>
    }

    // Desugaring is not supported until artifact transforms start passing dependencies
    private val enableDesugaring = false

    @get:Classpath
    @get:InputArtifact
    abstract val primaryInput: File

    override fun transform(outputs: TransformOutputs) {

        val name = Files.getNameWithoutExtension(primaryInput.name)
        val outputDir = outputs.dir(name)
        mkdirs(outputDir)

        val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
            parameters.minSdkVersion.get(),
            parameters.debuggable.get(),
            ClassFileProviderFactory(listOf()),
            ClassFileProviderFactory(listOf()),
            enableDesugaring,
            MessageReceiverImpl(
                SyncOptions.ErrorFormatMode.MACHINE_PARSABLE,
                LoggerFactory.getLogger(DexingTransform::class.java)
            )
        )

        ClassFileInputs.fromPath(primaryInput.toPath()).use {
                classFileInput -> classFileInput .entries { _ -> true }.use { classesInput ->
                    d8DexBuilder.convert(
                        classesInput,
                        outputDir.toPath(),
                        false
                    )
                }
        }
    }
}

fun getDexingArtifactConfigurations(scopes: Collection<VariantScope>): Set<DexingArtifactConfiguration> {
    return scopes.map {
        DexingArtifactConfiguration(
            it.minSdkVersion.featureLevel,
            it.variantConfiguration.buildType.isDebuggable
        )
    }.toSet()
}

data class DexingArtifactConfiguration(val minSdk: Int, val isDebuggable: Boolean)

@JvmField
val ATTR_MIN_SDK: Attribute<String> = Attribute.of("dexing-min-sdk", String::class.java)
@JvmField
val ATTR_IS_DEBUGGABLE: Attribute<String> =
    Attribute.of("dexing-is-debuggable", String::class.java)

fun getAttributeMap(minSdk: Int, isDebuggable: Boolean) =
    mapOf(ATTR_MIN_SDK to minSdk.toString(), ATTR_IS_DEBUGGABLE to isDebuggable.toString())