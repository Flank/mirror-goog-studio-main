/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you mabazel y not use this file except in compliance with the License.
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

package com.android.tools.agent.appinspection.nojarjar

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoRepository
import androidx.window.layout.WindowInfoRepositoryImpl
import androidx.window.layout.WindowLayoutInfo
import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.android.tools.agent.nojarjar.FoldObserverImpl
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.FoldEvent.FoldOrientation
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.FoldEvent.FoldState
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CyclicBarrier

class FoldObserverImplTest {
    @get:Rule
    val mainLooperRule = MainLooperRule()

    @Test
    fun testObserver() {
        val channel = Channel<WindowLayoutInfo>()
        WindowInfoRepository.instance = WindowInfoRepositoryImpl(channel.consumeAsFlow())
        val latch = CyclicBarrier(2)
        val observer = FoldObserverImpl { latch.await() }
        val activity = Activity()
        val view = View(activity)
        val group = ViewGroup(activity).apply { addView(view) }
        observer.startObservingFoldState(group)

        runBlocking {
            channel.send(WindowLayoutInfo(listOf(FoldingFeature("HALF_OPENED", "HORIZONTAL"))))
        }
        latch.await()
        assertThat(observer.foldState).isEqualTo(FoldState.HALF_OPEN)
        assertThat(observer.orientation).isEqualTo(FoldOrientation.HORIZONTAL)

        runBlocking {
            channel.send(WindowLayoutInfo(listOf(FoldingFeature("FLAT", "VERTICAL"))))
        }
        latch.await()
        assertThat(observer.foldState).isEqualTo(FoldState.FLAT)
        assertThat(observer.orientation).isEqualTo(FoldOrientation.VERTICAL)
        observer.shutdown()
    }
}
