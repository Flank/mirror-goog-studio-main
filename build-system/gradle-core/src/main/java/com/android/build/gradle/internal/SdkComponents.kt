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
    val targetProvider = project.providers.provider { getTarget()!! }
    val buildToolInfoProvider = project.providers.provider { getBuildToolsInfo()!! }

    // -- Stable Public Api
    val adbExecutableProvider = project.providers.provider { getAdbExecutable()!! }
    val annotationsJarProvider = project.providers.provider { getAnnotationsJar()!! }
    val buildToolsRevisionProvider = project.providers.provider { getBuildToolsRevision()!! }

    val renderScriptSupportJarProvider: Provider<File> = project.providers.provider { getRenderScriptSupportJar() }
    val supportNativeLibFolderProvider: Provider<File> = project.providers.provider { getSupportNativeLibFolder() }
    val supportBlasLibFolderProvider: Provider<File> = project.providers.provider { getSupportBlasLibFolder() }

    private var fallbackResultsSupplier: Supplier<Pair<SdkInfo, TargetInfo>?> = Suppliers.memoize { runFallbackSdkHandler() }

    private fun runFallbackSdkHandler(): Pair<SdkInfo, TargetInfo>? {
        fallbackSdkHandler.setSdkLibData(options.sdkLibDataFactory.getSdkLibData())
        if (options.injectSdkMavenRepos) {
            fallbackSdkHandler.addLocalRepositories(project)
        }

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

    fun getSdkInfo(): SdkInfo? {
        return fallbackResultsSupplier.get()?.first
    }

    fun getAdbExecutable(): File? {
        return getSdkInfo()?.adb
    }

    fun getAnnotationsJar(): File? {
        return getSdkInfo()?.annotationsJar
    }

    fun getBuildToolsRevision(): Revision? {
        return getBuildToolsInfo()?.revision
    }

    fun getTargetInfo(): TargetInfo? {
        return  fallbackResultsSupplier.get()?.second
    }

    fun getTarget(): IAndroidTarget? {
        return fallbackResultsSupplier.get()?.second?.target
    }

    fun getBuildToolsInfo(): BuildToolInfo? {
        return fallbackResultsSupplier.get()?.second?.buildTools
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
        return fallbackSdkHandler.ndkFolder
    }

    fun getCMakeExecutable(): File? {
        return fallbackSdkHandler.cmakePathInLocalProp
    }

    fun getNdkSymlinkDirInLocalProp(): File? {
        return fallbackSdkHandler.ndkSymlinkDirInLocalProp
    }

    fun getPathForTargetElementProvider(id: Int, project: Project): Provider<String> {
        return project.providers.provider { getTarget()!!.getPath(id) }
    }
}

class SdkComponentsOptions(
    val platformTargetHashSupplier: Supplier<String>,
    val buildToolRevisionSupplier: Supplier<Revision>,
    val sdkLibDataFactory: SdkLibDataFactory,
    val injectSdkMavenRepos: Boolean,
    val useAndroidX: Boolean)
