/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceFileState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Integer.min
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Implementation of the '`sync` protocol, using [DeviceFileState] as the backing "file system"
 */
class SyncCommandHandler : DeviceCommandHandler("sync") {

    override fun invoke(server: FakeAdbServer, socket: Socket, device: DeviceState, args: String) {
        try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // Tell client we accepted the `sync` service request
            CommandHandler.writeOkay(output)

            //
            // Handle the various "SEND", "RECV", etc. requests
            while (true) {
                // Sync protocol:
                // https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SYNC.TXT
                // Bytes 0-3: 'SEND', 'RECV', 'LIST', etc.
                when (val syncRequest = readSyncRequest(input)) {
                    "SEND" -> handleSendProtocol(device, input, output)
                    "RECV" -> handleRecvProtocol(device, input, output)
                    "STAT" -> handleStatProtocol(device, input, output)
                    else -> throwUnsupportedRequest(output, syncRequest)
                }
            }
        } catch (ignored: IOException) {
            // Unable to respond to the client, and we can't do anything about it. Swallow the exception and continue on
        }
    }

    /**
     * Data is sent by the peer using 'DATA' and 'DONE' packets
     */
    private fun handleSendProtocol(device: DeviceState, input: InputStream, output: OutputStream) {
        val (path, permission) = readSendHeader(input, output)
        val bytePackets = ArrayList<ByteArray>()
        while (true) {
            // Bytes 0-3: 'DATA' or 'DONE'
            when (val sendRequest = readSyncRequest(input)) {
                "DATA" -> {
                    val bytes = readDataPacket(input)
                    bytePackets.add(bytes)
                }
                "DONE" -> {
                    val modifiedDate = readDonePacket(input)
                    val bytes = bytePackets.flatMap { it.asIterable() }.toByteArray()
                    val file = DeviceFileState(path, permission, modifiedDate, bytes)
                    device.createFile(file)
                    sendSyncOkay(output)
                    break
                }
                else -> {
                    throwUnsupportedRequest(output, sendRequest)
                }
            }
        }
    }

    /**
     * Response is sent to the peer using 'DATA', 'DONE' or 'FAIL' packets
     */
    private fun handleRecvProtocol(device: DeviceState, input: InputStream, output: OutputStream) {
        val path = readRecvHeader(input)
        val fileState = device.getFile(path)
        if (fileState == null) {
            val reason = "File does not exist: '$path'"
            sendSyncFail(output, reason)
            return  // We do not throw, as we can accept another sync request
        }
        if (!fileState.isOwnerReadable()) {
            sendSyncFail(output, "File is not readable: '$path'")
            return
        }
        val bytesToSend = fileState.bytes
        var offset = 0
        var remainingCount = bytesToSend.size
        while (remainingCount > 0) {
            val count = min(remainingCount, 4_096)
            sendSyncData(output, bytesToSend, offset, count)
            offset += count
            remainingCount -= count
        }
        sendSyncDone(output)
    }

    /**
     * Response is four int32s: id, mode, size, time
     */
    private fun handleStatProtocol(device: DeviceState, input: InputStream, output: OutputStream) {
        val path = readRecvHeader(input)
        val fileState = device.getFile(path)
        if (fileState == null) {
            val reason = "File does not exist: '$path'"
            sendSyncFail(output, reason)
            return  // We do not throw, as we can accept another sync request
        }
        writeInt32(output, /* id= */ 0)
        writeInt32(output, fileState.permission)
        writeInt32(output, fileState.bytes.size)
        writeInt32(output, fileState.modifiedDate)
    }

    private fun readSyncRequest(input: InputStream): String {
        val bytes = readExactly(input, 4)
        return String(bytes, UTF_8)
    }

    private data class SendHeader(val path: String, val permissions: Int)

    private fun readSendHeader(input: InputStream, output: OutputStream): SendHeader {
        // Bytes 0-3: 'SEND'
        // Bytes 4-7: request size (little endian)
        // Bytes 8-xx: An utf-8 string with the remote file path followed by ',' followed by the permissions as a string
        val length = readLength(input)
        val bytes = readExactly(input, length)
        val header = String(bytes, UTF_8)
        val index = header.indexOf(',')
        if (index < 0 || index == header.length - 1) {
            val errorMessage = "Send protocol error: path and/or permissions missing"
            sendSyncFail(output, errorMessage)
            throw IOException(errorMessage)
        }
        return SendHeader(
            header.substring(0, index),
            header.substring(index + 1, header.length).toInt()
        )
    }

    private fun readRecvHeader(input: InputStream): String {
        // Bytes 0-3: 'RECV'
        // Bytes 4-7: request size (little endian)
        // Bytes 8-xx: An utf-8 string with the remote file path
        val length = readLength(input)
        val bytes = readExactly(input, length)
        return String(bytes, UTF_8)
    }

    private fun readDataPacket(input: InputStream): ByteArray {
        // Bytes 0-3: 'DATA'
        // Bytes 4-7: request size (little endian)
        // Bytes 8-xx: file bytes
        val length = readLength(input)
        return readExactly(input, length)
    }

    private fun readDonePacket(input: InputStream): Int {
        // Bytes 0-3: 'DONE'
        // Bytes 4-7: modified date (in seconds)
        return readInt32(input)
    }

    private fun readLength(input: InputStream): Int {
        return readInt32(input)
    }

    private fun sendSyncOkay(output: OutputStream) {
        writeOkay(output)
        writeInt32(output, 0)
    }

    private fun sendSyncFail(output: OutputStream, reason: String) {
        writeFail(output)
        writeInt32(output, reason.length)
        writeString(output, reason)
    }

    private fun sendSyncDone(output: OutputStream) {
        writeDone(output)
        writeInt32(output, 0)
    }

    private fun sendSyncData(output: OutputStream, bytes: ByteArray, offset: Int, count: Int) {
        writeData(output)
        writeInt32(output, count)
        output.write(bytes, offset, count)
    }

    private fun writeDone(stream: OutputStream) {
        stream.write("DONE".toByteArray(UTF_8))
    }

    private fun writeData(stream: OutputStream) {
        stream.write("DATA".toByteArray(UTF_8))
    }

    private fun readInt32(input: InputStream): Int {
        // Note: This could be way more efficient, but fake adb is for testing only
        val bytes = readExactly(input, 4)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
    }

    private fun writeInt32(output: OutputStream, value: Int) {
        // Note: This could be way more efficient, but fake adb is for testing only
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        output.write(bytes)
    }

    private fun throwUnsupportedRequest(output: OutputStream, syncRequest: String) {
        val message = "Unsupported sync request '${syncRequest}'"
        sendSyncFail(output, message)
        throw IOException(message)
    }

    private fun readExactly(input: InputStream, len: Int): ByteArray {
        val buffer = ByteArray(len)
        if (len == 0) {
            return buffer
        }
        var pos = 0
        while (pos < len) {
            val byteCount = input.read(buffer, pos, len - pos)
            if (byteCount < 0) {
                throw EOFException("Unexpected EOF")
            }
            if (byteCount == 0) {
                throw IOException("Unexpected stream implementation")
            }
            pos += byteCount
        }
        return buffer
    }
}
