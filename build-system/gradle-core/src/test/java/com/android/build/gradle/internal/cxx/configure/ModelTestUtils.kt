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


package com.android.build.gradle.internal.cxx.configure

import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.api.variant.impl.VariantBuilderImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.dsl.CmakeOptions
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.ExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.ExternalNativeCmakeOptions
import com.android.build.gradle.internal.dsl.NdkBuildOptions
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.ndk.*
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.prefs.AndroidLocationsProvider
import com.android.repository.Revision
import com.android.utils.FileUtils.join
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import org.gradle.api.provider.Provider

fun createCmakeProjectCxxAbiForTest(projectParentFolder: TemporaryFolder): CxxAbiModel {
    val global = Mockito.mock(GlobalScope::class.java)
    val projectOptions = Mockito.mock(ProjectOptions::class.java)
    val extension = Mockito.mock(BaseExtension::class.java)
    val externalNativeBuild = Mockito.mock(ExternalNativeBuild::class.java)
    val externalNativeBuildOptions = Mockito.mock(ExternalNativeBuildOptions::class.java)
    val externalNativeCmakeOptions = Mockito.mock(ExternalNativeCmakeOptions::class.java)
    val cmake = Mockito.mock(CmakeOptions::class.java)
    val ndkBuild = Mockito.mock(NdkBuildOptions::class.java)
    val project = Mockito.mock(Project::class.java)
    val androidLocationProvider = Mockito.mock(AndroidLocationsProvider::class.java)
    val sdkComponents = Mockito.mock(SdkComponentsBuildService::class.java)
    val sdkDirectoryProvider = Mockito.mock(Provider::class.java) as Provider<Directory>
    val sdkDirectory = Mockito.mock(Directory::class.java)
    val ndkHandler = Mockito.mock(NdkHandler::class.java)
    val ndkPlatform = Mockito.mock(NdkPlatform::class.java)
    val ndkInfo = Mockito.mock(NdkInfo::class.java)
    val ndkInstallStatus = NdkInstallStatus.Valid(ndkPlatform)
    val variantImpl = Mockito.mock(VariantImpl::class.java)
    val baseVariantData = Mockito.mock(BaseVariantData::class.java)
    val variantScope = Mockito.mock(VariantScope::class.java)
    val buildFeatures = Mockito.mock(BuildFeatureValues::class.java)
    val variantDslInfo = Mockito.mock(VariantDslInfo::class.java)
    val splits = Mockito.mock(Splits::class.java)
    val mergedNdkConfig = Mockito.mock(MergedNdkConfig::class.java)
    val projectInfo = Mockito.mock(ProjectInfo::class.java)
    val minSdkVersion = AndroidVersionImpl(19)
    Mockito.doReturn(global).`when`(variantImpl).globalScope
    Mockito.doReturn(variantScope).`when`(variantImpl).variantScope
    Mockito.doReturn(baseVariantData).`when`(variantImpl).variantData
    projectParentFolder.create()
    val projectDir = projectParentFolder.newFolder("project")
    val moduleDir = join(projectDir, "module")
    val buildDir = join(projectDir, "build")
    val sdkDir = projectParentFolder.newFolder("sdk")
    val ndkFolder = join(sdkDir, "ndk", "17.2.4988734")
    val ndkSourceProperties = join(ndkFolder, "source.properties")
    val intermediatesDir = join(projectDir, "intermediates")
    ndkSourceProperties.parentFile.mkdirs()
    ndkSourceProperties.writeText(
        """
                Pkg.Desc = Android NDK
                Pkg.Revision = 17.2.4988734
            """.trimIndent())
    Mockito.doReturn(extension).`when`(global).extension
    Mockito.doReturn(splits).`when`(extension).splits
    Mockito.doReturn(externalNativeBuild).`when`(extension).externalNativeBuild
    Mockito.doReturn(cmake).`when`(externalNativeBuild).cmake
    Mockito.doReturn(ndkBuild).`when`(externalNativeBuild).ndkBuild
    Mockito.doReturn(join(moduleDir, "src", "CMakeLists.txt")).`when`(cmake).path
    Mockito.doReturn(projectInfo).`when`(projectInfo).getProject()
    Mockito.doReturn(projectDir).`when`(project).rootDir
    Mockito.doReturn(moduleDir).`when`(project).projectDir
    Mockito.doReturn("app:").`when`(project).path
    Mockito.doReturn(intermediatesDir).`when`(projectInfo).getIntermediatesDir()
    Mockito.doReturn(File("build.gradle")).`when`(project).buildFile
    Mockito.doReturn(FakeGradleProvider(sdkComponents)).`when`(global).sdkComponents
    Mockito.doReturn(ndkHandler).`when`(sdkComponents).versionedNdkHandler(
        Mockito.anyString(), Mockito.anyString(), Mockito.anyString()
    )
    Mockito.doReturn(sdkDirectoryProvider).`when`(sdkComponents).sdkDirectoryProvider
    Mockito.doReturn(sdkDirectory).`when`(sdkDirectoryProvider).get()
    Mockito.doReturn(File(".")).`when`(sdkDirectory).asFile
    Mockito.doReturn(ndkInstallStatus).`when`(ndkHandler).ndkPlatform
    Mockito.doReturn(ndkInstallStatus).`when`(ndkHandler).getNdkPlatform(true)
    Mockito.doReturn(ndkInfo).`when`(ndkPlatform).ndkInfo
    Mockito.doReturn(Revision.parseRevision(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION)).`when`(ndkPlatform).revision
    Mockito.doReturn(Stl.LIBCXX_SHARED).`when`(ndkInfo).getDefaultStl(NativeBuildSystem.CMAKE)
    Mockito.doReturn(buildDir).`when`(project).buildDir
    Mockito.doReturn(ndkFolder).`when`(ndkInstallStatus.getOrThrow()).ndkDirectory
    Mockito.doReturn("debug").`when`(variantImpl).name
    Mockito.doReturn(buildFeatures).`when`(variantImpl).buildFeatures
    Mockito.doReturn(false).`when`(buildFeatures).prefab
    Mockito.doReturn(variantDslInfo).`when`(variantImpl).variantDslInfo
    Mockito.doReturn(minSdkVersion).`when`(variantImpl).minSdkVersion
    Mockito.doReturn(true).`when`(variantImpl).debuggable
    Mockito.doReturn(externalNativeBuildOptions).`when`(variantDslInfo).externalNativeBuildOptions
    Mockito.doReturn(externalNativeCmakeOptions).`when`(externalNativeBuildOptions).externalNativeCmakeOptions
    Mockito.doReturn(mergedNdkConfig).`when`(variantDslInfo).ndkConfig
    Mockito.doReturn(setOf<String>()).`when`(externalNativeCmakeOptions).abiFilters
    Mockito.doReturn(listOf<String>()).`when`(externalNativeCmakeOptions).arguments
    Mockito.doReturn(listOf<String>()).`when`(externalNativeCmakeOptions).cFlags
    Mockito.doReturn(listOf<String>()).`when`(externalNativeCmakeOptions).cppFlags
    Mockito.doReturn(setOf<String>()).`when`(externalNativeCmakeOptions).targets
    Mockito.doReturn(setOf<String>()).`when`(mergedNdkConfig).abiFilters
    val componentParameters = tryCreateConfigurationParameters(
            projectOptions,
            variantImpl
    )!!
    val module = createCxxModuleModel(sdkComponents, androidLocationProvider, componentParameters)
    val variant = createCxxVariantModel(componentParameters, module)
    return createCxxAbiModel(
        sdkComponents,
        componentParameters,
        variant,
        Abi.X86)
}
