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

import static org.junit.Assert.assertEquals;

import com.android.ide.common.blame.Message;
import com.android.utils.StdLogger;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link CmakeOutputParser}. */
public class CmakeOutputParserTest {
    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private ToolOutputParser mParser;
    private File mSourceFile;

    @Before
    public void setUp() throws IOException {
        mParser =
                new ToolOutputParser(
                        new CmakeOutputParser(), new StdLogger(StdLogger.Level.VERBOSE));
        mSourceFile = mTemporaryFolder.newFile();
    }

    @Test
    public void testMultilineCmakeWarningInFileWithoutLineNumberOrColumn() {
        String prefix = "CMake Warning: Warning in cmake code at\n";
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedWarning = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedWarning.getSourcePath().trim());
        assertEquals(
                "[line number]",
                -1,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                -1,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testMultilineCmakeWarningInFileWithLineNumber() {
        String prefix = "CMake Warning: Warning in cmake code at\n";
        int lineNumber = 13;
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber =
                String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedWarning = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedWarning.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartLine());
    }

    @Test
    public void testMultilineCmakeWarningInFileWithLineNumberAndColumnNumber() {
        String prefix = "CMake Warning: Warning in cmake code at\n";
        int lineNumber = 13;
        int columnNumber = 42;
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber =
                String.format(
                        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedWarning = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedWarning.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                columnNumber,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testMultilineCmakeErrorInFileWithoutLineNumberOrColumn() {
        String prefix = "CMake Error: Error in cmake code at\n";
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedError = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedError.getSourcePath().trim());
        assertEquals(
                "[line number]",
                -1,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                -1,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testMultilineCmakeErrorInFileWithLineNumber() {
        String prefix = "CMake Error: Error in cmake code at\n";
        int lineNumber = 13;
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber =
                String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
        String warning = "CMake Warning: this shouldn't get parsed\n";
        String err = warning + prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 2, messages.size());

        Message formattedError = messages.get(1);
        assertEquals("[source path]", filePath.trim(), formattedError.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartLine());
    }

    @Test
    public void testMultilineCmakeErrorInFileWithLineNumberAndColumnNumber() {
        String prefix = "CMake Error: Error in cmake code at\n";
        int lineNumber = 13;
        int columnNumber = 42;
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber =
                String.format(
                        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedError = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedError.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                columnNumber,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testSingleLineCmakeErrorInFileWithoutLineNumberOrColumn() {
        String prefix = "CMake Error: Error in cmake code at ";
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedError = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedError.getSourcePath().trim());
        assertEquals(
                "[line number]",
                -1,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                -1,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testSingleLineCmakeErrorInFileWithLineNumber() {
        String prefix = "CMake Error: Error in cmake code at ";
        String filePath = mSourceFile.getAbsolutePath();
        int lineNumber = 13;
        String fileAndLineNumber =
                String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedError = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedError.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                -1,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testSingleLineCmakeErrorInFileWithLineNumberAndColumn() {
        String prefix = "CMake Error: Error in cmake code at ";
        String filePath = mSourceFile.getAbsolutePath();
        int lineNumber = 13;
        int columnNumber = 42;
        String fileAndLineNumber =
                String.format(
                        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedError = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedError.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                columnNumber,
                formattedError.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testSingleLineCmakeWarningInFileWithoutLineNumberOrColumn() {
        String prefix = "CMake Warning: Warning in cmake code at ";
        String filePath = mSourceFile.getAbsolutePath();
        String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedWarning = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedWarning.getSourcePath().trim());
        assertEquals(
                "[line number]",
                -1,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                -1,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testSingleLineCmakeWarningInFileWithLineNumber() {
        String prefix = "CMake Warning: Warning in cmake code at ";
        String filePath = mSourceFile.getAbsolutePath();
        int lineNumber = 13;
        String fileAndLineNumber =
                String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedWarning = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedWarning.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                -1,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testSingleLineCmakeWarningInFileWithLineNumberAndColumn() {
        String prefix = "CMake Warning: Warning in cmake code at ";
        String filePath = mSourceFile.getAbsolutePath();
        int lineNumber = 13;
        int columnNumber = 42;
        String fileAndLineNumber =
                String.format(
                        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
        String err = prefix + fileAndLineNumber;
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());

        Message formattedWarning = messages.get(0);
        assertEquals("[source path]", filePath.trim(), formattedWarning.getSourcePath().trim());
        assertEquals(
                "[line number]",
                lineNumber,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartLine());
        assertEquals(
                "[column number]",
                columnNumber,
                formattedWarning.getSourceFilePositions().get(0).getPosition().getStartColumn());
    }

    @Test
    public void testLongErrorMessage() throws IOException {
        File makefile = mTemporaryFolder.newFile("CMakeLists.txt");
        String errorMessage = "CMake Error at %s:49 (message): %s";
        String loremIpsum =
                "Lorem ipsum dolor\n"
                        + "amet, consectetur adipiscing elit.  Etiam ac aliquam lacus.  Nullam suscipit nisl\n"
                        + "vitae sodales varius.  Donec eu enim ante.  Maecenas congue ante a nibh tristique,\n"
                        + "in sagittis velit suscipit.  Ut hendrerit molestie augue quis sodales.  Praesent ac\n"
                        + "consectetur est.  Duis at auctor neque.";

        errorMessage =
                String.format(
                        Locale.getDefault(), errorMessage, makefile.getAbsolutePath(), loremIpsum);

        List<Message> messages = mParser.parseToolOutput(errorMessage);
        String expectedErrorText = loremIpsum.replace('\n', ' ');
        Message formattedError = messages.get(0);
        assertEquals("[message count]", 1, messages.size());
        assertEquals(
                "[source path]",
                makefile.getAbsolutePath().trim(),
                formattedError.getSourcePath().trim());
        assertEquals("[error message]", expectedErrorText.trim(), formattedError.getText().trim());
    }
}
