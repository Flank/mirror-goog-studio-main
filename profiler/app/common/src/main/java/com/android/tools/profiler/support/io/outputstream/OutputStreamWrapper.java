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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * This class wraps {@link FileOutputStream} and {@link PrintStream} class constructors, these
 * constructors are called via reflection instead of the original ones, each method returns an
 * object of {@link FileOutputStream$} or {@link PrintStream$}.
 */
public class OutputStreamWrapper {

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileOutputStream wrapFileOutputStreamConstructor(File file)
        throws FileNotFoundException {
        return new FileOutputStream$(file);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileOutputStream wrapFileOutputStreamConstructor(File file, boolean append)
        throws FileNotFoundException {
        return new FileOutputStream$(file, append);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileOutputStream wrapFileOutputStreamConstructor(FileDescriptor fdObj) {
        return new FileOutputStream$(fdObj);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileOutputStream wrapFileOutputStreamConstructor(String name)
        throws FileNotFoundException {
        return new FileOutputStream$(name);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileOutputStream wrapFileOutputStreamConstructor(String name, boolean append)
        throws FileNotFoundException {
        return new FileOutputStream$(name, append);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintStream wrapPrintStreamConstructor(File file) throws FileNotFoundException {
        return new PrintStream$(file);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintStream wrapPrintStreamConstructor(File file, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        return new PrintStream$(file, csn);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintStream wrapPrintStreamConstructor(String fileName)
        throws FileNotFoundException {
        return new PrintStream$(fileName);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static PrintStream wrapPrintStreamConstructor(String fileName, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        return new PrintStream$(fileName, csn);
    }
}
