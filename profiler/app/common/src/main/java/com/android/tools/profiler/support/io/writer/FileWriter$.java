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

package com.android.tools.profiler.support.io.writer;

import com.android.tools.profiler.support.io.IoTracker;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class FileWriter$ extends FileWriter {

    private final IoTracker ioTracker = new IoTracker();

    FileWriter$(String fileName) throws IOException {
        super(fileName);
        ioTracker.trackNewFileSession(fileName);
    }

    FileWriter$(String fileName, boolean append) throws IOException {
        super(fileName, append);
        ioTracker.trackNewFileSession(fileName);
    }

    FileWriter$(File file) throws IOException {
        super(file);
        String filePath = file.getPath();
        ioTracker.trackNewFileSession(filePath);
    }

    FileWriter$(File file, boolean append) throws IOException {
        super(file, append);
        String filePath = file.getPath();
        ioTracker.trackNewFileSession(filePath);
    }

    FileWriter$(FileDescriptor fd) {
        super(fd);
        //TODO: find a way to get file path from fd
        String filePath = "";
        ioTracker.trackNewFileSession(filePath);
    }

    @Override
    public void write(int c) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(c);
        ioTracker.trackIoCall(Character.BYTES, startTimestamp, false);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(cbuf, off, len);
        ioTracker.trackIoCall(len * Character.BYTES, startTimestamp, false);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(str, off, len);
        ioTracker.trackIoCall(len * Character.BYTES, startTimestamp, false);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(cbuf);
        ioTracker.trackIoCall(cbuf.length * Character.BYTES, startTimestamp, false);
    }

    @Override
    public void write(String str) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.write(str);
        ioTracker.trackIoCall(str.length() * Character.BYTES, startTimestamp, false);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.append(csq);
        ioTracker.trackIoCall(csq.length() * Character.BYTES, startTimestamp, false);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.append(csq, start, end);
        ioTracker.trackIoCall((end - start) * Character.BYTES, startTimestamp, false);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        super.append(c);
        ioTracker.trackIoCall(Character.BYTES, startTimestamp, false);
        return this;
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
