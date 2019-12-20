/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ddmlib

import com.android.ddmlib.JdwpPacket.JDWP_HEADER_LEN
import com.android.ddmlib.jdwp.JdwpInterceptor
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer
import java.nio.ByteBuffer

class FakeClientBuilder {
  val client: Client = mock(Client::class.java)

  fun registerResponse(request: ArgumentMatcher<JdwpPacket>, responseType: Int, payload: ByteBuffer) = apply {
    payload.rewind()
    val size = payload.remaining()
    val rawBuf = ChunkHandler.allocBuffer(size + JDWP_HEADER_LEN)
    val returnPacket = JdwpPacket(rawBuf)
    val buf = ChunkHandler.getChunkDataBuf(rawBuf)
    buf.put(payload)
    ChunkHandler.finishChunkPacket(returnPacket, responseType, size)

    `when`(client.send(argThat(request), any())).thenAnswer(Answer { invocation ->
      val interceptor: JdwpInterceptor = invocation.getArgument(1)
      interceptor.intercept(client, returnPacket)
    })
  }

  fun build(): Client {
    return client
  }
}