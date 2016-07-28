/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.testutils;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Utility methods to deal with loading the test data.
 */
public class TestUtils {

    /**
     * returns a File for the subfolder of the test resource data.
     *
     * <p>This is basically {@code src/test/resources/testData/$name"}.
     *
     * <p>Note that this folder is relative to the root project which is where gradle
     * sets the current working dir when running the tests.
     *
     * <p>If you need a full folder path, use {@link #getCanonicalRoot(String...)}.
     *
     * @param names the names of the subfolders.
     *
     * @return a File
     */
    @NonNull
    public static File getRoot(String... names) {
        File root = new File("src/test/resources/testData/");

        for (String name : names) {
            root = new File(root, name);

            // Hack: The sdk-common tests are not configured properly; running tests
            // works correctly from Gradle but not from within the IDE. The following
            // hack works around this quirk:
            if (!root.isDirectory() && !root.getPath().contains("sdk-common")) {
                File r = new File("sdk-common", root.getPath()).getAbsoluteFile();
                if (r.isDirectory()) {
                    root = r;
                }
            }

            TestCase.assertTrue("Test folder '" + name + "' does not exist! "
                    + "(Tip: Check unit test launch config pwd)",
                    root.isDirectory());

        }

        return root;
    }

    /**
     * returns a File for the subfolder of the test resource data.
     *
     * The full path is canonized.
     * This is basically ".../src/test/resources/testData/$name".
     *
     * @param names the names of the subfolders.
     *
     * @return a File
     */
    public static File getCanonicalRoot(String... names) throws IOException {
        File root = getRoot(names);
        return root.getCanonicalFile();
    }

    public static void deleteFile(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFile(f);
                }
            }
        } else if (dir.isFile()) {
            assertTrue(dir.getPath(), dir.delete());
        }
    }

    public static File createTempDirDeletedOnExit() {
        final File tempDir = Files.createTempDir();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                deleteFile(tempDir);
            }
        });

        return tempDir;
    }

    /**
     * Returns the SDK directory as built from the Android source tree.
     *
     * @return the SDK directory
     */
    @NonNull
    public static File getSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            }
        }

        throw new IllegalStateException("SDK directory not defined with ANDROID_HOME");
    }

    public static void waitForFileSystemTick() throws InterruptedException, IOException {
        waitForFileSystemTick(getFreshTimestamp());
    }

    /**
     * Sleeps the current thread for enough time to ensure that the local file system had enough
     * time to notice a "tick". This method is usually called in tests when it is necessary to
     * ensure filesystem writes are detected through timestamp modification.
     *
     * @param currentTimestamp last timestamp read from disk
     * @throws InterruptedException waiting interrupted
     * @throws IOException issues creating a temporary file
     */
    public static void waitForFileSystemTick(long currentTimestamp)
            throws InterruptedException, IOException {
        while (getFreshTimestamp() <= currentTimestamp) {
            Thread.sleep(100);
        }
    }

    private static long getFreshTimestamp() throws IOException {
        File notUsed = File.createTempFile(TestUtils.class.getName(), "waitForFileSystemTick");
        long freshTimestamp = notUsed.lastModified();
        FileUtils.delete(notUsed);
        return freshTimestamp;
    }

    @NonNull
    public static String getDiff(@NonNull String before, @NonNull  String after) {
        return getDiff(before.split("\n"), after.split("\n"));
    }

    @NonNull
    public static String getDiff(@NonNull String[] before, @NonNull String[] after) {
        // Based on the LCS section in http://introcs.cs.princeton.edu/java/96optimization/
        StringBuilder sb = new StringBuilder();

        int n = before.length;
        int m = after.length;

        // Compute longest common subsequence of x[i..m] and y[j..n] bottom up
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (before[i].equals(after[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        int i = 0;
        int j = 0;
        while ((i < n) && (j < m)) {
            if (before[i].equals(after[j])) {
                i++;
                j++;
            } else {
                sb.append("@@ -");
                sb.append(Integer.toString(i + 1));
                sb.append(" +");
                sb.append(Integer.toString(j + 1));
                sb.append('\n');
                while (i < n && j < m && !before[i].equals(after[j])) {
                    if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                        sb.append('-');
                        if (!before[i].trim().isEmpty()) {
                            sb.append(' ');
                        }
                        sb.append(before[i]);
                        sb.append('\n');
                        i++;
                    } else {
                        sb.append('+');
                        if (!after[j].trim().isEmpty()) {
                            sb.append(' ');
                        }
                        sb.append(after[j]);
                        sb.append('\n');
                        j++;
                    }
                }
            }
        }

        if (i < n || j < m) {
            assert i == n || j == m;
            sb.append("@@ -");
            sb.append(Integer.toString(i + 1));
            sb.append(" +");
            sb.append(Integer.toString(j + 1));
            sb.append('\n');
            for (; i < n; i++) {
                sb.append('-');
                if (!before[i].trim().isEmpty()) {
                    sb.append(' ');
                }
                sb.append(before[i]);
                sb.append('\n');
            }
            for (; j < m; j++) {
                sb.append('+');
                if (!after[j].trim().isEmpty()) {
                    sb.append(' ');
                }
                sb.append(after[j]);
                sb.append('\n');
            }
        }

        return sb.toString();
    }

}
