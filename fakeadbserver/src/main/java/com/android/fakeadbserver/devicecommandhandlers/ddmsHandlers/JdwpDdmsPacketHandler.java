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

import com.android.annotations.NonNull;
import com.android.fakeadbserver.ClientState;
import java.io.OutputStream;

public interface JdwpDdmsPacketHandler {

    /**
     * Interface for fake debugger to handle incoming packets
     *
     * @param packet The packet that is being handled
     * @param client The client associated with the connection
     * @param oStream The stream to write the response to
     * @return If true the fake debugger should continue accepting packets, if false it should
     *     terminate the session
     */
    boolean handlePacket(
            @NonNull JdwpDdmsPacket packet,
            @NonNull ClientState client,
            @NonNull OutputStream oStream);
}
