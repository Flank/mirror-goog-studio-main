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

/* JNI utilities for sending AppInspections messages across the underlying transport pipeline. */
class NativeTransport {
    /**
     * Send a large, binary payload, breaking it up into smaller pieces if necessary before
     * streaming it across a network boundary. This then returns an ID (unique across all inspectors
     * attached to this process) which can be used by other messages to reference the payload.
     *
     * <p>Sending many chunks overall takes longer than just sending everything in one large
     * message, but chunking allows us to avoid running into GRPC transmission size limits while
     * also playing friendly with other stream events that might be trying to go through at the same
     * time.
     */
    public static native long sendPayload(byte[] eventData, int length, int chunkSize);

    public static native void sendCreateInspectorResponseSuccess(int commandId);

    public static native void sendCreateInspectorResponseError(int commandId, String errorMessage);

    public static native void sendCreateInspectorResponseVersionIncompatible(
            int commandId, String errorMessage);

    public static native void sendCreateInspectorResponseLibraryMissing(
            int commandId, String errorMessage);

    public static native void sendCreateInspectorResponseAppProguarded(
            int commandId, String errorMessage);

    public static native void sendDisposeInspectorResponseSuccess(int commandId);

    public static native void sendDisposeInspectorResponseError(int commandId, String errorMessage);

    public static native void sendRawResponseData(int commandId, byte[] responseData, int length);

    public static native long sendRawResponsePayload(int commandId, long payloadId);

    public static native void sendRawResponseError(int commandId, String errorMessage);

    public static native void sendGetLibraryCompatibilityInfoResponse(
            int commandId, Object[] results, int length);

    public static native void sendDisposedEvent(String inspectorId, String errorMessage);

    public static native void sendRawEventData(String inspectorId, byte[] eventData, int length);

    public static native void sendRawEventPayload(String inspectorId, long payloadId);
}
