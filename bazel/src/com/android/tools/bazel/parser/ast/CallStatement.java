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
import java.util.List;

/**
 * A function call as a statement.
 */
public class CallStatement extends Statement {
    private final CallExpression call;

    public CallStatement(CallExpression call) {
        super(call.getStart(), call.getEnd());
        this.call = call;
    }

    @Override
    public void doWrite(PrintWriter writer) {
        call.write("", writer);
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        call.preOrder(nodes);
    }

    @Override
    public void postOrder(List<Node> nodes) {
        call.postOrder(nodes);
        nodes.add(this);
    }

    public CallExpression getCall() {
        return call;
    }

    boolean isManaged() {
        Expression tags = call.getArgument("tags");
        return (tags instanceof ListExpression) && ((ListExpression)tags).contains("managed");
    }
}
