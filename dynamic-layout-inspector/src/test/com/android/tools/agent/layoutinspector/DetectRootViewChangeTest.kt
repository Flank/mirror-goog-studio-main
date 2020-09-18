/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.agent.layoutinspector

import android.view.View
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import java.util.concurrent.TimeUnit

class DetectRootViewChangeTest {
    private val step = TimeUnit.SECONDS.toMillis(1)
    private var myService: LayoutInspectorService? = mock()

    @After
    fun tearDown() {
        myService = null
    }

    @Test
    fun testAddRemove() {
        val v1 = createView()
        val views = mutableListOf(v1)
        `when`(myService!!.rootViews).thenReturn(views)

        val changeDetector = DetectRootViewChange(myService!!)
        var startCount = 0
        var stopCount = 0
        `when`(myService!!.startLayoutInspector(any())).thenAnswer { startCount++ }
        `when`(myService!!.stopCapturing(any())).thenAnswer { stopCount++ }

        // Verify nothing has happened yet
        assertThat(startCount).isEqualTo(0)
        assertThat(stopCount).isEqualTo(0)
        verifyZeroInteractions(v1)

        // Add a view and verify that it's added and nothing is removed
        val v2 = createView().also { views.add(it) }
        changeDetector.handler.advance(step)
        assertThat(startCount).isEqualTo(1)
        assertThat(stopCount).isEqualTo(0)
        verify(myService!!).startLayoutInspector(v2)
        verify(myService!!).startLayoutInspector(eq(v2))
        verifyZeroInteractions(v1)
        verifyZeroInteractions(v2)

        // Add a view and remove the previous one
        val v3 = createView().also { views.add(it) }.also { views.remove(v2) }
        changeDetector.handler.advance(step)
        assertThat(startCount).isEqualTo(2)
        assertThat(stopCount).isEqualTo(1)
        verify(myService!!).startLayoutInspector(v3)
        verify(myService!!).stopCapturing(v2)
        verifyZeroInteractions(v1)
        verifyZeroInteractions(v2)
        verifyZeroInteractions(v3)

        // Remove a view
        views.remove(v3)
        changeDetector.handler.advance(step)
        assertThat(startCount).isEqualTo(2)
        assertThat(stopCount).isEqualTo(2)
        verify(myService!!).stopCapturing(v3)
        verify(v1).invalidate()
        verifyZeroInteractions(v2)
        verifyZeroInteractions(v3)

        // Stop the change detector
        assertThat(changeDetector.handler.waitingMessages()).isGreaterThan(0)
        changeDetector.cancel()
        assertThat(changeDetector.handler.waitingMessages()).isEqualTo(0)
    }

    @Test
    fun testRemoveLast() {
        val v1 = createView()
        val views = mutableListOf(v1)
        `when`(myService!!.rootViews).thenReturn(views).thenReturn(emptyList())

        val changeDetector = DetectRootViewChange(myService!!)
        var startCount = 0
        var stopCount = 0
        `when`(myService!!.startLayoutInspector(any())).thenAnswer { startCount++ }
        `when`(myService!!.stopCapturing(any())).thenAnswer { stopCount++ }

        changeDetector.handler.advance(step)
        assertThat(startCount).isEqualTo(0)
        assertThat(stopCount).isEqualTo(1)

        assertThat(changeDetector.roots).isEmpty()
    }

    private fun createView(): View {
        val view: View = mock()
        `when`(view.post(any())).thenAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            true
        }
        return view
    }
}
