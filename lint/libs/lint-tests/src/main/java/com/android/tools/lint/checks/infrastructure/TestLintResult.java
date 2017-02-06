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

package com.android.tools.lint.checks.infrastructure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.Warning;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import org.intellij.lang.annotations.Language;

/**
 * The result of running a {@link TestLintTask}.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class TestLintResult {
    private final String output;
    private final Exception exception;
    private final TestLintTask task;
    private final List<Warning> warnings;

    TestLintResult(@NonNull TestLintTask task, @Nullable String output, @Nullable Exception e,
            List<Warning> warnings) {
        this.task = task;
        this.output = output;
        this.exception = e;
        this.warnings = warnings;
    }

    /**
     * Checks that the lint result had the expected report format.
     *
     * @param expectedText the text to expect
     */
    public TestLintResult expect(@NonNull String expectedText) {
        assertEquals(expectedText, describeOutput());

        return this;
    }

    private String describeOutput() {
        if (exception != null) {
            StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer));

            if (output != null) {
                writer.write(output);
            }

            return writer.toString();
        } else {
            if (output == null) {
                return "";
            }
            return output;
        }
    }

    /**
     * Checks that there were no errors or exceptions.
     */
    public TestLintResult expectClean() {
        expect("No warnings.");
        return this;
    }

    /**
     * Checks that the results correspond to the messages inlined in the source files
     */
    public TestLintResult expectInlinedMessages() {
        for (ProjectDescription project : task.projects) {
            for (TestFile file : project.files) {
                String plainContents;
                String contents;
                try {
                    plainContents = file.getContents();
                    contents = file.getRawContents();
                    if (contents == null || plainContents == null) {
                        continue;
                    }
                } catch (Throwable ignore) {
                    continue;
                }

                String targetPath = file.getTargetPath();
                boolean isXml = targetPath.endsWith(SdkConstants.DOT_XML);

                try {
                    // TODO: Make comment token warnings depend on the source language
                    // For now, only handling Java

                    // We'll perform this check by going through all the files
                    // in the project, removing any inlined error messages in the file,
                    // then inserting error messages from the lint check, then asserting
                    // that the original file (with inlined error messages) is identical
                    // to the annotated file.

                    // We'll use a Swing document such that we can remove error comment
                    // ranges from the doc, and use the Position class to easily map
                    // offsets in reported errors to the corresponding location in the
                    // document after those edits have been made.

                    Document doc = new PlainDocument();
                    doc.insertString(0, isXml ? plainContents : contents, null);
                    Map<Integer, javax.swing.text.Position> positionMap = Maps.newHashMap();

                    // Find any errors reported in this document
                    List<Warning> matches = findWarnings(targetPath);

                    for (Warning warning : matches) {
                        Location location = warning.location;
                        Position start = location.getStart();
                        Position end = location.getEnd();

                        int startOffset = start != null ? start.getOffset() : 0;
                        int endOffset = end != null ? end.getOffset() : 0;

                        javax.swing.text.Position startPos = doc.createPosition(startOffset);
                        javax.swing.text.Position endPos = doc.createPosition(endOffset);

                        positionMap.put(startOffset, startPos);
                        positionMap.put(endOffset, endPos);
                    }

                    // Next remove any error regions from the document
                    stripMarkers(isXml, doc, contents);

                    // Finally write the expected errors back in
                    for (Warning warning : matches) {
                        Location location = warning.location;

                        Position start = location.getStart();
                        Position end = location.getEnd();

                        int startOffset = start != null ? start.getOffset() : 0;
                        int endOffset = end != null ? end.getOffset() : 0;

                        javax.swing.text.Position startPos = positionMap.get(startOffset);
                        javax.swing.text.Position endPos = positionMap.get(endOffset);

                        assertNotNull(startPos);
                        assertNotNull(endPos);

                        String startMarker;
                        String endMarker;
                        String message = warning.message;

                        // Use plain ascii in the test golden files for now. (This also ensures
                        // that the markup is well-formed, e.g. if we have a ` without a matching
                        // closing `, the ` would show up in the plain text.)
                        message = TextFormat.RAW.convertTo(message, TextFormat.TEXT);

                        if (isXml) {
                            String tag = warning.severity.getDescription().toLowerCase(Locale.ROOT);
                            startMarker = "<?" + tag + " message=\""
                                    + message + "\"?>";
                            endMarker = "<?" + tag + "?>";
                        } else {
                            // Java, Gradle, Kotlin, ...
                            startMarker = "/*" + message + "*/";
                            endMarker = "/**/";
                        }

                        startOffset = startPos.getOffset();
                        endOffset = endPos.getOffset();

                        doc.insertString(endOffset, endMarker, null);
                        doc.insertString(startOffset, startMarker, null);
                    }

                    // Assert equality
                    assertEquals(contents, doc.getText(0, doc.getLength()));
                } catch (BadLocationException ignore) {
                }
            }
        }

        return this;
    }

    private static void stripMarkers(boolean isXml, Document doc, String contents)
            throws BadLocationException {

        if (isXml) {
            // For processing instructions just remove all non-XML processing instructions
            // (we don't need to match beginning and ending ones)
            int index = contents.length();
            while (index >= 0) {
                int endEndOffset = contents.lastIndexOf("?>", index);
                if (endEndOffset == -1) {
                    break;
                }
                int endStartOffset = contents.lastIndexOf("<?", endEndOffset);
                if (endStartOffset == -1) {
                    break;
                }
                if (contents.startsWith("<?xml", endStartOffset)) {
                    index = endStartOffset - 1;
                    continue;
                }

                doc.remove(endStartOffset, endEndOffset + "?>".length() - endStartOffset);

                index = endStartOffset;
            }
        } else {
            // For Java/Groovy/Kotlin etc we don't want to remove *all* block comments;
            // only those that end with /**/. Note that this may not handle nested
            // ones correctly.
            int index = contents.length();
            while (index >= 0) {
                int endOffset = contents.lastIndexOf("/**/", index);
                if (endOffset == -1) {
                    break;
                }
                int regionStart = contents.lastIndexOf("/*", endOffset - 1);
                if (regionStart == -1) {
                    break;
                }
                int commentEnd = contents.indexOf("*/", regionStart + 2);
                if (commentEnd == -1 || commentEnd > endOffset) {
                    break;
                }

                doc.remove(endOffset, 4);
                doc.remove(regionStart, commentEnd + 2 - regionStart);

                index = regionStart;
            }
        }
    }

    @NonNull
    private List<Warning> findWarnings(@NonNull String targetFile) {
        // The target file should be platform neutral (/, not \ as separator)
        assertTrue(targetFile, !targetFile.contains("\\"));

        // Find any errors reported in this document
        List<Warning> matches = Lists.newArrayList();
        for (Warning warning : warnings) {
            String path = warning.file.getPath().replace(File.separatorChar, '/');
            if (path.endsWith(targetFile)) {
                matches.add(warning);
            }
        }

        // Sort by descending offset
        matches.sort((o1, o2) -> o2.offset - o1.offset);

        return matches;
    }

    public void expectMatches(@Language("RegExp") @NonNull String regexp) {
        String output = describeOutput();
        Pattern pattern = Pattern.compile(regexp);
        boolean found = pattern.matcher(output).find();
        if (!found) {
            fail("Did not find pattern\n  " + regexp + "\n in \n" + output);
        }
    }
}
