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

import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.build.gradle.internal.cxx.configure.CmakeLocator
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.configure.defaultCmakeVersion
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.AbiSplitOptions
import com.android.build.gradle.internal.dsl.CmakeOptions
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.ExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.NdkBuildOptions
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.ndk.NdkInstallStatus
import com.android.build.gradle.internal.ndk.NdkPlatform
import com.android.build.gradle.internal.ndk.NdkR19Info
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.android.prefs.AndroidLocationsProvider
import com.android.repository.Revision
import com.android.utils.FileUtils.join
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.io.File
import java.util.*

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
    val home = join(tempFolder, "home")
    val projects = join(tempFolder, "projects")
    val throwUnmocked = RuntimeExceptionAnswer()
    val global = mock(
        GlobalScope::class.java,
        throwUnmocked
    )
    val projectInfo = mock(
        ProjectInfo::class.java,
        throwUnmocked
    )

    val variantImpl: VariantImpl = mock(
        VariantImpl::class.java,
        throwUnmocked
    )

    val taskCreationServices: TaskCreationServices = mock(
        TaskCreationServices::class.java,
        throwUnmocked
    )

    val issueReporter: IssueReporter = mock(IssueReporter::class.java)

    val externalNativeBuild: ExternalNativeBuild = mock(
        ExternalNativeBuild::class.java,
        throwUnmocked
    )
    val cmake: CmakeOptions = mock(
        CmakeOptions::class.java,
        throwUnmocked
    )
    val ndkBuild: NdkBuildOptions = mock(
        NdkBuildOptions::class.java,
        throwUnmocked
    )
    val ndkInstallStatus = NdkInstallStatus.Valid(
        mock(
            NdkPlatform::class.java,
            throwUnmocked
    ))
    val coreExternalNativeBuildOptions = mock(
        ExternalNativeBuildOptions::class.java,
        throwUnmocked
    )
    val variantExternalNativeBuild = mock(
        com.android.build.api.variant.ExternalNativeBuild::class.java
    )

    val mergedNdkConfig = mock(
        MergedNdkConfig::class.java,
        throwUnmocked
    )

    val androidLocationProvider = mock(
        AndroidLocationsProvider::class.java
    )

    val sdkComponents = mock(
        SdkComponentsBuildService::class.java,
        throwUnmocked
    )
    val projectOptions = mock(
        ProjectOptions::class.java,
        throwUnmocked
    )
    val project = mock(
        Project::class.java,
        throwUnmocked
    )

    val allPlatformsProjectRootDir = join(projects, "MyProject")
    val projectRootDir = join(allPlatformsProjectRootDir,  "Source", "Android")
    val sdkDir = join(home, "Library", "Android", "sdk")
    val cmakeDir = join(sdkDir, "cmake", DEFAULT_CMAKE_VERSION, "bin")
    val ndkHandler = mock(
        SdkComponentsBuildService.VersionedNdkHandler::class.java,
        throwUnmocked
    )

    val minSdkVersion = AndroidVersionImpl(19)
    val cmakeFinder = mock(
        CmakeLocator::class.java,
        throwUnmocked
    )

    val buildFeatures = mock(BuildFeatureValues::class.java, throwUnmocked)

    val gradle = mock(
        Gradle::class.java
    )

    val variantDslInfo = mock(
        VariantDslInfo::class.java,
        throwUnmocked
    )

    val configurationParameters by lazy {
        tryCreateConfigurationParameters(
            projectOptions,
            variantImpl
        )!!
    }

    private fun <T> any(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    private fun <T> uninitialized(): T = null as T

    fun mockModule(appName : String) : File {
        val appFolder = join(projectRootDir, appName)

        val appFolderDirectory = mock(
            Directory::class.java,
            throwUnmocked
        )
        doReturn(appFolder).`when`(appFolderDirectory).asFile

        val buildDir = File(appFolder, "build")
        val intermediates = File(buildDir, "intermediates")
        val extension: BaseExtension = mock(
            BaseExtension::class.java,
            throwUnmocked
        )
        val abiSplitOptions = mock(
            AbiSplitOptions::class.java,
            throwUnmocked
        )
        val splits = mock(
            Splits::class.java,
            throwUnmocked
        )

        val prefabArtifactCollection = mock(ArtifactCollection::class.java, throwUnmocked)
        val prefabFileCollection = mock(FileCollection::class.java, throwUnmocked)

        doReturn(project).`when`(projectInfo).getProject()
        doReturn(intermediates).`when`(projectInfo).getIntermediatesDir()
        doReturn(appFolder).`when`(project).projectDir
        doReturn(buildDir).`when`(project).buildDir
        doReturn(join(buildDir, "build.gradle")).`when`(project).buildFile
        doReturn(projectRootDir).`when`(project).rootDir
        doReturn(extension).`when`(global).extension

        doReturn(appFolderDirectory).`when`(projectInfo).projectDirectory

        doReturn(extension).`when`(global).extension
        doReturn(externalNativeBuild).`when`(extension).externalNativeBuild
        doReturn(false).`when`(extension).generatePureSplits
        doReturn("12.3.4").`when`(extension).compileSdkVersion
        doReturn("29.3.4").`when`(extension).ndkVersion
        doReturn("/path/to/nowhere").`when`(extension).ndkPath

        doReturn(splits).`when`(extension).splits

        val variantScope: VariantScope = mock(
            VariantScope::class.java,
            throwUnmocked
        )

        val baseVariantData = mock(
            BaseVariantData::class.java,
            throwUnmocked
        )

        doReturn(global).`when`(this.variantImpl).globalScope
        doReturn(variantScope).`when`(this.variantImpl).variantScope
        doReturn(baseVariantData).`when`(this.variantImpl).variantData
        doReturn(taskCreationServices).`when`(this.variantImpl).services
        doReturn(issueReporter).`when`(this.taskCreationServices).issueReporter
        doReturn(projectInfo).`when`(this.taskCreationServices).projectInfo

        val variantDependencies = Mockito.mock(VariantDependencies::class.java)
        doReturn(prefabArtifactCollection).`when`(variantDependencies).getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
        )
        doReturn(variantDependencies).`when`(this.variantImpl).variantDependencies
        doReturn(minSdkVersion).`when`(this.variantImpl).minSdkVersion
        doReturn(prefabFileCollection).`when`(prefabArtifactCollection).artifactFiles
        doReturn(emptyList<File>().iterator()).`when`(prefabFileCollection).iterator()

        doReturn(coreExternalNativeBuildOptions).`when`(variantDslInfo).externalNativeBuildOptions
        doReturn(variantExternalNativeBuild).`when`(this.variantImpl).externalNativeBuild
        doReturn(mergedNdkConfig).`when`(variantDslInfo).ndkConfig
        doReturn(abiSplitOptions).`when`(splits).abi
        doReturn(setOf<String>()).`when`(splits).abiFilters
        doReturn(false).`when`(abiSplitOptions).isUniversalApk
        doReturn(":$appName").`when`(project).path
        return appFolder
    }

    init {
            val ndkFolder = join(sdkDir, "ndk", ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION)
            val meta = join(ndkFolder, "meta")
            meta.mkdirs()
            cmakeDir.mkdirs()
            File(meta, "platforms.json").writeText(platformsJson)
            File(meta, "abis.json").writeText(abisJson)
            val osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH)
            val osType = when {
                osName.contains("windows") -> "windows"
                osName.contains("mac") -> "darwin"
                else -> "linux"
            }
            val ndkPrebuilts = join(ndkFolder, "prebuilt")
            val ndkPrebuiltsHostRoot = join(ndkPrebuilts, "$osType-x86_64")
            ndkPrebuiltsHostRoot.mkdirs()
            val stls = listOf(
                "arm-linux-androideabi/libc++_shared.so",
                "aarch64-linux-android/libc++_shared.so",
                "i686-linux-android/libc++_shared.so",
                "x86_64-linux-android/libc++_shared.so"
            )
            val ndkStlRoot = join(ndkFolder, "toolchains/llvm/prebuilt/$osType-x86_64/sysroot/usr/lib")
            stls
                .map { join(ndkStlRoot, it) }
                .onEach { it.parentFile.mkdirs() }
                .onEach { it.writeText("fake STL generated by BasicModuleModelMock") }

            doReturn(cmake).`when`(externalNativeBuild).cmake
            doReturn(ndkBuild).`when`(externalNativeBuild).ndkBuild
            doReturn(null).`when`(cmake).path
            doReturn(null).`when`(ndkBuild).path
            doReturn(null).`when`(cmake).buildStagingDirectory
            doReturn(null).`when`(ndkBuild).buildStagingDirectory
            doReturn(Mockito.mock(SetProperty::class.java)).`when`(variantExternalNativeBuild).abiFilters
            doReturn(Mockito.mock(ListProperty::class.java)).`when`(variantExternalNativeBuild).arguments
            doReturn(Mockito.mock(ListProperty::class.java)).`when`(variantExternalNativeBuild).cFlags
            doReturn(Mockito.mock(ListProperty::class.java)).`when`(variantExternalNativeBuild).cppFlags
            doReturn(Mockito.mock(SetProperty::class.java)).`when`(variantExternalNativeBuild).targets
            doReturn(setOf<String>()).`when`(mergedNdkConfig).abiFilters
            doReturn("debug").`when`(variantImpl).name
            doReturn(buildFeatures).`when`(variantImpl).buildFeatures

            projectRootDir.mkdirs()
            sdkDir.mkdirs()

            doReturn(FakeGradleProvider(sdkComponents)).`when`(global).sdkComponents
            doReturn(projectOptions).`when`(taskCreationServices).projectOptions

            doReturn(FakeGradleProvider(FakeGradleDirectory(sdkDir))).`when`(sdkComponents).sdkDirectoryProvider
            doReturn(false).`when`(projectOptions)
                .get(BooleanOption.ENABLE_PROFILE_JSON)
            doReturn(BooleanOption.ENABLE_CMAKE_BUILD_COHABITATION.defaultValue).`when`(projectOptions)
                .get(BooleanOption.ENABLE_CMAKE_BUILD_COHABITATION)
            doReturn(true)
                .`when`(projectOptions).get(BooleanOption.BUILD_ONLY_TARGET_ABI)
            doReturn(false).`when`(buildFeatures).prefab
            doReturn(true)
                .`when`(projectOptions).get(BooleanOption.ENABLE_SIDE_BY_SIDE_CMAKE)
            doReturn(null)
                .`when`(projectOptions).get(StringOption.IDE_BUILD_TARGET_ABI)
            doReturn("verbose")
                .`when`(projectOptions).get(StringOption.NATIVE_BUILD_OUTPUT_LEVEL)

            doReturn(defaultCmakeVersion.toString()).`when`(cmake).version
            doReturn(listOf(Abi.X86, Abi.X86_64, Abi.ARMEABI_V7A, Abi.ARM64_V8A)).`when`(ndkInstallStatus.getOrThrow()).supportedAbis
            doReturn(listOf(Abi.X86)).`when`(ndkInstallStatus.getOrThrow()).defaultAbis

            doReturn(ndkHandler).`when`(sdkComponents).versionedNdkHandler(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString()
            )
            doReturn(ndkInstallStatus).`when`(ndkHandler).ndkPlatform
            doReturn(ndkInstallStatus).`when`(ndkHandler).getNdkPlatform(true)
            doReturn(variantDslInfo).`when`(variantImpl).variantDslInfo
            doReturn(true).`when`(variantImpl).debuggable

            val ndkInfo = NdkR19Info(ndkFolder)
            doReturn(ndkInfo).`when`(ndkInstallStatus.getOrThrow()).ndkInfo
            doReturn(ndkFolder).`when`(ndkInstallStatus.getOrThrow()).ndkDirectory
            doReturn(Revision.parseRevision(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION)).`when`(ndkInstallStatus.getOrThrow()).revision
            doReturn(cmakeDir.parentFile).`when`(cmakeFinder)
                .findCmakePath(any(), any(), any(), any(), any())

            doReturn(null).`when`(gradle).parent

            mockModule("app1")


        }

    class RuntimeExceptionAnswer : Answer<Any> {
        override fun answer(invocation: InvocationOnMock): Any {
            throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
        }
    }
}
