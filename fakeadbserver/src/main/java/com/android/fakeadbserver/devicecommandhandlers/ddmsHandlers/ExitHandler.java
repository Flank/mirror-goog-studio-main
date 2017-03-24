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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers;

import static com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacket.encodeChunkType;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.ClientState;
import java.io.OutputStream;

public class ExitHandler implements JdwpDdmsPacketHandler {

    public static final int CHUNK_TYPE = encodeChunkType("EXIT");

    @Override
    public boolean handlePacket(
            @NonNull JdwpDdmsPacket packet,
            @NonNull ClientState client,
            @NonNull OutputStream oStream) {
        // Kill the connection
        return false;
    }
}
