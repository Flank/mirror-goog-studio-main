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

class DdmsHeloChunkTest {

    @Test
    fun testParsingWithAllFieldsWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsHeloChunk.writePayload(
                buffer,
                protocolVersion = 1,
                pid = 101,
                vmIdentifier = "myVm",
                processName = "foo",
                userId = 10,
                abi = "x86",
                jvmFlags = "flags",
                isNativeDebuggable = false,
                packageName = "bar"
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.HELO
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val heloChunk = DdmsHeloChunk.parse(chunk)

        // Assert
        Assert.assertEquals(1, heloChunk.protocolVersion)
        Assert.assertEquals(101, heloChunk.pid)
        Assert.assertEquals("myVm", heloChunk.vmIdentifier)
        Assert.assertEquals("foo", heloChunk.processName)
        Assert.assertEquals(10, heloChunk.userId)
        Assert.assertEquals("x86", heloChunk.abi)
        Assert.assertEquals("flags", heloChunk.jvmFlags)
        Assert.assertEquals(false, heloChunk.isNativeDebuggable)
        Assert.assertEquals("bar", heloChunk.packageName)
    }

    @Test
    fun testParsingWithMissingUserIdWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsHeloChunk.writePayload(
                buffer,
                protocolVersion = 1,
                pid = 101,
                vmIdentifier = "myVm",
                processName = "foo",
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.HELO
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val heloChunk = DdmsHeloChunk.parse(chunk)

        // Assert
        Assert.assertEquals(1, heloChunk.protocolVersion)
        Assert.assertEquals(101, heloChunk.pid)
        Assert.assertEquals("myVm", heloChunk.vmIdentifier)
        Assert.assertEquals("foo", heloChunk.processName)
        Assert.assertEquals(null, heloChunk.userId)
        Assert.assertEquals(null, heloChunk.abi)
        Assert.assertEquals(null, heloChunk.jvmFlags)
        Assert.assertEquals(false, heloChunk.isNativeDebuggable)
        Assert.assertEquals(null, heloChunk.packageName)
    }

    @Test
    fun testParsingWithMissingAbiWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsHeloChunk.writePayload(
                buffer,
                protocolVersion = 1,
                pid = 101,
                vmIdentifier = "myVm",
                processName = "foo",
                userId = 10,
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.HELO
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val heloChunk = DdmsHeloChunk.parse(chunk)

        // Assert
        Assert.assertEquals(1, heloChunk.protocolVersion)
        Assert.assertEquals(101, heloChunk.pid)
        Assert.assertEquals("myVm", heloChunk.vmIdentifier)
        Assert.assertEquals("foo", heloChunk.processName)
        Assert.assertEquals(10, heloChunk.userId)
        Assert.assertEquals(null, heloChunk.abi)
        Assert.assertEquals(null, heloChunk.jvmFlags)
        Assert.assertEquals(false, heloChunk.isNativeDebuggable)
        Assert.assertEquals(null, heloChunk.packageName)
    }

    @Test
    fun testParsingWithMissingJvmFlagsWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsHeloChunk.writePayload(
                buffer,
                protocolVersion = 1,
                pid = 101,
                vmIdentifier = "myVm",
                processName = "foo",
                userId = 10,
                abi = "x64",
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.HELO
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val heloChunk = DdmsHeloChunk.parse(chunk)

        // Assert
        Assert.assertEquals(1, heloChunk.protocolVersion)
        Assert.assertEquals(101, heloChunk.pid)
        Assert.assertEquals("myVm", heloChunk.vmIdentifier)
        Assert.assertEquals("foo", heloChunk.processName)
        Assert.assertEquals(10, heloChunk.userId)
        Assert.assertEquals("x64", heloChunk.abi)
        Assert.assertEquals(null, heloChunk.jvmFlags)
        Assert.assertEquals(false, heloChunk.isNativeDebuggable)
        Assert.assertEquals(null, heloChunk.packageName)
    }

    @Test
    fun testParsingWithMissingNativeDebuggableWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsHeloChunk.writePayload(
                buffer,
                protocolVersion = 1,
                pid = 101,
                vmIdentifier = "myVm",
                processName = "foo",
                userId = 10,
                abi = "x64",
                jvmFlags = "blah"
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.HELO
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val heloChunk = DdmsHeloChunk.parse(chunk)

        // Assert
        Assert.assertEquals(1, heloChunk.protocolVersion)
        Assert.assertEquals(101, heloChunk.pid)
        Assert.assertEquals("myVm", heloChunk.vmIdentifier)
        Assert.assertEquals("foo", heloChunk.processName)
        Assert.assertEquals(10, heloChunk.userId)
        Assert.assertEquals("x64", heloChunk.abi)
        Assert.assertEquals("blah", heloChunk.jvmFlags)
        Assert.assertEquals(false, heloChunk.isNativeDebuggable)
        Assert.assertEquals(null, heloChunk.packageName)
    }

    @Test
    fun testParsingWithMissingPackageNameWorks() = runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsHeloChunk.writePayload(
                buffer,
                protocolVersion = 1,
                pid = 101,
                vmIdentifier = "myVm",
                processName = "foo",
                userId = 10,
                abi = "x64",
                jvmFlags = "blah",
                isNativeDebuggable = true
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.HELO
            length = payload.remaining()
            data = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val heloChunk = DdmsHeloChunk.parse(chunk)

        // Assert
        Assert.assertEquals(1, heloChunk.protocolVersion)
        Assert.assertEquals(101, heloChunk.pid)
        Assert.assertEquals("myVm", heloChunk.vmIdentifier)
        Assert.assertEquals("foo", heloChunk.processName)
        Assert.assertEquals(10, heloChunk.userId)
        Assert.assertEquals("x64", heloChunk.abi)
        Assert.assertEquals("blah", heloChunk.jvmFlags)
        Assert.assertEquals(true, heloChunk.isNativeDebuggable)
        Assert.assertEquals(null, heloChunk.packageName)
    }
}
