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

package com.android.tools.lint.checks.infrastructure;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_BEGINNING;
import static com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_END;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.testutils.TestUtils;
import com.android.tools.lint.Incident;
import com.android.tools.lint.LintFixPerformer;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintFix.DataMap;
import com.android.tools.lint.detector.api.LintFix.GroupType;
import com.android.tools.lint.detector.api.LintFix.LintFixGroup;
import com.android.tools.lint.detector.api.LintFix.ReplaceString;
import com.android.tools.lint.detector.api.LintFix.SetAttribute;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.utils.PositionXmlParser;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import kotlin.text.StringsKt;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Verifier which can simulate IDE quickfixes and check fix data */
public class LintFixVerifier {
    private final TestLintTask task;
    private final List<Incident> incidents;
    private int diffWindow = 0;
    private Boolean reformat;
    private boolean robot = false;

    LintFixVerifier(TestLintTask task, List<Incident> incidents) {
        this.task = task;
        this.incidents = incidents;
    }

    /** Sets up 2 lines of context in the diffs */
    public LintFixVerifier window() {
        diffWindow = 2;
        return this;
    }

    /**
     * Specifies whether a robot is driving the quickfixes (which means it will only allow
     * auto-fixable lint fixes. Default is false.
     */
    public LintFixVerifier robot(boolean isRobot) {
        robot = isRobot;
        return this;
    }

    /** Sets up a specific number of lines of contexts around diffs */
    public LintFixVerifier window(int size) {
        assertTrue(size >= 0 && size <= 100);
        diffWindow = size;
        return this;
    }

    /**
     * Sets whether lint should reformat before and after files before diffing. If not set
     * explicitly to true or false, it will default to true for XML files that set/remove attributes
     * and false otherwise. (May not have any effect on other file types than XML.)
     */
    public LintFixVerifier reformatDiffs(boolean reformatDiffs) {
        this.reformat = reformatDiffs;
        return this;
    }

    /**
     * Checks what happens with the given fix in this result as applied to the given test file, and
     * making sure that the result is the new contents
     *
     * @param fix the fix description, or null to pick the first one
     * @param after the file after applying the fix
     * @return this
     */
    public LintFixVerifier checkFix(@Nullable String fix, @NonNull TestFile after) {
        checkFixes(fix, after, null);
        return this;
    }

    /**
     * Applies the fixes and provides diffs of all the affected files, then compares it against the
     * expected result.
     *
     * @param expected the diff description resulting from applying the diffs
     * @return this
     */
    public LintFixVerifier expectFixDiffs(@NonNull String expected) {
        StringBuilder diff = new StringBuilder(100);
        checkFixes(null, null, diff);
        String actual =
                StringsKt.trimIndent(diff.toString().replace("\r\n", "\n")).replace('$', '＄');
        expected = StringsKt.trimIndent(expected).replace('$', '＄');
        if (!expected.equals(actual)) {
            // Until 3.2 canary 10 the line numbers were off by one; try adjusting
            if (!bumpFixLineNumbers(expected).trim().equals(actual.trim())) {
                assertEquals(expected, actual);
            }
        }
        return this;
    }

    /**
     * Given fix-delta output, increases the line numbers by one (needed to gracefully handle older
     * fix diffs where the line numbers were 0-based instead of 1-based like the error output.)
     */
    @NonNull
    private static String bumpFixLineNumbers(@NonNull String output) {
        StringBuilder sb = new StringBuilder(output.length());
        Pattern pattern = Pattern.compile("((Fix|Data) for .* line )(\\d+)(: .+)");
        for (String line : output.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String prefix = matcher.group(1);
                String lineNumber = matcher.group(3);
                String suffix = matcher.group(4);
                sb.append(prefix);
                sb.append((Integer.parseInt(lineNumber) + 1));
                sb.append(suffix);
            } else {
                sb.append(line);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Nullable
    private TestFile findTestFile(@NonNull String path) {
        path = path.replace(File.separatorChar, '/');
        for (ProjectDescription project : task.projects) {
            for (TestFile file : project.getFiles()) {
                if (file.getTargetPath().equals(path)) {
                    return file;
                }
            }
        }

        return null;
    }

    private void checkFixes(
            @Nullable String fixName,
            @Nullable TestFile expectedFile,
            @Nullable StringBuilder diffs) {
        assertTrue(expectedFile != null || diffs != null);
        List<String> names = Lists.newArrayList();
        for (Incident incident : incidents) {
            LintFix data = incident.getFix();
            if (robot && !data.robot) {
                // Fix requires human intervention
                continue;
            }
            List<LintFix> list;
            if (data instanceof LintFixGroup) {
                LintFixGroup group = (LintFixGroup) data;
                if (group.type == GroupType.COMPOSITE) {
                    // separated out again in applyFix
                    list = Collections.singletonList(data);
                } else {
                    list = group.fixes;
                }
            } else {
                list = Collections.singletonList(data);
            }

            for (LintFix lintFix : list) {
                Location location = LintFixPerformer.Companion.getLocation(incident);
                Project project = incident.getProject();
                String targetPath =
                        project != null
                                ? project.getDisplayPath(location.getFile())
                                : location.getFile().getPath();
                TestFile file = findTestFile(targetPath);
                if (file == null) {
                    fail("Didn't find test file " + targetPath);
                }
                String before = file.getContents();
                assertNotNull(file.getTargetPath(), before);

                if (lintFix instanceof DataMap && diffs != null) {
                    // Doesn't edit file, but include in diffs so fixes can verify the
                    // correct data is passed
                    appendDataMap(incident, (DataMap) lintFix, diffs);
                }

                String after;
                Boolean reformat = this.reformat;
                after = applyFix(incident, lintFix, before);
                if (after == null) {
                    continue;
                }

                if (reformat == null && haveSetAttribute(lintFix)) {
                    reformat = true;
                }

                if (expectedFile != null) {
                    assertEquals(
                            StringsKt.trimIndent(expectedFile.getContents()),
                            StringsKt.trimIndent(after));
                }

                if (diffs != null) {
                    if (reformat != null
                            && reformat
                            && incident.getDisplayPath().endsWith(DOT_XML)) {
                        try {
                            before =
                                    XmlPrettyPrinter.prettyPrint(
                                            XmlUtils.parseDocument(before, true), true);
                            after =
                                    XmlPrettyPrinter.prettyPrint(
                                            XmlUtils.parseDocument(after, true), true);
                        } catch (SAXException | IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    appendDiff(incident, lintFix.getDisplayName(), before, after, diffs);
                }

                String name = lintFix.getDisplayName();
                if (fixName != null && !fixName.equals(name)) {
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                    continue;
                }
                names.add(fixName);
            }
        }
    }

    @Nullable
    private static String applyFix(
            @NonNull Incident incident, @NonNull LintFix lintFix, @NonNull String before) {
        if (lintFix instanceof ReplaceString) {
            ReplaceString replaceFix = (ReplaceString) lintFix;
            return checkReplaceString(replaceFix, incident, before);
        } else if (lintFix instanceof SetAttribute) {
            SetAttribute setFix = (SetAttribute) lintFix;
            return checkSetAttribute(setFix, before, incident);
        } else if (lintFix instanceof LintFixGroup
                && ((LintFixGroup) lintFix).type == GroupType.COMPOSITE) {
            for (LintFix nested : ((LintFixGroup) lintFix).fixes) {
                String after = applyFix(incident, nested, before);
                if (after == null) {
                    return null;
                }
                before = after;
            }
            return before;
        }
        return null;
    }

    private static boolean haveSetAttribute(@NonNull LintFix lintFix) {
        if (lintFix instanceof SetAttribute) {
            return true;
        } else if (lintFix instanceof LintFixGroup
                && ((LintFixGroup) lintFix).type == GroupType.COMPOSITE) {
            for (LintFix nested : ((LintFixGroup) lintFix).fixes) {
                if (haveSetAttribute(nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String checkSetAttribute(
            @NonNull SetAttribute setFix, @NonNull String contents, @NonNull Incident incident) {
        Location location = setFix.range != null ? setFix.range : incident.getLocation();
        Position start = location.getStart();
        Position end = location.getEnd();
        assert start != null;
        assert end != null;

        try {
            org.w3c.dom.Document document;
            document = PositionXmlParser.parse(contents);

            Node node = PositionXmlParser.findNodeAtOffset(document, start.getOffset());
            assertNotNull("No node found at offset " + start.getOffset(), node);
            if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                node = ((Attr) node).getOwnerElement();
            } else if (node.getNodeType() != Node.ELEMENT_NODE) {
                // text, comments
                node = node.getParentNode();
            }
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                fail(
                        "Didn't find element at offset "
                                + start.getOffset()
                                + " (line "
                                + start.getLine()
                                + 1
                                + ", column "
                                + start.getColumn()
                                + 1
                                + ") in "
                                + incident.getDisplayPath()
                                + ":\n"
                                + contents);
            }
            Element element = (Element) node;
            String value = setFix.value;
            String namespace = setFix.namespace;
            if (value == null) {
                if (namespace != null) {
                    element.removeAttributeNS(namespace, setFix.attribute);
                } else {
                    element.removeAttribute(setFix.attribute);
                }
            } else {
                // Indicate the caret position by "|"
                if (setFix.dot >= 0 && setFix.dot <= value.length()) {
                    if (setFix.mark >= 0 && setFix.mark != setFix.dot) {
                        // Selection
                        assert setFix.mark < setFix.dot;
                        value =
                                value.substring(0, setFix.mark)
                                        + "["
                                        + value.substring(setFix.mark, setFix.dot)
                                        + "]|"
                                        + value.substring(setFix.dot);
                    } else {
                        // Just caret
                        value = value.substring(0, setFix.dot) + "|" + value.substring(setFix.dot);
                    }
                }

                if (namespace != null) {
                    // Workaround for the fact that the namespace-setter method
                    // doesn't seem to work on these documents
                    String prefix = document.lookupPrefix(namespace);
                    if (prefix == null) {
                        String base = "ns";
                        if (ANDROID_URI.equals(namespace)) {
                            base = ANDROID_NS_NAME;
                        } else if (TOOLS_URI.equals(namespace)) {
                            base = "tools";
                        } else if (AUTO_URI.equals(namespace)) {
                            base = "app";
                        }
                        Element root = document.getDocumentElement();
                        int index = 1;
                        while (true) {
                            prefix = base + (index == 1 ? "" : Integer.toString(index));
                            if (!root.hasAttribute(XMLNS_PREFIX + prefix)) {
                                break;
                            }
                            index++;
                        }
                        root.setAttribute(XMLNS_PREFIX + prefix, namespace);
                    }

                    element.setAttribute(prefix + ":" + setFix.attribute, value);
                } else {
                    element.setAttribute(setFix.attribute, value);
                }
            }

            return XmlPrettyPrinter.prettyPrint(document, true);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String checkReplaceString(
            @NonNull ReplaceString replaceFix,
            @NonNull Incident incident,
            @NonNull String contents) {
        String oldPattern = replaceFix.oldPattern;
        String oldString = replaceFix.oldString;
        Location location = replaceFix.range != null ? replaceFix.range : incident.getLocation();

        Position start = location.getStart();
        Position end = location.getEnd();
        assert start != null;
        assert end != null;
        String locationRange = contents.substring(start.getOffset(), end.getOffset());
        int startOffset;
        int endOffset;
        String replacement = replaceFix.replacement;

        if (oldString == null && oldPattern == null) {
            // Replace the whole range
            startOffset = start.getOffset();
            endOffset = end.getOffset();

            // See if there's nothing left on the line; if so, delete the whole line
            boolean allSpace = true;
            for (int offset = 0; offset < replacement.length(); offset++) {
                char c = contents.charAt(offset);
                if (!Character.isWhitespace(c)) {
                    allSpace = false;
                    break;
                }
            }

            if (allSpace) {
                int lineBegin = startOffset;
                while (lineBegin > 0) {
                    char c = contents.charAt(lineBegin - 1);
                    if (c == '\n') {
                        break;
                    } else if (!Character.isWhitespace(c)) {
                        allSpace = false;
                        break;
                    }
                    lineBegin--;
                }

                int lineEnd = endOffset;
                while (lineEnd < contents.length()) {
                    char c = contents.charAt(lineEnd);
                    lineEnd++;
                    if (c == '\n') {
                        break;
                    }
                }
                if (allSpace) {
                    startOffset = lineBegin;
                    endOffset = lineEnd;
                }
            }
        } else if (oldString != null) {
            int index = locationRange.indexOf(oldString);
            if (index != -1) {
                startOffset = start.getOffset() + index;
                endOffset = start.getOffset() + index + oldString.length();
            } else if (oldString.equals(INSERT_BEGINNING)) {
                startOffset = start.getOffset();
                endOffset = startOffset;
            } else if (oldString.equals(INSERT_END)) {
                startOffset = end.getOffset();
                endOffset = startOffset;
            } else {
                fail(
                        "Did not find \""
                                + oldString
                                + "\" in \""
                                + locationRange
                                + "\" as suggested in the quickfix. Consider calling "
                                + "ReplaceStringBuilder#range() to set a larger range to "
                                + "search than the default highlight range.");
                return null;
            }
        } else {
            //noinspection ConstantConditions
            assertTrue(oldPattern != null);
            Pattern pattern = Pattern.compile(oldPattern);
            Matcher matcher = pattern.matcher(locationRange);
            if (!matcher.find()) {
                fail(
                        "Did not match pattern \""
                                + oldPattern
                                + "\" in \""
                                + locationRange
                                + "\" as suggested in the quickfix");
                return null;
            } else {
                startOffset = start.getOffset();
                endOffset = startOffset;

                if (matcher.groupCount() > 0) {
                    if (oldPattern.contains("target")) {
                        try {
                            startOffset += matcher.start("target");
                            endOffset += matcher.end("target");
                        } catch (IllegalArgumentException ignore) {
                            // Occurrence of "target" not actually a named group
                            startOffset += matcher.start(1);
                            endOffset += matcher.end(1);
                        }
                    } else {
                        startOffset += matcher.start(1);
                        endOffset += matcher.end(1);
                    }
                } else {
                    startOffset += matcher.start();
                    endOffset += matcher.end();
                }

                replacement = replaceFix.expandBackReferences(matcher);
            }
        }

        if (replaceFix.shortenNames) {
            // This isn't fully shortening names, it's only removing fully qualified names
            // for symbols already imported.
            //
            // Also, this will not correctly handle some conflicts. This is only used for
            // unit testing lint fixes, not for actually operating on code; for that we're using
            // IntelliJ's built in import cleanup when running in the IDE.
            Set<String> imported = new HashSet<>();
            for (String line : contents.split("\n")) {
                if (line.startsWith("package ")) {
                    int to = line.indexOf(';');
                    if (to == -1) {
                        to = line.length();
                    }
                    imported.add(line.substring("package ".length(), to).trim() + ".");
                } else if (line.startsWith("import ")) {
                    int from = "import ".length();
                    if (line.startsWith("static ")) {
                        from += " static ".length();
                    }
                    int to = line.indexOf(';');
                    if (to == -1) {
                        to = line.length();
                    }
                    if (line.charAt(to - 1) == '*') {
                        to--;
                    }
                    imported.add(line.substring(from, to).trim());
                }
            }

            for (String full : imported) {
                if (replacement.contains(full)) {
                    if (!full.endsWith(".")) {
                        int index = full.lastIndexOf('.');
                        if (index == -1) {
                            continue;
                        }
                        full = full.substring(0, index + 1);
                    }
                    replacement = replacement.replace(full, "");
                }
            }
        }

        String s = contents.substring(0, startOffset) + replacement + contents.substring(endOffset);

        // Insert selection/caret markers if configured for this fix
        if (replaceFix.selectPattern != null) {
            Pattern pattern = Pattern.compile(replaceFix.selectPattern);
            Matcher matcher = pattern.matcher(s);
            if (matcher.find(start.getOffset())) {
                int selectStart;
                int selectEnd;
                if (matcher.groupCount() > 0) {
                    selectStart = matcher.start(1);
                    selectEnd = matcher.end(1);
                } else {
                    selectStart = matcher.start();
                    selectEnd = matcher.end();
                }
                if (selectStart == selectEnd) {
                    s = s.substring(0, selectStart) + "|" + s.substring(selectEnd);

                } else {
                    s =
                            s.substring(0, selectStart)
                                    + "["
                                    + s.substring(selectStart, selectEnd)
                                    + "]"
                                    + s.substring(selectEnd);
                }
            }
        }

        return s;
    }

    private void appendDiff(
            @NonNull Incident incident,
            @Nullable String fixDescription,
            @NonNull String before,
            @NonNull String after,
            @NonNull StringBuilder diffs) {
        String diff = TestUtils.getDiff(before, after, diffWindow);
        if (!diff.isEmpty()) {
            String targetPath = incident.getDisplayPath().replace(File.separatorChar, '/');
            diffs.append("Fix for ")
                    .append(targetPath)
                    .append(" line ")
                    .append(incident.getLine() + 1)
                    .append(": ");
            if (fixDescription != null) {
                diffs.append(fixDescription).append(":\n");
            }
            diffs.append(diff);
        }
    }

    private static void appendDataMap(
            @NonNull Incident incident, @NonNull DataMap map, @NonNull StringBuilder diffs) {
        String targetPath = incident.getDisplayPath();
        diffs.append("Data for ")
                .append(targetPath.replace(File.separatorChar, '/'))
                .append(" line ")
                .append(incident.getLine() + 1)
                .append(": ");
        String fixDescription = map.getDisplayName();
        if (fixDescription != null) {
            diffs.append(fixDescription).append(":\n");
        }
        List<Object> keys = Lists.newArrayList(map.keys());
        keys.sort(Comparator.comparing(Object::toString));
        for (Object key : keys) {
            diffs.append("  ");
            if (key instanceof Class<?>) {
                diffs.append(((Class<?>) key).getSimpleName());
            } else {
                assert key instanceof String;
                diffs.append(key.toString());
            }
            diffs.append(" : ");
            if (key instanceof Class<?>) {
                diffs.append(map.get((Class<?>) key));
            } else {
                diffs.append(map.get(key.toString()));
            }
        }
    }
}
