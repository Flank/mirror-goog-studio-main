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

package android.view.inspector

import androidx.annotation.VisibleForTesting

class StaticInspectionCompanionProvider {

    companion object {

        private val providers = mutableMapOf<Class<*>, InspectionCompanion<*>>()

        @VisibleForTesting
        fun <T> register(cls: Class<T>, provider: InspectionCompanion<T>) {
            providers[cls] = provider
        }

        @VisibleForTesting
        fun cleanup() {
            providers.clear()
        }
    }

    fun <T> provide(cls: Class<T>): InspectionCompanion<T>? {
        @Suppress("UNCHECKED_CAST")
        return providers[cls] as? InspectionCompanion<T>
    }
}
