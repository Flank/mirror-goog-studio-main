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

package com.android.ide.common.blame.parser;

import com.android.annotations.NonNull;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.utils.ILogger;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses output from cmake. */
public class CmakeOutputParser implements PatternAwareOutputParser {
    private static final String CMAKE_ERROR = "CMake Error";
    private static final String CMAKE_WARNING = "CMake Warning";
    private static final String ERROR = "Error";
    private final Pattern cmakeErrorOrWarning =
            Pattern.compile("^\\s*CMake (Error|Warning)(: (Error|Warning) in cmake code)? at.*");
    private final Pattern doubleDashLine = Pattern.compile("^\\s*-- .*");
    private final Pattern fileAndLineNumber = Pattern.compile("^(.*):([0-9]+)? *:([0-9]+)?(.+)?");
    private final Pattern errorFileAndLineNumber =
            Pattern.compile(
                    "CMake (Error|Warning).*at ([^:]+):([0-9]+)?.*(\\([^:]*\\))?:([0-9]+)?(.+)?");

    @Override
    public boolean parse(
            @NonNull String line,
            @NonNull OutputLineReader reader,
            @NonNull List<Message> messages,
            @NonNull ILogger logger)
            throws ParsingFailedException {
        if (cmakeErrorOrWarning.matcher(line).matches()) {
            StringBuilder fullMessage = new StringBuilder(line + " ");
            String nextLine;
            // stop when nextLine is blank or matches the CMake prefix
            while ((nextLine = reader.readLine()) != null) {
                if (doubleDashLine.matcher(nextLine).matches()) {
                    messages.add(
                            new Message(Message.Kind.SIMPLE, nextLine, SourceFilePosition.UNKNOWN));
                } else if (nextLine.isEmpty() || cmakeErrorOrWarning.matcher(nextLine).matches()) {
                    reader.pushBack();
                    break;
                } else {
                    fullMessage.append(nextLine).append(" ");
                }
            }

            line = fullMessage.toString();
            return matchesErrorFileAndLineNumberError(line, messages)
                    || matchesFileAndLineNumberError(line, messages);
        }

        return false;
    }

    /**
     * Matches the following error or warning parsing CMakeLists.txt: <code>
     * CMake Error: ... at
     * /path/to/file:1234:1234
     * [Description of the error.]
     * </code> Or the same error on a single line. If the line number and/or column number are
     * missing, it defaults to -1, and won't affect the code link. If the description is missing, it
     * will use the full line as the description.
     *
     * <p>This also matches a "CMake Warning:" with the same structure.
     */
    private boolean matchesFileAndLineNumberError(
            @NonNull String line, @NonNull List<Message> messages) {
        Matcher matcher = fileAndLineNumber.matcher(line);
        if (matcher.matches()) {
            File file = new File(matcher.group(1));
            if (!file.isAbsolute()) {
                // If the path is not absolute, we can't produce a clickable error message.
                return false;
            }

            Message.Kind kind = Message.Kind.WARNING;
            for (Message m : messages) {
                if (m.getText().startsWith(CMAKE_ERROR)) {
                    kind = Message.Kind.ERROR;
                } else if (m.getText().startsWith(CMAKE_WARNING)) {
                    kind = Message.Kind.WARNING;
                }
            }

            int lineNumber = -1;
            if (matcher.group(2) != null) {
                lineNumber = Integer.valueOf(matcher.group(2));
            }

            int columnNumber = -1;
            if (matcher.group(3) != null) {
                columnNumber = Integer.valueOf(matcher.group(3));
            }

            String errorMessage = line;
            if (matcher.group(4) != null) {
                errorMessage = matcher.group(4);
            }

            SourceFilePosition position =
                    new SourceFilePosition(file, new SourcePosition(lineNumber, columnNumber, -1));
            Message message = new Message(kind, errorMessage, position);
            messages.add(message);
            return true;
        }

        return false;
    }

    /**
     * Matches the following error or warning parsing CMakeLists.txt: <code>
     *  CMake Error ... at
     *  /path/to/file:1234 (message):1234
     *  [Description of the error.]
     * </code> Or the same error on a single line. If the line number and/or column number are
     * missing, it defaults to -1, and won't affect the code link. If the description is missing, it
     * will use the full line as the description.
     *
     * <p>This also matches a warning with the same format.
     */
    private boolean matchesErrorFileAndLineNumberError(
            @NonNull String line, @NonNull List<Message> messages) {
        Matcher matcher = errorFileAndLineNumber.matcher(line);
        if (matcher.matches()) {
            File file = new File(matcher.group(2));
            if (!file.isAbsolute()) {
                return false;
            }

            Message.Kind kind = Message.Kind.WARNING;
            if (matcher.group(1).equals(ERROR)) {
                kind = Message.Kind.ERROR;
            }

            int lineNumber = -1;
            if (matcher.group(3) != null) {
                lineNumber = Integer.valueOf(matcher.group(3));
            }

            int columnNumber = -1;
            if (matcher.group(5) != null) {
                columnNumber = Integer.valueOf(matcher.group(5));
            }

            String errorMessage = line;
            if (matcher.group(6) != null) {
                errorMessage = matcher.group(6);
            }

            SourceFilePosition position =
                    new SourceFilePosition(file, new SourcePosition(lineNumber, columnNumber, -1));
            Message message = new Message(kind, errorMessage, position);
            messages.add(message);
            return true;
        }

        return false;
    }
}
