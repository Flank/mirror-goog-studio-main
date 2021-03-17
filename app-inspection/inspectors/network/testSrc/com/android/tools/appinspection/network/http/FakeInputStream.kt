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

package com.android.tools.appinspection.network.http

import java.io.ByteArrayInputStream
import java.io.InputStream

class FakeInputStream(data: ByteArray) : InputStream() {

    private val bytes = ByteArrayInputStream(data)

    override fun close() = bytes.close()

    override fun read() = bytes.read()

    override fun read(b: ByteArray) = bytes.read(b)

    override fun read(b: ByteArray, off: Int, len: Int) = bytes.read(b, off, len)

    override fun skip(n: Long) = bytes.skip(n)

    override fun available() = bytes.available()

    override fun mark(readlimit: Int) = bytes.mark(readlimit)

    override fun reset() = bytes.reset()

    override fun markSupported() = bytes.markSupported()
}
