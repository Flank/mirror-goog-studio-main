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
package com.android.adblib.tools.debugging.packets

import com.android.adblib.tools.debugging.packets.JdwpCommands.CmdSet.SET_THREADREF
import com.android.adblib.tools.debugging.packets.JdwpCommands.ThreadRefCmd.CMD_THREADREF_NAME
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class MutableJdwpPacketTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testMutableJdwpPacketCommandProperties() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 1_000_000
        packet.packetId = -10
        packet.packetCmdSet = 160
        packet.packetCmd = 240

        // Assert
        assertEquals(1_000_000, packet.packetLength)
        assertEquals(-10, packet.packetId)
        assertTrue(packet.isCommand)
        assertFalse(packet.isReply)
        assertEquals(160, packet.packetCmdSet)
        assertEquals(240, packet.packetCmd)
    }

    @Test
    fun testMutableJdwpPacketReplyProperties() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 1_000_000
        packet.packetId = -10
        packet.isReply = true
        packet.errorCode = 1_000

        // Assert
        assertEquals(1_000_000, packet.packetLength)
        assertEquals(-10, packet.packetId)
        assertFalse(packet.isCommand)
        assertTrue(packet.isReply)
        assertEquals(1_000, packet.errorCode)
    }

    @Test
    fun testMutableJdwpPacketThrowsIfInvalidLength() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        packet.packetLength = 5

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketThrowsIfInvalidCmdSet() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 1_000_000
        packet.packetId = -10

        exceptionRule.expect(IllegalArgumentException::class.java)
        packet.packetCmdSet = -10

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketThrowsIfInvalidCmd() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 1_000_000
        packet.packetId = -10

        exceptionRule.expect(IllegalArgumentException::class.java)
        packet.packetCmd = -10

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketThrowsIfInvalidErrorCode() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 1_000_000
        packet.packetId = -10

        exceptionRule.expect(IllegalArgumentException::class.java)
        packet.errorCode = -10

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketThrowsIfInvalidFlags() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 1_000_000
        packet.packetId = -10

        exceptionRule.expect(IllegalArgumentException::class.java)
        packet.packetFlags = -127

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketCanSetIsReply() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.isReply = true

        // Assert
        assertEquals(true, packet.isReply)
        assertEquals(false, packet.isCommand)
        assertEquals(JdwpPacketConstants.REPLY_PACKET_FLAG, packet.packetFlags)
    }

    @Test
    fun testMutableJdwpPacketCanSetIsCommand() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.isCommand = true

        // Assert
        assertEquals(false, packet.isReply)
        assertEquals(true, packet.isCommand)
        assertEquals(0, packet.packetFlags)
    }

    @Test
    fun testMutableJdwpPacketThrowsOnErrorCodeIfCommand() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.isCommand = true

        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val errorCode = packet.errorCode

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketThrowsOnCmdSetIfReply() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.isReply = true

        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val cmdSet = packet.packetCmdSet

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketThrowsOnCmdIfReply() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.isReply = true

        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val cmd = packet.packetCmd

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testMutableJdwpPacketToStringForCommandPacket() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 11
        packet.packetId = 10
        packet.packetCmdSet = SET_THREADREF.value
        packet.packetCmd = CMD_THREADREF_NAME.value
        val text = packet.toString()

        // Assert
        assertEquals("JdwpPacket(length=11, id=10, flags=0x00, isCommand=true, cmdSet=SET_THREADREF[11], cmd=CMD_THREADREF_NAME[1])", text)
    }

    @Test
    fun testMutableJdwpPacketToStringForReplyPacket() {
        // Prepare
        val packet = MutableJdwpPacket()

        // Act
        packet.packetLength = 11
        packet.packetId = 10
        packet.isReply = true
        packet.errorCode = 67
        val text = packet.toString()

        // Assert
        assertEquals("JdwpPacket(length=11, id=10, flags=0x80, isReply=true, errorCode=DELETE_METHOD_NOT_IMPLEMENTED[67])", text)
    }

}
