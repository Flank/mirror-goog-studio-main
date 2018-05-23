/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.testutils.diff;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and applies diff files in unified format.
 *
 * <p>Usage:
 *
 * <pre>
 *   UnifiedDiff diff = new UnifiedDiff(new File("/path/to/file.diff"));
 *   diff.apply(new File("source/dir"));
 * </pre>
 */
public class UnifiedDiff {

    public static final Pattern FROM_FILE = Pattern.compile("--- (.*)");
    public static final Pattern TO_FILE = Pattern.compile("\\+\\+\\+ (.*)");
    public static final Pattern CHUNK_SPEC =
            Pattern.compile("@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@.*");
    public final List<Diff> diffs;

    public UnifiedDiff(File file) throws IOException {
        this(Files.readAllLines(file.toPath()));
    }

    public UnifiedDiff(List<String> lines) {
        diffs = new LinkedList<>();
        parse(lines);
    }

    public void apply(File directory) throws IOException {
        for (Diff diff : diffs) {
            Path path = Paths.get(diff.from);
            File target = new File(directory, path.subpath(3, path.getNameCount()).toString());
            List<String> strings = Files.readAllLines(target.toPath());
            diff.apply(strings);
            Files.write(target.toPath(), strings, StandardCharsets.UTF_8);
        }
    }

    private void parse(List<String> lines) {
        ParseState state = ParseState.HEADER;
        Diff diff = null;
        Chunk chunk = null;
        String from = null;
        int remFrom = 0;
        int remTo = 0;

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            switch (state) {
                case HEADER:
                    {
                        if (FROM_FILE.matcher(line).matches()) {
                            state = ParseState.FROM_FILE;
                            continue; // redo the line
                        } else if (CHUNK_SPEC.matcher(line).matches()) {
                            state = ParseState.CHUNK_SPEC;
                            continue; // redo the line
                        }
                        break;
                    }
                case FROM_FILE:
                    {
                        Matcher matcher = FROM_FILE.matcher(line);
                        ensure(matcher.matches(), "Expected file marker \"---\"");
                        diff = null;
                        from = matcher.group(1);
                        state = ParseState.TO_FILE;
                        break;
                    }
                case TO_FILE:
                    {
                        Matcher matcher = TO_FILE.matcher(line);
                        ensure(matcher.matches(), "Expected file marker \"+++\"");
                        String to = matcher.group(1);
                        diff = new Diff(from, to);
                        diffs.add(diff);
                        state = ParseState.CHUNK_SPEC;
                        break;
                    }
                case CHUNK_SPEC:
                    {
                        Matcher matcher = CHUNK_SPEC.matcher(line);
                        ensure(matcher.matches(), "Expected chunk spec");
                        ensure(diff != null, "Chunk spec must be inside a diff");
                        int fromLine = Integer.valueOf(matcher.group(1));
                        int fromSize = Integer.valueOf(matcher.group(2));
                        int toLine = Integer.valueOf(matcher.group(3));
                        int toSize = Integer.valueOf(matcher.group(4));
                        chunk = new Chunk(fromLine, fromSize, toLine, toSize);
                        diff.add(chunk);
                        remFrom = fromSize;
                        remTo = toSize;
                        state = ParseState.CHUNK;
                        break;
                    }
                case CHUNK:
                    {
                        ensure(chunk != null, "Chunk line unexpected");
                        switch (line.charAt(0)) {
                            case ' ':
                                ensure(remFrom > 0, "Unexpected common line, at line " + i);
                                ensure(remTo > 0, "Unexpected common line, at line " + i);
                                remFrom--;
                                remTo--;
                                chunk.addLine(line.substring(1), Chunk.Type.COMMON);
                                break;
                            case '-':
                                ensure(remFrom > 0, "Unexpected 'from' line, at line " + i);
                                remFrom--;
                                chunk.addLine(line.substring(1), Chunk.Type.FROM);
                                break;
                            case '+':
                                ensure(remTo > 0, "Unexpected common line, at line " + i);
                                remTo--;
                                chunk.addLine(line.substring(1), Chunk.Type.TO);
                                break;
                            default:
                                ensure(false, "Unexpected type of diff line at line " + i);
                        }
                        if (remTo == 0 && remFrom == 0) {
                            state = ParseState.HEADER;
                        }
                        break;
                    }
            }
            i++;
        }
    }

    private void ensure(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum ParseState {
        HEADER,
        FROM_FILE,
        TO_FILE,
        CHUNK_SPEC,
        CHUNK,
    }

    public static class Diff {

        public final String from;
        public final String to;
        public List<Chunk> chunks;

        public Diff(String from, String to) {
            this.from = from;
            this.to = to;
            chunks = new ArrayList<>();
        }

        public void add(Chunk chunk) {
            chunks.add(chunk);
        }

        public void apply(List<String> lines) {
            int dx = 0;
            for (Chunk chunk : chunks) {
                ListIterator<String> it = lines.listIterator(chunk.fromLine - 1 + dx);
                for (Line line : chunk.lines) {
                    if (line.type == Chunk.Type.TO) {
                        it.add(line.line);
                        dx++;
                    } else {
                        String fromLine = it.next();
                        if (!line.line.equals(fromLine)) {
                            throw new IllegalStateException(
                                    "Line expected to be:\n"
                                            + line.line
                                            + "\nbut was:\n"
                                            + fromLine);
                        }
                        if (line.type == Chunk.Type.FROM) {
                            it.remove();
                            dx--;
                        }
                    }
                }
            }
        }
    }

    public static class Chunk {

        public final int fromLine;
        public final int fromSize;
        public final int toLine;
        public final int toSize;
        public List<Line> lines;

        public enum Type {
            COMMON,
            FROM,
            TO,
        }

        public Chunk(int fromLine, int fromSize, int toLine, int toSize) {
            this.fromLine = fromLine;
            this.fromSize = fromSize;
            this.toLine = toLine;
            this.toSize = toSize;
            lines = new LinkedList<>();
        }

        public void addLine(String line, Type type) {
            lines.add(new Line(line, type));
        }
    }

    private static class Line {
        public String line;
        public Chunk.Type type;

        public Line(String line, Chunk.Type type) {
            this.line = line;
            this.type = type;
        }

        @Override
        public String toString() {
            return type + " " + line;
        }
    }
}
