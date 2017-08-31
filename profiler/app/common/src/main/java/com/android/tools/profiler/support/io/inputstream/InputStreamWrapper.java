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

package com.android.tools.profiler.support.io.inputstream;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * This class wraps {@link FileInputStream} class constructors, these constructors are called via
 * reflection instead of the original ones, each method returns an object of {@link
 * FileInputStream$}.
 */
public class InputStreamWrapper {

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileInputStream wrapFileInputStreamConstructor(File file)
        throws FileNotFoundException {
        return new FileInputStream$(file);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileInputStream wrapFileInputStreamConstructor(FileDescriptor fdObj) {
        return new FileInputStream$(fdObj);
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static FileInputStream wrapFileInputStreamConstructor(String name)
        throws FileNotFoundException {
        return new FileInputStream$(name);
    }
}
