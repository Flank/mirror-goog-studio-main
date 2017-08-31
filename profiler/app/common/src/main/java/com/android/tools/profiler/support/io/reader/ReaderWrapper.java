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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * This class wraps {@link FileReader} class constructors, these constructors are called via
 * reflection instead of the original ones, each method returns an object of {@link
 * FileReader$}.
 */
public class ReaderWrapper {

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileReader wrapFileReaderConstructor(File file) throws FileNotFoundException {
        return new FileReader$(file);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileReader wrapFileReaderConstructor(FileDescriptor fd) {
        return new FileReader$(fd);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileReader wrapFileReaderConstructor(String fileName)
        throws FileNotFoundException {
        return new FileReader$(fileName);
    }
}
