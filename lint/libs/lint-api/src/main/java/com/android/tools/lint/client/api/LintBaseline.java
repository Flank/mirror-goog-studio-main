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

package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A lint baseline is a collection of warnings for a project that have been
 * obtained from a previous run of lint. These warnings are them exempt from
 * reporting. This lets you set a "baseline" with a known set of issues that you
 * haven't attempted to fix yet, but then be alerted whenever new issues crop
 * up.
 */
public class LintBaseline {
    /**
     * Root tag in baseline files (which can be the XML output report files from lint, or a
     * subset of these
     */
    @SuppressWarnings("unused") // used from IDE
    public static final String TAG_ISSUES = "issues";

    private static final String TAG_ISSUE = "issue";
    private static final String TAG_LOCATION = "location";

    private final LintClient client;
    private int foundErrorCount;
    private int foundWarningCount;
    private int baselineIssueCount;

    private Multimap<String, Entry> messageToEntry = ArrayListMultimap.create(100, 20);
    private final File baselineFile;

    public LintBaseline(@Nullable LintClient client, @NonNull File baselineFile) {
        this.client = client;
        this.baselineFile = baselineFile;
        readBaselineFile();
    }

    /**
     * Checks whether the given warning (of the given issue type, message and location)
     * is present in this baseline, and if so marks it as used such that a second call will
     * not find it.
     * <p>
     * When issue analysis is done you can call {@link #getFoundErrorCount()} and
     * {@link #getFoundWarningCount()} to get a count of the warnings or errors that were
     * matched during the run, and {@link #getFixedCount()} to get a count of the issues
     * that were present in the baseline that were not matched (e.g. have been fixed.)
     *
     * @param issue    the issue type
     * @param location the location of the error
     * @param message  the exact error message
     * @param severity the severity of the issue, used to count baseline match as error or warning
     * @return true if this error was found in the baseline and marked as used, and false if this
     * issue is not part of the baseline
     */
    public boolean findAndMark(@NonNull Issue issue, @NonNull Location location,
            @NonNull String message, @Nullable Severity severity) {
        Collection<Entry> entries = messageToEntry.get(message);
        if (entries == null) {
            return false;
        }

        File file = location.getFile();
        String path = file.getPath();
        String issueId = issue.getId();
        for (Entry entry : entries) {
            if (entry.issueId.equals(issueId)) {
                if (isSamePathSuffix(path, entry.path)) {
                    // Remove all linked entries. We don't loop through all the locations;
                    // they're allowed to vary over time, we just assume that all entries
                    // for the same warning should be cleared.
                    while (entry.previous != null) {
                        entry = entry.previous;
                    }
                    while (entry != null) {
                        messageToEntry.remove(entry.message, entry);
                        entry = entry.next;
                    }

                    if (severity == null) {
                        severity = issue.getDefaultSeverity();
                    }
                    if (severity == Severity.ERROR || severity == Severity.FATAL) {
                        foundErrorCount++;
                    } else {
                        foundWarningCount++;
                    }

                    return true;
                }
            }
        }

        return false;
    }

    public int getFoundErrorCount() {
        return foundErrorCount;
    }

    public int getFoundWarningCount() {
        return foundWarningCount;
    }

    public int getFixedCount() {
        return baselineIssueCount - foundErrorCount - foundWarningCount;
    }

    /** Like path.endsWith(suffix), but considers \\ and / identical */
    static boolean isSamePathSuffix(@NonNull String path, @NonNull String suffix) {
        int i = path.length() - 1;
        int j = suffix.length() - 1;
        if (j > i) {
            return false;
        }
        for (; j > 0; i--, j--) {
            char c1 = path.charAt(i);
            char c2 = suffix.charAt(j);
            if (c1 != c2) {
                if (c1 == '\\') {
                    c1 = '/';
                }
                if (c2 == '\\') {
                    c2 = '/';
                }
                if (c1 != c2) {
                    return false;
                }
            }
        }

        return true;
    }

    /** Read in the XML report */
    private void readBaselineFile() {
        if (!baselineFile.exists()) {
            return;
        }

        try (Reader reader = new BufferedReader(new FileReader(baselineFile))) {
            KXmlParser parser = new KXmlParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(reader);

            String issue = null;
            String message = null;
            String path = null;
            String line = null;
            Entry currentEntry = null;

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                int eventType = parser.getEventType();
                if (eventType == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if (tag.equals(TAG_LOCATION)) {
                        if (issue != null && message != null && path != null) {
                            Entry entry = new Entry(issue, message, path, line);
                            if (currentEntry != null) {
                                currentEntry.next = entry;
                            }
                            entry.previous = currentEntry;
                            currentEntry = entry;
                            messageToEntry.put(entry.message, entry);
                        }
                    } else if (tag.equals(TAG_ISSUE)) {
                        baselineIssueCount++;
                        issue = null;
                        message = null;
                        path = null;
                        line = null;
                        currentEntry = null;
                    }
                } else if (eventType != XmlPullParser.START_TAG) {
                    continue;
                }

                for (int i = 0, n = parser.getAttributeCount(); i < n; i++) {
                    String name = parser.getAttributeName(i);
                    String value = parser.getAttributeValue(i);
                    switch (name) {
                        case "id": issue = value; break;
                        case "message": message = value; break;
                        case "file": path = value; break;
                        case "line": line = value; break;
                    }
                }
            }
        } catch (IOException | XmlPullParserException e) {
            if (client != null) {
                client.log(e, null);
            } else {
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns the file which records the data in this baseline
     * @return
     */
    @NonNull
    public File getFile() {
        return baselineFile;
    }

    private static class Entry {
        public final String issueId;
        public final String message;
        public final String path;
        public final String line;
        /**
         * An issue can have multiple locations; we create a separate entry for each
         * but we link them together such that we can mark them all fixed
         */
        public Entry next;
        public Entry previous;

        public Entry(
                @NonNull String issueId,
                @NonNull String message,
                @NonNull String path,
                @Nullable String line) {
            this.issueId = issueId;
            this.message = message;
            this.path = path;
            this.line = line;
        }
    }
}
