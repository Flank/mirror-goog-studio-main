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
import org.gradle.api.logging.Logging
import java.io.FileWriter

val GSON = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .setPrettyPrinting()
    .create()

/**
 * Handles NDK related information.
 */
class NdkHandler(
    private val enableSideBySideNdk: Boolean,
    private val ndkVersionFromDsl: String?,
    private val compileSdkVersion: String,
    private val projectDir: File
) {
    private var currentNdkPlatform: NdkPlatform? = null
    private var sideBySideLocatorRecord: NdkLocatorRecord? = null

    val ndkPlatform: NdkPlatform
        get() =
            if (currentNdkPlatform != null) {
                currentNdkPlatform!!
            } else {
                val ndkDirectory: File? = if (enableSideBySideNdk) {
                    sideBySideLocatorRecord = findNdkPath(ndkVersionFromDsl, projectDir)
                    sideBySideLocatorRecord!!.ndkFolder
                } else {
                    findNdkDirectory(projectDir)
                }
                val ndkInfo: NdkInfo?
                val revision: Revision?

                if (ndkDirectory == null || !ndkDirectory.exists()) {
                    ndkInfo = null
                    revision = null
                } else {
                    revision = findRevision(ndkDirectory)
                    if (revision == null || revision.major < 14) {
                        ndkInfo = DefaultNdkInfo(ndkDirectory)
                    } else {
                        ndkInfo = NdkR14Info(ndkDirectory)
                    }
                }
                currentNdkPlatform = NdkPlatform(ndkDirectory, ndkInfo, revision, compileSdkVersion)
                currentNdkPlatform!!
            }

    /** Schedule the NDK to be rediscovered the next time it's needed  */
    private fun invalidateNdk() {
        this.currentNdkPlatform = null
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
        fun findRevision(ndkDirectory: File?): Revision? {
            if (ndkDirectory == null) {
                return null
            } else {
                val sourceProperties = File(ndkDirectory, "source.properties")
                if (!sourceProperties.exists()) {
                    // source.properties does not exist.  It's probably r10.  Use the DefaultNdkInfo.
                    return null
                }
                val properties = readProperties(sourceProperties)
                val version = properties.getProperty("Pkg.Revision")
                return if (version != null) {
                    Revision.parseRevision(version)
                } else {
                    null
                }
            }
        }

        private fun findNdkDirectory(projectDir: File): File? {
            val localProperties = File(projectDir, FN_LOCAL_PROPERTIES)
            var properties = Properties()
            if (localProperties.isFile) {
                properties = readProperties(localProperties)
            }

            val ndkDir = findNdkDirectory(properties, projectDir) ?: return null
            return if (checkNdkDir(ndkDir)) ndkDir else null
        }

        /**
         * Perform basic verification on the NDK directory.
         */
        private fun checkNdkDir(ndkDir: File): Boolean {
            if (!File(ndkDir, "platforms").isDirectory) {
                invalidNdkWarning("NDK is missing a \"platforms\" directory.", ndkDir)
                return false
            }
            if (!File(ndkDir, "toolchains").isDirectory) {
                invalidNdkWarning("NDK is missing a \"toolchains\" directory.", ndkDir)
                return false
            }
            return true
        }

        private fun invalidNdkWarning(message: String, ndkDir: File) {
            Logging.getLogger(NdkHandler::class.java)
                .warn(
                    "{}\n"
                            + "If you are using NDK, verify the ndk.dir is set to a valid NDK "
                            + "directory.  It is currently set to {}.\n"
                            + "If you are not using NDK, unset the NDK variable from ANDROID_NDK_HOME "
                            + "or local.properties to remove this warning.\n",
                    message,
                    ndkDir.absolutePath
                )
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
