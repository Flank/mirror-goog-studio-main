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

package androidx.window.layout

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.Flow

interface WindowInfoTracker {
    companion object {
        lateinit var instance: WindowInfoTracker

        fun getOrCreate(context: Context) : WindowInfoTracker = instance
    }

    fun windowLayoutInfo(activity: Activity): Flow<WindowLayoutInfo>
}

 class WindowInfoTrackerImpl(
     val myWindowLayoutInfo: Flow<WindowLayoutInfo>
 ): WindowInfoTracker {
     override fun windowLayoutInfo(activity: Activity) = myWindowLayoutInfo
 }
