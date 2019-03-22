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

import com.android.build.gradle.internal.core.Abi
import com.android.sdklib.AndroidVersion
import java.io.File

/**
 * Interface describing the NDK.
 */
interface NdkInfo {

    val default32BitsAbis: Collection<Abi>

    val defaultAbis: Collection<Abi>

    val supported32BitsAbis: Collection<Abi>

    val supportedAbis: Collection<Abi>
    /**
     * Retrieve the newest supported version if the specified version is not supported.
     *
     * An older NDK may not support the specified compiledSdkVersion.  In that case, determine what
     * is the newest supported version return it.
     */
    fun findLatestPlatformVersion(targetPlatformString: String): String?

    fun findSuitablePlatformVersion(
        abi: String,
        androidVersion: AndroidVersion?
    ): Int

    /** Return the executable for removing debug symbols from a shared object.  */
    fun getStripExecutable(abi: Abi): File
}
