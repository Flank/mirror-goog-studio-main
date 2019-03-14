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

package com.android.build.gradle.internal

import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.internal.compiler.RenderScriptProcessor
import com.android.builder.sdk.SdkInfo
import com.android.builder.sdk.TargetInfo
import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.google.common.base.Suppliers
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File
import java.util.function.Supplier

// TODO: Remove open, make it an interface and move impl to a separate class.
open class SdkComponents(
    private val options: SdkComponentsOptions,
    private val fallbackSdkHandler: SdkHandler,
    private val evalIssueReporter: EvalIssueReporter,
    private val project: Project) {

    // -- Public Api
    // -- Users of this will be migrated to specific file/jar/components calls
    val targetProvider: Provider<IAndroidTarget> = project.providers.provider { getTarget() }
    val buildToolInfoProvider: Provider<BuildToolInfo> = project.providers.provider { getBuildToolsInfo() }

    // -- Stable Public Api
    val sdkSetupCorrectly: Provider<Boolean> = project.providers.provider { getTargetInfo() != null }

    val buildToolsRevisionProvider: Provider<Revision> = project.providers.provider { getBuildToolsRevision() }
    val aidlExecutableProvider: Provider<File> = project.providers.provider { getAidlExecutable() }
    val adbExecutableProvider: Provider<File> = project.providers.provider { getAdbExecutable() }
    val coreLambdaStubsProvider: Provider<File> = project.providers.provider { getCoreLambaStubs() }
    val splitSelectExecutableProvider: Provider<File> = project.providers.provider { getSplitSelectExecutable() }

    val androidJarProvider: Provider<File> = project.providers.provider { getAndroidJar() }
    val annotationsJarProvider: Provider<File> = project.providers.provider { getAnnotationsJar() }
    val aidlFrameworkProvider: Provider<File> = project.providers.provider { getAidlFramework() }
    val targetHashStringProvider: Provider<String> = project.providers.provider { getTargetHashString() }

    val renderScriptSupportJarProvider: Provider<File> = project.providers.provider { getRenderScriptSupportJar() }
    val supportNativeLibFolderProvider: Provider<File> = project.providers.provider { getSupportNativeLibFolder() }
    val supportBlasLibFolderProvider: Provider<File> = project.providers.provider { getSupportBlasLibFolder() }
    val ndkFolderProvider: Provider<File> = project.providers.provider { getNdkFolder() }

    private var fallbackResultsSupplier: Supplier<Pair<SdkInfo, TargetInfo>?> = Suppliers.memoize { runFallbackSdkHandler() }
    val ndkHandlerSupplier : Supplier<NdkHandler> = Suppliers.memoize {
        NdkHandler(
            options.enableSideBySideNdk,
            options.ndkVersionSupplier.get(),
            options.platformTargetHashSupplier.get(),
            project.rootDir)
    }

    private fun runFallbackSdkHandler(): Pair<SdkInfo, TargetInfo>? {
        fallbackSdkHandler.setSdkLibData(options.sdkLibDataFactory.getSdkLibData())

        val result = fallbackSdkHandler.initTarget(
            checkNotNull(options.platformTargetHashSupplier.get()) {"Extension not initialized yet, couldn't access compileSdkVersion."},
            checkNotNull(options.buildToolRevisionSupplier.get()) {"Extension not initialized yet, couldn't access buildToolsVersion."},
            evalIssueReporter)
        // TODO: sdk components downloads must be removed into it's own task when not running in sync
        // mode.
        fallbackSdkHandler.ensurePlatformToolsIsInstalledWarnOnFailure(evalIssueReporter)
        return result?.let { Pair(result.first, result.second) }
    }

    fun unload() {
        fallbackSdkHandler.unload()
        // Reset the memoized supplier.
        fallbackResultsSupplier = Suppliers.memoize { runFallbackSdkHandler() }
    }

    // These old methods are expensive and require SDK Parsing or some kind of installation/download.
    // TODO: Add mechanism to warn if those are called during configuration time.

    fun installNdk(ndkHandler: NdkHandler) {
        fallbackResultsSupplier.get() // SDK needs to be initialized in order to install to work.
        fallbackSdkHandler.installNdk(ndkHandler)
    }

    fun installCmake(version: String) {
        fallbackResultsSupplier.get() // SDK needs to be initialized in order to install to work.
        fallbackSdkHandler.installCMake(version)
    }

    private fun getSdkInfo(): SdkInfo? {
        return fallbackResultsSupplier.get()?.first
    }

    private fun getAidlExecutable(): File? {
        return File(getBuildToolsInfo()?.getPath(BuildToolInfo.PathId.AIDL))
    }

    private fun getAidlFramework(): File? {
        return File(getTarget()?.getPath(IAndroidTarget.ANDROID_AIDL))
    }

    private fun getAdbExecutable(): File? {
        return getSdkInfo()?.adb
    }

    private fun getAndroidJar(): File? {
        return getTarget()?.getFile(IAndroidTarget.ANDROID_JAR)
    }

    private fun getAnnotationsJar(): File? {
        return getSdkInfo()?.annotationsJar
    }

    private fun getBuildToolsRevision(): Revision? {
        return getBuildToolsInfo()?.revision
    }

    private fun getSplitSelectExecutable(): File? {
        return File(getBuildToolsInfo()?.getPath(BuildToolInfo.PathId.SPLIT_SELECT))
    }

    private fun getCoreLambaStubs(): File? {
        return File(getBuildToolsInfo()?.getPath(BuildToolInfo.PathId.CORE_LAMBDA_STUBS))
    }

    private fun getTargetInfo(): TargetInfo? {
        return  fallbackResultsSupplier.get()?.second
    }

    private fun getTarget(): IAndroidTarget? {
        return getTargetInfo()?.target
    }

    private fun getBuildToolsInfo(): BuildToolInfo? {
        return getTargetInfo()?.buildTools
    }

    private fun getTargetHashString(): String {
        return getTarget()?.hashString() ?: ""
    }

    private fun getRenderScriptSupportJar(): File? {
        return getBuildToolsInfo()?.let {
            RenderScriptProcessor.getSupportJar(it.location, options.useAndroidX)
        }
    }

    private fun getSupportNativeLibFolder(): File? {
        return getBuildToolsInfo()?.let {
            RenderScriptProcessor.getSupportNativeLibFolder(it.location)
        }
    }

    private fun getSupportBlasLibFolder(): File? {
        return getBuildToolsInfo()?.let {
            RenderScriptProcessor.getSupportBlasLibFolder(it.location)
        }
    }

    // These old methods are less expensive and are computed on SdkHandler creation by navigating
    // through the directories set by the user using the configuration mechanisms.

    fun getSdkFolder(): File? {
        return fallbackSdkHandler.sdkFolder
    }

    fun getNdkFolder(): File? {
        return ndkHandlerSupplier.get().ndkPlatform.ndkDirectory
    }

    fun getCMakeExecutable(): File? {
        return fallbackSdkHandler.cmakePathInLocalProp
    }

    fun getNdkSymlinkDirInLocalProp(): File? {
        return fallbackSdkHandler.ndkSymlinkDirInLocalProp
    }
}

class SdkComponentsOptions(
    val platformTargetHashSupplier: Supplier<String>,
    val buildToolRevisionSupplier: Supplier<Revision>,
    val ndkVersionSupplier: Supplier<String>,
    val sdkLibDataFactory: SdkLibDataFactory,
    val useAndroidX: Boolean,
    val enableSideBySideNdk: Boolean)
