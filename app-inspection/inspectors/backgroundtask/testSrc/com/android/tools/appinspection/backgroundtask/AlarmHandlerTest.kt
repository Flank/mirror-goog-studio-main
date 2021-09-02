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

import android.app.ActivityThread
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.AlarmSet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AlarmHandlerTest {

    @get:Rule
    val inspectorRule = BackgroundTaskInspectorRule()

    private class TestListener : AlarmManager.OnAlarmListener

    @Test
    fun alarmSetWithIntent() {
        val alarmHandler = inspectorRule.inspector.alarmHandler
        val operation = PendingIntent(5, "package")
        alarmHandler.onAlarmSet(
            AlarmManager.RTC_WAKEUP, 2, 3, 4, operation, null, null
        )
        inspectorRule.connection.consume {
            with(alarmSet) {
                assertThat(type).isEqualTo(AlarmSet.Type.RTC_WAKEUP)
                assertThat(triggerMs).isEqualTo(2)
                assertThat(windowMs).isEqualTo(3)
                assertThat(intervalMs).isEqualTo(4)
                with(operation) {
                    assertThat(creatorPackage).isEqualTo("package")
                    assertThat(creatorUid).isEqualTo(5)
                }
            }
        }
    }

    @Test
    fun alarmCancelledWithIntent() {
        val alarmHandler = inspectorRule.inspector.alarmHandler
        val operation = PendingIntent(5, "package")
        alarmHandler.onAlarmSet(
            AlarmManager.RTC_WAKEUP, 2, 3, 4, operation, null, null
        )
        alarmHandler.onAlarmCancelled(operation)
        inspectorRule.connection.consume {
            assertThat(hasAlarmCancelled()).isTrue()
        }
    }

    @Test
    fun alarmFiredWithIntent() {
        val alarmHandler = inspectorRule.inspector.alarmHandler
        val pendingIntentHandler = inspectorRule.inspector.pendingIntentHandler
        val intent = Intent()
        intent.setIntExtra(1)
        val operation = PendingIntent(5, "package")

        pendingIntentHandler.onIntentCapturedEntry(intent)
        pendingIntentHandler.onIntentCapturedExit(operation)

        alarmHandler.onAlarmSet(
            AlarmManager.RTC_WAKEUP, 2, 3, 4, operation, null, null
        )
        val data = ActivityThread().newReceiverData()
        pendingIntentHandler.onReceiverDataCreated(data)
        data.setIntent(intent)
        pendingIntentHandler.onReceiverDataResult(data)

        inspectorRule.connection.consume {
            assertThat(hasAlarmFired()).isTrue()
        }
    }

    @Test
    fun alarmSetWithListener() {
        val alarmHandler = inspectorRule.inspector.alarmHandler
        val listener = TestListener()
        alarmHandler.onAlarmSet(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, 2, 3, 4, null,
            listener, "tag"
        )
        inspectorRule.connection.consume {
            with(alarmSet) {
                assertThat(type).isEqualTo(AlarmSet.Type.ELAPSED_REALTIME_WAKEUP)
                assertThat(triggerMs).isEqualTo(2)
                assertThat(windowMs).isEqualTo(3)
                assertThat(intervalMs).isEqualTo(4)
                assertThat(this.listener.tag).isEqualTo("tag")
            }
        }
    }

    @Test
    fun alarmCancelledWithListener() {
        val alarmHandler = inspectorRule.inspector.alarmHandler
        val listener = TestListener()
        alarmHandler.onAlarmSet(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, 2, 3, 4, null,
            listener, "tag"
        )
        alarmHandler.onAlarmCancelled(listener)
        inspectorRule.connection.consume {
            assertThat(hasAlarmCancelled()).isTrue()
        }
    }

    @Test
    fun alarmFiredWithListener() {
        val alarmHandler = inspectorRule.inspector.alarmHandler
        val listener = TestListener()
        alarmHandler.onAlarmSet(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, 2, 3, 4, null,
            listener, "tag"
        )
        alarmHandler.onAlarmFired(listener)
        inspectorRule.connection.consume {
            assertThat(hasAlarmFired()).isTrue()
        }
    }
}
