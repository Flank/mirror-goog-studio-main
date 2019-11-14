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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.SdkComponents
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.tryCreateCxxModuleModel
import com.android.build.gradle.internal.dsl.CmakeOptions
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.NdkBuildOptions
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.ndk.NdkInstallStatus
import com.android.build.gradle.internal.ndk.NdkPlatform
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.utils.FileUtils
import com.android.utils.FileUtils.join
import com.google.common.base.Supplier
import org.gradle.api.Project
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito

fun createCmakeProjectCxxAbiForTest(projectParentFolder: TemporaryFolder): CxxAbiModel {
    val global = Mockito.mock(GlobalScope::class.java)
    val projectOptions = Mockito.mock(ProjectOptions::class.java)
    val extension = Mockito.mock(BaseExtension::class.java)
    val externalNativeBuild = Mockito.mock(ExternalNativeBuild::class.java)
    val cmake = Mockito.mock(CmakeOptions::class.java)
    val ndkBuild = Mockito.mock(NdkBuildOptions::class.java)
    val project = Mockito.mock(Project::class.java)
    val sdkComponents = Mockito.mock(SdkComponents::class.java)
    val ndkHandler = Mockito.mock(NdkHandler::class.java)
    val ndkPlatform = NdkInstallStatus.Valid(Mockito.mock(NdkPlatform::class.java))
    val baseVariantData = Mockito.mock(BaseVariantData::class.java)
    val variantScope = Mockito.mock(VariantScope::class.java)
    Mockito.doReturn(baseVariantData).`when`(variantScope).variantData
    projectParentFolder.create()
    val projectDir = projectParentFolder.newFolder("project")
    val moduleDir = join(projectDir, "module")
    val buildDir = join(projectDir, "build")
    val sdkDir = projectParentFolder.newFolder("sdk")
    val ndkFolder = join(sdkDir, "ndk", "17.2.4988734")
    val ndkSourceProperties = join(ndkFolder, "source.properties")
    ndkSourceProperties.parentFile.mkdirs()
    ndkSourceProperties.writeText(
        """
                Pkg.Desc = Android NDK
                Pkg.Revision = 17.2.4988734
            """.trimIndent())
    Mockito.doReturn(extension).`when`(global).extension
    Mockito.doReturn(externalNativeBuild).`when`(extension).externalNativeBuild
    Mockito.doReturn(cmake).`when`(externalNativeBuild).cmake
    Mockito.doReturn(ndkBuild).`when`(externalNativeBuild).ndkBuild
    Mockito.doReturn(FileUtils.join(moduleDir, "src", "CMakeLists.txt")).`when`(cmake).path
    Mockito.doReturn(project).`when`(global).project
    Mockito.doReturn(projectOptions).`when`(global).projectOptions
    Mockito.doReturn(projectDir).`when`(project).rootDir
    Mockito.doReturn(moduleDir).`when`(project).projectDir
    Mockito.doReturn(sdkComponents).`when`(global).sdkComponents
    Mockito.doReturn(Supplier { ndkHandler }).`when`(sdkComponents).ndkHandlerSupplier
    Mockito.doReturn(ndkPlatform).`when`(ndkHandler).ndkPlatform
    Mockito.doReturn(buildDir).`when`(project).buildDir
    Mockito.doReturn(ndkFolder).`when`(ndkPlatform.getOrThrow()).ndkDirectory
    Mockito.doReturn("debug").`when`(baseVariantData).name
    Mockito.doReturn(false).`when`(projectOptions).get(BooleanOption.ENABLE_PREFAB)
    val module = tryCreateCxxModuleModel(global)!!
    val variant = createCxxVariantModel(module, variantScope)
    return createCxxAbiModel(
        variant,
        Abi.X86,
        global,
        baseVariantData)
}
