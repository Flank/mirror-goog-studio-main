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

import static com.android.testutils.WindowsPathUtilsKt.getWindowsShortNameFile;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import junit.framework.TestCase;

/**
 * Utility methods to deal with loading the test data.
 */
public class TestUtils {
    /** Default timeout for the {@link #eventually(Runnable)} check. */
    private static final Duration DEFAULT_EVENTUALLY_TIMEOUT = Duration.ofSeconds(10);

    /** Time to wait between checks to obtain the value of an eventually supplier. */
    private static final long EVENTUALLY_CHECK_CYCLE_TIME_MS = 10;

    @GuardedBy("TestUtils.class")
    private static File workspaceRoot = null;

    /**
     * Returns Kotlin version that is used in new project templates and integration tests.
     *
     * <p>This version is determined based on the checked-in kotlin-plugin prebuilt, and should be
     * in sync with the version in:
     *
     * <ul>
     *   <li>buildSrc/base/dependencies.properties
     *   <li>tools/base/third_party/BUILD (this is generated from dependencies.properties)
     *   <li>tools/base/build-system/integration-test/application/BUILD
     *   <li>tools/base/build-system/integration-test/databinding/BUILD.bazel
     *   <li>tools/adt/idea/android/BUILD
     *   <li>tools/base/.idea/libraries definition for kotlin-stdlib-jdk8
     *   <li>tools/idea/.idea/libraries definition for kotlin-stdlib-jdk8
     * </ul>
     */
    @NonNull
    public static String getKotlinVersionForTests() {
        try {
            return Files.readLines(getKotlinVersionFile(), Charsets.UTF_8).get(0);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not determine Kotlin plugin version", ex);
        }
    }

    @NonNull
    private static File getKotlinVersionFile() {
        if (System.getProperty("idea.gui.test.running.on.release") != null) {
            File homePath =
                    new File(System.getProperty("idea.gui.test.remote.ide.path"))
                            .getParentFile()
                            .getParentFile();
            return new File(homePath, "plugins/Kotlin/kotlinc/build.txt");
        } else {
            return getWorkspaceFile(
                    "prebuilts/tools/common/kotlin-plugin/Kotlin/kotlinc/build.txt");
        }
    }

    /**
     * Returns a File for the subfolder of the test resource data.
     *
     * @deprecated Use {@link #getWorkspaceRoot()} or {@link #getWorkspaceFile(String)} instead.
     */
    @Deprecated
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
        PathUtils.addRemovePathHook(tempDir.toPath());
        return tempDir;
    }

    /**
     * Returns the root of the entire Android Studio codebase.
     *
     * <p>From this path, you should be able to access any file in the workspace via its full path,
     * e.g.
     *
     * <p>new File(TestUtils.getWorkspaceRoot(), "tools/adt/idea/android/testSrc"); new
     * File(TestUtils.getWorkspaceRoot(), "prebuilts/studio/jdk");
     *
     * <p>If this method is called by code run via IntelliJ / Gradle, it will simply walk its
     * ancestor tree looking for the WORKSPACE file at its root; if called from Bazel, it will
     * simply return the runfiles directory (which should be a mirror of the WORKSPACE root except
     * only populated with explicitly declared dependencies).
     *
     * <p>Instead of calling this directly, prefer calling {@link #getWorkspaceFile(String)} as it
     * is more resilient to cross-platform testing.
     *
     * @throws IllegalStateException if the current directory of the test is not a subdirectory of
     *     the workspace directory when this method is called. This shouldn't happen if the test is
     *     run by Bazel or run by IntelliJ with default configuration settings (where the working
     *     directory is initialized to the module root).
     */
    @NonNull
    public static synchronized File getWorkspaceRoot() {
        // The logic below depends on the current working directory, so we save the results and hope the first call
        // is early enough for the user.dir property to be unchanged.
        if (workspaceRoot == null) {
            // If we are using Bazel (which defines the following env vars), simply use the sandboxed
            // root they provide us.
            String workspace = System.getenv("TEST_WORKSPACE");
            String workspaceParent = System.getenv("TEST_SRCDIR");
            if (workspace != null && workspaceParent != null) {
                workspaceRoot = new File(workspaceParent, workspace);
                return workspaceRoot;
            }

            File currDir = new File("");
            File initialDir = currDir;

            // If we're using a non-Bazel build system. At this point, assume our working
            // directory is located underneath our codebase's root folder, so keep navigating up until
            // we find it. If we're using Bazel, we should still look to see if there's a larger
            // outermost workspace since we might be within a nested workspace.
            while (currDir != null) {
                currDir = currDir.getAbsoluteFile();
                if (new File(currDir, "WORKSPACE").exists()) {
                    workspaceRoot = currDir;
                }
                currDir = currDir.getParentFile();
            }

            if (workspaceRoot == null) {
                throw new IllegalStateException(
                        "Could not find WORKSPACE root. Is the original working directory a "
                                + "subdirectory of the Android Studio codebase?\n\n"
                                + "pwd = "
                                + initialDir.getAbsolutePath());
            }
        }

        return workspaceRoot;
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
     */
    @NonNull
    public static File getWorkspaceFile(@NonNull String path) {
        File f = new File(getWorkspaceRoot(), path);

        if (!f.exists() && OsType.getHostOs() == OsType.WINDOWS) {
            // This file may be a binary with a .exe extension
            f = new File(f.getPath() + ".exe");
        }

        if (!f.exists()) {
            throw new IllegalArgumentException("File \"" + path + "\" not found at \"" + getWorkspaceRoot() + "\"");
        }

        return getWindowsShortNameFile(f);
    }

    /**
     * @return a directory which tests can output data to. If running through Bazel's test runner,
     *     this returns the directory as specified by the TEST_UNDECLARED_OUTPUT_DIR environment
     *     variable. Data written to this directory will be zipped and made available under the
     *     WORKSPACE_ROOT/bazel-testlogs/ after the test completes. For non-Bazel runs, this
     *     currently returns a tmp directory that is deleted on exit.
     */
    @NonNull
    public static File getTestOutputDir() {
        // If running via bazel, returns the sandboxed test output dir.
        String testOutputDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
        if (testOutputDir != null) {
            return new File(testOutputDir);
        }

        return createTempDirDeletedOnExit();
    }

    /**
     * Returns a file at {@code path} relative to the root for {@link #getLatestAndroidPlatform}.
     *
     * @throws IllegalStateException if the current OS is not supported.
     * @throws IllegalArgumentException if the path results in a file not found.
     */
    @NonNull
    public static File getPlatformFile(String path) {
        String latestAndroidPlatform = getLatestAndroidPlatform();
        File file =
                FileUtils.join(getSdk(), SdkConstants.FD_PLATFORMS, latestAndroidPlatform, path);
        if (!file.exists()) {
            throw new IllegalArgumentException(
                    "File \"" + path + "\" not found in platform " + latestAndroidPlatform);
        }
        return file;
    }

    /** Checks if tests were started by Bazel. */
    public static boolean runningFromBazel() {
        return System.getenv().containsKey("TEST_WORKSPACE");
    }

    /**
     * Returns the SDK directory relative to the workspace.
     *
     * @throws IllegalStateException if the current OS is not supported.
     * @throws IllegalArgumentException if the path results in a file not found.
     * @return a valid File object pointing at the SDK directory.
     */
    @NonNull
    public static String getRelativeSdk() {
        OsType osType = OsType.getHostOs();
        if (osType == OsType.UNKNOWN) {
            throw new IllegalStateException(
                    "SDK test not supported on unknown platform: " + OsType.getOsName());
        }

        String hostDir = osType.getFolderName();
        return "prebuilts/studio/sdk/" + hostDir;
    }

    /**
     * Returns the SDK directory.
     *
     * @throws IllegalStateException if the current OS is not supported.
     * @throws IllegalArgumentException if the path results in a file not found.
     * @return a valid File object pointing at the SDK directory.
     */
    @NonNull
    public static File getSdk() {
        return getWorkspaceFile(getRelativeSdk());
    }

    /**
     * Returns the path to checked-in NDK.
     *
     * @see #getSdk()
     */
    @NonNull
    public static File getNdk() {
        return new File(getSdk(), SdkConstants.FD_NDK);
    }

    /**
     * Returns the remote SDK directory.
     *
     * @throws IllegalArgumentException if the path results in a file not found.
     * @return a valid File object pointing at the remote SDK directory.
     */
    @NonNull
    public static Path getRemoteSdk() {
        return getWorkspaceFile("prebuilts/studio/sdk/remote/dl.google.com/android/repository")
                .toPath();
    }

    /** Returns the checked in AAPT2 binary that is shipped with the gradle plugin. */
    @NonNull
    public static Path getAapt2() {
        OsType osType = OsType.getHostOs();
        if (osType == OsType.UNKNOWN) {
            throw new IllegalStateException(
                    "AAPT2 not supported on unknown platform: " + OsType.getOsName());
        }
        String hostDir = osType.getFolderName();
        return getWorkspaceFile(
                        "prebuilts/tools/common/aapt/" + hostDir + "/" + SdkConstants.FN_AAPT2)
                .toPath();
    }

    @NonNull
    public static Path getDesugarLibJarWithVersion(String version) {
        return getWorkspaceFile(
                        "prebuilts/tools/common/m2/repository/com/android/tools/desugar_jdk_libs/"
                                + version
                                + "/desugar_jdk_libs-"
                                + version
                                + ".jar")
                .toPath();
    }

    @NonNull
    public static String getLatestAndroidPlatform() {
        return "android-28";
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
        return getDiff(before, after, 0);
    }

    @NonNull
    public static String getDiff(@NonNull String before, @NonNull  String after, int windowSize) {
        return getDiff(before.split("\n"), after.split("\n"), windowSize);
    }

    @NonNull
    public static String getDiff(@NonNull String[] before, @NonNull String[] after) {
        return getDiff(before, after, 0);
    }

    public static String getDiff(@NonNull String[] before, @NonNull String[] after,
            int windowSize) {
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

                if (windowSize > 0) {
                    for (int context = Math.max(0, i - windowSize); context < i; context++) {
                        sb.append("  ");
                        sb.append(before[context]);
                        sb.append("\n");
                    }
                }

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

                if (windowSize > 0) {
                    for (int context = i; context < Math.min(n, i + windowSize); context++) {
                        sb.append("  ");
                        sb.append(before[context]);
                        sb.append("\n");
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

    /**
     * Launches a new process to execute the class {@code toRun} main() method and blocks until the
     * process exits.
     *
     * @param toRun the class whose main() method will be executed in a new process.
     * @param args the arguments for the class {@code toRun} main() method
     * @throws RuntimeException if any (checked or runtime) exception occurs or the process returns
     *     an exit value other than 0
     */
    public static void launchProcess(@NonNull Class toRun, String... args) {
        List<String> commandAndArgs = new LinkedList<>();
        commandAndArgs.add(FileUtils.join(System.getProperty("java.home"), "bin", "java"));
        commandAndArgs.add("-cp");
        commandAndArgs.add(System.getProperty("java.class.path"));
        commandAndArgs.add(toRun.getName());
        commandAndArgs.addAll(Arrays.asList(args));

        ProcessBuilder processBuilder = new ProcessBuilder(commandAndArgs);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException(
                    "Process returned non-zero exit value: " + process.exitValue());
        }
    }

    // disable tests when running on Windows in Bazel.
    public static void disableIfOnWindowsWithBazel() {
        assumeFalse(
                (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
                        && System.getenv("TEST_TMPDIR") != null);
    }
}
