/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.repository.impl.manager

import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import java.io.File

/**
 * Redirect the given NDK package to install into a versioned sub-folder instead of ndk-bundle.
 * The resulting folder has the form ndk/r19 (for example). So only one variant of a given major
 * version can be installed at any one time.
 */
internal class NdkSideBySidePackage(private val pkg: RemotePackage) : RemotePackage by pkg {
    override fun getDisplayName(): String {
        return pkg.displayName + " (Side by side) " + ndkStyleVersion
    }

    override fun getPath(): String {
        return "ndk;$ndkStyleVersion"
    }

    override fun getInstallDir(manager: RepoManager, progress: ProgressIndicator): File {
        val result = pkg.getInstallDir(manager, progress)
        return File(result.parent, "ndk/$ndkStyleVersion")
    }

    private val ndkStyleVersion: String
        get() = "${version.major}.${version.minor}.${version.micro}"
}
