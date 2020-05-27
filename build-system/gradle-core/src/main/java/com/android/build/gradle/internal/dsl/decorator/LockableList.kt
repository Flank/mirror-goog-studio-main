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

package com.android.build.gradle.internal.dsl.decorator

import com.android.build.gradle.internal.dsl.Lockable
import javax.inject.Inject

/**
 * An implementation of a list for use in AGP DSL that can be locked.
 *
 * Locking implementation to follow.
 */
class LockableList<T> @Inject constructor(val name: String) : ArrayList<T>(), Lockable {
    override fun lock() {
        // TODO(b/140406102): Actually implement locking.
    }
}
