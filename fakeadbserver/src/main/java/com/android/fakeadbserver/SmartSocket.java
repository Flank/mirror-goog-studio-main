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

package com.android.fakeadbserver;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.SocketChannel;

public class SmartSocket implements AutoCloseable {

    @NonNull private SocketChannel mSocket;

    SmartSocket(@NonNull SocketChannel socket) {
        this.mSocket = socket;
        // TODO: Should we declare a setSoTimeout of 10s on the socket?
        // Otherwise, a bad test fails with timeout which can take a long time.
        // If we go this way, we must add a SocketTimeoutException handler in the main loop runner.
    }

    Socket getSocket() {
        return mSocket.socket();
    }

    @NonNull
    ServiceRequest readServiceRequest() throws IOException {
        byte[] lengthString = new byte[4];
        readFully(lengthString);
        int requestLength = Integer.parseInt(new String(lengthString), 16);

        byte[] payloadBytes = new byte[requestLength];
        readFully(payloadBytes);
        String payload = new String(payloadBytes, UTF_8);
        return new ServiceRequest(payload);
    }

    private void readFully(@NonNull byte[] buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length) {
            bytesRead +=
                    mSocket.socket()
                            .getInputStream()
                            .read(buffer, bytesRead, buffer.length - bytesRead);
        }
    }

    void sendOkay() throws IOException {
        OutputStream stream = mSocket.socket().getOutputStream();
        stream.write("OKAY".getBytes(US_ASCII));
    }

    void sendFailWithReason(@NonNull String reason) {
        try {
            OutputStream stream = mSocket.socket().getOutputStream();
            stream.write("FAIL".getBytes(US_ASCII));
            byte[] reasonBytes = reason.getBytes(UTF_8);
            assert reasonBytes.length < 65536;
            stream.write(String.format("%04x", reason.length()).getBytes(US_ASCII));
            stream.write(reasonBytes);
            stream.flush();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws Exception {
        mSocket.close();
    }
}
