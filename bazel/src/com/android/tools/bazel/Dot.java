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

package com.android.tools.bazel;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Dot {
    private final String name;
    private final List<String> edges;

    public Dot(String name) {
        this.name = name;
        this.edges = new ArrayList<>();
    }

    public void addEdge(String from, String to, String color) {
        addEdge(from, to, color, "solid");
    }

    public void saveTo(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("digraph " + name + "{");
            for (String edge : edges) {
                writer.println(edge);
            }
            writer.println("}");
        }
    }

    public void addEdge(String from, String to, String color, String style) {
        edges.add("\"" + from + "\" -> \"" + to + "\" [color = \"" + color + "\" style=\"" + style + "\"]");
    }
}
