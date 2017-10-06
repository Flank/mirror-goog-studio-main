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
 * A literal value represented by a single token.
 */
public class LiteralExpression extends Expression {

    public LiteralExpression(Token token) {
        super(token, token);
    }

    @Override
    public void write(String indent, PrintWriter writer) {
        for (String preComment : preComments) {
            writer.append(preComment).append(indent);
        }
        writer.append(getStart().value());
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
    }

    @Override
    public void postOrder(List<Node> nodes) {
        nodes.add(this);
    }

    public static LiteralExpression string(String source) {
        return new LiteralExpression(Token.string(source));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LiteralExpression) {
            LiteralExpression e = (LiteralExpression) obj;
            return e.getLiteral().equals(this.getLiteral());
        }
        return super.equals(obj);
    }
}
