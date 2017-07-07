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
        assertEquals("[message count]", 3, messages.size());
        assertEquals("[prefix line]", prefix.trim(), messages.get(0).getText().trim());
        assertEquals(
                "[error location]", fileAndLineNumber.trim(), messages.get(1).getText().trim());

        Message formattedWarning = messages.get(2);
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
        assertEquals("[message count]", 3, messages.size());
        assertEquals("[prefix line]", prefix.trim(), messages.get(0).getText().trim());
        assertEquals(
                "[error location]", fileAndLineNumber.trim(), messages.get(1).getText().trim());

        Message formattedWarning = messages.get(2);
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
        assertEquals("[message count]", 3, messages.size());
        assertEquals("[prefix line]", prefix.trim(), messages.get(0).getText().trim());
        assertEquals(
                "[error location]", fileAndLineNumber.trim(), messages.get(1).getText().trim());

        Message formattedWarning = messages.get(2);
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
        assertEquals("[message count]", 3, messages.size());
        assertEquals("[prefix line]", prefix.trim(), messages.get(0).getText().trim());
        assertEquals(
                "[error location]", fileAndLineNumber.trim(), messages.get(1).getText().trim());

        Message formattedError = messages.get(2);
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
        assertEquals("[message count]", 4, messages.size());
        assertEquals("[prefix line]", prefix.trim(), messages.get(1).getText().trim());
        assertEquals(
                "[error location]", fileAndLineNumber.trim(), messages.get(2).getText().trim());

        Message formattedError = messages.get(3);
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
        assertEquals("[message count]", 3, messages.size());
        assertEquals("[prefix line]", prefix.trim(), messages.get(0).getText().trim());
        assertEquals(
                "[error location]", fileAndLineNumber.trim(), messages.get(1).getText().trim());

        Message formattedError = messages.get(2);
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
        assertEquals("[message count]", 2, messages.size());
        assertEquals("[error line]", err.trim(), messages.get(0).getText().trim());

        Message formattedError = messages.get(1);
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
        assertEquals("[message count]", 2, messages.size());
        assertEquals("[error line]", err.trim(), messages.get(0).getText().trim());

        Message formattedError = messages.get(1);
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
        assertEquals("[message count]", 2, messages.size());
        assertEquals("[error line]", err.trim(), messages.get(0).getText().trim());

        Message formattedError = messages.get(1);
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
        assertEquals("[message count]", 2, messages.size());
        assertEquals("[error line]", err.trim(), messages.get(0).getText().trim());

        Message formattedWarning = messages.get(1);
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
        assertEquals("[message count]", 2, messages.size());
        assertEquals("[error line]", err.trim(), messages.get(0).getText().trim());

        Message formattedWarning = messages.get(1);
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
        assertEquals("[message count]", 2, messages.size());
        assertEquals("[error line]", err.trim(), messages.get(0).getText().trim());

        Message formattedWarning = messages.get(1);
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
}
