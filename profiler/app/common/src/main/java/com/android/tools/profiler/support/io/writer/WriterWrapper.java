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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

/**
 * This class wraps {@link FileWriter} and {@link PrintWriter} class constructors, these
 * constructors are called via reflection instead of the original ones, each method returns an
 * object of {@link FileWriter$} or {@link PrintWriter$}.
 */
public class WriterWrapper {

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileWriter wrapFileWriterConstructor(File file) throws IOException {
        return new FileWriter$(file);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileWriter wrapFileWriterConstructor(File file, boolean append)
        throws IOException {
        return new FileWriter$(file, append);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileWriter wrapFileWriterConstructor(FileDescriptor fd) {
        return new FileWriter$(fd);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileWriter wrapFileWriterConstructor(String fileName) throws IOException {
        return new FileWriter$(fileName);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileWriter wrapFileWriterConstructor(String fileName, boolean append)
        throws IOException {
        return new FileWriter$(fileName, append);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintWriter wrapPrintWriterConstructor(File file) throws FileNotFoundException {
        return new PrintWriter$(file);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintWriter wrapPrintWriterConstructor(File file, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        return new PrintWriter$(file, csn);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintWriter wrapPrintWriterConstructor(String fileName)
        throws FileNotFoundException {
        return new PrintWriter$(fileName);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintWriter wrapPrintWriterConstructor(String fileName, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        return new PrintWriter$(fileName, csn);
    }
}
