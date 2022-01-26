/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.agent.appinspection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.inspection.Connection
import com.android.tools.agent.shared.FoldObserver
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.util.concurrent.atomic.AtomicBoolean

fun createFoldSupport(
    connection: Connection,
    continuous: () -> Boolean,
    @VisibleForTesting foldObserverForTesting: FoldObserver? = null
) = try {
    FoldSupport(connection, continuous, foldObserverForTesting)
} catch (e: Exception) {
    null
}

/**
 * Support for listening to foldable device state changes and sending relevant events to studio.
 */
class FoldSupport(
    private val connection: Connection,
    private val continuous: () -> Boolean,
    @VisibleForTesting foldObserverForTesting: FoldObserver? = null
) {

    private val observer: FoldObserver

    // During setup fold events can be triggered by both the angle sensor and the androidx.window
    // library. We want to send exactly one event during setup, so track whether that's happened.
    private var sentInitialFoldEvent = false

    // If we've sent fold information and then the fold state later becomes null, we need to
    // send an event to studio saying we've gone into non-folding mode (e.g. the device is
    // closed).
    private var isFoldActive = false

    private var currentHingeAngle: Int? = null
    private var sensorsInitialized = AtomicBoolean(false)

    init {
        observer = foldObserverForTesting ?: try {
            // Since FoldObserverImpl has to be built without jarjar so as to interact with the
            // app's androidx.coroutines objects, it can't be a declared dependency of this,
            // and so has to be invoked through reflection.
            val foldObserverClass =
                Class.forName("com.android.tools.agent.nojarjar.FoldObserverImpl")
            foldObserverClass?.getConstructor(Any::class.java)
                ?.newInstance(::sendFoldStateEvent) as FoldObserver
        } catch (e: Exception) {
            // couldn't instantiate, probably because library isn't found.
            throw InstantiationException()
        }
    }

    /**
     * Initialize FoldSupport, including setting up the hinge angle listener.
     * Must be called at least once, after which calls will be ignored.
     */
    fun initialize(context: Context) {
        if (!sensorsInitialized.compareAndSet(false, true)) {
            return
        }

        val sensorManager = context.getSystemService(SensorManager::class.java)
        sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)?.let { hingeAngleSensor ->
            sensorManager.registerListener(object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    currentHingeAngle = event.values[0].toInt()
                    sendFoldStateEvent()
                }

                override fun onAccuracyChanged(p0: Sensor?, p1: Int) = Unit
            }, hingeAngleSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun start(root: View) {
        observer.startObservingFoldState(root)
    }

    fun stop(root: View) {
        observer.stopObservingFoldState(root)
    }

    fun shutdown() {
        observer.shutdown()
    }

    /**
     * Send a fold state event to studio if we need to.
     */
    fun sendFoldStateEvent() {
        if ((!continuous() && sentInitialFoldEvent) ||
            (!isFoldActive && observer.foldState == null)) {
            return
        }
        sentInitialFoldEvent = true
        isFoldActive = observer.foldState != null
        sendFoldStateEventNow()
    }

    /**
     * Send a fold state event to studio if we have any fold state information at all.
     */
    fun sendFoldStateEventNow() {
        val foldState = observer.foldState
        if (currentHingeAngle == null && foldState == null) {
            return
        }
        connection.sendEvent {
            foldEventBuilder.apply {
                angle = currentHingeAngle ?: LayoutInspectorViewProtocol.FoldEvent.SpecialAngles.NO_FOLD_ANGLE_VALUE
                foldState?.let { this.foldState = it }
                this.orientation = observer.orientation ?: LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.NONE
            }
        }
    }
}

