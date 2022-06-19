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
package com.android.adblib.tools.debugging.packets.ddms.chunks

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.utils.ResizableBuffer
import org.junit.Assert
import org.junit.Test

class DdmsApnmChunkTest {

    @Test
    fun testParsingWithAllFieldsWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsApnmChunk.writePayload(
                buffer,
                processName = "foo",
                userId = 10,
                packageName = "bar"
            )
            buffer.forChannelWrite()
        }

        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.APNM
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val apnmChunk = DdmsApnmChunk.parse(chunk)

        // Assert
        Assert.assertEquals("foo", apnmChunk.processName)
        Assert.assertEquals(10, apnmChunk.userId)
        Assert.assertEquals("bar", apnmChunk.packageName)
    }

    @Test
    fun testParsingWithMissingPackageNameWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsApnmChunk.writePayload(
                buffer,
                processName = "foo2",
                userId = 10,
                packageName = null
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.APNM
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val apnmChunk = DdmsApnmChunk.parse(chunk)

        // Assert
        Assert.assertEquals("foo2", apnmChunk.processName)
        Assert.assertEquals(10, apnmChunk.userId)
        Assert.assertEquals(null, apnmChunk.packageName)
    }

    @Test
    fun testParsingWithMissingUsedIDWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsApnmChunk.writePayload(
                buffer,
                processName = "foo2",
                userId = null,
                packageName = null
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.APNM
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val apnmChunk = DdmsApnmChunk.parse(chunk)

        // Assert
        Assert.assertEquals("foo2", apnmChunk.processName)
        Assert.assertEquals(null, apnmChunk.userId)
        Assert.assertEquals(null, apnmChunk.packageName)
    }
}
