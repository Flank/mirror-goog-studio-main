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

import com.google.common.collect.ImmutableList;
import java.util.LinkedList;
import java.util.List;

public abstract class Node {

    private final Token start;

    private final Token end;

    protected List<String> preComments = new LinkedList<>();

    private String postComment;

    protected Node(Token start, Token end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Adds this node and all its children to {@code nodes} in pre order.
     */
    public abstract void preOrder(List<Node> nodes);

    /**
     * Adds this node and all its children to {@code nodes} in post order.
     */
    public abstract void postOrder(List<Node> nodes);

    public Token getStart() {
        return start;
    }

    public Token getEnd() {
        return end;
    }

    public void addPreComment(Token comment) {
        preComments.add(comment.value().replaceAll(" +$", ""));
    }

    public void addPostComment(Token comment) {
        if (postComment != null) {
            throw new AssertionError("more than one post-comment");
        }
        postComment = comment.value().trim();
    }

    public boolean hasPreComment() {
        return !preComments.isEmpty();
    }

    public boolean hasPostComment() {
        return postComment != null;
    }

    public ImmutableList<String> getPreComments() {
        return ImmutableList.copyOf(preComments);
    }

    public String getPostComment() {
        return postComment;
    }
}
