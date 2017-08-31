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

package com.android.tools.profiler.support.io.randomaccessfile;

import com.android.tools.profiler.support.io.IoTracker;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RandomAccessFile$ extends RandomAccessFile {

    private final IoTracker ioTracker = new IoTracker();

    RandomAccessFile$(String name, String mode) throws FileNotFoundException {
        super(name, mode);
        ioTracker.trackNewFileSession(name);
    }

    RandomAccessFile$(File file, String mode) throws FileNotFoundException {
        super(file, mode);
        String filePath = file.getPath();
        ioTracker.trackNewFileSession(filePath);
    }

    @Override
    public int read() throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int i = super.read();
        ioTracker.trackIoCall(Byte.BYTES, startTimestamp, true);
        return i;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int numberOfBytes = super.read(b, off, len);
        ioTracker.trackIoCall(numberOfBytes, startTimestamp, true);
        return numberOfBytes;
    }

    @Override
    public int read(byte[] b) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int numberOfBytes = super.read(b);
        ioTracker.trackIoCall(numberOfBytes, startTimestamp, true);
        return numberOfBytes;
    }

    @Override
    public void write(int b) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(b);
        ioTracker.trackIoCall(Byte.BYTES, startTimestamp, false);
    }

    @Override
    public void write(byte[] b) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(b);
        ioTracker.trackIoCall(b.length, startTimestamp, false);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(b, off, len);
        ioTracker.trackIoCall(len, startTimestamp, false);
    }

    @Override
    public void close() throws IOException {
        super.close();
        ioTracker.trackTerminatingFileSession();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        ioTracker.trackTerminatingFileSession();
    }
}
