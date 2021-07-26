/*
 * Copyright (C) 2021 The Android Open Source Project
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

/** Represents accessing an index/key of an array/dictionary. e.g., ARRAY[FOO]. */
public class IndexExpression extends Expression {
    private final Expression array;
    private final Expression index;

    public IndexExpression(Expression array, Expression index) {
        super(array.getStart(), index.getEnd());
        this.array = array;
        this.index = index;
    }

    @Override
    public void write(String indent, PrintWriter writer) {
        array.write(indent, writer);
        writer.append("[");
        index.write(indent, writer);
        writer.append("]");
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        array.preOrder(nodes);
        index.preOrder(nodes);
    }

    @Override
    public void postOrder(List<Node> nodes) {
        index.postOrder(nodes);
        array.postOrder(nodes);
        nodes.add(this);
    }
}
