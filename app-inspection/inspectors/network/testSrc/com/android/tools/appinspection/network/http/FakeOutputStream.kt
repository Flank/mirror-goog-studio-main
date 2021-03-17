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

import java.io.ByteArrayOutputStream
import java.io.OutputStream

class FakeOutputStream : OutputStream() {

    private val stream = ByteArrayOutputStream()

    override fun close() {
        stream.close()
    }

    override fun flush() {
        stream.flush()
    }

    override fun write(b: Int) {
        stream.write(b)
    }

    override fun write(b: ByteArray) {
        stream.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        stream.write(b, off, len)
    }
}
