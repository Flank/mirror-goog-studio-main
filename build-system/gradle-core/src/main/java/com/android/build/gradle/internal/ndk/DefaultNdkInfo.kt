/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.ndk

import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.PlatformConfigurator
import com.android.repository.Revision
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.collect.Maps
import java.io.File
import java.nio.file.Files
import java.util.Locale
import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.Logging
import java.io.FileFilter
import kotlin.streams.toList

/** Default NdkInfo. Used for r13 and earlier.  */
open class DefaultNdkInfo(protected val rootDirectory: File) : NdkInfo {

    private val platformConfigurator: PlatformConfigurator = PlatformConfigurator(rootDirectory)

    private val abiInfoList: List<AbiInfo> = NdkAbiFile(ndkMetaAbisFile(rootDirectory)).abiInfoList

    private val defaultToolchainVersions = Maps.newHashMap<Abi, String>()

    override fun findSuitablePlatformVersion(
        abi: String,
        androidVersion: AndroidVersion?
    ): Int {
        return platformConfigurator.findSuitablePlatformVersion(abi, androidVersion)
    }

    private fun getToolchainPrefix(abi: Abi): String {
        return abi.gccToolchainPrefix
    }

    /**
     * Return the directory containing the toolchain.
     *
     * @param abi target ABI of the toolchains
     * @return a directory that contains the executables.
     */
    private fun getToolchainPath(abi: Abi): File {
        val toolchainAbi = getToolchainAbi(abi)
        var version = getDefaultToolchainVersion(toolchainAbi)
        version = if (version.isEmpty()) "" else "-$version"  // prepend '-' if non-empty.

        val prebuiltFolder = File(
            rootDirectory,
            "toolchains/" + getToolchainPrefix(toolchainAbi) + version + "/prebuilt"
        )

        val osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH)
        val hostOs: String
        hostOs = when {
            osName.contains("windows") -> "windows"
            osName.contains("mac") -> "darwin"
            else -> "linux"
        }

        // There should only be one directory in the prebuilt folder.  If there are more than one
        // attempt to determine the right one based on the operating system.
        val toolchainPaths = prebuiltFolder.listFiles(FileFilter { it.isDirectory })
            ?: throw InvalidUserDataException("Unable to find toolchain: $prebuiltFolder")

        if (toolchainPaths.size == 1) {
            return toolchainPaths[0]
        }

        // Use 64-bit toolchain if available.
        var toolchainPath = File(prebuiltFolder, "$hostOs-x86_64")
        if (toolchainPath.isDirectory) {
            return toolchainPath
        }

        // Fallback to 32-bit if we can't find the 64-bit toolchain.
        val osString = if (osName == "windows") hostOs else "$hostOs-x86"
        toolchainPath = File(prebuiltFolder, osString)
        return if (toolchainPath.isDirectory) {
            toolchainPath
        } else {
            throw InvalidUserDataException("Unable to find toolchain prebuilt folder in: $prebuiltFolder")
        }
    }

    protected open fun getToolchainAbi(abi: Abi): Abi {
        return abi
    }

    /** Return the executable for removing debug symbols from a shared object.  */
    override fun getStripExecutable(abi: Abi): File {
        val toolchainAbi = getToolchainAbi(abi)
        return FileUtils.join(
            getToolchainPath(toolchainAbi), "bin", toolchainAbi.gccExecutablePrefix + "-strip"
        )
    }

    /**
     * Return the default version of the specified toolchain for a target abi.
     *
     *
     * The default version is the highest version found in the NDK for the specified toolchain
     * and ABI. The result is cached for performance.
     */
    private fun getDefaultToolchainVersion(abi: Abi): String {
        val toolchainAbi = getToolchainAbi(abi)
        val defaultVersion = defaultToolchainVersions[toolchainAbi]
        if (defaultVersion != null) {
            return defaultVersion
        }

        val toolchainPrefix = getToolchainPrefix(toolchainAbi)
        val toolchains = File(rootDirectory, "toolchains")
        val toolchainsForAbi =
            toolchains.listFiles { _, filename -> filename.startsWith(toolchainPrefix) }
        if (toolchainsForAbi == null || toolchainsForAbi.isEmpty()) {
            throw RuntimeException(
                "No toolchains found in the NDK toolchains folder for ABI with prefix: $toolchainPrefix"
            )
        }

        // Once we have a list of toolchains, we look the highest version
        var bestRevision: Revision? = null
        var bestVersionString = ""
        for (toolchainFolder in toolchainsForAbi) {
            val folderName = toolchainFolder.name

            var revision = Revision(0)
            var versionString = ""
            if (folderName.length > toolchainPrefix.length + 1) {
                // Find version if folderName is in the form {prefix}-{version}
                try {
                    versionString = folderName.substring(toolchainPrefix.length + 1)
                    revision = Revision.parseRevision(versionString)
                } catch (ignore: NumberFormatException) {
                }

            }
            if (bestRevision == null || revision > bestRevision) {
                bestRevision = revision
                bestVersionString = versionString
            }
        }
        defaultToolchainVersions[toolchainAbi] = bestVersionString
        return bestVersionString
    }

    override val default32BitsAbis  get() =
        abiInfoList
            .stream()
            .filter { abiInfo -> abiInfo.isDefault && !abiInfo.isDeprecated }
            .map { it.abi }
            .filter { abi -> !abi.supports64Bits() }
            .toList()

    override val defaultAbis get() =
        abiInfoList
            .stream()
            .filter { abiInfo -> abiInfo.isDefault && !abiInfo.isDeprecated }
            .map<Abi>{ it.abi }
            .toList()

    override val supported32BitsAbis get() =
        abiInfoList
            .stream()
            .map  { it.abi }
            .filter { abi -> !abi.supports64Bits() }
            .toList()

    override val supportedAbis get() =
        abiInfoList
            .stream()
            .map{ it.abi }
            .toList()

    override fun validate(): String? {
        val platformsDir = rootDirectory.resolve("platforms")
        if (!platformsDir.isDirectory) {
            return "$platformsDir is not a directory."
        }
        val toolchainsDir = rootDirectory.resolve("toolchains")
        if (!toolchainsDir.isDirectory) {
            return "$toolchainsDir is not a directory."
        }
        return null
    }
}
