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

package com.android.tools.agent.app.inspection;

class Responses {
    public static native void replyError(int commandId, String errorMessage);

    public static native void replySuccess(int commandId);

    public static native void sendEvent(
            int commandId, byte[] eventData, int length, String inspectorId);

    public static void sendEvent(byte[] eventData, int length, String inspectorId) {
        sendEvent(0, eventData, length, inspectorId);
    }
}
