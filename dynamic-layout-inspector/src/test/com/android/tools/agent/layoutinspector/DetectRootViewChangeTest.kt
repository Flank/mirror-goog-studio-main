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

import android.os.AsyncTask
import android.view.View
import com.android.testutils.MockitoKt.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class DetectRootViewChangeTest {
    private val myService: LayoutInspectorService = mock()
    private var myChangeDetector: DetectRootViewChange = DetectRootViewChange(myService)

    @Before
    fun setUp() {
        myChangeDetector.myCheckInterval = 1
    }

    @After
    fun tearDown() {
        myChangeDetector.cancel(true)
        waitForState(AsyncTask.Status.FINISHED)
    }

    @Test
    fun testAddRemove() {
        val startSem = Semaphore(0)
        val stopSem = Semaphore(0)
        `when`(myService.startLayoutInspector(any())).thenAnswer { startSem.release() }
        `when`(myService.stopCapturing(any())).thenAnswer { stopSem.release() }
        val (v1, v1Sem) = createView()
        val views = mutableListOf(v1)
        `when`(myService.rootViews).thenReturn(views)
        myChangeDetector.start(mutableListOf(v1))
        waitForState(AsyncTask.Status.RUNNING)
        // Verify nothing has happened yet
        verify(myService, times(0)).startLayoutInspector(any())
        verify(myService, times(0)).stopCapturing(any())
        verifyZeroInteractions(v1)

        // Add a view and verify that it's added and nothing is removed
        val (v2, _) = createView()
        views.add(v2)
        startSem.acquire()
        assertEquals(0, startSem.availablePermits())
        assertEquals(0, stopSem.availablePermits())
        verify(myService).startLayoutInspector(v2)
        verify(myService, times(0)).stopCapturing(any())
        verifyZeroInteractions(v1)
        verifyZeroInteractions(v2)

        // Add a view and remove the previous one
        val (v3, _) = createView()
        views.remove(v2)
        views.add(v3)
        startSem.acquire()
        stopSem.acquire()
        assertEquals(0, startSem.availablePermits())
        assertEquals(0, stopSem.availablePermits())
        verify(myService).startLayoutInspector(v3)
        verify(myService).stopCapturing(v2)
        verifyZeroInteractions(v1)
        verifyZeroInteractions(v2)
        verifyZeroInteractions(v3)

        // Remove a view
        views.remove(v3)
        stopSem.acquire()
        assertEquals(0, startSem.availablePermits())
        assertEquals(0, stopSem.availablePermits())
        verify(myService).stopCapturing(v3)
        v1Sem.acquire()
        verify(v1).invalidate()
        verifyZeroInteractions(v2)
        verifyZeroInteractions(v3)
    }

    private fun createView(): Pair<View, Semaphore> {
        val view: View = mock()
        val postSemaphore = Semaphore(0)
        `when`(view.post(any())).thenAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            postSemaphore.release()
            true
        }
        return view to postSemaphore
    }

    private fun waitForState(state: AsyncTask.Status) {
        val start = System.currentTimeMillis()
        while (myChangeDetector.status != state) {
            if (System.currentTimeMillis() - start > TimeUnit.SECONDS.toMillis(1)) {
                fail("Timeout waiting for root view change detector reach state $state.")
            }
            Thread.sleep(10)
        }
    }
}