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

package com.android.tools.appinspection

import android.app.Activity
import android.app.ActivityThread
import android.app.PendingIntent
import android.content.Intent
import com.android.tools.appinspection.PendingIntentHandlerImpl.IntentWrapper.Companion.wrap
import java.util.concurrent.ConcurrentHashMap

/**
 * Method name for [PendingIntent.getActivity(Context, int, Intent, int, Bundle)] to capture
 * the [Intent] used to create a [PendingIntent] that starts an [android.app.Activity].
 */
const val GET_ACTIVITY_METHOD_NAME = "getActivity" +
        "(Landroid/content/Context;ILandroid/content/Intent;I" +
        "Landroid/os/Bundle;)Landroid/app/PendingIntent;"

/**
 * Method name for [PendingIntent#getService(Context, int, Intent, int)] to capture the
 * [Intent] used to create a [PendingIntent] that starts an [android.app.Service].
 */
const val GET_SERVICES_METHOD_NAME = "getService" +
        "(Landroid/content/Context;ILandroid/content/Intent;I)" +
        "Landroid/app/PendingIntent;"

/**
 * Method name for [PendingIntent.getBroadcast(Context, int, Intent, int)] to capture the
 * [Intent] used to create a [PendingIntent] that starts an
 * [android.content.BroadcastReceiver].
 */
const val GET_BROADCAST_METHOD_NAME = "getBroadcast" +
        "(Landroid/content/Context;ILandroid/content/Intent;I)" +
        "Landroid/app/PendingIntent;"

/**
 * Method name for [Instrumentation.callActivityOnCreate(Activity, Bundle)] to capture
 * Activity Intent.
 *
 * Due to b/77549390, instrumenting [Activity] causes Transport to crash. So we add the
 * hook to [Instrumentation#callActivityOnCreate(Activity, Bundle)], which calls
 * [Activity#onCreate(Bundle)].
 */
const val CALL_ACTIVITY_ON_CREATE_METHOD_NAME = "callActivityOnCreate" +
        "(Landroid/app/Activity;Landroid/os/Bundle;)V"

/**
 * Method name for [Instrumentation#callActivityOnCreate(Activity, Bundle,
 * PersistableBundle)] to capture Activity Intent.
 *
 * Due to b/77549390, instrumenting [Activity] causes Profiler to crash. So we add the
 * hook to [Instrumentation#callActivityOnCreate(Activity, Bundle, PersistableBundle)],
 * which calls [Activity#onCreate(Bundle, PersistableBundle)].
 */
const val CALL_ACTIVITY_ON_CREATE_PERSISTABLE_BUNDLE_METHOD_NAME = "callActivityOnCreate" +
        "(Landroid/app/Activity;Landroid/os/Bundle;Landroid/" +
        "os/PersistableBundle;)V"

/**
 * Method name for [IntentService.onStartCommand(Intent, int, int)] to capture Service
 * Intent.
 */
const val ON_START_COMMAND_METHOD_NAME = "onStartCommand(Landroid/content/Intent;II)I"

/**
 * Method name for [ActivityThread.handleReceiver(ReceiverData)] to capture
 * a ReceiverData containing the needed [Intent].
 * The [Intent] field in ReceiverData is not properly set at the beginning of the method,
 * so we need to wait until [SET_PENDING_RESULT_METHOD_NAME] being called to capture the [Intent].
 */
const val HANDLE_RECEIVER_METHOD_NAME = "handleReceiver" +
        "(Landroid/app/ActivityThread\$ReceiverData;)V"

/**
 * Method name for [android.content.BroadcastReceiver.setPendingResult(PendingResult)].
 * If the [PendingResult] is same with the ReceiverData captured from method
 * [HANDLE_RECEIVER_METHOD_NAME], it is safe to say that the method is called from
 * [ActivityThread] and we can extract the [Intent] properly.
 */
const val SET_PENDING_RESULT_METHOD_NAME = "setPendingResult" +
        "(Landroid/content/BroadcastReceiver\$PendingResult;)V"

/**
 * A handler class that adds necessary hooks to track [Intent] and its related
 * [PendingIntent].
 */
interface PendingIntentHandler {

    fun onIntentCapturedEntry(intent: Intent)
    fun onIntentCapturedExit(pendingIntent: PendingIntent): PendingIntent
    fun onIntentReceived(intent: Intent)
    fun onReceiverDataCreated(data: Any)
    fun onReceiverDataResult(data: Any)
}

class PendingIntentHandlerImpl(private val alarmHandler: AlarmHandler) : PendingIntentHandler {

    /**
     * Wraps an [Intent] and overrides its `equals` and `hashCode` methods so we
     * can use it as a HashMap key. Two intents are considered equal iff [Intent.filterEquals]
     * returns true.
     */
    private class IntentWrapper private constructor(private val mIntent: Intent) {

        override fun equals(other: Any?): Boolean {
            return (other is IntentWrapper
                    && mIntent.filterEquals(other.mIntent))
        }

        override fun hashCode() = mIntent.filterHashCode()

        companion object {

            fun Intent.wrap() = IntentWrapper(this)
        }
    }

    /**
     * A mapping from [Intent] to [PendingIntent] so when an Intent is sent we
     * can trace back to its PendingIntent (if any) for event tracking.
     */
    private val intentMap = ConcurrentHashMap<IntentWrapper, PendingIntent>()

    /* Intent shared between an entry hook and an exit hook for the same method. */
    private val intentData = ThreadLocal<Intent>()

    /**
     * ReceiverData captured from handleReceiver to find the correct calling of
     * method setPendingResult.
     *
     * @see HANDLE_RECEIVER_METHOD_NAME
     * @see SET_PENDING_RESULT_METHOD_NAME
     */
    private val receiverData = ThreadLocal<Any>()

    override fun onIntentCapturedEntry(intent: Intent) {
        intentData.set(intent)
    }

    override fun onIntentCapturedExit(pendingIntent: PendingIntent): PendingIntent {
        intentMap[intentData.get().wrap()] = pendingIntent
        return pendingIntent
    }

    override fun onIntentReceived(intent: Intent) {
        val pendingIntent = intentMap[intent.wrap()] ?: return
        if (intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) != 0) {
            alarmHandler.onAlarmFired(pendingIntent)
        }
    }

    override fun onReceiverDataCreated(data: Any) {
        receiverData.set(data)
    }

    override fun onReceiverDataResult(data: Any) {
        val clazz = Class.forName("android.app.ActivityThread\$ReceiverData")
        if (data == receiverData.get()) {
            val field = clazz.getDeclaredField("intent")
            field.isAccessible = true
            val intent = field.get(data) as? Intent ?: return
            onIntentReceived(intent)
        }
    }
}
