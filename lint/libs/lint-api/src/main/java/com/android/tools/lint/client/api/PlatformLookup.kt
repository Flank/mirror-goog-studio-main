/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.sdklib.AndroidTargetHash.PLATFORM_HASH_PREFIX
import com.android.sdklib.IAndroidTarget

// Do we support add-ons?
// There are some things left to do in [SimplePlatformLookup], such as
// sorting out the correct hash string (right now the SDK manager
// uses the vendor display name instead of the vendor id, which
// we're not bothering to pull out of the package.xml file).
// As well as reading libs/ and manifest.init for library metadata.
// There is not a lot of work left, but lint has never supported this
// correctly and it seems to be an obsolete mechanism at this point
// so not worth the trouble
const val SUPPORTS_ADD_ONS = false

/**
 * An interface for looking up Android target platforms, by API level,
 * by code name, etc, as well as "give me the latest"
 */
interface PlatformLookup {
    /**
     * Returns the scanned platforms as a list of [IAndroidTarget]
     * (though note that these [IAndroidTarget] only supports a small
     * subset of the API surface; unsupported methods will throw an
     * exception or when applicable just return empty results. Already
     * cached; okay to be called repeatedly.
     */
    fun getTargets(includeAddOns: Boolean = false): List<IAndroidTarget>

    /**
     * Returns the latest installed platform. If [includePreviews] is
     * false, it will return the most recent stable version. If [minApi]
     * is higher than 1, and if the highest version installed is at a
     * lower API level than that, null will be returned instead. Does
     * not include add-ons.
     */
    fun getLatestSdkTarget(
        minApi: Int = 1,
        includePreviews: Boolean = true,
        includeAddOns: Boolean = false
    ): IAndroidTarget?

    /**
     * Returns the platform installed for the given [buildTargetHash],
     * if found.
     */
    fun getTarget(buildTargetHash: String): IAndroidTarget?

    /**
     * Returns the platform installed for the given [api], if found. If
     * there is a stable version available it will prefer that version
     * over any previews for the same API level.
     */
    fun getTarget(api: Int): IAndroidTarget? {
        getTarget(PLATFORM_HASH_PREFIX + api.toString())?.let { return it }

        // Matches are usually at the end
        val first = getTargets().lastOrNull { it.version.apiLevel == api }
            ?: return null
        if (first.version.codename != null) {
            return getTargets().lastOrNull {
                it.version.apiLevel == api && it.version.codename == null
            } ?: first
        }

        return first
    }
}
