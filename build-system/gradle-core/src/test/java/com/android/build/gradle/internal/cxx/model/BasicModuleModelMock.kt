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

package com.android.build.gradle.internal.cxx.model

import com.android.SdkConstants
import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.SdkComponents
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.AbiSplitOptions
import com.android.build.gradle.internal.dsl.CoreBuildType
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.CoreNdkOptions
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.model.CoreCmakeOptions
import com.android.build.gradle.internal.model.CoreExternalNativeBuild
import com.android.build.gradle.internal.model.CoreNdkBuildOptions
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.ndk.NdkInfo
import com.android.build.gradle.internal.ndk.NdkPlatform
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.model.ApiVersion
import com.android.builder.model.ProductFlavor
import com.android.sdklib.AndroidVersion
import org.gradle.api.Project
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.io.File
import java.util.function.Supplier

/**
 * Set up up a mock for constructing [CxxModuleModel]. It takes a lot of plumbing so this can
 * be reused between tests that need this.
 */
open class BasicModuleModelMock {
    val tempFolder = TemporaryFolder()
    val throwUnmocked = RuntimeExceptionAnswer()
    val global = mock(
        GlobalScope::class.java,
        throwUnmocked
    )
    val extension = mock(
        AndroidConfig::class.java,
        throwUnmocked
    )
    val externalNativeBuild = mock(
        CoreExternalNativeBuild::class.java,
        throwUnmocked
    )
    val cmake = mock(
        CoreCmakeOptions::class.java,
        throwUnmocked
    )
    val ndkBuild = mock(
        CoreNdkBuildOptions::class.java,
        throwUnmocked
    )
    val ndkPlatform = mock(
        NdkPlatform::class.java,
        throwUnmocked
    )
    val baseVariantData = mock(
        BaseVariantData::class.java,
        throwUnmocked
    )
    val gradleVariantConfiguration = mock(
        GradleVariantConfiguration::class.java,
        throwUnmocked
    )
    val coreExternalNativeBuildOptions = mock(
        CoreExternalNativeBuildOptions::class.java,
        throwUnmocked
    )
    val coreNdkOptions = mock(
        CoreNdkOptions::class.java,
        throwUnmocked
    )

    val sdkComponents = mock(
        SdkComponents::class.java,
        throwUnmocked
    )
    val projectOptions = mock(
        ProjectOptions::class.java,
        throwUnmocked
    )
    val splits = mock(
        Splits::class.java,
        throwUnmocked
    )
    val abiSplitOptions = mock(
        AbiSplitOptions::class.java,
        throwUnmocked
    )
    val project = mock(
        Project::class.java,
        throwUnmocked
    )
    val coreBuildType = mock(
        CoreBuildType::class.java,
        throwUnmocked
    )
    val projectRootDir by lazy { tempFolder.newFolder("root-dir") }
    val localProperties by lazy {
        File(
            projectRootDir,
            SdkConstants.FN_LOCAL_PROPERTIES
        )
    }
    val ndkHandler = mock(
        NdkHandler::class.java,
        throwUnmocked
    )
    val productFlavor = mock(
        ProductFlavor::class.java,
        throwUnmocked
    )
    val ndkInfo = mock(
        NdkInfo::class.java
    )
    val minSdkVersion = mock(
        ApiVersion::class.java,
        throwUnmocked
    )
    private fun <T> any(): T {
        Mockito.any<T>()
        return uninitialized()
    }
    private fun <T> uninitialized(): T = null as T

    init {
        doReturn(extension).`when`(global).extension
        doReturn(externalNativeBuild).`when`(extension).externalNativeBuild
        doReturn(cmake).`when`(externalNativeBuild).cmake
        doReturn(ndkBuild).`when`(externalNativeBuild).ndkBuild
        doReturn(null).`when`(cmake).path
        doReturn(null).`when`(ndkBuild).path
        doReturn(null).`when`(cmake).buildStagingDirectory
        doReturn(null).`when`(ndkBuild).buildStagingDirectory
        doReturn(gradleVariantConfiguration).`when`(baseVariantData).variantConfiguration
        doReturn(coreExternalNativeBuildOptions).`when`(gradleVariantConfiguration)
            .externalNativeBuildOptions
        doReturn(coreNdkOptions).`when`(gradleVariantConfiguration).ndkConfig
        doReturn(setOf<String>()).`when`(coreNdkOptions).abiFilters
        doReturn("debug").`when`(baseVariantData).name
        tempFolder.create()
        projectRootDir.mkdirs()

        doReturn(sdkComponents).`when`(global).sdkComponents
        doReturn(projectOptions).`when`(global).projectOptions
        doReturn(project).`when`(global).project
        val app = tempFolder.newFolder("Source", "Android", "app")
        val buildDir = File(app, "build")
        val intermediates = File(buildDir, "intermediates")
        doReturn(intermediates).`when`(global).intermediatesDir
        doReturn(File("./sdk-folder")).`when`(sdkComponents).getSdkFolder()
        doReturn(false).`when`(projectOptions)
            .get(BooleanOption.ENABLE_NATIVE_COMPILER_SETTINGS_CACHE)
        doReturn(true)
            .`when`(projectOptions).get(BooleanOption.BUILD_ONLY_TARGET_ABI)
        doReturn(true)
            .`when`(projectOptions).get(BooleanOption.ENABLE_SIDE_BY_SIDE_CMAKE)
        doReturn(null)
            .`when`(projectOptions).get(StringOption.IDE_BUILD_TARGET_ABI)
        doReturn(false).`when`(extension).generatePureSplits
        doReturn(splits).`when`(extension).splits
        doReturn(abiSplitOptions).`when`(splits).abi
        doReturn(setOf<String>()).`when`(splits).abiFilters
        doReturn(false).`when`(abiSplitOptions).isUniversalApk
        doReturn(":app").`when`(project).path
        doReturn(app).`when`(project).projectDir
        doReturn(buildDir).`when`(project).buildDir
        doReturn(projectRootDir).`when`(project).rootDir
        doReturn("3.10.2").`when`(cmake).version
        doReturn(listOf(Abi.X86)).`when`(ndkPlatform).supportedAbis
        doReturn(listOf(Abi.X86)).`when`(ndkPlatform).defaultAbis
        doReturn(coreBuildType).`when`(gradleVariantConfiguration).buildType
        doReturn(true).`when`(coreBuildType).isDebuggable
        doReturn(Supplier { ndkHandler }).`when`(sdkComponents).ndkHandlerSupplier
        doReturn(ndkPlatform).`when`(ndkHandler).ndkPlatform
        doReturn(true).`when`(ndkPlatform).isConfigured
        doReturn(productFlavor).`when`(gradleVariantConfiguration).mergedFlavor
        doReturn(minSdkVersion).`when`(productFlavor).minSdkVersion
        doReturn(18).`when`(minSdkVersion).apiLevel
        doReturn(null).`when`(minSdkVersion).codename
        doReturn(ndkInfo).`when`(ndkPlatform).ndkInfo
        Mockito.`when`(ndkInfo.findSuitablePlatformVersion(
            "x86",
            AndroidVersion(minSdkVersion.apiLevel, minSdkVersion.codename))).thenReturn(18)
        doNothing().`when`(ndkHandler).writeNdkLocatorRecord(any())
    }

    class RuntimeExceptionAnswer : Answer<Any> {
        override fun answer(invocation: InvocationOnMock): Any {
            throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
        }
    }
}