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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.BuildSessionImpl
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import java.util.UUID

/** Service for a build session. See [BuildSessionImpl] for what a build session is. */
class CxxBuildSessionService {
    companion object {
        private var instance: CxxBuildSessionService? = null
        @JvmStatic
        fun getInstance(): CxxBuildSessionService {
            BuildSessionImpl.getSingleton()
                .executeOnce(
                    CxxBuildSessionService::class.java.name,
                    "createCxxPerBuildService"
                ) { instance = CxxBuildSessionService() }
            return instance!!
        }
    }

    /** The build ID of this build session. */
    val buildId: UUID = UUID.randomUUID()
    /** All ABIs that are built in this build session. */
    val allBuiltAbis: MutableSet<CxxAbiModel> = mutableSetOf()
}
