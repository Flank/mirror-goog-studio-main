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
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class PrintStream$ extends PrintStream {

    PrintStream$(String fileName) throws FileNotFoundException {
        super(new FileOutputStream$(fileName));
    }

    PrintStream$(String fileName, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        super(new FileOutputStream$(fileName), false, csn);
    }

    PrintStream$(File file) throws FileNotFoundException {
        super(new FileOutputStream$(file));
    }

    PrintStream$(File file, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        super(new FileOutputStream$(file), false, csn);
    }
}
