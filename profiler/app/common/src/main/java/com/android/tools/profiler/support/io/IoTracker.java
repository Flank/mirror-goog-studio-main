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

package com.android.tools.profiler.support.io;

/**
 * This class is responsible for tracking I/O events, such as opening a file, reading,
 * writing and closing a file.
 */
public class IoTracker {

    /**
     * Each file session has a globally unique ID to identify it.
     */
    private final long mySessionId = nextId();
    /**
     * Indicates that this file session is terminated.
     */
    private boolean myIsTerminated = false;

    public void trackNewFileSession(String filePath) {
        if (myIsTerminated) {
            return;
        }
        trackNewFileSession(mySessionId, filePath);
    }

    public void trackIoCall(int numberOfBytes, long startTimestamp, boolean read) {
        if (numberOfBytes > 0 && !myIsTerminated) {
            trackIoCall(mySessionId, numberOfBytes, startTimestamp, read);
        }
    }

    public void trackTerminatingFileSession() {
        if (myIsTerminated) {
            return;
        }
        trackTerminatingFileSession(mySessionId);
        // This indicates that the file session is terminated and any future
        // operations will be discarded.
        myIsTerminated = true;
    }

    @Override
    protected void finalize() throws Throwable {
        trackTerminatingFileSession();
    }

    /**
     * Gets the current time from the device clock in nano seconds to be consistent with the native
     * code that uses the same method to set some timestamps.
     *
     * @return the current time in nano seconds.
     */

    public static native long getTimeInNanos();

    /**
     * Gets the next available session ID.
     *
     * @return the next available session ID.
     */
    private native long nextId();

    /**
     * Sends info about a new file opening session.
     *
     * @param sessionId represents a file opening session
     * @param filePath the path to the file that is being read from or written to.
     */
    private native void trackNewFileSession(long sessionId, String filePath);

    /**
     * Sends info about an I/O call to be tracked.
     *
     * @param sessionId represents a file opening session
     * @param numberOfBytes the number of bytes read or written.
     * @param startTimestamp the timestamp when reading or writing started.
     * @param read true if the I/O call is reading from a file, and false if it's writing.
     */
    private native void trackIoCall(
            long sessionId, int numberOfBytes, long startTimestamp, boolean read);

    /**
     * Sends info about terminating a file session.
     *
     * @param sessionId represents a file opening session
     */
    private native void trackTerminatingFileSession(long sessionId);
}
