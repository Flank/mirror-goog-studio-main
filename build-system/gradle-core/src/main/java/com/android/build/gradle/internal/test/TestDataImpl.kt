/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.test

import com.android.build.api.component.impl.AndroidTestPropertiesImpl
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.test.BuiltArtifactsSplitOutputMatcher.computeBestOutput
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * Implementation of [TestData] on top of a [TestVariantData]
 */
class TestDataImpl(
    private val testVariantData: AndroidTestPropertiesImpl,
    testApkDir: Provider<Directory>,
    testedApksDir: FileCollection?
) : AbstractTestDataImpl(
    testVariantData.variantDslInfo,
    testVariantData.variantSources,
    testApkDir,
    testedApksDir
) {

    init {
        if (testVariantData
                .outputs
                .getSplitsByType(
                    VariantOutputConfiguration.OutputType.ONE_OF_MANY
                )
                .isNotEmpty()
        ) {
            throw RuntimeException("Multi-output in test variant not yet supported")
        }
    }

    @Throws(
        ParserConfigurationException::class,
        SAXException::class,
        IOException::class
    )
    override fun load(folder: File) {
        // do nothing, there is nothing in the metadata file we cannot get from the tested scope.
    }

    override val applicationId: Provider<String>
        get() = testVariantData.applicationId

    override val testedApplicationId: Provider<String>
        get() = testVariantData.testedConfig.applicationId

    override val isLibrary: Boolean
        get() {
            return testVariantData.testedVariant.variantType.isAar
        }

    override fun getTestedApks(
        deviceConfigProvider: DeviceConfigProvider, logger: ILogger
    ): ImmutableList<File> {
        val testedVariant = testVariantData.testedVariant
        val apks =
            ImmutableList.builder<File>()
        val builtArtifacts = BuiltArtifactsLoaderImpl()
            .load(
                testedVariant
                    .artifacts
                    .getFinalProduct(
                        InternalArtifactType.APK
                    )
                    .get()
            )
            ?: return ImmutableList.of()
        apks.addAll(
            computeBestOutput(
                deviceConfigProvider,
                builtArtifacts,
                testedVariant.variantDslInfo.supportedAbis
            )
        )
        return apks.build()
    }
}