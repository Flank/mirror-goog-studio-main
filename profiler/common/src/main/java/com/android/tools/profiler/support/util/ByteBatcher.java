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

package com.android.tools.profiler.support.util;

import java.io.ByteArrayOutputStream;

/**
 * Utility class for buffering bytes locally before reporting them.
 *
 * Use {@link #addByte(int)} and {@link #addBytes(byte[], int, int)} to add bytes individually to
 * this batcher, which will automatically trigger a {@link FlushReceiver} callback any time the
 * number of bytes goes over the batch threshold.
 *
 * When you are done adding bytes to the batcher, call {@link #flush()} to trigger the callback
 * with final remaining bytes, if any.
 */
public final class ByteBatcher {
    public interface FlushReceiver {
        void receive(byte[] bytes);
    }
    private static final int DEFAULT_THRESHOLD = 1024;
    private final int myThreshold;
    private final ByteArrayOutputStream myStream;
    private final FlushReceiver myReceiver;

    public ByteBatcher(FlushReceiver flushReceiver) {
        this(flushReceiver, DEFAULT_THRESHOLD);
    }

    public ByteBatcher(FlushReceiver flushReceiver, int capacity) {
        myReceiver = flushReceiver;
        myThreshold = capacity;
        myStream = new ByteArrayOutputStream(capacity);
    }

    public void addByte(int byteValue) {
        assert(myStream.size() < myThreshold);
        myStream.write(byteValue);

        if (myStream.size() == myThreshold) {
            flush();
        }
    }

    public void addBytes(byte[] bytes, int offset, int length) {
        while (myStream.size() + length >= myThreshold) {
            int currLen = myThreshold - myStream.size();
            myStream.write(bytes, offset, currLen);
            offset += currLen;
            length -= currLen;
            flush();
        }

        if (length > 0) {
            myStream.write(bytes, offset, length);
        }
        assert(myStream.size() < myThreshold);
    }

    public void flush() {
        if (myStream.size() == 0) {
            return;
        }
        myReceiver.receive(myStream.toByteArray());
        myStream.reset();
    }
}
