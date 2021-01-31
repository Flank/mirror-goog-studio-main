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

package com.android.tools.agent.appinspection.testutils

import android.os.Looper
import org.junit.rules.ExternalResource
import java.util.concurrent.CountDownLatch

/**
 * Applying this rule creates a thread that will be set up as the main
 * thread for this test.
 */
class MainLooperRule : ExternalResource() {

    private val finishedLatch = CountDownLatch(1)

    override fun before() {
        val preparedLatch = CountDownLatch(1)
        Thread(
            {
                Looper.prepareMainLooper()
                preparedLatch.countDown()
                Looper.loop() // Blocks until quit is called
                finishedLatch.countDown()
            }, "MainLooperThread"
        ).start()
        preparedLatch.await()
    }

    override fun after() {
        Looper.getMainLooper().quit()
        finishedLatch.await()
    }
}
