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

import com.google.common.truth.Truth
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun checkNonProgressEvent(
    eventQueue: ArrayBlockingQueue<ByteArray>, block: (LayoutInspectorViewProtocol.Event) -> Unit
) = checkNextEventMatching(
    eventQueue,
    { it.specializedCase != LayoutInspectorViewProtocol.Event.SpecializedCase.PROGRESS_EVENT },
    block)

fun checkNextEventMatching(
    eventQueue: BlockingQueue<ByteArray>,
    condition: (LayoutInspectorViewProtocol.Event) -> Boolean,
    block: (LayoutInspectorViewProtocol.Event) -> Unit
) {
    val startTime = System.currentTimeMillis()
    var found = false
    while (startTime + TimeUnit.SECONDS.toMillis(10) > System.currentTimeMillis()) {
        val bytes = eventQueue.poll(10, TimeUnit.SECONDS) ?: throw TimeoutException()
        val event = LayoutInspectorViewProtocol.Event.parseFrom(bytes)
        if (!condition(event)) {
            // skip events that don't meet the condition
            continue
        }
        block(event)
        found = true
        break
    }
    Truth.assertThat(found).isTrue()
}
