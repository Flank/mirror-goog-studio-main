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

package com.android.tools.bazel.model;

import com.android.tools.bazel.parser.ast.CallStatement;
import com.google.common.collect.ImmutableList;

import java.io.IOException;

/**
 * A partially supported java_library with no sources.
 */
public class JavaLibrary extends BazelRule {

    public JavaLibrary(Package pkg, String name) {
        super(pkg, name);
    }

    @Override
    public void update() throws IOException {
        CallStatement call = getCallStatement("java_library", name);

        setArgument(call, "runtime_deps", dependencies);
        setArgument(call, "exports", exported);
        setArgument(call, "visibility", ImmutableList.of("//visibility:public"));
        addElementToList(call, "tags", "managed");
    }
}

