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

import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.SdkComponents
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.cxx.configure.CmakeLocator
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
import com.android.build.gradle.internal.ndk.NdkInstallStatus
import com.android.build.gradle.internal.ndk.NdkPlatform
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.DefaultApiVersion
import com.android.builder.model.ProductFlavor
import com.android.repository.Revision
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils.join
import org.gradle.api.Project
import org.mockito.Mockito
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
    private val abisJson = """
    {
      "armeabi-v7a": {
        "bitness": 32,
        "default": true,
        "deprecated": false
      },
      "arm64-v8a": {
        "bitness": 64,
        "default": true,
        "deprecated": false
      },
      "x86": {
        "bitness": 32,
        "default": true,
        "deprecated": false
      },
      "x86_64": {
        "bitness": 64,
        "default": true,
        "deprecated": false
      }
    }
    """.trimIndent()

    private val platformsJson = """
    {
      "min": 16,
      "max": 29,
      "aliases": {
        "20": 19,
        "25": 24,
        "J": 16,
        "J-MR1": 17,
        "J-MR2": 18,
        "K": 19,
        "L": 21,
        "L-MR1": 22,
        "M": 23,
        "N": 24,
        "N-MR1": 24,
        "O": 26,
        "O-MR1": 27,
        "P": 28,
        "Q": 29
      }
    }
    """.trimIndent()
    val tempFolder = createTempDir()
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
    val ndkInstallStatus = NdkInstallStatus.Valid(
        mock(
            NdkPlatform::class.java,
            throwUnmocked
    ))
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
    val projectRootDir = join(tempFolder, "project-dir")
    val sdkDir = join(tempFolder, "sdk-dir")
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
    val minSdkVersion = DefaultApiVersion(18)
    val cmakeFinder = mock(
        CmakeLocator::class.java,
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
        projectRootDir.mkdirs()
        sdkDir.mkdirs()

        doReturn(sdkComponents).`when`(global).sdkComponents
        doReturn(projectOptions).`when`(global).projectOptions
        doReturn(project).`when`(global).project
        val app = join(projectRootDir, "Source", "Android", "app")
        val buildDir = File(app, "build")
        val intermediates = File(buildDir, "intermediates")
        doReturn(intermediates).`when`(global).intermediatesDir
        doReturn(sdkDir).`when`(sdkComponents).getSdkFolder()
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
        doReturn(listOf(Abi.X86)).`when`(ndkInstallStatus.getOrThrow()).supportedAbis
        doReturn(listOf(Abi.X86)).`when`(ndkInstallStatus.getOrThrow()).defaultAbis
        doReturn(coreBuildType).`when`(gradleVariantConfiguration).buildType
        doReturn(true).`when`(coreBuildType).isDebuggable
        doReturn(Supplier { ndkHandler }).`when`(sdkComponents).ndkHandlerSupplier
        doReturn(ndkInstallStatus).`when`(ndkHandler).ndkPlatform
        doReturn(productFlavor).`when`(gradleVariantConfiguration).mergedFlavor
        doReturn(minSdkVersion).`when`(productFlavor).minSdkVersion
        doReturn(ndkInfo).`when`(ndkInstallStatus.getOrThrow()).ndkInfo
        Mockito.`when`(ndkInfo.findSuitablePlatformVersion(
            "x86",
            AndroidVersion(minSdkVersion.apiLevel, minSdkVersion.codename))).thenReturn(18)
        doNothing().`when`(ndkHandler).writeNdkLocatorRecord(any())
        val ndkFolder = join(sdkDir, "ndk", "19.2.3")
        doReturn(ndkFolder).`when`(ndkInstallStatus.getOrThrow()).ndkDirectory
        doReturn(Revision.parseRevision("19.2.3")).`when`(ndkInstallStatus.getOrThrow()).revision
        doReturn(join(sdkDir, "cmake", "3.10.2")).`when`(cmakeFinder)
            .findCmakePath(any(), any(), any(), any())

        val meta = join(ndkFolder, "meta")
        meta.mkdirs()
        File(meta, "platforms.json").writeText(platformsJson)
        File(meta, "abis.json").writeText(abisJson)
    }

    class RuntimeExceptionAnswer : Answer<Any> {
        override fun answer(invocation: InvocationOnMock): Any {
            throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
        }
    }
}