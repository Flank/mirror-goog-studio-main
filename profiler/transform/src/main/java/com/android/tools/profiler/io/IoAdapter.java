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

package com.android.tools.profiler.io;

import org.objectweb.asm.ClassVisitor;

public class IoAdapter {

    public static ClassVisitor addIoAdapters(ClassVisitor visitor) {
        visitor = new InputStreamAdapter(visitor);
        visitor = new OutputStreamAdapter(visitor);
        visitor = new ReaderAdapter(visitor);
        visitor = new WriterAdapter(visitor);
        visitor = new ScannerAdapter(visitor);
        visitor = new RandomAccessFileAdapter(visitor);
        return visitor;
    }
}
