/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.bazel.parser.ast;

import java.io.PrintWriter;

/**
 * A top-level statement.
 */
public abstract class Statement extends Node {

    protected Statement(Token start, Token end) {
        super(start, end);
    }

    public final void write(PrintWriter writer) {
        for (String comment : preComments) {
            writer.append(comment);
        }
        doWrite(writer);
        for (String comment : postComments) {
            writer.append("  " + comment);
        }
        writer.write("\n");
    }

    public abstract void doWrite(PrintWriter writer);
}
