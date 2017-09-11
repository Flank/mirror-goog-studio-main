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

package com.android.tools.profiler.support.io.reader;

import com.android.tools.profiler.support.io.IoTracker;
import java.io.*;
import java.nio.CharBuffer;

public class FileReader$ extends FileReader {

    private final IoTracker ioTracker = new IoTracker();

    FileReader$(String fileName) throws FileNotFoundException {
        super(fileName);
        ioTracker.trackNewFileSession(fileName);
    }

    FileReader$(File file) throws FileNotFoundException {
        super(file);
        String filePath = file.getPath();
        ioTracker.trackNewFileSession(filePath);
    }

    FileReader$(FileDescriptor fd) {
        super(fd);
        //TODO: find a way to get file path from fd
        String filePath = "";
        ioTracker.trackNewFileSession(filePath);
    }

    @Override
    public int read() throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int i = super.read();
        ioTracker.trackIoCall(Character.BYTES, startTimestamp, true);
        return i;
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int charactersRead = super.read(cbuf, offset, length);
        ioTracker.trackIoCall(charactersRead * Character.BYTES, startTimestamp, true);
        return charactersRead;
    }

    @Override
    public int read(CharBuffer target) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int charactersRead = super.read(target);
        ioTracker.trackIoCall(charactersRead * Character.BYTES, startTimestamp, true);
        return charactersRead;
    }

    @Override
    public int read(char[] cbuf) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int charactersRead = super.read(cbuf);
        ioTracker.trackIoCall(charactersRead * Character.BYTES, startTimestamp, true);
        return charactersRead;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            ioTracker.trackTerminatingFileSession();
        }
    }
}
