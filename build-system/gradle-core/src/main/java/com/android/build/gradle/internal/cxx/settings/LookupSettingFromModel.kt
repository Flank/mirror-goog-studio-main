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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.lookup
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ANDROID_GRADLE_IS_HOSTING

/**
 * Look up [Macro] equivalent value from the C/C++ build abi model.
 */
fun CxxAbiModel.resolveMacroValue(macro : Macro) : String {
    if (macro == NDK_ANDROID_GRADLE_IS_HOSTING) return "1"
    return lookup(macro.bind ?: return "$macro") ?: ""
}
