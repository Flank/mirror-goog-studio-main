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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.DeviceSelector
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD_SET
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsApnmChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsFeatChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsHeloChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsReaqChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsWaitChunk
import com.android.adblib.tools.debugging.packets.ddms.clone
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.EOFException
import java.nio.ByteBuffer

/**
 * The process name (and package name) can be set to this value when the process is not yet fully
 * initialized. We should ignore this value to make sure we only return "valid" process/package name.
 * Note that sometimes the process name (or package name) can also be empty.
 */
private val EARLY_PROCESS_NAMES = arrayOf("<pre-initialized>", "")

/**
 * Reads [JdwpProcessProperties] from a JDWP connection.
 */
internal class JdwpProcessPropertiesCollector(
  val session: AdbSession,
  val device: DeviceSelector,
  val pid: Int
) {

    private val logger = thisLogger(session)

    /**
     * Collects [JdwpProcessProperties] from a new JDWP session to the process [pid]
     * and emits them to [flowCollector].
     *
     * Throws EOFException if not enough information about the process could be collected, e.g.
     * if there is already another JDWP session open for the process.
     */
    suspend fun collect(flowCollector: FlowCollector<JdwpProcessProperties>) {
        val jdwpSession = JdwpSessionHandler.create(session, device, pid)
        jdwpSession.use {
            val collector = Collector(session, pid, flowCollector)
            collectProcessPropertiesImpl(jdwpSession, collector)
        }
    }

    private suspend fun collectProcessPropertiesImpl(
        jdwpSession: JdwpSessionHandler,
        collector: Collector
    ) {
        // Send a few ddms commands to collect process information
        val commands = IntroCommands(
            heloCommand = sendHeloPacket(jdwpSession),
            reaqCommand = sendReaqPacket(jdwpSession),
            featCommand = sendFeatPacket(jdwpSession)
        )
        collectDdmsReplyChunks(jdwpSession, collector, commands)
    }

    /**
     * Wait for various packets the Android VM sends when connecting to a process:
     * * `HELO` usually contains most info about the process (see [DdmsHeloChunk])
     * * `APNM` contains process and package names, as well as user ID.
     * * `WAIT` is a notification the process is waiting for a debugger to attach
     */
    private suspend fun collectDdmsReplyChunks(
        jdwpSession: JdwpSessionHandler,
        collector: Collector,
        commands: IntroCommands
    ) {

        val workBuffer = ResizableBuffer()
        while (true) {

            // This will throw an exception if EOF was reached
            val jdwpPacket = try {
                jdwpSession.receivePacket()
            } catch (e: EOFException) {
                // EOF happens in at least 2 common cases:
                // 1. On Android 27 and earlier, Android VM terminates a JDWP connection
                //    right away if there is already an active JDWP connection. On API 28 and later,
                //    Android VM queues JDWP connections if there is already an active one.
                // 2. If the process terminates before the timeout expires. This is sort of a race
                //    condition between us trying to retrieve data about a process, and the process
                //    terminating.
                logger.info { "pid=$pid: EOF while reading JDWP packet ($e)" }
                throw e
            }

            // Here is the situation of DDMS packets we receive from an Android VM:
            // * Sometimes (i.e. depending on the Android API level) we receive both "HELO"
            //   and "APNM", where "HELO" *or* "APNM" contain a "fake" process name, i.e. the
            //   "<pre-initialized>" string or the "" string. The order is non-deterministic,
            //   i.e. it looks like there is a race condition somewhere in the Android VM.
            //   So, basically, we need to ignore any instance of this "fake" name and hope we
            //   get a valid one at some point.
            // * Sometimes we consistently receive "HELO" with all the data correct. This seems
            //   to happen when re-attaching to the same process a 2nd time, for example.
            // * We have seen case where both "HELO" and "APNM" messages contain no data at all.
            //   This seems to be how the Android VM signals that the corresponding command
            //   packet were invalid. This should only happen if there is a bug in the code
            //   sending DDMS packet, i.e. if the code in this source file sends malformed
            //   packets (e.g. incorrect length).
            // * Wrt to the "WAIT" packet, we only receive it if the Android VM is waiting for
            //   a debugger to attach. The definition of "attaching a debugger" according to the
            //   "Android VM" is "receiving any valid JDWP command packet". Unfortunately, there
            //   is no "negative" version of the "WAIT" packet, meaning if the process is not
            //   in the "waiting for a debugger" state, there is no packet sent.

            // `HELO` packet is a reply to the `HELO` command we sent to the VM
            if (jdwpPacket.isReply && jdwpPacket.packetId == commands.heloCommand.packetId) {
                processHeloReply(collector, jdwpPacket, workBuffer)
            }

            // `FEAT` packet is a reply to the `FEAT` command we sent to the VM
            if (jdwpPacket.isReply && jdwpPacket.packetId == commands.featCommand.packetId) {
                processFeatReply(collector, jdwpPacket, workBuffer)
            }

            // `REAQ` packet is a reply to the `REAQ` command we sent to the VM
            if (jdwpPacket.isReply && jdwpPacket.packetId == commands.reaqCommand.packetId) {
                processReaqReply(collector, jdwpPacket, workBuffer)
            }

            // `WAIT` and `APNM` are DDMS chunks embedded in a JDWP command packet sent from
            // the VM to us
            if (jdwpPacket.isCommand(DDMS_CMD_SET, DDMS_CMD)) {
                // For completeness, we process all chunks embedded in a JDWP packet, even
                // though in practice there is always one and only one DDMS chunk per JDWP
                // packet.
                jdwpPacket.ddmsChunks().collect { chunk ->
                    val chunkCopy = chunk.clone()
                    when (chunkCopy.type) {
                        DdmsChunkTypes.WAIT -> {
                            processWaitCommand(collector, chunkCopy, workBuffer)
                        }
                        DdmsChunkTypes.APNM -> {
                            processApnmCommand(collector, chunkCopy, workBuffer)
                        }
                        else -> {
                            logger.debug { "pid=$pid: Skipping unexpected chunk: $chunkCopy" }
                        }
                    }
                }
            }
        }
    }

    private suspend fun processHeloReply(
        collector: Collector,
        packet: JdwpPacketView,
        workBuffer: ResizableBuffer
    ) {
        // This happens on "userdebug" devices with older APIs (e.g. emulator with API 24).
        // Processes are reported via "track-jdwp" but the device does not give any useful data
        // for the `HELO` command.
        if (!packet.isEmpty) {
            val heloChunkView = packet.ddmsChunks().first().clone()
            val heloChunk = DdmsHeloChunk.parse(heloChunkView, workBuffer)
            logger.debug { "pid=$pid: `HELO` chunk: $heloChunk" }
            collector.collect {
                it.copy(
                    processName = filterFakeName(heloChunk.processName),
                    userId = heloChunk.userId,
                    packageName = filterFakeName(heloChunk.packageName),
                    vmIdentifier = heloChunk.vmIdentifier,
                    abi = heloChunk.abi,
                    jvmFlags = heloChunk.jvmFlags,
                    isNativeDebuggable = heloChunk.isNativeDebuggable
                )
            }
        } else {
            logger.debug { "pid=$pid: `HELO` chunk: Skipping empty (invalid) reply packet" }
        }
    }

    private suspend fun processFeatReply(
        collector: Collector,
        packet: JdwpPacketView,
        workBuffer: ResizableBuffer
    ) {
        // This happens on userdebug devices with older APIs (e.g. emulator with API 24).
        // Processes are reported via "track-jdwp" but the device does not give any useful data for the `HELO` command.
        if (!packet.isEmpty) {
            val featChunkView = packet.ddmsChunks().first().clone()
            val featChunk = DdmsFeatChunk.parse(featChunkView, workBuffer)
            logger.debug { "pid=$pid: `FEAT` chunk: $featChunk" }
            collector.collect {
                it.copy(features = featChunk.features)
            }
        } else {
            logger.debug { "pid=$pid: `FEAT` chunk: Skipping empty (invalid) reply packet" }
        }
    }

    private suspend fun processReaqReply(
        collector: Collector,
        packet: JdwpPacketView,
        workBuffer: ResizableBuffer
    ) {
        // This happens on userdebug devices with older APIs (e.g. emulator with API 24).
        // Processes are reported via "track-jdwp" but the device does not give any useful data for the `HELO` command.
        if (!packet.isEmpty) {
            val reaqChunkView = packet.ddmsChunks().first().clone()
            val reaqChunk = DdmsReaqChunk.parse(reaqChunkView, workBuffer)
            logger.debug { "pid=$pid: `REAQ` chunk: $reaqChunk" }
            collector.collect {
                it.copy(/*TODO*/)
            }
        } else {
            logger.debug { "pid=$pid: `REAQ` chunk: Skipping empty (invalid) reply packet" }
        }
    }

    private suspend fun processWaitCommand(
        collector: Collector,
        chunkCopy: DdmsChunkView,
        workBuffer: ResizableBuffer
    ) {
        val waitChunk = DdmsWaitChunk.parse(chunkCopy, workBuffer)
        logger.debug { "pid=$pid: `WAIT` chunk: $waitChunk" }
        collector.collect { it.copy(isWaitingForDebugger = true) }
    }

    private suspend fun processApnmCommand(
        collector: Collector,
        chunkCopy: DdmsChunkView,
        workBuffer: ResizableBuffer
    ) {
        val apnmChunk = DdmsApnmChunk.parse(chunkCopy, workBuffer)
        logger.debug { "pid=$pid: `APNM` chunk: $apnmChunk" }
        collector.collect {
            it.copy(
                processName = filterFakeName(apnmChunk.processName),
                userId = apnmChunk.userId,
                packageName = filterFakeName(apnmChunk.packageName)
            )
        }
    }

    private suspend fun sendReaqPacket(jdwpSession: JdwpSessionHandler): JdwpPacketView {
        // Prepare chunk data buffer
        val data = ResizableBuffer().order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)

        // Send it as a JDWP packet
        val packet =
            createDdmsChunkPacket(
                jdwpSession.nextPacketId(),
                DdmsChunkTypes.REAQ,
                data.forChannelWrite()
            )
        jdwpSession.sendPacket(packet)
        return packet
    }

    private suspend fun sendHeloPacket(jdwpSession: JdwpSessionHandler): JdwpPacketView {
        // Prepare chunk data buffer
        val data = ResizableBuffer().order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)
        data.appendInt(DdmsHeloChunk.SERVER_PROTOCOL_VERSION)

        // Send it as a JDWP packet
        val packet =
            createDdmsChunkPacket(
                jdwpSession.nextPacketId(),
                DdmsChunkTypes.HELO,
                data.forChannelWrite()
            )
        jdwpSession.sendPacket(packet)
        return packet
    }

    private suspend fun sendFeatPacket(jdwpSession: JdwpSessionHandler): JdwpPacketView {
        // Prepare chunk data buffer
        val data = ResizableBuffer().order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)

        // Send it as a JDWP packet
        val packet =
            createDdmsChunkPacket(
                jdwpSession.nextPacketId(),
                DdmsChunkTypes.FEAT,
                data.forChannelWrite()
            )
        jdwpSession.sendPacket(packet)
        return packet
    }

    private suspend fun createDdmsChunkPacket(
        packetId: Int,
        chunkType: Int,
        chunkData: ByteBuffer
    ): JdwpPacketView {
        val chunk = MutableDdmsChunk()
        chunk.type = chunkType
        chunk.length = chunkData.remaining()
        chunk.data = AdbBufferedInputChannel.forByteBuffer(chunkData)
        val workBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(workBuffer)
        chunk.writeToChannel(outputChannel)
        val serializedChunk = workBuffer.forChannelWrite()

        val packet = MutableJdwpPacket()
        packet.packetId = packetId
        packet.packetLength = PACKET_HEADER_LENGTH + serializedChunk.remaining()
        packet.packetCmdSet = DDMS_CMD_SET
        packet.packetCmd = DDMS_CMD
        packet.data = AdbBufferedInputChannel.forByteBuffer(serializedChunk)

        logger.debug { "Preparing to send $chunk" }
        return packet
    }

    private fun filterFakeName(processOrPackageName: String?): String? {
        return if (EARLY_PROCESS_NAMES.contains(processOrPackageName)) {
            return null
        } else {
            processOrPackageName
        }
    }

    @Suppress("SpellCheckingInspection")
    data class IntroCommands(
        val heloCommand: JdwpPacketView,
        val reaqCommand: JdwpPacketView,
        val featCommand: JdwpPacketView
    )

    class Collector(
      session: AdbSession,
      pid: Int,
      private val flowCollector: FlowCollector<JdwpProcessProperties>
    ) {

        private val logger = thisLogger(session)

        private var currentProperties = JdwpProcessProperties(pid)

        suspend fun collect(updater: (JdwpProcessProperties) -> JdwpProcessProperties) {
            val newProperties = updater(currentProperties)
            if (newProperties != currentProperties) {
                logger.debug { "Collecting new process properties: old=$currentProperties, new=$newProperties" }
                currentProperties = newProperties
                flowCollector.emit(currentProperties)
            }
        }
    }
}
