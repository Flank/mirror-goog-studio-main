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

package com.android.tools.appinspection.backgroundtask

import android.os.PowerManager
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.WakeLockAcquired
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.WakeLockReleased
import com.android.tools.appinspection.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class WakeLockHandlerTest {

    @get:Rule
    val inspectorRule = BackgroundTaskInspectorRule()

    @Test
    fun wakeLockAcquired() {
        val wakeLockHandler = inspectorRule.inspector.wakeLockHandler
        wakeLockHandler.onNewWakeLockEntry(1, "tag")
        val wakeLock = PowerManager().newWakeLock(3, "tag2")
        wakeLockHandler.onNewWakeLockExit(wakeLock)
        wakeLockHandler.onWakeLockAcquired(wakeLock, 10)

        inspectorRule.connection.consume {
            with(wakeLockAcquired) {
                assertThat(tag).isEqualTo("tag")
                assertThat(level).isEqualTo(WakeLockAcquired.Level.PARTIAL_WAKE_LOCK)
            }
        }
    }

    @Test
    fun wakeLockAcquiredWithoutParameter() {
        val wakeLockHandler = inspectorRule.inspector.wakeLockHandler
        val wakeLock = PowerManager().newWakeLock(1, "tag")
        wakeLockHandler.onWakeLockAcquired(wakeLock, 10)

        inspectorRule.connection.consume {
            with(wakeLockAcquired) {
                assertThat(tag).isEqualTo("tag")
                assertThat(level).isEqualTo(WakeLockAcquired.Level.PARTIAL_WAKE_LOCK)
            }
        }
    }

    @Test
    fun wakeLockReleased() {
        val wakeLockHandler = inspectorRule.inspector.wakeLockHandler
        val wakeLock = PowerManager().newWakeLock(1, "tag")
        wakeLock.isHeld = true
        wakeLockHandler.onWakeLockReleasedEntry(wakeLock, RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        wakeLockHandler.onWakeLockReleasedExit()
        inspectorRule.connection.consume {
            with(wakeLockReleased) {
                assertThat(flagsList[0]).isEqualTo(
                    WakeLockReleased.ReleaseFlag.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY
                )
                assertThat(isHeld).isTrue()
            }
        }
    }
}
