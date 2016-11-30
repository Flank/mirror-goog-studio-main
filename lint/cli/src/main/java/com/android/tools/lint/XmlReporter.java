/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint;

import static com.android.tools.lint.detector.api.TextFormat.RAW;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * A reporter which emits lint results into an XML report.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class XmlReporter extends Reporter {
    private final Writer writer;
    private boolean intendedForBaseline;

    /**
     * Constructs a new {@link XmlReporter}
     *
     * @param client the client
     * @param output the output file
     * @throws IOException if an error occurs
     */
    public XmlReporter(@NonNull LintCliClient client, @NonNull File output) throws IOException {
        super(client, output);
        writer = new BufferedWriter(Files.newWriter(output, Charsets.UTF_8));
    }

    public boolean isIntendedForBaseline() {
        return intendedForBaseline;
    }

    public void setIntendedForBaseline(boolean intendedForBaseline) {
        this.intendedForBaseline = intendedForBaseline;
    }

    @Override
    public void write(@NonNull Stats stats, List<Warning> issues) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        // Format 4: added urls= attribute with all more info links, comma separated
        writer.write("<issues format=\"4\"");
        String revision = client.getClientRevision();
        if (revision != null) {
            writer.write(String.format(" by=\"lint %1$s\"", revision));
        }
        writer.write(">\n");

        if (!issues.isEmpty()) {
            for (Warning warning : issues) {
                writer.write('\n');
                indent(writer, 1);
                writer.write("<issue");
                Issue issue = warning.issue;
                writeAttribute(writer, 2, "id", issue.getId());
                if (!intendedForBaseline) {
                    writeAttribute(writer, 2, "severity",
                            warning.severity.getDescription());
                }
                writeAttribute(writer, 2, "message", warning.message);

                if (!isIntendedForBaseline()) {
                    writeAttribute(writer, 2, "category",
                            issue.getCategory().getFullName());
                    writeAttribute(writer, 2, "priority",
                            Integer.toString(issue.getPriority()));
                    // We don't need issue metadata in baselines
                    writeAttribute(writer, 2, "summary",
                            issue.getBriefDescription(RAW));
                    writeAttribute(writer, 2, "explanation",
                            issue.getExplanation(RAW));

                    List<String> moreInfo = issue.getMoreInfo();
                    if (!moreInfo.isEmpty()) {
                        // Compatibility with old format: list first URL
                        writeAttribute(writer, 2, "url",
                                moreInfo.get(0));
                        writeAttribute(writer, 2, "urls",

                                Joiner.on(',').join(issue.getMoreInfo()));
                    }
                }
                if (warning.errorLine != null && !warning.errorLine.isEmpty()) {
                    String line = warning.errorLine;
                    int index1 = line.indexOf('\n');
                    if (index1 != -1) {
                        int index2 = line.indexOf('\n', index1 + 1);
                        if (index2 != -1) {
                            String line1 = line.substring(0, index1);
                            String line2 = line.substring(index1 + 1, index2);
                            writeAttribute(writer, 2, "errorLine1", line1);
                            writeAttribute(writer, 2, "errorLine2", line2);
                        }
                    }
                }

                if (warning.isVariantSpecific()) {
                    writeAttribute(writer, 2, "includedVariants", Joiner.on(',').join(warning.getIncludedVariantNames()));
                    writeAttribute(writer, 2, "excludedVariants", Joiner.on(',').join(warning.getExcludedVariantNames()));
                }

                if (!isIntendedForBaseline() &&
                        client.getRegistry() instanceof BuiltinIssueRegistry) {
                    if (hasAutoFix(issue)) {
                        writeAttribute(writer, 2, "quickfix", "studio");
                    }
                }

                assert (warning.file != null) == (warning.location != null);

                if (warning.file != null) {
                    assert warning.location.getFile() == warning.file;
                }

                Location location = warning.location;
                if (location != null) {
                    writer.write(">\n");
                    while (location != null) {
                        indent(writer, 2);
                        writer.write("<location");
                        String path = LintCliClient.getDisplayPath(warning.project,
                                location.getFile(),
                                // Don't use absolute paths in baseline files
                                client.flags.isFullPath() && !intendedForBaseline);
                        writeAttribute(writer, 3, "file", path);
                        Position start = location.getStart();
                        if (start != null) {
                            int line = start.getLine();
                            int column = start.getColumn();
                            if (line >= 0) {
                                // +1: Line numbers internally are 0-based, report should be
                                // 1-based.
                                writeAttribute(writer, 3, "line",
                                        Integer.toString(line + 1));
                                if (column >= 0) {
                                    writeAttribute(writer, 3, "column",
                                            Integer.toString(column + 1));
                                }
                            }
                        }

                        writer.write("/>\n");
                        location = location.getSecondary();
                    }
                    indent(writer, 1);
                    writer.write("</issue>\n");
                } else {
                    writer.write('\n');
                    indent(writer, 1);
                    writer.write("/>\n");
                }
            }
        }

        writer.write("\n</issues>\n");
        writer.close();

        if (!client.getFlags().isQuiet()
                && (stats.errorCount > 0 || stats.warningCount > 0)) {
            String url = SdkUtils.fileToUrlString(output.getAbsoluteFile());
            System.out.println(String.format("Wrote XML report to %1$s", url));
        }
    }

    private static void writeAttribute(Writer writer, int indent, String name, String value)
            throws IOException {
        writer.write('\n');
        indent(writer, indent);
        writer.write(name);
        writer.write('=');
        writer.write('"');
        writer.write(XmlUtils.toXmlAttributeValue(value));
        writer.write('"');
    }

    private static void indent(Writer writer, int indent) throws IOException {
        for (int level = 0; level < indent; level++) {
            writer.write("    ");
        }
    }
}