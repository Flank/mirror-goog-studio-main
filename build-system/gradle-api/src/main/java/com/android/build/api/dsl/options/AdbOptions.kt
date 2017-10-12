/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.InitializableObject

/** Options for the adb tool.  */
interface AdbOptions : InitializableObject<AdbOptions> {

    /** Returns the time out used for all adb operations.  */
    var timeOutInMs: Int

    /** Returns the list of FULL_APK installation options.  */
    val installOptions: Collection<String>
}
