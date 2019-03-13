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

package com.android.build.gradle.internal.ndk

import com.android.build.gradle.internal.core.Abi
import com.android.repository.Revision
import java.io.File

/**
 * Represents a particular NDK version (like r19) along with a compileSdkVersion.
 * The NDK-only behavior is further delegated to NdkInfo.
 *
 * This class has two modes:
 *
 * (1) NDK was located. In this case isConfigured == true and caller can use other functions
 *     in this class.
 *
 * (2) An attempt was made to locate NDK but it failed. In this case isConfigured == false and
 *     calling other functions will result in an exception.
 */
data class NdkPlatform(
    private val ndkDirectoryOrNull: File?,
    private val ndkInfoOrNull: NdkInfo?,
    private val revisionOrNull: Revision?,
    private val compileSdkVersion: String) {

    val isConfigured = ndkDirectoryOrNull != null && ndkDirectoryOrNull.isDirectory

    val ndkDirectory : File by lazy { ndkDirectoryOrNull ?: throw RuntimeException(noNdkMessage()) }
    val ndkInfo : NdkInfo by lazy { ndkInfoOrNull ?: throw RuntimeException(noNdkMessage()) }
    val revision : Revision by lazy { revisionOrNull ?: throw RuntimeException(noNdkMessage()) }

    /**
     * Whether or not the NDK + compileSdkVersion supports 64 bit ABIs.
     */
    private val supports64Bits : Boolean by lazy {
        val platformVersion = ndkInfo.findLatestPlatformVersion(compileSdkVersion)
        if (platformVersion == null) {
            false
        } else {
            val targetString = platformVersion!!.replace("android-", "")
            try {
                Integer.parseInt(targetString) >= 20
            } catch (ignored: NumberFormatException) {
                // "android-L" supports 64-bits.
                true
            }
        }
    }

    /**
     * List of ABIs supported by this NDK + compileSdkVersion.
     */
    val supportedAbis : List<Abi> by lazy {
        (if (supports64Bits) ndkInfo.supportedAbis else ndkInfo.supported32BitsAbis).toList()
    }

    /**
     * List of default ABIs for this NDK + compileSdkVersion.
     * Default means this is the list to be used when the user specifies no ABIs.
     */
    val defaultAbis : List<Abi> by lazy {
        (if (supports64Bits) ndkInfo.defaultAbis else ndkInfo.default32BitsAbis).toList()
    }

    private fun noNdkMessage() = "No NDK available to locate compile SDK " +
            "version $compileSdkVersion. Caller should check ndk.isConfigured first."
}
