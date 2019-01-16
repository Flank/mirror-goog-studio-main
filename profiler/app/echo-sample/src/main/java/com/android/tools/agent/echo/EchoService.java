/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.agent.echo;

/**
 * This class is a singleton to make accessing it easier from JNI. When the echo
 * command is passed a pid, it is routed to an agent attached to that process.
 * The agent then passes any recieved echo commands to the echo command handler.
 * The echo command handler uses JNI to call
 * EchoService.Initialize().onEchoCommmand([data]); This function grabs the
 * Release/SDK versions, and appends the incomming message before sending back
 * an echo event with the combined string as the payload.
 */
public class EchoService {

    private static EchoService sInstance;

    public static EchoService Instance() {
        if (sInstance == null) {
            sInstance = new EchoService();
        }
        return sInstance;
    }

    private EchoService() {
        sendEchoMessage("<from Java> Echo Service Created, no command needed to send messages.");
    }

    /**
     * Native function that handles creating the Event proto and pouplating the
     * EchoCommand data with the supplied message.
     */
    private native void sendEchoMessage(String message);

    /**
     * This method is called when an echo command is recieved by the agent.
     */
    public void onEchoCommand(String message) {
        String release = android.os.Build.VERSION.RELEASE;
        int sdk = android.os.Build.VERSION.SDK_INT;
        sendEchoMessage("<from Java> " + release + "-" + sdk + ": " + message);
    }
}
