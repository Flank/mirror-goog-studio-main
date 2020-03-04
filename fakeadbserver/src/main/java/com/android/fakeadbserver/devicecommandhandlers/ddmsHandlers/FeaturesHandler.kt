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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.CommandHandler
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

private const val FEAT_CHUNK_HEADER_LENGTH = 4

/**
 * Provides a way to add specific features to a Client from a FakeAdbServer
 *
 * To use create a JdwpCommandHandler and add this as a packet handler.
 * Provide a map with the features wanted for each expected client pid.
 */
class FeaturesHandler(private val featureMap: Map<Int, List<String>>,
                      private val defaultFeatures: List<String>) : CommandHandler(), JdwpDdmsPacketHandler {

  override fun handlePacket(packet: JdwpDdmsPacket, client: ClientState, oStream: OutputStream): Boolean {
    val features = featureMap[client.pid] ?: defaultFeatures
    if (features.isEmpty()) {
      return false
    }
    val featureLength = features.sumBy { 4 + 2 * it.length }
    val payloadLength: Int = FEAT_CHUNK_HEADER_LENGTH + featureLength

    val payload = ByteArray(payloadLength)
    val payloadBuffer = ByteBuffer.wrap(payload)
    payloadBuffer.putInt(features.size)
    for (feature in features) {
      payloadBuffer.putInt(feature.length)
      for (c in feature) {
        payloadBuffer.putChar(c)
      }
    }

    val responsePacket = JdwpDdmsPacket.createResponse(packet.id, CHUNK_TYPE, payload)

    try {
      responsePacket.write(oStream)
    }
    catch (e: IOException) {
      writeFailResponse(oStream, "Could not write FEAT response packet")
      return false
    }

    if (client.isWaiting) {
      val waitPayload = ByteArray(1)
      val waitPacket = JdwpDdmsPacket.create(JdwpDdmsPacket.encodeChunkType("WAIT"), waitPayload)
      try {
        waitPacket.write(oStream)
      }
      catch (e: IOException) {
        writeFailResponse(oStream, "Could not write WAIT packet")
        return false
      }
    }
    return true
  }

  companion object {
    @JvmField
    val CHUNK_TYPE = JdwpDdmsPacket.encodeChunkType("FEAT")
  }
}
