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

package com.android.build.gradle.internal.ndk

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.build.gradle.internal.cxx.configure.findNdkPath

import com.android.SdkConstants
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.cxx.configure.NdkLocatorRecord
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.sdk.InstallFailedException
import com.android.builder.sdk.LicenceNotAcceptedException
import com.android.builder.sdk.SdkLibData
import com.android.builder.sdk.SdkLoader
import com.android.repository.Revision
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.util.Properties
import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.Logging
import java.io.FileWriter

val GSON = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .setPrettyPrinting()
    .create()

sealed class NdkInstallStatus {
    /**
     * Indicates a valid NDK install.
     */
    class Valid(val platform: NdkPlatform) : NdkInstallStatus()

    /**
     * Indicates an installed but invalid NDK.
     *
     * An NDK is invalid if the directory exists but is otherwise determined to be not usable. We
     * may have been unable to determine the NDK's revision, it could be an unsupported NDK, or the
     * NDK-specific validation may have failed (commonly because the install was corrupt and was
     * missing necessary components).
     *
     * The contained error message describes the detected failure.
     */
    class Invalid(val errorMessage: String) : NdkInstallStatus()

    /**
     * Indicates a missing NDK install.
     *
     * If the NDK directory was not able to be determined or the directory does not exist, the NDK
     * has not yet been installed.
     */
    object NotInstalled : NdkInstallStatus()

    val isConfigured: Boolean
        get() {
            return this is Valid
        }

    fun getOrThrow(): NdkPlatform {
        when (this) {
            is Valid -> return this.platform
            is Invalid -> throw InvalidUserDataException(errorMessage)
            is NotInstalled -> throw InvalidUserDataException("NDK is not installed")
        }
    }
}

/**
 * Handles NDK related information.
 */
class NdkHandler(
    private val evalIssueReporter: EvalIssueReporter,
    private val enableSideBySideNdk: Boolean,
    private val ndkVersionFromDsl: String?,
    private val compileSdkVersion: String,
    private val projectDir: File
) {
    private var ndkInstallStatus: NdkInstallStatus? = null
    private var sideBySideLocatorRecord: NdkLocatorRecord? = null

    private fun findNdk(): File? {
        return if (enableSideBySideNdk) {
            val record = findNdkPath(evalIssueReporter, ndkVersionFromDsl,
                projectDir)
            sideBySideLocatorRecord = record
            record.ndkFolder
        } else {
            findNdkDirectory(projectDir)
        }
    }

    private fun getNdkInfo(ndkDirectory: File, revision: Revision): NdkInfo {
        return if (revision.major >= 19) {
            NdkR19Info(ndkDirectory)
        } else if (revision.major >= 14) {
            NdkR14Info(ndkDirectory)
        } else {
            DefaultNdkInfo(ndkDirectory)
        }
    }

    private fun getNdkStatus(): NdkInstallStatus {
        val ndkDirectory = findNdk()
        if (ndkDirectory == null || !ndkDirectory.exists()) {
            return NdkInstallStatus.NotInstalled
        }

        val findRevisionResult = findRevision(ndkDirectory)
        val revision = when (findRevisionResult) {
            is FindRevisionResult.Found -> findRevisionResult.revision
            is FindRevisionResult.Error -> return NdkInstallStatus.Invalid(
                findRevisionResult.message
            )
        }

        val ndkInfo = getNdkInfo(ndkDirectory, revision)

        val error = ndkInfo.validate()
        if (error != null) {
            return NdkInstallStatus.Invalid(error)
        }

        return NdkInstallStatus.Valid(
            NdkPlatform(ndkDirectory, ndkInfo, revision, compileSdkVersion)
        )
    }

    val ndkPlatform: NdkInstallStatus
        get() {
            if (ndkInstallStatus == null) {
                ndkInstallStatus = getNdkStatus()
            }

            return ndkInstallStatus!!
        }

    /** Schedule the NDK to be rediscovered the next time it's needed  */
    private fun invalidateNdk() {
        this.ndkInstallStatus = null
    }

    /**
     * Install NDK from the SDK. When NDK SxS is enabled the latest available SxS version is used.
     */
    fun installFromSdk(sdkLoader: SdkLoader, sdkLibData: SdkLibData) {
        try {
            if (enableSideBySideNdk) {
                sdkLoader.installSdkTool(sdkLibData, SdkConstants.FD_NDK_SIDE_BY_SIDE)
            } else {
                sdkLoader.installSdkTool(sdkLibData, SdkConstants.FD_NDK)
            }
        } catch (e: LicenceNotAcceptedException) {
            throw RuntimeException(e)
        } catch (e: InstallFailedException) {
            throw RuntimeException(e)
        }

        invalidateNdk()
    }

    /**
     * Write the side-by-side NDK locator to file.
     */
    fun writeNdkLocatorRecord(file : File) {
        if (sideBySideLocatorRecord != null) {
            file.parentFile.mkdirs()
            FileWriter(file).use { writer->
                GSON.toJson(sideBySideLocatorRecord, writer)
            }
        }
    }

    sealed class FindRevisionResult {
        data class Found(val revision: Revision) : FindRevisionResult()
        data class Error(val message: String) : FindRevisionResult()

        fun getOrThrow(): Revision {
            when (this) {
                is Found -> return revision
                is Error -> throw RuntimeException(message)
            }
        }
    }

    companion object {

        private fun readProperties(file: File): Properties {
            val properties = Properties()
            try {
                FileInputStream(file).use { fis ->
                    InputStreamReader(
                        fis,
                        Charsets.UTF_8
                    ).use { reader -> properties.load(reader) }
                }
            } catch (ignored: FileNotFoundException) {
                // ignore since we check up front and we don't want to fail on it anyway
                // in case there's an env var.
            } catch (e: IOException) {
                throw RuntimeException(String.format("Unable to read %1\$s.", file), e)
            }

            return properties
        }

        @VisibleForTesting
        @JvmStatic
        fun findRevision(ndkDirectory: File): FindRevisionResult {
            val sourceProperties = File(ndkDirectory, "source.properties")
            if (!sourceProperties.exists()) {
                val releaseTxt = ndkDirectory.resolve("RELEASE.TXT")
                return if (releaseTxt.exists()) {
                    FindRevisionResult.Error("NDK at $ndkDirectory is not supported (pre-r11)")
                } else {
                    // This should never happen for a valid install of the NDK. Presumably it's
                    // either corrupted or possibly so old that it doesn't even have a RELEASE.TXT.
                    FindRevisionResult.Error("$sourceProperties does not exist")
                }
            }

            val properties = readProperties(sourceProperties)
            val version = properties.getProperty("Pkg.Revision")
            return if (version != null) {
                FindRevisionResult.Found(Revision.parseRevision(version))
            } else {
                FindRevisionResult.Error("Could not parse Pkg.Revision from $sourceProperties")
            }
        }

        private fun findNdkDirectory(projectDir: File): File? {
            val localProperties = File(projectDir, FN_LOCAL_PROPERTIES)
            var properties = Properties()
            if (localProperties.isFile) {
                properties = readProperties(localProperties)
            }

            return findNdkDirectory(properties, projectDir)
        }

        /**
         * Determine the location of the NDK directory.
         *
         *
         * The NDK directory can be set in the local.properties file, using the ANDROID_NDK_HOME
         * environment variable or come bundled with the SDK.
         *
         *
         * Return null if NDK directory is not found.
         */
        private fun findNdkDirectory(properties: Properties, projectDir: File): File? {
            val ndkDirProp = properties.getProperty("ndk.dir")
            if (ndkDirProp != null) {
                return File(ndkDirProp)
            }

            val ndkEnvVar = System.getenv("ANDROID_NDK_HOME")
            if (ndkEnvVar != null) {
                return File(ndkEnvVar)
            }

            val sdkLocation = SdkHandler.findSdkLocation(properties, projectDir)
            val sdkFolder = sdkLocation.first
            if (sdkFolder != null) {
                // Worth checking if the NDK came bundled with the SDK
                val ndkBundle = File(sdkFolder, SdkConstants.FD_NDK)
                if (ndkBundle.isDirectory) {
                    return ndkBundle
                }
            }
            return null
        }
    }
}
