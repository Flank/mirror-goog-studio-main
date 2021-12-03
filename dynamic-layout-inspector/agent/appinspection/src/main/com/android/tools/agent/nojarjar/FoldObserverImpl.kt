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

package com.android.tools.agent.nojarjar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.android.tools.agent.shared.FoldObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Tracks the fold state using the android.window:window library.
 *
 * This class interacts directly with the library built into an app, and so has to be separately
 * from the rest of the agent so it will be able to pick up the kotlin and coroutines versions used
 * by the app rather than the inspector.
 * This also means that this class must be invoked via reflection, as must the sendFoldStateEvent
 * callback. Additionally, `sendFoldStateEvent`, the callback that the state has changed, can't be
 * declared explicitly as `() -> Unit`, and must itself be invoked by reflection as well.
 */
@Suppress("unused") // invoked by reflection
class FoldObserverImpl(private val sendFoldStateEvent: Any) : FoldObserver {

    private val foldingFeatureClass = Class.forName("androidx.window.layout.FoldingFeature")
    private val windowLayoutInfoClass = Class.forName("androidx.window.layout.WindowLayoutInfo")
    // The relevant class name was changed between beta02 and beta03.
    private val windowRepositoryCompanionClass = try {
        // beta03 and later
        Class.forName("androidx.window.layout.WindowInfoTracker\$Companion")
    }
    catch (e: ClassNotFoundException) {
        // Pre-beta03
        Class.forName("androidx.window.layout.WindowInfoRepository\$Companion")
    }
    private val windowRepositoryClass = try {
        // beta03 and later
        Class.forName("androidx.window.layout.WindowInfoTracker")
    }
    catch (e: ClassNotFoundException) {
        // Pre-beta03
        Class.forName("androidx.window.layout.WindowInfoRepository")
    }
    private val windowRepositoryGetter = try {
        // beta04 and later
        windowRepositoryCompanionClass.getDeclaredMethod("getOrCreate", Context::class.java)
    }
    catch (e: NoSuchMethodException) {
        // Pre-beta04
        windowRepositoryCompanionClass.getDeclaredMethod("getOrCreate", Activity::class.java)
    }

    private val windowRepositoryCompanion = windowRepositoryClass.getField("Companion").get(null)
    private val windowLayoutInfoGetter = try {
        // beta04 and later
        windowRepositoryClass.getDeclaredMethod("windowLayoutInfo", Activity::class.java)
    }
    catch (e: NoSuchMethodException) {
        // Pre-beta04
        windowRepositoryClass.getDeclaredMethod("getWindowLayoutInfo")
    }
    private val displayFeaturesGetter = try {
        // beta04 and later
        windowLayoutInfoClass.getDeclaredMethod("displayFeatures")
    }
    catch (e: NoSuchMethodException) {
        // Pre-beta04
        windowLayoutInfoClass.getDeclaredMethod("getDisplayFeatures")
    }

    private val listeners = mutableMapOf<View, Job?>()
    private val context = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(context)

    private var currentFoldFeature: Any? = null

    override val foldState: LayoutInspectorViewProtocol.FoldEvent.FoldState?
        get() =
            currentFoldFeature?.let { feature ->
                when (foldingFeatureClass?.getMethod("getState")
                    ?.invoke(feature)
                    ?.toString()) {
                    "HALF_OPENED" -> LayoutInspectorViewProtocol.FoldEvent.FoldState.HALF_OPEN
                    "FLAT" -> LayoutInspectorViewProtocol.FoldEvent.FoldState.FLAT
                    null -> null
                    else -> LayoutInspectorViewProtocol.FoldEvent.FoldState.UNKNOWN_FOLD_STATE
                }
            }

    override val orientation: LayoutInspectorViewProtocol.FoldEvent.FoldOrientation?
        get() = currentFoldFeature?.let { feature ->
            when (foldingFeatureClass?.getMethod("getOrientation")
                ?.invoke(feature)
                ?.toString()) {
                "HORIZONTAL" -> LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.HORIZONTAL
                "VERTICAL" -> LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.VERTICAL
                else -> LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.UNKNOWN_FOLD_ORIENTATION
            }
        }

    private tailrec fun Context.getActivity(): Activity? {
        return this as? Activity
            ?: (this as? ContextWrapper)?.baseContext?.getActivity()
    }

    override fun startObservingFoldState(rootView: View) {
        listeners.computeIfAbsent(rootView) { view ->
            val windowInfo = runOnMainThread {
                // The DecorView doesn't have the Activity as context, so we need to get a different
                // view.
                val viewGroup = view as? ViewGroup ?: return@runOnMainThread null
                val activity = viewGroup.getChildAt(0)?.context?.getActivity()
                if (viewGroup.childCount > 0) {
                    if (activity != null) {
                        val windowInfoRepo = windowRepositoryGetter.invoke(windowRepositoryCompanion, activity)
                        if (windowLayoutInfoGetter.parameterCount == 1) {
                            // beta04 and later
                            windowLayoutInfoGetter.invoke(windowInfoRepo, activity) as Flow<*>
                        }
                        else {
                            // pre-beta04
                            windowLayoutInfoGetter.invoke(windowInfoRepo) as Flow<*>
                        }
                    }
                    else null
                }
                else null
            }.get() ?: return@computeIfAbsent null

            scope.launch {
                windowInfo.collect { newLayoutInfo ->
                    val features = displayFeaturesGetter.invoke(newLayoutInfo) as List<*>
                    currentFoldFeature =
                        features.firstOrNull {
                            it != null && foldingFeatureClass.isAssignableFrom(
                                it.javaClass
                            )
                        }

                    sendFoldStateEvent::class.java.getMethod("invoke").apply {
                        // Necessary for unit tests, maybe due to a kotlin bug?
                        isAccessible = true
                        invoke(sendFoldStateEvent)
                    }
                }
            }
        }
    }

    override fun stopObservingFoldState(rootView: View) {
        listeners[rootView]?.cancel()
    }

    override fun shutdown() {
        context.cancel()
    }
}

// This is a copy of `ThreadUtils.runOnMainThread`, but we can't depend on that since this class
// isn't jarjar'd (and the type of `block` would be different anyway, for that reason).
fun <T> runOnMainThread(block: () -> T): CompletableFuture<T> {
    return if (!Looper.getMainLooper().isCurrentThread) {
        val future = CompletableFuture<T>()
        Handler.createAsync(Looper.getMainLooper()).post {
            try {
                future.complete(block())
            }
            catch (exception: Exception) {
                future.completeExceptionally(exception)
            }
        }
        future
    }
    else {
        try {
            CompletableFuture.completedFuture(block())
        }
        catch (exception: Exception) {
            CompletableFuture<T>().apply { completeExceptionally(exception) }
        }
    }
}


