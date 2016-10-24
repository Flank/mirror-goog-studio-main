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

import static com.android.SdkConstants.DOT_JPG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.tools.lint.detector.api.LintUtils.describeCounts;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;
import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.SdkUtils;
import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reporter which emits lint results into an HTML report.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class HtmlReporter extends Reporter {
    public static final boolean INLINE_RESOURCES =
            !VALUE_FALSE.equals(System.getProperty("lint.inline-resources"));
    private static final boolean USE_HOLO_STYLE = true;
    @SuppressWarnings("ConstantConditions")
    private static final String CSS = USE_HOLO_STYLE
            ? "hololike.css" : "default.css";

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

    protected final Writer mWriter;
    protected final LintCliFlags mFlags;
    private String mStripPrefix;
    private String mFixUrl;

    /**
     * Creates a new {@link HtmlReporter}
     *
     * @param client the associated client
     * @param output the output file
     * @param flags the command line flags
     * @throws IOException if an error occurs
     */
    public HtmlReporter(
            @NonNull LintCliClient client,
            @NonNull File output,
            @NonNull LintCliFlags flags) throws IOException {
        super(client, output);
        mWriter = new BufferedWriter(Files.newWriter(output, Charsets.UTF_8));
        mFlags = flags;
    }

    @Override
    public void write(@NonNull Stats stats, List<Warning> issues) throws IOException {
        Map<Issue, String> missing = computeMissingIssues(issues);

        mWriter.write(
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />" +
                "<title>" + mTitle + "</title>\n");

        writeStyleSheet();

        if (!mSimpleFormat) {
            // JavaScript for collapsing/expanding long lists
            mWriter.write(
                "<script language=\"javascript\" type=\"text/javascript\"> \n" +
                "<!--\n" +
                "function reveal(id) {\n" +
                "if (document.getElementById) {\n" +
                "document.getElementById(id).style.display = 'block';\n" +
                "document.getElementById(id+'Link').style.display = 'none';\n" +
                "}\n" +
                "}\n" +
                "//--> \n" +
                "</script>\n");
        }

        mWriter.write(
                "</head>\n" +
                "<body>\n" +
                "<h1>" +
                mTitle +
                "</h1>\n" +
                "<div class=\"titleSeparator\"></div>\n");

        mWriter.write(String.format("Check performed at %1$s.",
                new Date().toString()));
        mWriter.write("<br/>\n");
        mWriter.write(String.format("%1$s found",
                describeCounts(stats.errorCount, stats.warningCount, false)));
        if (stats.baselineErrorCount > 0 || stats.baselineWarningCount > 0) {
            File baselineFile = mFlags.getBaselineFile();
            assert baselineFile != null;
            mWriter.write(String.format(" (%1$s filtered by baseline %2$s)",
                    describeCounts(stats.baselineErrorCount, stats.baselineWarningCount, false),
                    baselineFile.getName()));
        }
        mWriter.write(":");
        mWriter.write("<br/><br/>\n");

        Issue previousIssue = null;
        if (!issues.isEmpty()) {
            List<List<Warning>> related = new ArrayList<>();
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

            writeOverview(related, missing.size());

            Category previousCategory = null;
            for (List<Warning> warnings : related) {
                Warning first = warnings.get(0);
                Issue issue = first.issue;

                if (issue.getCategory() != previousCategory) {
                    previousCategory = issue.getCategory();
                    mWriter.write("\n<a name=\"");
                    mWriter.write(issue.getCategory().getFullName());
                    mWriter.write("\"></a>\n");
                    mWriter.write("<div class=\"category\"><a href=\"#\" title=\"Return to top\">");
                    mWriter.write(issue.getCategory().getFullName());
                    mWriter.write("</a><div class=\"categorySeparator\"></div>\n");
                    mWriter.write("</div>\n");
                }

                mWriter.write("<a name=\"" + issue.getId() + "\"></a>\n");
                mWriter.write("<div class=\"issue\">\n");

                // Explain this issue
                mWriter.write("<div class=\"id\"><a href=\"#\" title=\"Return to top\">");
                mWriter.write(issue.getId());
                mWriter.write(": ");
                mWriter.write(issue.getBriefDescription(HTML));
                mWriter.write("</a><div class=\"issueSeparator\"></div>\n");
                mWriter.write("</div>\n");

                mWriter.write("<div class=\"warningslist\">\n");
                boolean partialHide = !mSimpleFormat && warnings.size() > SPLIT_LIMIT;

                int count = 0;
                for (Warning warning : warnings) {
                    if (partialHide && count == SHOWN_COUNT) {
                        String id = warning.issue.getId() + "Div";
                        mWriter.write("<button id=\"");
                        mWriter.write(id);
                        mWriter.write("Link\" onclick=\"reveal('");
                        mWriter.write(id);
                        mWriter.write("');\" />");
                        mWriter.write(String.format("+ %1$d More Occurrences...",
                                warnings.size() - SHOWN_COUNT));
                        mWriter.write("</button>\n");
                        mWriter.write("<div id=\"");
                        mWriter.write(id);
                        mWriter.write("\" style=\"display: none\">\n");
                    }
                    count++;
                    String url = null;
                    if (warning.path != null) {
                        url = writeLocation(warning.file, warning.path, warning.line);
                        mWriter.write(':');
                        mWriter.write(' ');
                    }

                    // Is the URL for a single image? If so, place it here near the top
                    // of the error floating on the right. If there are multiple images,
                    // they will instead be placed in a horizontal box below the error
                    boolean addedImage = false;
                    if (url != null && warning.location != null
                            && warning.location.getSecondary() == null) {
                        addedImage = addImage(url, warning.location);
                    }
                    mWriter.write("<span class=\"message\">");
                    mWriter.append(RAW.convertTo(warning.message, HTML));
                    mWriter.write("</span>");
                    if (addedImage) {
                        mWriter.write("<br clear=\"right\"/>");
                    } else {
                        mWriter.write("<br />");
                    }

                    // Insert surrounding code block window
                    if (warning.line >= 0 && warning.fileContents != null) {
                        mWriter.write("<pre class=\"errorlines\">\n");
                        appendCodeBlock(warning.fileContents, warning.line, warning.offset);
                        mWriter.write("\n</pre>");
                    }
                    mWriter.write('\n');
                    if (warning.location != null && warning.location.getSecondary() != null) {
                        mWriter.write("<ul>");
                        Location l = warning.location.getSecondary();
                        int otherLocations = 0;
                        while (l != null) {
                            String message = l.getMessage();
                            if (message != null && !message.isEmpty()) {
                                Position start = l.getStart();
                                int line = start != null ? start.getLine() : -1;
                                String path = mClient.getDisplayPath(warning.project, l.getFile());
                                writeLocation(l.getFile(), path, line);
                                mWriter.write(':');
                                mWriter.write(' ');
                                mWriter.write("<span class=\"message\">");
                                mWriter.append(RAW.convertTo(message, HTML));
                                mWriter.write("</span>");
                                mWriter.write("<br />");

                                String name = l.getFile().getName();
                                if (!(endsWith(name, DOT_PNG) || endsWith(name, DOT_JPG))) {
                                    CharSequence s = mClient.readFile(l.getFile());
                                    if (s.length() > 0) {
                                        mWriter.write("<pre class=\"errorlines\">\n");
                                        int offset = start != null ? start.getOffset() : -1;
                                        appendCodeBlock(s, line, offset);
                                        mWriter.write("\n</pre>");
                                    }
                                }
                            } else {
                                otherLocations++;
                            }

                            l = l.getSecondary();
                        }
                        mWriter.write("</ul>");
                        if (otherLocations > 0) {
                            String id = "Location" + count + "Div";
                            mWriter.write("<button id=\"");
                            mWriter.write(id);
                            mWriter.write("Link\" onclick=\"reveal('");
                            mWriter.write(id);
                            mWriter.write("');\" />");
                            mWriter.write(String.format("+ %1$d Additional Locations...",
                                    otherLocations));
                            mWriter.write("</button>\n");
                            mWriter.write("<div id=\"");
                            mWriter.write(id);
                            mWriter.write("\" style=\"display: none\">\n");

                            mWriter.write("Additional locations: ");
                            mWriter.write("<ul>\n");
                            l = warning.location.getSecondary();
                            while (l != null) {
                                Position start = l.getStart();
                                int line = start != null ? start.getLine() : -1;
                                String path = mClient.getDisplayPath(warning.project, l.getFile());
                                mWriter.write("<li> ");
                                writeLocation(l.getFile(), path, line);
                                mWriter.write("\n");
                                l = l.getSecondary();
                            }
                            mWriter.write("</ul>\n");

                            mWriter.write("</div><br/><br/>\n");
                        }
                    }

                    // Place a block of images?
                    if (!addedImage && url != null && warning.location != null
                            && warning.location.getSecondary() != null) {
                        addImage(url, warning.location);
                    }

                    if (warning.isVariantSpecific()) {
                        mWriter.write("\n");
                        mWriter.write("Applies to variants: ");
                        mWriter.write(Joiner.on(", ").join(warning.getIncludedVariantNames()));
                        mWriter.write("<br/>\n");
                        mWriter.write("Does <b>not</b> apply to variants: ");
                        mWriter.write(Joiner.on(", ").join(warning.getExcludedVariantNames()));
                        mWriter.write("<br/>\n");
                    }
                }
                if (partialHide) { // Close up the extra div
                    mWriter.write("</div>\n");
                }

                mWriter.write("</div>\n");
                writeIssueMetadata(issue, first.severity, null);

                mWriter.write("</div>\n");
            }

            if (!mClient.isCheckingSpecificIssues()) {
                writeMissingIssues(missing);
            }

            writeSuppressInfo();
        } else {
            mWriter.write("Congratulations!");
        }
        mWriter.write("\n</body>\n</html>");
        mWriter.close();

        if (!mClient.getFlags().isQuiet()
                && (stats.errorCount > 0 || stats.warningCount > 0)) {
            String url = SdkUtils.fileToUrlString(mOutput.getAbsoluteFile());
            System.out.println(String.format("Wrote HTML report to %1$s", url));
        }
    }

    private void writeIssueMetadata(Issue issue, Severity severity, String disabledBy)
            throws IOException {
        mWriter.write("<div class=\"metadata\">");

        if (mClient.getRegistry() instanceof BuiltinIssueRegistry) {
            boolean adtHasFix = QuickfixHandler.ADT.hasAutoFix(issue);
            boolean studioHasFix = QuickfixHandler.STUDIO.hasAutoFix(issue);
            if (adtHasFix || studioHasFix) {
                String adt = "Eclipse/ADT";
                String studio = "Android Studio/IntelliJ";
                String tools = adtHasFix && studioHasFix
                        ? (studio + " & " + adt) : studioHasFix ? studio : adt;
                mWriter.write("Note: This issue has an associated quickfix operation in " + tools);
                if (!INLINE_RESOURCES) {
                    mWriter.write(getFixIcon());
                } else if (mFixUrl != null) {
                    mWriter.write("&nbsp;<img alt=\"Fix\" border=\"0\" align=\"top\" src=\"");
                    mWriter.write(mFixUrl);
                    mWriter.write("\" />\n");
                }

                mWriter.write("<br>\n");
            }
        }

        if (disabledBy != null) {
            mWriter.write(String.format("Disabled By: %1$s<br/>\n", disabledBy));
        }

        mWriter.write("Priority: ");
        mWriter.write(String.format("%1$d / 10", issue.getPriority()));
        mWriter.write("<br/>\n");
        mWriter.write("Category: ");
        mWriter.write(issue.getCategory().getFullName());
        mWriter.write("</div>\n");

        mWriter.write("Severity: ");
        if (severity == Severity.ERROR || severity == Severity.FATAL) {
            mWriter.write("<span class=\"error\">");
        } else if (severity == Severity.WARNING) {
            mWriter.write("<span class=\"warning\">");
        } else {
            mWriter.write("<span>");
        }
        appendEscapedText(severity.getDescription());
        mWriter.write("</span>");

        mWriter.write("<div class=\"summary\">\n");
        mWriter.write("Explanation: ");
        String description = issue.getBriefDescription(HTML);
        mWriter.write(description);
        if (!description.isEmpty()
                && Character.isLetter(description.charAt(description.length() - 1))) {
            mWriter.write('.');
        }
        mWriter.write("</div>\n");
        mWriter.write("<div class=\"explanation\">\n");
        String explanationHtml = issue.getExplanation(HTML);
        mWriter.write(explanationHtml);
        mWriter.write("\n</div>\n");
        List<String> moreInfo = issue.getMoreInfo();
        mWriter.write("<br/>");
        mWriter.write("<div class=\"moreinfo\">");
        mWriter.write("More info: ");
        int count = moreInfo.size();
        if (count > 1) {
            mWriter.write("<ul>");
        }
        for (String uri : moreInfo) {
            if (count > 1) {
                mWriter.write("<li>");
            }
            mWriter.write("<a href=\"");
            mWriter.write(uri);
            mWriter.write("\">"    );
            mWriter.write(uri);
            mWriter.write("</a>\n");
        }
        if (count > 1) {
            mWriter.write("</ul>");
        }
        mWriter.write("</div>");

        mWriter.write("<br/>");
        mWriter.write(String.format(
                "To suppress this error, use the issue id \"%1$s\" as explained in the " +
                "%2$sSuppressing Warnings and Errors%3$s section.",
                issue.getId(),
                "<a href=\"#SuppressInfo\">", "</a>"));
        mWriter.write("<br/>\n");
    }

    private void writeSuppressInfo() throws IOException {
        //getSuppressHelp
        mWriter.write("\n<a name=\"SuppressInfo\"></a>\n");
        mWriter.write("<div class=\"category\">");
        mWriter.write("Suppressing Warnings and Errors");
        mWriter.write("<div class=\"categorySeparator\"></div>\n");
        mWriter.write("</div>\n");
        mWriter.write(TextFormat.RAW.convertTo(Main.getSuppressHelp(), TextFormat.HTML));
        mWriter.write('\n');
    }

    protected Map<Issue, String> computeMissingIssues(List<Warning> warnings) {
        Set<Project> projects = new HashSet<>();
        Set<Issue> seen = new HashSet<>();
        for (Warning warning : warnings) {
            projects.add(warning.project);
            seen.add(warning.issue);
        }
        Configuration cliConfiguration = mClient.getConfiguration();
        Map<Issue, String> map = Maps.newHashMap();
        for (Issue issue : mClient.getRegistry().getIssues()) {
            if (!seen.contains(issue)) {
                if (mClient.isSuppressed(issue)) {
                    map.put(issue, "Command line flag");
                    continue;
                }

                if (!issue.isEnabledByDefault() && !mClient.isAllEnabled()) {
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

    private void writeMissingIssues(Map<Issue, String> missing) throws IOException {
        mWriter.write("\n<a name=\"MissingIssues\"></a>\n");
        mWriter.write("<div class=\"category\">");
        mWriter.write("Disabled Checks");
        mWriter.write("<div class=\"categorySeparator\"></div>\n");
        mWriter.write("</div>\n");

        mWriter.write(
                "The following issues were not run by lint, either " +
                "because the check is not enabled by default, or because " +
                "it was disabled with a command line flag or via one or " +
                "more lint.xml configuration files in the project directories.");
        mWriter.write("\n<br/><br/>\n");

        List<Issue> list = new ArrayList<>(missing.keySet());
        Collections.sort(list);


        for (Issue issue : list) {
            mWriter.write("<a name=\"" + issue.getId() + "\"></a>\n");
            mWriter.write("<div class=\"issue\">\n");

            // Explain this issue
            mWriter.write("<div class=\"id\">");
            mWriter.write(issue.getId());
            mWriter.write("<div class=\"issueSeparator\"></div>\n");
            mWriter.write("</div>\n");
            String disabledBy = missing.get(issue);
            writeIssueMetadata(issue, issue.getDefaultSeverity(), disabledBy);
            mWriter.write("</div>\n");
        }
    }

    protected void writeStyleSheet() throws IOException {
        if (USE_HOLO_STYLE) {
            mWriter.write(
                "<link rel=\"stylesheet\" type=\"text/css\" " +
                "href=\"http://fonts.googleapis.com/css?family=Roboto\" />\n" );
        }

        URL cssUrl = HtmlReporter.class.getResource(CSS);
        if (mSimpleFormat || INLINE_RESOURCES) {
            // Inline the CSS
            mWriter.write("<style>\n");
            InputStream input = cssUrl.openStream();
            byte[] bytes = ByteStreams.toByteArray(input);
            try {
                Closeables.close(input, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
            String css = new String(bytes, Charsets.UTF_8);
            mWriter.write(css);
            mWriter.write("</style>\n");
        } else {
            String ref = addLocalResources(cssUrl);
            if (ref != null) {
                mWriter.write(
                "<link rel=\"stylesheet\" type=\"text/css\" href=\""
                            + ref + "\" />\n");
            }
        }
    }

    private void writeOverview(List<List<Warning>> related, int missingCount)
            throws IOException {
        // Write issue id summary
        mWriter.write("<table class=\"overview\">\n");

        String errorUrl = null;
        String warningUrl = null;
        if (!INLINE_RESOURCES && !mSimpleFormat) {
            errorUrl = addLocalResources(getErrorIconUrl());
            warningUrl = addLocalResources(getWarningIconUrl());
            mFixUrl = addLocalResources(HtmlReporter.class.getResource("lint-run.png"));
        }

        Category previousCategory = null;
        for (List<Warning> warnings : related) {
            Issue issue = warnings.get(0).issue;

            boolean isError = false;
            for (Warning warning : warnings) {
                if (warning.severity == Severity.ERROR || warning.severity == Severity.FATAL) {
                    isError = true;
                    break;
                }
            }

            if (issue.getCategory() != previousCategory) {
                mWriter.write("<tr><td></td><td class=\"categoryColumn\">");
                previousCategory = issue.getCategory();
                String categoryName = issue.getCategory().getFullName();
                mWriter.write("<a href=\"#");
                mWriter.write(categoryName);
                mWriter.write("\">");
                mWriter.write(categoryName);
                mWriter.write("</a>\n");
                mWriter.write("</td></tr>");
                mWriter.write("\n");
            }
            mWriter.write("<tr>\n");

            // Count column
            mWriter.write("<td class=\"countColumn\">");
            mWriter.write(Integer.toString(warnings.size()));
            mWriter.write("</td>");

            mWriter.write("<td class=\"issueColumn\">");

            if (INLINE_RESOURCES) {
                String markup = isError ? getErrorIcon() : getWarningIcon();
                mWriter.write(markup);
                mWriter.write('\n');
            } else {
                String imageUrl = isError ? errorUrl : warningUrl;
                if (imageUrl != null) {
                    mWriter.write("<img border=\"0\" align=\"top\" src=\"");
                    mWriter.write(imageUrl);
                    mWriter.write("\" alt=\"");
                    mWriter.write(isError ? "Error" : "Warning");
                    mWriter.write("\" />\n");
                }
            }

            mWriter.write("<a href=\"#");
            mWriter.write(issue.getId());
            mWriter.write("\">");
            mWriter.write(issue.getId());
            mWriter.write(": ");
            mWriter.write(issue.getBriefDescription(HTML));
            mWriter.write("</a>\n");

            mWriter.write("</td></tr>\n");
        }

        if (missingCount > 0 && !mClient.isCheckingSpecificIssues()) {
            mWriter.write("<tr><td></td>");
            mWriter.write("<td class=\"categoryColumn\">");
            mWriter.write("<a href=\"#MissingIssues\">");
            mWriter.write(String.format("Disabled Checks (%1$d)",
                    missingCount));

            mWriter.write("</a>\n");
            mWriter.write("</td></tr>");
        }

        mWriter.write("</table>\n");
        mWriter.write("<br/>");
    }

    private String writeLocation(File file, String path, int line) throws IOException {
        String url;
        mWriter.write("<span class=\"location\">");

        url = getUrl(file);
        if (url != null) {
            mWriter.write("<a href=\"");
            mWriter.write(url);
            mWriter.write("\">");
        }

        String displayPath = stripPath(path);
        if (url != null && url.startsWith("../") && new File(displayPath).isAbsolute()) {
            displayPath = url;
        }
        mWriter.write(displayPath);
        //noinspection VariableNotUsedInsideIf
        if (url != null) {
            mWriter.write("</a>");
        }
        if (line >= 0) {
            // 0-based line numbers, but display 1-based
            mWriter.write(':');
            mWriter.write(Integer.toString(line + 1));
        }
        mWriter.write("</span>");
        return url;
    }

    private boolean addImage(String url, Location location) throws IOException {
        if (url != null && endsWith(url, DOT_PNG)) {
            if (location.getSecondary() != null) {
                // Emit many images
                // Add in linked images as well
                List<String> urls = new ArrayList<>();
                while (location != null && location.getFile() != null) {
                    String imageUrl = getUrl(location.getFile());
                    if (imageUrl != null
                            && endsWith(imageUrl, DOT_PNG)) {
                        urls.add(imageUrl);
                    }
                    location = location.getSecondary();
                }
                if (!urls.isEmpty()) {
                    // Sort in order
                    Collections.sort(urls, (s1, s2) -> getDpiRank(s1) - getDpiRank(s2));
                    mWriter.write("<table>");
                    mWriter.write("<tr>");
                    for (String linkedUrl : urls) {
                        // Image series: align top
                        mWriter.write("<td>");
                        mWriter.write("<a href=\"");
                        mWriter.write(linkedUrl);
                        mWriter.write("\">");
                        mWriter.write("<img border=\"0\" align=\"top\" src=\"");
                        mWriter.write(linkedUrl);
                        mWriter.write("\" /></a>\n");
                        mWriter.write("</td>");
                    }
                    mWriter.write("</tr>");

                    mWriter.write("<tr>");
                    for (String linkedUrl : urls) {
                        mWriter.write("<th>");
                        int index = linkedUrl.lastIndexOf("drawable-");
                        if (index != -1) {
                            index += "drawable-".length();
                            int end = linkedUrl.indexOf('/', index);
                            if (end != -1) {
                                mWriter.write(linkedUrl.substring(index, end));
                            }
                        }
                        mWriter.write("</th>");
                    }
                    mWriter.write("</tr>\n");

                    mWriter.write("</table>\n");
                }
            } else {
                // Just this image: float to the right
                mWriter.write("<img class=\"embedimage\" align=\"right\" src=\"");
                mWriter.write(url);
                mWriter.write("\" />");
            }

            return true;
        }

        return false;
    }

    /** Provide a sorting rank for a url */
    private static int getDpiRank(String url) {
        if (url.contains("-xhdpi")) {
            return 0;
        } else if (url.contains("-hdpi")) {
            return 1;
        } else if (url.contains("-mdpi")) {
            return 2;
        } else if (url.contains("-ldpi")) {
            return 3;
        } else {
            return 4;
        }
    }

    private void appendCodeBlock(CharSequence contents, int lineno, int offset)
            throws IOException {
        int max = lineno + 3;
        int min = lineno - 3;
        for (int l = min; l < max; l++) {
            if (l >= 0) {
                int lineOffset = LintCliClient.getLineOffset(contents, l);
                if (lineOffset == -1) {
                    break;
                }

                mWriter.write(String.format("<span class=\"lineno\">%1$4d</span> ", (l + 1)));

                String line = LintCliClient.getLineOfOffset(contents, lineOffset);
                if (offset != -1 && lineOffset <= offset && lineOffset+line.length() >= offset) {
                    // Text nodes do not always have correct lines/offsets
                    //assert l == lineno;

                    // This line contains the beginning of the offset
                    // First print everything before
                    int delta = offset - lineOffset;
                    appendEscapedText(line.substring(0, delta));
                    mWriter.write("<span class=\"errorspan\">");
                    appendEscapedText(line.substring(delta));
                    mWriter.write("</span>");
                } else if (offset == -1 && l == lineno) {
                    mWriter.write("<span class=\"errorline\">");
                    appendEscapedText(line);
                    mWriter.write("</span>");
                } else {
                    appendEscapedText(line);
                }
                if (l < max - 1) {
                    mWriter.write("\n");
                }
            }
        }
    }

    protected void appendEscapedText(String textValue) throws IOException {
        for (int i = 0, n = textValue.length(); i < n; i++) {
            char c = textValue.charAt(i);
            if (c == '<') {
                mWriter.write("&lt;");
            } else if (c == '&') {
                mWriter.write("&amp;");
            } else if (c == '\n') {
                mWriter.write("<br/>\n");
            } else {
                if (c > 255) {
                    mWriter.write("&#");
                    mWriter.write(Integer.toString(c));
                    mWriter.write(';');
                } else {
                    mWriter.write(c);
                }
            }
        }
    }

    private String stripPath(String path) {
        if (mStripPrefix != null && path.startsWith(mStripPrefix)
                && path.length() > mStripPrefix.length()) {
            int index = mStripPrefix.length();
            if (path.charAt(index) == File.separatorChar) {
                index++;
            }
            return path.substring(index);
        }

        return path;
    }

    /** Sets path prefix to strip from displayed file names */
    void setStripPrefix(String prefix) {
        mStripPrefix = prefix;
    }

    static URL getWarningIconUrl() {
        return HtmlReporter.class.getResource("lint-warning.png");
    }

    static URL getErrorIconUrl() {
        return HtmlReporter.class.getResource("lint-error.png");
    }

    static String getErrorIcon() {
        return "<img border=\"0\" align=\"top\" width=\"15\" height=\"15\" alt=\"Error\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA8AAAAPCAYAAAA71pVKAAAB00lEQVR42nWTS0hbQRiF587MzkUXooi6UHAjhNKNSkGhCDXxkZhUIwWhBLoRsQpi3bXmIboSV2aliI+WKqLUtqsuSxclrhRBUMnVmIpa2oIkQon+zhlr9F7jwOEw/znfLO6dYcy2Arys6AUv6x7klTNh4ViFY485u2+N8Uc8yB1DH0Vt6ki2UkZ20LkS/Eh6CXPk6FnAKVHNJ3nViind9E/6tTKto3TxaU379Qw5euhn4QXxOGzKFjqT7Vmlwx8IC357jh76GvzC64pj4mn6VLbRbf0Nvdcw3J6hr7gS9o3XDxwIN/0RPot+h95pGG7P0AfH1oVz6UR4ya5foXkNw3Pl4Ngub/p6yD1k13FoTsPwXDk4ti89SwnuJrtigYiGY4FhypWDY2aeb0CJ4rzZou9GPc0Y1drtGfrgWLzweUm8uPNsx2ikrHgjHT6LUOrzD/rpDpIlU0JfcaX6d8UfdoW38/20ZbiuxF10MHL1tRNvp2/mSuihn70kZl2/MJ+8Xtkq8NOm4VRqoIUKLy0Hx2mx3PN/5iTk6KFvuaJmyxux3zE8tFPTm9p84KMNdcAGa9COvZqnkaN37wNJvpooSvZFexIvx2b3OkdX4dgne6N3XtUl5wqoyBY2uZQAAAAASUVORK5CYII=\" />";
    }

    static String getWarningIcon() {
        return "<img border=\"0\" align=\"top\" width=\"16\" height=\"15\" alt=\"Warning\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAPCAQAAABHeoekAAAA3klEQVR42nWPsWoCQRCGVyJiF9tAsNImbcDKR/ABBEurYCsBsfQRQiAPYGPyAAnYWQULS9MErNU2Vsr/ObMX7g6O+xd2/5n5dmY3hFQEBVVpuCsVT/yoUl6u4XotBz4E4qR2YYyH6ugEWY8comR/t+tvPPJtSLPYvhvvTswtbdCmCOwjMHXAzjP9kB/ByB7nejbgy43WVPF3WNG+p9+kzkozdhGAQdZh7BlHdGTL3z98pp6Um7okKdvHNuIzWk+9xN+yINOcHps0OnAfuOOoHJH3pmHghhYP2VJcaXx7BaKz9YB2HVrDAAAAAElFTkSuQmCC\"/>";
    }

    static String getFixIcon() {
        return "<img border=\"0\" align=\"top\" width=\"16\" height=\"16\" alt=\"Fix\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA1ElEQVR42s2Szw7BQBCH+wTezAM4u7h7BBKEa3v1AK7iKvoAuDg5uSoaqTR2u90xs9P1J2FLHNjky29mu/Nt09Tz/mb1gxF8wlPBKoG3cAoiCbAVTCQe69bcN8+dgp1k9oTgpL4+bYIXVKCNEqfgIJk4w0RirGmIhklCe07BeBPCEQ9ZOsUwpd17KRiuQ3O4u/DhpMDkfU8kquQNesVQdVIzSX2KQ2l+wykQeKAx4w9GSf05532LU5BpZrD0rzUhLVAiwAtAaYbqXDPKpkvw1a/8s3UBSc/bWGUWa6wAAAAASUVORK5CYII=\"/>";
    }
}
