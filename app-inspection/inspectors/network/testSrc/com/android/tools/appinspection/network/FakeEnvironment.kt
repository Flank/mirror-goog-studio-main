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

package com.android.tools.appinspection.network

import androidx.inspection.ArtTooling
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorExecutors
import com.google.common.util.concurrent.MoreExecutors

class FakeEnvironment : InspectorEnvironment {

    val fakeArtTooling = NetworkArtTooling()

    override fun executors() = object : InspectorExecutors {
        private val primaryExecutor = MoreExecutors.directExecutor()

        override fun handler() = throw NotImplementedError()
        override fun primary() = primaryExecutor
        override fun io() = throw NotImplementedError()
    }

    override fun artTooling(): ArtTooling {
        return fakeArtTooling
    }
}
