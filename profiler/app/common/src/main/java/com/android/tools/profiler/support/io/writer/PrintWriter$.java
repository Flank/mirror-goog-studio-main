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

import com.android.tools.profiler.support.io.outputstream.FileOutputStream$;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;

public class PrintWriter$ extends PrintWriter {

    private static Charset toCharset(String csn) throws UnsupportedEncodingException {
        Objects.requireNonNull(csn, "charsetName");
        try {
            return Charset.forName(csn);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException unused) {
            // UnsupportedEncodingException should be thrown as we should have the same
            // behaviour as in the original PrintWriter#toCharSet method.
            throw new UnsupportedEncodingException(csn);
        }
    }

    PrintWriter$(String fileName) throws FileNotFoundException {
        super(new BufferedWriter(new OutputStreamWriter(new FileOutputStream$(fileName))), false);
    }

    PrintWriter$(String fileName, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        super(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream$(new File(fileName)), toCharset(csn))),
            false);
    }

    PrintWriter$(File file) throws FileNotFoundException {
        super(new BufferedWriter(new OutputStreamWriter(new FileOutputStream$(file))), false);
    }

    PrintWriter$(File file, String csn)
        throws FileNotFoundException, UnsupportedEncodingException {
        super(
            new BufferedWriter(new OutputStreamWriter(new FileOutputStream$(file), toCharset(csn))),
            false);
    }
}
