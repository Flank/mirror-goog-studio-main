/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.bazel.parser.ast.CallExpression;
import com.android.tools.bazel.parser.ast.CallStatement;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class FileGroup extends BazelRule {
    public FileGroup(Package pkg, String name) {
        super(pkg, name);
    }

    private List<String> sources = new LinkedList<>();

    @Override
    public void update() throws IOException {
        CallStatement statement = getCallStatement("filegroup", name);

        CallExpression call = statement.getCall();
        call.setArgument("srcs", sources);

        if (!statement.isFromFile()) {
            call.setArgument("visibility", ImmutableList.of("//visibility:public"));
        }
        String reason = "must match IML order";
        call.setDoNotSort("srcs", reason);

        statement.setIsManaged();
    }

    public void addSource(String source) {
        sources.add(source);
    }
}
