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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel

/**
 * Provides access to various elements of a DDMS "chunk". A DDMS "chunk" always starts with
 * an 8-byte header, followed by variable size buffer of [length] bytes.
 */
internal interface DdmsChunkView {
    /**
     * The chunk type, a 4-byte integer, see [DdmsChunkTypes]
     */
    val type: Int

    /**
     * The length (in bytes) of [payload], a 4-byte integer.
     */
    val length: Int

    /**
     * An [AdbBufferedInputChannel] that provides access to [length] bytes of payload.
     */
    val payload: AdbBufferedInputChannel
}
