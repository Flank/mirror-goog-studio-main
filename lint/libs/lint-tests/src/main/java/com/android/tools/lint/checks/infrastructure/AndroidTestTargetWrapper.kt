/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.lint.checks.infrastructure

import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget

/**
 * Test utility which wraps a real [IAndroidTarget] but presents itself
 * as having an alternative [version]. Used to test preview releases or
 * specific releases (identified by `compileSdkVersion` in test files)
 * that present themselves as that specific build hash but is using
 * whatever platform is actually available to the test (unless the
 * actual target one is available).
 */
internal class AndroidTestTargetWrapper(
    private val target: IAndroidTarget,
    private val version: AndroidVersion
) : IAndroidTarget by target {
    override fun getVersion(): AndroidVersion = version

    override fun getVersionName(): String {
        return version.apiString
    }

    override fun hashString(): String {
        return AndroidTargetHash.PLATFORM_HASH_PREFIX + version.apiString
    }
}
