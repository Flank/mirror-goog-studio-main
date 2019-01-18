/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.fakeadbserver;

import static com.google.common.base.Charsets.UTF_8;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;

public abstract class CommandHandler {

    protected static void writeOkay(@NonNull OutputStream stream) throws IOException {
        stream.write("OKAY".getBytes(UTF_8));
    }

    protected static void writeOkayResponse(@NonNull OutputStream stream, @NonNull String response)
            throws IOException {
        try {
            writeOkay(stream);
            write4ByteHexIntString(stream, response.length());
            writeString(stream, response);
        } catch (IOException ignored) {
        }
    }

    protected static void writeFail(@NonNull OutputStream stream) throws IOException {
        stream.write("FAIL".getBytes(UTF_8));
    }

    protected static boolean writeFailResponse(@NonNull OutputStream stream,
            @NonNull String reason) {
        try {
            writeFail(stream);
            write4ByteHexIntString(stream, reason.length());
            writeString(stream, reason);
        } catch (IOException ignored) {
        }
        return false;
    }

    protected static void write4ByteHexIntString(@NonNull OutputStream stream, int value)
            throws IOException {
        stream.write(String.format("%04x", value).getBytes(UTF_8));
    }

    protected static void writeString(@NonNull OutputStream stream, @NonNull String string)
            throws IOException {
        stream.write(string.getBytes(UTF_8));
    }
}
