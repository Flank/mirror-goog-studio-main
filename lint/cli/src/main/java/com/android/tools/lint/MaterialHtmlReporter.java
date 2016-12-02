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

package com.android.tools.lint;

import static com.android.SdkConstants.DOT_JPG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;
import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reporter which emits lint results into an HTML report.
 * Like {@link HtmlReporter} but uses a newer Material style.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class MaterialHtmlReporter extends Reporter {
    /**
     * Maximum number of warnings allowed for a single issue type before we
     * split up and hide all but the first {@link #SHOWN_COUNT} items.
     */
    private static final int SPLIT_LIMIT = 8;
    /**
     * When a warning has at least {@link #SPLIT_LIMIT} items, then we show the
     * following number of items before the "Show more" button/link.
     */
    private static final int SHOWN_COUNT = SPLIT_LIMIT - 3;

    /** Number of lines to show around code snippets */
    static final int CODE_WINDOW_SIZE = 3;

    private static final String LIGHT_COLOR_PROPERTY = "lint.html.light-colors";

    /** Whether we're syntax highlighting code snippets with Darcula instead of light theme */
    static final boolean USE_DARCULA_FOR_CODE_SNIPPETS = !Boolean.getBoolean(LIGHT_COLOR_PROPERTY);

    /**
     * Whether we should try to use browser support for wavy underlines.
     * Underlines are not working well; see https://bugs.chromium.org/p/chromium/issues/detail?id=165462 for when to re-enable
     */
    private static final boolean USE_WAVY_UNDERLINES_FOR_ERRORS = false;

    /**
     * Stylesheet for the HTML report.
     * Note that the {@link LintSyntaxHighlighter} also depends on these class names.
     */
    static final String CSS_STYLES = ""
            + "section.section--center {\n"
            + "    max-width: 860px;\n"
            + "}\n"
            + ".mdl-card__supporting-text + .mdl-card__actions {\n"
            + "    border-top: 1px solid rgba(0, 0, 0, 0.12);\n"
            + "}\n"
            + "main > .mdl-layout__tab-panel {\n"
            + "  padding: 8px;\n"
            + "  padding-top: 48px;\n"
            + "}\n"
            + "\n"
            + ".mdl-card__actions {\n"
            + "    margin: 0;\n"
            + "    padding: 4px 40px;\n"
            + "    color: inherit;\n"
            + "}\n"
            + ".mdl-card > * {\n"
            + "    height: auto;\n"
            + "}\n"
            + ".mdl-card__actions a {\n"
            + "    color: #00BCD4;\n"
            + "    margin: 0;\n"
            + "}\n"
            + ".error-icon {\n"
            + "    color: #bb7777;\n"
            + "    vertical-align: bottom;\n"
            + "}\n"
            + ".warning-icon {\n"
            + "    vertical-align: bottom;\n"
            + "}\n"
            + ".mdl-layout__content section:not(:last-of-type) {\n"
            + "  position: relative;\n"
            + "  margin-bottom: 48px;\n"
            + "}\n"
            + "\n"
            + ".mdl-card .mdl-card__supporting-text {\n"
            + "  margin: 40px;\n"
            + "  -webkit-flex-grow: 1;\n"
            + "      -ms-flex-positive: 1;\n"
            + "          flex-grow: 1;\n"
            + "  padding: 0;\n"
            + "  color: inherit;\n"
            + "  width: calc(100% - 80px);\n"
            + "}\n"
            // Bug workaround - without this the hamburger icon is off center
            + "div.mdl-layout__drawer-button .material-icons {\n"
            + "    line-height: 48px;\n"
            + "}\n"
            // Make titles look better:
            + ".mdl-card .mdl-card__supporting-text {\n"
            + "    margin-top: 0px;\n"
            + "}\n"
            + ".chips {\n"
            + "    float: right;\n"
            + "    vertical-align: middle;\n"
            + "}\n"
            + (USE_DARCULA_FOR_CODE_SNIPPETS ? (""
            + "pre.errorlines {\n"
            + "    background-color: #2b2b2b;\n"
            + "    color: #a9b7c6;\n"
            + "    font-family: monospace;\n"
            + "    font-size: 0.9rem;"
            + "    line-height: 0.9rem;\n" // ensure line number gutter looks contiguous
            + "    padding: 6px;\n"
            + "    border: 1px solid #e0e0e0;\n"
            + "    overflow: scroll;\n"
            + "}\n"
            + ".prefix {\n"
            + "    color: #9876aa;\n"
            + "}\n"
            + ".attribute {\n"
            + "    color: #BABABA;\n"
            + "}\n"
            + ".value {\n"
            + "    color: #6a8759;\n"
            + "}\n"
            + ".tag {\n"
            + "    color: #e8bf6a;\n"
            + "}\n"
            + ".comment {\n"
            + "    color: #808080;\n"
            + "}\n"
            + ".javadoc {\n"
            + "    font-style: italic;\n"
            + "    color: #629755;\n"
            + "}\n"
            + ".annotation {\n"
            + "    color: #BBB529;\n"
            + "}\n"
            + ".string {\n"
            + "    color: #6a8759;\n"
            + "}\n"
            + ".number {\n"
            + "    color: #6897bb;\n"
            + "}\n"
            + ".keyword {\n"
            + "    color: #cc7832;\n"
            + "}\n"
            + ".caretline {\n"
            + "    background-color: #323232;\n"
            + "}\n"
            + ".lineno {\n"
            + "    color: #606366;\n"
            + "    background-color: #313335;\n"
            + "}\n"
            + ".error {\n"
            + (!USE_WAVY_UNDERLINES_FOR_ERRORS ? ""
            + "    text-decoration: none;\n"
            + "    background-color: #622a2a;\n"
            + "" : ""
               // wavy underlines
            + "    text-decoration: underline wavy #ff0000;\n"
            + "    text-decoration-color: #ff0000;\n"
            + "    -webkit-text-decoration-color: #ff0000;\n"
            + "    -moz-text-decoration-color: #ff0000;\n")
            + "}\n"
            + ".warning {\n"
            + (!USE_WAVY_UNDERLINES_FOR_ERRORS ? ""
            + "    text-decoration: none;\n"
            + "    background-color: #52503a;\n"
            + "" : ""
            // wavy underlines
            + "    text-decoration: underline wavy #f49810;\n"
            + "    text-decoration-color: #f49810;\n"
            + "    -webkit-text-decoration-color: #f49810;\n"
            + "    -moz-text-decoration-color: #f49810;\n")
            + "}\n"
            + "")

            // Light theme
            : (""
                    // Syntax highlighting
                    + "pre.errorlines {\n"
                    + "    background-color: white;\n"
                    + "    font-family: monospace;\n"
                    + "    border: 1px solid #e0e0e0;\n"
                    //+ "    line-height: 16px;\n" // ensure line number gutter looks contiguous
                    + "    line-height: 0.9rem;\n" // ensure line number gutter looks contiguous
                    + "    font-size: 0.9rem;"
                    + "}\n"
                    + ".prefix {\n"
                    + "    color: #660e7a;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".attribute {\n"
                    + "    color: #0000ff;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".value {\n"
                    + "    color: #008000;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".tag {\n"
                    + "    color: #000080;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".comment {\n"
                    + "    color: #808080;\n"
                    + "    font-style: italic;\n"
                    + "}\n"
                    + ".javadoc {\n"
                    + "    color: #808080;\n"
                    + "    font-style: italic;\n"
                    + "}\n"
                    + ".annotation {\n"
                    + "    color: #808000;\n"
                    + "}\n"
                    + ".string {\n"
                    + "    color: #008000;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".number {\n"
                    + "    color: #0000ff;\n"
                    + "}\n"
                    + ".keyword {\n"
                    + "    color: #000080;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".caretline {\n"
                    + "    background-color: #fffae3;\n"
                    + "}\n"
                    + ".lineno {\n"
                    + "    color: #999999;\n"
                    + "    background-color: #f0f0f0;\n"
                    + "}\n"
                    + ".error {\n"
                    + (!USE_WAVY_UNDERLINES_FOR_ERRORS ? ""
                    + "    text-decoration: none;\n"
                    + "    background-color: #f8d8d8;\n"
                    + "" : ""
                    // wavy underlines
                    + "    text-decoration: underline wavy #ff0000;\n"
                    + "    text-decoration-color: #ff0000;\n"
                    + "    -webkit-text-decoration-color: #ff0000;\n"
                    + "    -moz-text-decoration-color: #ff0000;\n")
                    + "}\n"
                    + ".warning {\n"
                    + (!USE_WAVY_UNDERLINES_FOR_ERRORS ? ""
                    + "    text-decoration: none;\n"
                    + "    background-color: #f6ebbc;\n"
                    + "" : ""
                    // wavy underlines
                    + "    text-decoration: underline wavy #f49810;\n"
                    + "    text-decoration-color: #f49810;\n"
                    + "    -webkit-text-decoration-color: #f49810;\n"
                    + "    -moz-text-decoration-color: #f49810;\n")
                    + "}\n"
            ))


            + ".overview {\n"
            + "    padding: 10pt;\n"
            + "    width: 100%;\n"
            + "    overflow: auto;\n"
            + "    border-collapse:collapse;\n"
            + "}\n"
            + ".overview tr {\n"
            + "    border-bottom: solid 1px #eeeeee;\n"
            + "}\n"
            + ".categoryColumn a {\n"
            + "     text-decoration: none;\n"
            + "     color: inherit;\n"
            + "}\n"
            + ".countColumn {\n"
            + "    text-align: right;\n"
            + "    padding-right: 20px;\n"
            + "    width: 50px;\n"
            + "}\n"
            + ".issueColumn {\n"
            + "   padding-left: 16px;\n"
            + "}\n"
            + ".categoryColumn {\n"
            + "   position: relative;\n"
            + "   left: -50px;\n"
            + "   padding-top: 20px;\n"
            + "   padding-bottom: 5px;\n"
            + "}\n"
            + "\n"
            ;

    protected final Writer writer;
    protected final LintCliFlags flags;
    private HtmlBuilder builder;
    @SuppressWarnings("StringBufferField")
    private StringBuilder sb;
    private String highlightedFile;
    private LintSyntaxHighlighter highlighter;

    /**
     * Creates a new {@link MaterialHtmlReporter}
     *
     * @param client the associated client
     * @param output the output file
     * @param flags the command line flags
     * @throws IOException if an error occurs
     */
    public MaterialHtmlReporter(
            @NonNull LintCliClient client,
            @NonNull File output,
            @NonNull LintCliFlags flags) throws IOException {
        super(client, output);
        writer = new BufferedWriter(Files.newWriter(output, Charsets.UTF_8));
        this.flags = flags;
    }

    @Override
    public void write(@NonNull Stats stats, List<Warning> issues) throws IOException {
        Map<Issue, String> missing = computeMissingIssues(issues);
        List<List<Warning>> related = computeIssueLists(issues);

        startReport(stats);

        writeNavigationHeader(stats, () -> {
            append("      <a class=\"mdl-navigation__link\" href=\"#overview\">"
                    + "<i class=\"material-icons\">dashboard</i>Overview</a>\n");

            for (List<Warning> warnings : related) {
                Warning first = warnings.get(0);
                String anchor = first.issue.getId();
                String desc = first.issue.getBriefDescription(TextFormat.HTML);
                append("      <a class=\"mdl-navigation__link\" href=\"#" + anchor
                        + "\">");
                if (first.severity.isError()) {
                    append("<i class=\"material-icons error-icon\">error</i>");
                } else {
                    append("<i class=\"material-icons warning-icon\">warning</i>");
                }
                append(desc + " (" + warnings.size() + ")</a>\n");
            }
        });

        writeNewHtmlFormatInfoCard();

        if (!issues.isEmpty()) {
            writeCard(() -> writeOverview(related, missing.size()), "Overview", true);

            Category previousCategory = null;
            for (List<Warning> warnings : related) {
                Category category = warnings.get(0).issue.getCategory();
                if (category != previousCategory) {
                    previousCategory = category;
                    append("\n<a name=\"");
                    append(category.getFullName());
                    append("\"></a>\n");
                }

                writeIssueCard(warnings);
            }


            if (!client.isCheckingSpecificIssues()) {
                writeMissingIssues(missing);
            }

            writeSuppressIssuesCard();
        } else {
            writeCard(() -> append("Congratulations!"), "No Issues Found");
        }

        finishReport();
        writeReport();

        if (!client.getFlags().isQuiet()
                && (stats.errorCount > 0 || stats.warningCount > 0)) {
            String url = SdkUtils.fileToUrlString(output.getAbsoluteFile());
            System.out.println(String.format("Wrote HTML report to %1$s", url));
        }
    }
    
    private void append(@NonNull String s) {
        sb.append(s);
    }

    private void append(char s) {
        sb.append(s);
    }

    private void writeSuppressIssuesCard() {
        append("\n<a name=\"SuppressInfo\"></a>\n");
        writeCard(() -> {
            append(TextFormat.RAW.convertTo(Main.getSuppressHelp(), TextFormat.HTML));
            this.append('\n');
        }, "Suppressing Warnings and Errors");
    }

    private void writeIssueCard(List<Warning> warnings) {
        Issue firstIssue = warnings.get(0).issue;
        append("<a name=\"" + firstIssue.getId() + "\"></a>\n");
        writeCard(() -> {
                    Warning first = warnings.get(0);
                    Issue issue = first.issue;

                    append("<div class=\"issue\">\n");

                    append("<div class=\"warningslist\">\n");
                    boolean partialHide = !simpleFormat && warnings.size() > SPLIT_LIMIT;

                    int count = 0;
                    for (Warning warning : warnings) {
                        // Don't show thousands of matches for common errors; this just
                        // makes some reports huge and slow to render and nobody really wants to
                        // inspect 50+ individual reports of errors of the same type
                        if (count >= 50) {
                            if (count == 50) {
                                append("<br/><b>NOTE: "
                                        + Integer.toString(warnings.size() - count)
                                        + " results omittted.</b><br/><br/>");
                            }
                            count++;
                            continue;
                        }
                        if (partialHide && count == SHOWN_COUNT) {
                            String id = warning.issue.getId() + "Div";

                            append("<button");
                            append(" class=\"mdl-button mdl-js-button mdl-button--primary\"");
                            append(" id=\"");
                            append(id);
                            append("Link\" onclick=\"reveal('");
                            append(id);
                            append("');\" />");
                            append(String.format("+ %1$d More Occurrences...",
                                    warnings.size() - SHOWN_COUNT));
                            append("</button>\n");
                            append("<div id=\"");
                            append(id);
                            append("\" style=\"display: none\">\n");
                        }
                        count++;
                        String url = null;
                        if (warning.path != null) {
                            url = writeLocation(warning.file, warning.path, warning.line);
                            append(':');
                            append(' ');
                        }

                        // Is the URL for a single image? If so, place it here near the top
                        // of the error floating on the right. If there are multiple images,
                        // they will instead be placed in a horizontal box below the error
                        boolean addedImage = false;
                        if (url != null && warning.location != null
                                && warning.location.getSecondary() == null) {
                            addedImage = addImage(url, warning.location);
                        }
                        append("<span class=\"message\">");
                        append(RAW.convertTo(warning.message, HTML));
                        append("</span>");
                        if (addedImage) {
                            append("<br clear=\"right\"/>");
                        } else {
                            append("<br />");
                        }

                        // Insert surrounding code block window
                        if (warning.line >= 0 && warning.fileContents != null) {
                            appendCodeBlock(warning.file, warning.fileContents,
                                    warning.offset, warning.endOffset, warning.severity);

                        }
                        append('\n');
                        if (warning.location != null && warning.location.getSecondary() != null) {
                            append("<ul>");
                            Location l = warning.location.getSecondary();
                            int otherLocations = 0;
                            int shownSnippetsCount = 0;
                            while (l != null) {
                                String message = l.getMessage();
                                if (message != null && !message.isEmpty()) {
                                    Position start = l.getStart();
                                    int line = start != null ? start.getLine() : -1;
                                    String path = client
                                            .getDisplayPath(warning.project, l.getFile());
                                    writeLocation(l.getFile(), path, line);
                                    append(':');
                                    append(' ');
                                    append("<span class=\"message\">");
                                    append(RAW.convertTo(message, HTML));
                                    append("</span>");
                                    append("<br />");

                                    String name = l.getFile().getName();
                                    // Only display up to 3 inlined views to keep big reports from
                                    // getting massive in rendering cost
                                    if (shownSnippetsCount < 3
                                            && !(endsWith(name, DOT_PNG) || endsWith(name,
                                            DOT_JPG))) {
                                        CharSequence s = client.readFile(l.getFile());
                                        if (s.length() > 0) {
                                            int offset = start != null ? start.getOffset() : -1;
                                            appendCodeBlock(l.getFile(), s, offset, -1,
                                                    warning.severity);
                                        }
                                        shownSnippetsCount++;
                                    }
                                } else {
                                    otherLocations++;
                                }

                                l = l.getSecondary();
                            }
                            append("</ul>");
                            if (otherLocations > 0) {
                                String id = "Location" + count + "Div";
                                append("<button id=\"");
                                append(id);
                                append("Link\" onclick=\"reveal('");
                                append(id);
                                append("');\" />");
                                append(String.format("+ %1$d Additional Locations...",
                                        otherLocations));
                                append("</button>\n");
                                append("<div id=\"");
                                append(id);
                                append("\" style=\"display: none\">\n");

                                append("Additional locations: ");
                                append("<ul>\n");
                                l = warning.location.getSecondary();
                                while (l != null) {
                                    Position start = l.getStart();
                                    int line = start != null ? start.getLine() : -1;
                                    String path = client
                                            .getDisplayPath(warning.project, l.getFile());
                                    append("<li> ");
                                    writeLocation(l.getFile(), path, line);
                                    append("\n");
                                    l = l.getSecondary();
                                }
                                append("</ul>\n");

                                append("</div><br/><br/>\n");
                            }
                        }

                        // Place a block of images?
                        if (!addedImage && url != null && warning.location != null
                                && warning.location.getSecondary() != null) {
                            addImage(url, warning.location);
                        }

                        if (warning.isVariantSpecific()) {
                            append("\n");
                            append("Applies to variants: ");
                            append(Joiner.on(", ").join(warning.getIncludedVariantNames()));
                            append("<br/>\n");
                            append("Does <b>not</b> apply to variants: ");
                            append(Joiner.on(", ").join(warning.getExcludedVariantNames()));
                            append("<br/>\n");
                        }
                    }
                    if (partialHide) { // Close up the extra div
                        append("</div>\n"); // partial hide
                    }

                    append("</div>\n"); // class=warningslist

                    writeIssueMetadata(issue, first.severity, null, true);

                    append("</div>\n"); // class=issue

                    append("<div class=\"chips\">\n");
                    writeChip(issue.getId());
                    Category category = issue.getCategory();
                    while (category != null && category != Category.LINT) {
                        writeChip(category.getName());
                        category = category.getParent();

                    }
                    writeChip(first.severity.getDescription());
                    writeChip("Priority " + issue.getPriority() + "/10");
                    append("</div>\n"); //class=chips

                }, XmlUtils.toXmlTextValue(firstIssue.getBriefDescription(TextFormat.TEXT)), true,
                new Action("Explain", getExplanationId(firstIssue),
                        "reveal")); // HTML style isn't handled right by card widget
    }

    /**
     * Sorts the list of warnings into a list of lists where each list contains warnings
     * for the same base issue type
     */
    @NonNull
    private static List<List<Warning>> computeIssueLists(@NonNull List<Warning> issues) {
        Issue previousIssue = null;
        List<List<Warning>> related = new ArrayList<>();
        if (!issues.isEmpty()) {
            List<Warning> currentList = null;
            for (Warning warning : issues) {
                if (warning.issue != previousIssue) {
                    previousIssue = warning.issue;
                    currentList = new ArrayList<>();
                    related.add(currentList);
                }
                assert currentList != null;
                currentList.add(warning);
            }
        }
        return related;
    }

    private void writeNewHtmlFormatInfoCard() {
        writeCard(() -> {
            append("This is a new version of Android Lint's HTML report. ");
            append("You can temporarily switch back to the old format by setting "
                    + "the following property in your <code>gradle.properties</code> file:");
            append("<pre>\n");
            append("    " + NEW_FORMAT_PROPERTY + "=true\n");
            append("</pre>\n");

            if (USE_DARCULA_FOR_CODE_SNIPPETS) {
                append("<br>\n"
                        + "You can also switch to light colors in the syntax highlighted snippets "
                        + "by setting the flag<br/><pre>\n    " + LIGHT_COLOR_PROPERTY
                        + "=true</pre>");
            } else {
                append(""
                        + "You can also switch to dark colors in the syntax highlighted snippets "
                        + "by setting the flag<br/><pre>\n    " + LIGHT_COLOR_PROPERTY
                        + "=false</pre>");
            }
            append(""
                    + "Please also file a tools bug on <a href=\"http://b.android.com\">http://b.android.com</a> "
                    + "explaining what is wrong with the new format or what it is missing.\n");
        }, "New Lint Format", true);
    }

    private void startReport(@NonNull Stats stats) {
        sb = new StringBuilder(1800 * stats.count());
        builder = new HtmlBuilder(sb);

        writeOpenHtmlTag();
        writeHeadTag();
        writeOpenBodyTag();
    }

    private void finishReport() {
        writeCloseNavigationHeader();
        writeCloseBodyTag();
        writeCloseHtmlTag();
    }

    private void writeNavigationHeader(@NonNull Stats stats, @NonNull Runnable appender) {
        append(""
                + "<div class=\"mdl-layout mdl-js-layout mdl-layout--fixed-header\">\n"
                + "  <header class=\"mdl-layout__header\">\n"
                + "    <div class=\"mdl-layout__header-row\">\n"
                + "      <span class=\"mdl-layout-title\">" + title + ": "
                + LintUtils.describeCounts(stats.errorCount, stats.warningCount, false)
                + "</span>\n"
                + "      <div class=\"mdl-layout-spacer\"></div>\n"
                + "      <nav class=\"mdl-navigation mdl-layout--large-screen-only\">\n");

        append(String.format("Check performed at %1$s", new Date().toString()));

        append(""
                + "      </nav>\n"
                + "    </div>\n"
                + "  </header>\n"
                + "  <div class=\"mdl-layout__drawer\">\n"
                + "    <span class=\"mdl-layout-title\">Issue Types</span>\n"
                + "    <nav class=\"mdl-navigation\">\n");

        appender.run();

        append(""
                + "    </nav>\n"
                + "  </div>\n"
                + "  <main class=\"mdl-layout__content\">\n"
                + "    <div class=\"mdl-layout__tab-panel is-active\">");
    }

    private void writeCloseNavigationHeader() {
        append(""
                + "    </div>\n"
                + "  </main>\n"
                + "</div>");
    }

    private void writeOpenBodyTag() {
        append("" +
                "<body class=\"mdl-color--grey-100 mdl-color-text--grey-700 mdl-base\">\n");
    }

    private void writeCloseBodyTag() {
        append("\n</body>\n");
    }


    private void writeOpenHtmlTag() {
        append(""
                        + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
    }

    private void writeCloseHtmlTag() {
        append("</html>");
    }

    private void writeHeadTag() {
        append(""
                + "\n"
                + "<head>\n"
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                + "<title>" + title + "</title>\n");

        // Material
        append(""
                + "<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/icon?family=Material+Icons\">\n"
                // Based on https://getmdl.io/customize/index.html
                //+ "<link rel=\"stylesheet\" href=\"https://code.getmdl.io/1.2.0/material.indigo-pink.min.css\">\n"
                //+ " <link rel="stylesheet" href="https://code.getmdl.io/1.2.1/material.grey-light_blue.min.css" /> \n"
                + " <link rel=\"stylesheet\" href=\"https://code.getmdl.io/1.2.1/material.blue-indigo.min.css\" />\n"
                + "<link rel=\"stylesheet\" href=\"http://fonts.googleapis.com/css?family=Roboto:300,400,500,700\" type=\"text/css\">\n"
                + "<script defer src=\"https://code.getmdl.io/1.2.0/material.min.js\"></script>\n");
        append("<style>\n"
                + CSS_STYLES
                + "</style>\n");

        // JavaScript for collapsing/expanding long lists
        append(""
                + "<script language=\"javascript\" type=\"text/javascript\"> \n"
                + "<!--\n"
                + "function reveal(id) {\n"
                + "if (document.getElementById) {\n"
                + "document.getElementById(id).style.display = 'block';\n"
                + "document.getElementById(id+'Link').style.display = 'none';\n"
                + "}\n"
                + "}\n"
                + "function hideid(id) {\n"
                + "if (document.getElementById) {\n"
                + "document.getElementById(id).style.display = 'none';\n"
                //+ "document.getElementById(id).hidden=true;\n" +
                + "}\n"
                + "}\n"
                + "//--> \n"
                + "</script>\n");

        append("</head>\n");
    }

    private void writeIssueMetadata(Issue issue, Severity severity, String disabledBy,
            boolean hide) {
        append("<div class=\"metadata\">");

        if (disabledBy != null) {
            append(String.format("Disabled By: %1$s<br/>\n", disabledBy));
        }

        append("<div class=\"explanation\"");
        if (hide) {
            append(" id=\"" + getExplanationId(issue) + "\" style=\"display: none;\"");
        }
        append(">\n");
        String explanationHtml = issue.getExplanation(HTML);
        append(explanationHtml);
        List<String> moreInfo = issue.getMoreInfo();
        append("<br/>");

        // TODO: Skip MoreInfo links already present in the HTML to avoid redundancy.

        int count = moreInfo.size();
        if (count > 0) {
            append("<div class=\"moreinfo\">");
            append("More info: ");
            if (count > 1) {
                append("<ul>");
            }
            for (String uri : moreInfo) {
                if (count > 1) {
                    append("<li>");
                }
                append("<a href=\"");
                append(uri);
                append("\">");
                append(uri);
                append("</a>\n");
            }
            if (count > 1) {
                append("</ul>");
            }
            append("</div>");
        }

        append("<br/>");

        if (client.getRegistry() instanceof BuiltinIssueRegistry) {
            if (Reporter.hasAutoFix(issue)) {
                append(
                        "Note: This issue has an associated quickfix operation in Android Studio and IntelliJ IDEA.");
                append("<br>\n");
            }
        }

        append(String.format(
                "To suppress this error, use the issue id \"%1$s\" as explained in the " +
                "%2$sSuppressing Warnings and Errors%3$s section.",
                issue.getId(),
                "<a href=\"#SuppressInfo\">", "</a>"));
        append("<br/>\n");
        append("</div>"); //class=moreinfo
        append("\n</div>\n"); //class=explanation
    }

    protected Map<Issue, String> computeMissingIssues(List<Warning> warnings) {
        Set<Project> projects = new HashSet<>();
        Set<Issue> seen = new HashSet<>();
        for (Warning warning : warnings) {
            projects.add(warning.project);
            seen.add(warning.issue);
        }
        Configuration cliConfiguration = client.getConfiguration();
        Map<Issue, String> map = Maps.newHashMap();
        for (Issue issue : client.getRegistry().getIssues()) {
            if (!seen.contains(issue)) {
                if (client.isSuppressed(issue)) {
                    map.put(issue, "Command line flag");
                    continue;
                }

                if (!issue.isEnabledByDefault() && !client.isAllEnabled()) {
                    map.put(issue, "Default");
                    continue;
                }

                if (cliConfiguration != null && !cliConfiguration.isEnabled(issue)) {
                    map.put(issue, "Command line supplied --config lint.xml file");
                    continue;
                }

                // See if any projects disable this warning
                for (Project project : projects) {
                    if (!project.getConfiguration(null).isEnabled(issue)) {
                        map.put(issue, "Project lint.xml file");
                        break;
                    }
                }
            }
        }

        return map;
    }

    private void writeMissingIssues(@NonNull Map<Issue, String> missing) {
        if (!client.isCheckingSpecificIssues()) {
            append("\n<a name=\"MissingIssues\"></a>\n");

            writeCard(() -> {
                append(""
                    + "One or more issues were not run by lint, either \n"
                    + "because the check is not enabled by default, or because \n"
                    + "it was disabled with a command line flag or via one or \n"
                    + "more <code>lint.xml</code> configuration files in the project "
                        + "directories.\n");

                append("<div id=\"SuppressedIssues\" style=\"display: none;\">");
                List<Issue> list = new ArrayList<>(missing.keySet());
                Collections.sort(list);
                append("<br/><br/>");

                for (Issue issue : list) {
                    append("<div class=\"issue\">\n");

                    // Explain this issue
                    append("<div class=\"id\">");
                    append(issue.getId());
                    append("<div class=\"issueSeparator\"></div>\n");
                    append("</div>\n");
                    String disabledBy = missing.get(issue);
                    writeIssueMetadata(issue, issue.getDefaultSeverity(), disabledBy, false);
                    append("</div>\n");

                }

                append("</div>"); //SuppressedIssues
            }, "Disabled Checks", true,
                    new Action("List Missing Issues", "SuppressedIssues", "reveal"));
        }
    }

    private void writeOverview(List<List<Warning>> related, int missingCount) {
        // Write issue id summary
        append("<table class=\"overview\">\n");

        Category previousCategory = null;
        for (List<Warning> warnings : related) {
            Issue issue = warnings.get(0).issue;

            boolean isError = false;
            for (Warning warning : warnings) {
                if (warning.severity.isError()) {
                    isError = true;
                    break;
                }
            }

            if (issue.getCategory() != previousCategory) {
                append("<tr><td class=\"countColumn\"></td><td class=\"categoryColumn\">");
                previousCategory = issue.getCategory();
                String categoryName = issue.getCategory().getFullName();
                append("<a href=\"#");
                append(categoryName);
                append("\">");
                append(categoryName);
                append("</a>\n");
                append("</td></tr>");
                append("\n");
            }
            append("<tr>\n");

            // Count column
            append("<td class=\"countColumn\">");
            append(Integer.toString(warnings.size()));
            append("</td>");

            append("<td class=\"issueColumn\">");

            if (isError) {
                append("<i class=\"material-icons error-icon\">error</i>");
            } else {
                append("<i class=\"material-icons warning-icon\">warning</i>");
            }
            append('\n');

            append("<a href=\"#");
            append(issue.getId());
            append("\">");
            append(issue.getId());
            append("</a>");
            append(": ");
            append(issue.getBriefDescription(HTML));

            append("</td></tr>\n");
        }

        if (missingCount > 0 && !client.isCheckingSpecificIssues()) {
            append("<tr><td></td>");
            append("<td class=\"categoryColumn\">");
            append("<a href=\"#MissingIssues\">");
            append(String.format("Disabled Checks (%1$d)",
                    missingCount));

            append("</a>\n");
            append("</td></tr>");
        }

        append("</table>\n");
        append("<br/>");
    }

    private static String getCardId(int cardNumber) {
        return "card" + cardNumber;
    }

    private static String getExplanationId(Issue issue) {
        return "explanation" + issue.getId();
    }

    public void writeCardHeader(String title, int cardNumber) {
        append(""
                + "<section class=\"section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp\" id=\""
                + getCardId(cardNumber) + "\" style=\"display: block;\">\n"
                + "            <div class=\"mdl-card mdl-cell mdl-cell--12-col\">\n");
        if (title != null) {
            append(""
                    + "  <div class=\"mdl-card__title\">\n"
                    + "    <h2 class=\"mdl-card__title-text\">" + title + "</h2>\n"
                    + "  </div>\n");
        }

        append(""
                + "              <div class=\"mdl-card__supporting-text\">\n");
    }

    private static class Action {
        public String title;
        public String id;
        public String function;

        public Action(String title, String id, String function) {
            this.title = title;
            this.id = id;
            this.function = function;
        }
    }

    public void writeCardAction(@NonNull Action... actions) {
        append(""
                + "              </div>\n"
                + "              <div class=\"mdl-card__actions mdl-card--border\">\n");
        for (Action action : actions) {
            append(""
                    + "<button class=\"mdl-button mdl-js-button mdl-js-ripple-effect\""
                    + " id=\"" + action.id + "Link\""
                    + " onclick=\"" + action.function + "('" + action.id + "');" + "\">\n"
                    + action.title
                    + "</button>");
        }
    }

    public void writeCardFooter() {
        append(""
                + "            </div>\n"
                + "            </div>\n"
                + "          </section>");
    }

    public void writeCard(@NonNull Runnable appender, @Nullable String title) {
        writeCard(appender, title, false);
    }

    public void writeChip(@NonNull String text) {
        append(""
                + "<span class=\"mdl-chip\">\n"
                + "    <span class=\"mdl-chip__text\">" + text + "</span>\n"
                + "</span>\n");
    }

    int cardNumber = 0;

    public void writeCard(@NonNull Runnable appender, @Nullable String title, boolean dismissible,
            Action... actions) {
        int card = cardNumber++;
        writeCardHeader(title, card);
        appender.run();
        if (dismissible) {
            String dismissTitle = "Dismiss";
            if ("New Lint Report Format".equals(title)) {
                dismissTitle = "Got It";
            }
            actions = ObjectArrays.concat(actions, new Action(dismissTitle, getCardId(card),
                    "hideid"));
            writeCardAction(actions);
        }
        writeCardFooter();
    }

    private String writeLocation(File file, String path, int line) {
        String url;
        append("<span class=\"location\">");

        url = getUrl(file);
        if (url != null) {
            append("<a href=\"");
            append(url);
            append("\">");
        }

        String displayPath = stripPath(path);
        if (url != null && url.startsWith("../") && new File(displayPath).isAbsolute()) {
            displayPath = url;
        }
        append(displayPath);
        //noinspection VariableNotUsedInsideIf
        if (url != null) {
            append("</a>");
        }
        if (line >= 0) {
            // 0-based line numbers, but display 1-based
            append(':');
            append(Integer.toString(line + 1));
        }
        append("</span>");
        return url;
    }

    private boolean addImage(String url, Location location) {
        if (url != null && endsWith(url, DOT_PNG)) {
            if (location.getSecondary() != null) {
                // Emit many images
                // Add in linked images as well
                List<String> urls = new ArrayList<>();
                while (location != null) {
                    String imageUrl = getUrl(location.getFile());
                    if (imageUrl != null
                            && endsWith(imageUrl, DOT_PNG)) {
                        urls.add(imageUrl);
                    }
                    location = location.getSecondary();
                }
                if (!urls.isEmpty()) {
                    // Sort in order
                    urls.sort(Comparator.comparingInt(HtmlReporter::getDpiRank));
                    append("<table>");
                    append("<tr>");
                    for (String linkedUrl : urls) {
                        // Image series: align top
                        append("<td>");
                        append("<a href=\"");
                        append(linkedUrl);
                        append("\">");
                        append("<img border=\"0\" align=\"top\" src=\"");
                        append(linkedUrl);
                        append("\" /></a>\n");
                        append("</td>");
                    }
                    append("</tr>");

                    append("<tr>");
                    for (String linkedUrl : urls) {
                        append("<th>");
                        int index = linkedUrl.lastIndexOf("drawable-");
                        if (index != -1) {
                            index += "drawable-".length();
                            int end = linkedUrl.indexOf('/', index);
                            if (end != -1) {
                                append(linkedUrl.substring(index, end));
                            }
                        }
                        append("</th>");
                    }
                    append("</tr>\n");

                    append("</table>\n");
                }
            } else {
                // Just this image: float to the right
                append("<img class=\"embedimage\" align=\"right\" src=\"");
                append(url);
                append("\" />");
            }

            return true;
        }

        return false;
    }

    @Override
    public void writeProjectList(@NonNull Stats stats,
            @NonNull List<MultiProjectHtmlReporter.ProjectEntry> projects) throws IOException {
        startReport(stats);

        writeNavigationHeader(stats, () -> {
            for (MultiProjectHtmlReporter.ProjectEntry entry : projects) {
                append("      <a class=\"mdl-navigation__link\" href=\""
                        + XmlUtils.toXmlAttributeValue(entry.fileName) + "\">"
                        + entry.path + " (" + (entry.errorCount + entry.warningCount) + ")</a>\n");
            }
        });

        if (stats.errorCount == 0 && stats.warningCount == 0) {
            writeCard(() -> append("Congratulations!"), "No Issues Found");
            return;
        }

        writeCard(() -> {
            // Write issue id summary
            append("<table class=\"overview\">\n");

            append("<tr><th>");
            append("Project");
            append("</th><th class=\"countColumn\">");

            append("Errors");
            append("</th><th class=\"countColumn\">");

            append("Warnings");
            append("</th></tr>\n");
            for (MultiProjectHtmlReporter.ProjectEntry entry : projects) {

                append("<tr><td>");
                append("<a href=\"");
                append(XmlUtils.toXmlAttributeValue(entry.fileName));
                append("\">");
                append(entry.path);
                append("</a></td><td class=\"countColumn\">");
                append(Integer.toString(entry.errorCount));
                append("</td><td class=\"countColumn\">");
                append(Integer.toString(entry.warningCount));
                append("</td></tr>\n");

                append("<tr>\n");
            }

            append("</table>\n");
            append("<br/>");
        }, "Projects");

        finishReport();
        writeReport();
    }

    private void writeReport() throws IOException {
        writer.write(sb.toString());
        writer.close();
        sb = null;
        builder = null;
    }

    @NonNull
    private LintSyntaxHighlighter getHighlighter(@NonNull File file,
            @NonNull CharSequence contents) {
        if (highlightedFile == null || !highlightedFile.equals(file.getPath())) {
            highlighter = new LintSyntaxHighlighter(file.getName(), contents.toString());
            highlighter.setPadCaretLine(true);
            highlightedFile = file.getPath();
        }

        return highlighter;
    }

    /** Insert syntax highlighted XML */
    private void appendCodeBlock(@NonNull File file, @NonNull CharSequence contents,
            int startOffset, int endOffset, @NonNull Severity severity) {
        getHighlighter(file, contents).generateHtml(builder, startOffset, endOffset,
                severity.isError());
    }
}
