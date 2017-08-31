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

package com.android.tools.profiler.support.io.outputstream;

import com.android.tools.profiler.support.io.IoTracker;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileOutputStream$ extends FileOutputStream {

    private final IoTracker ioTracker = new IoTracker();

    public FileOutputStream$(String name) throws FileNotFoundException {
        super(name);
        ioTracker.trackNewFileSession(name);
    }

    FileOutputStream$(String name, boolean append) throws FileNotFoundException {
        super(name, append);
        ioTracker.trackNewFileSession(name);
    }

    public FileOutputStream$(File file) throws FileNotFoundException {
        super(file);
        String filePath = file.getPath();
        ioTracker.trackNewFileSession(filePath);
    }

    FileOutputStream$(File file, boolean append) throws FileNotFoundException {
        super(file, append);
        String filePath = file.getPath();
        ioTracker.trackNewFileSession(filePath);
    }

    FileOutputStream$(FileDescriptor fdObj) {
        super(fdObj);
        //TODO: find a way to get file path from fd
        String filePath = "";
        ioTracker.trackNewFileSession(filePath);
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
    protected void finalize() throws IOException {
        super.finalize();
        ioTracker.trackTerminatingFileSession();
    }
}
