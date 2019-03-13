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

import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoPackage
import com.google.common.collect.ComparisonChain

/**
 * Mark the contained package as obsolete. This has the effect of hidding the package in SDK
 * manager tool window unless the user unchecks "Hide obsolete packages" checkbox.
 */
class NdkLegacyPackage(pkg: RemotePackage) : RemotePackage by pkg {
    override fun obsolete(): Boolean {
        return true
    }

    /**
     * Can't delegate to pkg because it uses javaClass.name. The delegatee version sees
     * the delegatee's class name.
     */
    override fun compareTo(o: RepoPackage) =
        ComparisonChain.start()
            .compare(path, o.path)
            .compare(version, o.version)
            .compare(javaClass.name, o.javaClass.name)
            .result()
}