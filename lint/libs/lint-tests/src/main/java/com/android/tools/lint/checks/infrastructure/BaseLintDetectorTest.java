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

import com.android.SdkConstants;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Base test case for lint tests.
 *
 * <p>Contains a number of general utility methods to help writing test cases, such as looking up a
 * temporary directory, comparing golden files, computing string diffs, etc.
 */
@SuppressWarnings("javadoc")
public abstract class BaseLintDetectorTest extends TestCase implements TestResourceProvider {
    /** Update golden files if different from the actual results */
    private static final boolean UPDATE_DIFFERENT_FILES = false;
    /** Create golden files if missing */
    private static final boolean UPDATE_MISSING_FILES = true;

    private static File sTempDir = null;
    protected static final Set<File> sCleanDirs = Sets.newHashSet();

    @SuppressWarnings("MethodMayBeStatic")
    protected String getTestDataRelPath() {
        fail("Must be overridden");
        return null;
    }

    public static int getCaretOffset(String fileContent, String caretLocation) {
        assertTrue(caretLocation, caretLocation.contains("^"));

        int caretDelta = caretLocation.indexOf('^');
        assertTrue(caretLocation, caretDelta != -1);

        // String around caret/range without the range and caret marker characters
        String caretContext;
        if (caretLocation.contains("[^")) {
            caretDelta--;
            assertTrue(caretLocation, caretLocation.startsWith("[^", caretDelta));
            int caretRangeEnd = caretLocation.indexOf(']', caretDelta + 2);
            assertTrue(caretLocation, caretRangeEnd != -1);
            caretContext =
                    caretLocation.substring(0, caretDelta)
                            + caretLocation.substring(caretDelta + 2, caretRangeEnd)
                            + caretLocation.substring(caretRangeEnd + 1);
        } else {
            caretContext =
                    caretLocation.substring(0, caretDelta)
                            + caretLocation.substring(caretDelta + 1); // +1: skip "^"
        }

        int caretContextIndex = fileContent.indexOf(caretContext);
        assertTrue("Caret content " + caretContext + " not found in file", caretContextIndex != -1);
        return caretContextIndex + caretDelta;
    }

    public static String addSelection(
            String newFileContents, int selectionBegin, int selectionEnd) {
        // Insert selection markers -- [ ] for the selection range, ^ for the caret
        String newFileWithCaret;
        if (selectionBegin < selectionEnd) {
            newFileWithCaret =
                    newFileContents.substring(0, selectionBegin)
                            + "[^"
                            + newFileContents.substring(selectionBegin, selectionEnd)
                            + "]"
                            + newFileContents.substring(selectionEnd);
        } else {
            // Selected range
            newFileWithCaret =
                    newFileContents.substring(0, selectionBegin)
                            + "^"
                            + newFileContents.substring(selectionBegin);
        }

        return newFileWithCaret;
    }

    public static String getCaretContext(String file, int offset) {
        int windowSize = 20;
        int begin = Math.max(0, offset - windowSize / 2);
        int end = Math.min(file.length(), offset + windowSize / 2);

        return "..." + file.substring(begin, offset) + "^" + file.substring(offset, end) + "...";
    }

    /** Get the location to write missing golden files to */
    protected File getTargetDir() {
        // Set $ADT_SDK_SOURCE_PATH to point to your git "sdk" directory; if done, then
        // if you run a unit test which refers to a golden file which does not exist, it
        // will be created directly into the test data directory and you can rerun the
        // test
        // and it should pass (after you verify that the golden file contains the correct
        // result of course).
        String sdk = System.getenv("ADT_SDK_SOURCE_PATH");
        if (sdk != null) {
            File sdkPath = new File(sdk);
            if (sdkPath.exists()) {
                File testData =
                        new File(sdkPath, getTestDataRelPath().replace('/', File.separatorChar));
                if (testData.exists()) {
                    addCleanupDir(testData);
                    return testData;
                }
            }
        }
        return getTempDir();
    }

    public static File getTempDir() {
        if (sTempDir == null) {
            File base = new File(System.getProperty("java.io.tmpdir"));
            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                base = new File("/tmp");
            }

            // On Windows, we don't want to pollute the temp folder (which is generally
            // already incredibly busy). So let's create a temp folder for the results.

            Calendar c = Calendar.getInstance();
            String name = String.format("sdkTests_%1$tF_%1$tT", c).replace(':', '-');
            File tmpDir = new File(base, name);
            if (!tmpDir.exists() && tmpDir.mkdir()) {
                sTempDir = tmpDir;
            } else {
                sTempDir = base;
            }
            addCleanupDir(sTempDir);
        }

        return sTempDir;
    }

    @SuppressWarnings("MethodMayBeStatic")
    protected String removeSessionData(String data) {
        return data;
    }

    @Override
    public InputStream getTestResource(String relativePath, boolean expectExists) {
        String path = "testdata" + File.separator + relativePath;
        InputStream stream = BaseLintDetectorTest.class.getResourceAsStream(path);
        if (!expectExists && stream == null) {
            return null;
        }
        return stream;
    }

    @SuppressWarnings("resource")
    protected String readTestFile(String relativePath, boolean expectExists) throws IOException {
        InputStream stream = getTestResource(relativePath, expectExists);
        if (expectExists) {
            assertNotNull(relativePath + " does not exist", stream);
        } else if (stream == null) {
            return null;
        }

        String xml = new String(ByteStreams.toByteArray(stream), Charsets.UTF_8);
        try {
            Closeables.close(stream, true /* swallowIOException */);
        } catch (IOException e) {
            // cannot happen
        }

        assertTrue(!xml.isEmpty());

        // Remove any references to the project name such that we are isolated from
        // that in golden file.
        // Appears in strings.xml etc.
        xml = removeSessionData(xml);

        return xml;
    }

    protected void assertEqualsGolden(String basename, String actual) throws IOException {
        assertEqualsGolden(basename, actual, basename.substring(basename.lastIndexOf('.') + 1));
    }

    protected void assertEqualsGolden(String basename, String actual, String newExtension)
            throws IOException {
        String testName = getName();
        if (testName.startsWith("test")) {
            testName = testName.substring(4);
            if (Character.isUpperCase(testName.charAt(0))) {
                testName = Character.toLowerCase(testName.charAt(0)) + testName.substring(1);
            }
        }
        String expectedName;
        String extension = basename.substring(basename.lastIndexOf('.') + 1);
        if (newExtension == null) {
            newExtension = extension;
        }
        expectedName =
                basename.substring(0, basename.indexOf('.'))
                        + "-expected-"
                        + testName
                        + '.'
                        + newExtension;
        String expected = readTestFile(expectedName, false);
        if (expected == null) {
            File expectedPath =
                    new File(UPDATE_MISSING_FILES ? getTargetDir() : getTempDir(), expectedName);
            Files.asCharSink(expectedPath, Charsets.UTF_8).write(actual);
            System.out.println("Expected - written to " + expectedPath + ":\n");
            System.out.println(actual);
            fail(
                    "Did not find golden file ("
                            + expectedName
                            + "): Wrote contents as "
                            + expectedPath);
        } else {
            if (!expected.replaceAll("\r\n", "\n").equals(actual.replaceAll("\r\n", "\n"))) {
                File expectedPath = new File(getTempDir(), expectedName);
                File actualPath =
                        new File(getTempDir(), expectedName.replace("expected", "actual"));
                Files.asCharSink(expectedPath, Charsets.UTF_8).write(expected);
                Files.asCharSink(actualPath, Charsets.UTF_8).write(actual);
                // Also update data dir with the current value
                if (UPDATE_DIFFERENT_FILES) {
                    Files.asCharSink(new File(getTargetDir(), expectedName), Charsets.UTF_8)
                            .write((actual));
                }
                System.out.println("The files differ: diff " + expectedPath + " " + actualPath);
                assertEquals(
                        "The files differ - see " + expectedPath + " versus " + actualPath,
                        expected,
                        actual);
            }
        }
    }

    protected static void deleteFile(File dir) {
        TestUtils.deleteFile(dir);
    }

    protected static File makeTestFile(
            File dir, String name, String relative, final InputStream contents) throws IOException {
        if (relative != null) {
            dir = new File(dir, relative);
            if (!dir.exists()) {
                boolean mkdir = dir.mkdirs();
                assertTrue(dir.getPath(), mkdir);
            }
        } else if (!dir.exists()) {
            boolean mkdir = dir.mkdirs();
            assertTrue(dir.getPath(), mkdir);
        }
        File tempFile = new File(dir, name);
        if (tempFile.exists()) {
            tempFile.delete();
        }

        java.nio.file.Files.copy(contents, tempFile.toPath());

        return tempFile;
    }

    protected File getTestfile(File targetDir, String relativePath) throws IOException {
        // Support replacing filenames and paths with a => syntax, e.g.
        //   dir/file.txt=>dir2/dir3/file2.java
        // will read dir/file.txt from the test data and write it into the target
        // directory as dir2/dir3/file2.java

        String targetPath = relativePath;
        int replaceIndex = relativePath.indexOf("=>");
        if (replaceIndex != -1) {
            // foo=>bar
            targetPath = relativePath.substring(replaceIndex + "=>".length());
            relativePath = relativePath.substring(0, replaceIndex);
        }

        InputStream stream = getTestResource(relativePath, true);
        assertNotNull(relativePath + " does not exist", stream);
        int index = targetPath.lastIndexOf('/');
        String relative = null;
        String name = targetPath;
        if (index != -1) {
            name = targetPath.substring(index + 1);
            relative = targetPath.substring(0, index);
        }

        return makeTestFile(targetDir, name, relative, stream);
    }

    protected static void addCleanupDir(File dir) {
        sCleanDirs.add(dir);
        try {
            sCleanDirs.add(dir.getCanonicalFile());
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
        sCleanDirs.add(dir.getAbsoluteFile());
    }

    @SuppressWarnings("MethodMayBeStatic")
    protected String cleanup(String result) {
        List<File> sorted = new ArrayList<>(sCleanDirs);
        // Process dirs in order such that we match longest substrings first
        sorted.sort(
                (file1, file2) -> {
                    String path1 = file1.getPath();
                    String path2 = file2.getPath();
                    int delta = path2.length() - path1.length();
                    if (delta != 0) {
                        return delta;
                    } else {
                        return path1.compareTo(path2);
                    }
                });

        for (File dir : sorted) {
            if (result.contains(dir.getPath())) {
                result = result.replace(dir.getPath(), "/TESTROOT");
            }
        }

        // The output typically contains a few directory/filenames.
        // On Windows we need to change the separators to the unix-style
        // forward slash to make the test as OS-agnostic as possible.
        if (File.separatorChar != '/') {
            result = result.replace(File.separatorChar, '/');
        }

        result = result.replace(System.lineSeparator(), "\n");
        return result;
    }

    /** Get the location to write missing golden files to */
    @SuppressWarnings("MethodMayBeStatic")
    protected File findSrcDir() {
        // Set $ANDROID_SRC to point to your git AOSP working tree
        String rootPath = System.getenv("ANDROID_SRC");
        if (rootPath == null) {
            String sdk = System.getenv("ADT_SDK_SOURCE_PATH");
            if (sdk != null) {
                File root = new File(sdk);
                if (root.exists()) {
                    return root.getName().equals("sdk") ? root.getParentFile() : root;
                }
            }
        } else {
            File root = new File(rootPath);
            if (root.exists()) {
                return root;
            }
        }

        return null;
    }

    protected File findSrcRelativeDir(String relative) {
        // Set $ANDROID_SRC to point to your git AOSP working tree
        File root = findSrcDir();
        if (root != null) {
            File testData = new File(root, relative.replace('/', File.separatorChar));
            if (testData.exists()) {
                return testData;
            }
        }

        return null;
    }
}
