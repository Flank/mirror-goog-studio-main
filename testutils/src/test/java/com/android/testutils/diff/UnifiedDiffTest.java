/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.testutils.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class UnifiedDiffTest {

    public static final List<String> FILE =
            Arrays.asList(
                    "1", "2", "3", "4", "from1", "6", "7", "8", "9", "10", "11", "common1",
                    "common2", "from1", "from2", "common3", "common4");

    public static final List<String> DIFF_1 =
            Arrays.asList(
                    "diff --something",
                    "--- a/file/path.txt",
                    "+++ b/file/path.txt",
                    "@@ -5,1 +1,1 @@",
                    "-from1",
                    "+to1",
                    "@@ -12,6 +10,7 @@",
                    " common1",
                    " common2",
                    "-from1",
                    "-from2",
                    "+to1",
                    "+to2",
                    "+to3",
                    " common3",
                    " common4",
                    "--- a/file/path2.txt",
                    "+++ b/file/path2.txt");

    public static final List<String> AFTER_DIFF_1 =
            Arrays.asList(
                    "1", "2", "3", "4", "to1", "6", "7", "8", "9", "10", "11", "common1", "common2",
                    "to1", "to2", "to3", "common3", "common4");

    public static final List<String> DIFF_2 =
            Arrays.asList(
                    "diff --something",
                    "--- a/file/path.txt",
                    "+++ b/file/path.txt",
                    "@@ -1,4 +1,1 @@",
                    " 1",
                    "-2",
                    "-3",
                    "-4",
                    "@@ -8,3 +10,5 @@",
                    " 8",
                    "-9",
                    "+9 new",
                    " 10");

    public static final List<String> AFTER_DIFF_2 =
            Arrays.asList(
                    "1", "from1", "6", "7", "8", "9 new", "10", "11", "common1", "common2", "from1",
                    "from2", "common3", "common4");

    @Test
    public void testParse() {
        UnifiedDiff diff = new UnifiedDiff(DIFF_1);
        assertEquals(2, diff.diffs.size());

        assertEquals("a/file/path.txt", diff.diffs.get(0).from);
        assertEquals("b/file/path.txt", diff.diffs.get(0).to);

        assertEquals("a/file/path2.txt", diff.diffs.get(1).from);
        assertEquals("b/file/path2.txt", diff.diffs.get(1).to);
    }

    @Test
    public void testApply() {
        UnifiedDiff diff = new UnifiedDiff(DIFF_1);
        ArrayList<String> file = new ArrayList<>(FILE);
        diff.diffs.get(0).apply(file);
        assertEquals(AFTER_DIFF_1, file);
    }

    @Test
    public void testApplyThatShiftsLines() {
        UnifiedDiff diff = new UnifiedDiff(DIFF_2);
        ArrayList<String> file = new ArrayList<>(FILE);
        diff.diffs.get(0).apply(file);
        assertEquals(AFTER_DIFF_2, file);
    }

    @Test
    public void testFullDiff() throws IOException {
        File data = TestUtils.resolveWorkspacePath("tools/base/testutils/src/test/data").toFile();
        File tmp = TestUtils.createTempDirDeletedOnExit().toFile();
        File before = new File(data, "before");
        File after = new File(data, "after");
        FileUtils.copyDirectory(before, tmp);
        UnifiedDiff diff = new UnifiedDiff(new File(data, "diff.txt"));
        diff.apply(tmp, 2);

        assertDirectoriesEqual(after, tmp);

        diff.invert().apply(tmp, 2);

        assertDirectoriesEqual(before, tmp);
    }

    private static void assertDirectoriesEqual(File expected, File value) throws IOException {
        File[] it = FileUtils.getAllFiles(value).toArray(File.class);
        File[] ex = FileUtils.getAllFiles(expected).toArray(File.class);
        Arrays.sort(it);
        Arrays.sort(ex);
        for (int i = 0; i < it.length && i < ex.length; i++) {
            Path e = ex[i].toPath();
            Path r = it[i].toPath();
            assertEquals(
                    expected.toPath().relativize(e).toString(),
                    value.toPath().relativize(r).toString());
            assertEquals(
                    "File content of " + expected.toPath().relativize(e).toString(),
                    comparisonString(e),
                    comparisonString(r));
        }
        if (it.length > ex.length) {
            fail("Unexpected file " + it[ex.length] + " was found.");
        }
        if (it.length < ex.length) {
            fail("Expected file " + ex[it.length] + " was not found.");
        }
    }

    private static String comparisonString(Path file) throws IOException {
        return UnifiedDiff.withVisibleCarriageReturn(
                new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
}
