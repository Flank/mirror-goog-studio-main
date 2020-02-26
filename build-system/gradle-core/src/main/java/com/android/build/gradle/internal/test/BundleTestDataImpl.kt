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

package com.android.build.gradle.internal.test

import com.android.build.api.component.impl.AndroidTestPropertiesImpl
import com.android.build.gradle.internal.tasks.getApkFiles
import com.android.build.gradle.internal.testing.TestData
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Implementation of [TestData] for tests that run against
 * the bundle APKs.
 *
 * For the moment, that is only dynamic feature modules.
 */
internal class BundleTestDataImpl(
    private val testVariantData: AndroidTestPropertiesImpl,
    testApkDir: Provider<Directory>,
    private val moduleName: String?,
    private val apkBundle: FileCollection
) : AbstractTestDataImpl(
    testVariantData.variantDslInfo,
    testVariantData.variantSources,
    testApkDir,
    null
) {

    override val applicationId: Provider<String>
        get() = testVariantData.applicationId

    override val testedApplicationId: Provider<String>
        get() = testVariantData.testedConfig.applicationId

    override val isLibrary: Boolean
        get() = testVariantData.testedVariant.variantType.isAar

    override fun getTestedApks(
        deviceConfigProvider: DeviceConfigProvider,
        logger: ILogger
    ): ImmutableList<File> {
        if (moduleName != null && deviceConfigProvider.apiLevel < 21) {
            // Bundle tool fuses APKs below 21, requesting a module will return an error even if that
            // module is fused.
            // TODO(https://issuetracker.google.com/119663247): Return the fused APK if the requested module was fused.
            logger.warning("Testing dynamic features on devices API < 21 is not currently supported.")
            return ImmutableList.of<File>()
        }
        return getApkFiles(apkBundle.singleFile.toPath(), deviceConfigProvider, moduleName).map{it.toFile()}.toImmutableList()
    }

    override fun getTestedApksFromBundle(): FileCollection? = apkBundle

}
