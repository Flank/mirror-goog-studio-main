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

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Utility methods to deal with loading the test data.
 */
public class TestUtils {
    /**
     * Default timeout for the {@link #eventually(Runnable)} check.
     */
    private static final Duration DEFAULT_EVENTUALLY_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Time to wait between checks to obtain the value of an eventually supplier.
     */
    private static final long EVENTUALLY_CHECK_CYCLE_TIME_MS = 10;

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
     * Returns the root of the entire Android Studio codebase.
     *
     * From this path, you should be able to access any file in the workspace via its full path,
     * e.g.
     *
     * new File(TestUtils.getWorkspaceRoot(), "tools/adt/idea/android/testSrc");
     * new File(TestUtils.getWorkspaceRoot(), "prebuilts/studio/jdk");
     *
     * If this method is called by code run via IntelliJ / Gradle, it will simply walk its
     * ancestor tree looking for the WORKSPACE file at its root; if called from Bazel, it will
     * simply return the runfiles directory (which should be a mirror of the WORKSPACE root except
     * only populated with explicitly declared dependencies).
     *
     * Instead of calling this directly, prefer calling {@link #getWorkspaceFile(String)} as it
     * is more resilient to cross-platform testing.
     *
     * @throws IllegalStateException if the current directory of the test is not a subdirectory of
     * the workspace directory when this method is called. This shouldn't happen if the test is run
     * by Bazel or run by IntelliJ with default configuration settings (where the working directory
     * is initialized to the module root).
     */
    @NonNull
    public static File getWorkspaceRoot() {
        // If we are using Bazel (which defines the following env vars), simply use the sandboxed
        // root they provide us.
        String workspace = System.getenv("TEST_WORKSPACE");
        String workspaceParent = System.getenv("TEST_SRCDIR");
        if (workspace != null && workspaceParent != null) {
            return new File(workspaceParent, workspace);
        }

        // If here, we're using a non-Bazel build system. At this point, assume our working
        // directory is located underneath our codebase's root folder, so keep navigating up until
        // we find it.
        File pwd = new File("");
        File currDir = pwd;
        while (!new File(currDir, "WORKSPACE").exists()) {
            currDir = currDir.getAbsoluteFile().getParentFile();

            if (currDir == null) {
                throw new IllegalStateException(
                        "Could not find WORKSPACE root. Is the original working directory a " +
                                "subdirectory of the Android Studio codebase?\n\n" +
                                "pwd = " + pwd.getAbsolutePath());
            }
        }

        return currDir;
    }

    /**
     * Given a full path to a file from the base of the current workspace, return the file.
     *
     * e.g.
     * TestUtils.getWorkspaceFile("tools/adt/idea/android/testSrc");
     * TestUtils.getWorkspaceFile("prebuilts/studio/jdk");
     *
     * This method guarantees the file exists, throwing an exception if not found, so tests can
     * safely use the file immediately after receiving it.
     *
     * In order to have the same method call work on both Windows and non-Windows machines, if the
     * current OS is Windows and the target path is found with a common windows extension on it,
     * then it will automatically be returned, e.g. "/path/to/binary" -> "/path/to/binary.exe".
     *
     * @throws IllegalArgumentException if the path results in a file that's not found.
     *
     * @return a valid File object pointing at the requested workspace file.
     */
    @NonNull
    public static File getWorkspaceFile(@NonNull String path) {
        File f = new File(getWorkspaceRoot(), path);

        if (!f.exists() && OsType.getHostOs() == OsType.WINDOWS) {
            // This file may be a binary with a .exe extension
            // TODO: Confirm this works on Windows
            f = new File(f.getPath() + ".exe");
        }

        if (!f.exists()) {
            throw new IllegalArgumentException("File \"" + path + "\" not found.");
        }

        return f;
    }

    /**
     * Given a path to a file relative to the SDK's root, return the file.
     *
     * @throws IllegalStateException if the current OS is not supported.
     * @throws IllegalArgumentException if the path results in a file not found.
     *
     * @return a valid File object pointing at the requested SDK file.
     */
    @NonNull
    public static File getSdkFile(String path) {
        OsType osType = OsType.getHostOs();
        if (osType == OsType.UNKNOWN) {
            throw new IllegalStateException(
                    "SDK test not supported on unknown platform: " + OsType.getOsName());
        }

        String hostDir = osType.getFolderName();
        return getWorkspaceFile("prebuilts/studio/sdk/" + hostDir + (path.isEmpty() ? path : "/" + path));
    }

    /**
     * Returns a file at {@code path} relative to the root for {@link #getLatestAndroidPlatform}.
     *
     * @throws IllegalStateException if the current OS is not supported.
     * @throws IllegalArgumentException if the path results in a file not found.
     */
    @NonNull
    public static File getPlatformFile(String path) {
        return getSdkFile("platforms/" + getLatestAndroidPlatform() + "/" + path);
    }

    /**
     * Return the SDK directory.
     *
     * @throws IllegalStateException if the current OS is not supported.
     * @throws IllegalArgumentException if the path results in a file not found.
     *
     * @return a valid File object pointing at the SDK directory.
     */
    @NonNull
    public static File getSdk() {
        return getSdkFile("");
    }

    @NonNull
    public static String getLatestAndroidPlatform() {
        return "android-24";
    }

    /**
     * Sleeps the current thread for enough time to ensure that the local file system had enough
     * time to notice a "tick". This method is usually called in tests when it is necessary to
     * ensure filesystem writes are detected through timestamp modification.
     *
     * @throws InterruptedException waiting interrupted
     * @throws IOException issues creating a temporary file
     */
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

    /**
     * Asserts that a runnable will eventually not throw an assertion exception. Equivalent to
     * {@link #eventually(Runnable, Duration)}, but using a default timeout
     *
     * @param runnable a description of the failure, if the condition never becomes {@code true}
     */
    public static void eventually(@NonNull Runnable runnable) {
        eventually(runnable, DEFAULT_EVENTUALLY_TIMEOUT);
    }

    /**
     * Asserts that a runnable will eventually not throw {@link AssertionError} before
     * {@code timeoutMs} milliseconds have ellapsed
     *
     * @param runnable a description of the failure, if the condition never becomes {@code true}
     * @param duration the timeout for the predicate to become true
     */
    public static void eventually(@NonNull Runnable runnable, Duration duration) {
        AssertionError lastError = null;

        Instant timeoutTime = Instant.now().plus(duration);
        while (Instant.now().isBefore(timeoutTime)) {
            try {
                runnable.run();
                return;
            } catch (AssertionError e) {
                /*
                 * It is OK to throw this. Save for later.
                 */
                lastError = e;
            }

            try {
                Thread.sleep(EVENTUALLY_CHECK_CYCLE_TIME_MS);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        throw new AssertionError(
                "Timed out waiting for runnable not to throw; last error was:",
                lastError);
    }
}
