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

import static com.android.tools.lint.LintCliFlags.ERRNO_CREATED_BASELINE;
import static com.android.tools.lint.LintCliFlags.ERRNO_ERRORS;
import static com.android.tools.lint.LintCliFlags.ERRNO_EXISTS;
import static com.android.tools.lint.LintCliFlags.ERRNO_INVALID_ARGS;
import static com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS;

import com.android.SdkConstants;
import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.AccessibilityDetector;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import org.intellij.lang.annotations.Language;

@SuppressWarnings("javadoc")
public class MainTest extends AbstractCheckTest {
    public interface Cleanup {
        String cleanup(String s);
    }

    @Override
    public String cleanup(String result) {
        return super.cleanup(result);
    }

    private void checkDriver(
            String expectedOutput, String expectedError, int expectedExitCode, String[] args) {
        checkDriver(
                expectedOutput,
                expectedError,
                expectedExitCode,
                args,
                MainTest.this::cleanup,
                null);
    }

    public static void checkDriver(
            String expectedOutput,
            String expectedError,
            int expectedExitCode,
            String[] args,
            @Nullable Cleanup cleanup,
            @Nullable LintListener listener) {

        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            System.setOut(new PrintStream(output));
            final ByteArrayOutputStream error = new ByteArrayOutputStream();
            System.setErr(new PrintStream(error));

            Main main =
                    new Main() {
                        @Override
                        protected void initializeDriver(@NonNull LintDriver driver) {
                            super.initializeDriver(driver);
                            if (listener != null) {
                                driver.addLintListener(listener);
                            }
                        }
                    };
            int exitCode = main.run(args);

            String stderr = error.toString();
            if (cleanup != null) {
                stderr = cleanup.cleanup(stderr);
            }
            if (expectedError != null && !expectedError.trim().equals(stderr.trim())) {
                assertEquals(expectedError, stderr); // instead of fail: get difference in output
            }
            if (expectedOutput != null) {
                String stdout = output.toString();
                expectedOutput = StringsKt.trimIndent(expectedOutput);
                stdout = StringsKt.trimIndent(stdout);
                if (cleanup != null) {
                    expectedOutput = cleanup.cleanup(expectedOutput);
                    stdout = cleanup.cleanup(stdout);
                }
                if (!expectedOutput
                        .replace('\\', '/')
                        .trim()
                        .equals(stdout.replace('\\', '/').trim())) {
                    assertEquals(expectedOutput.trim(), stdout.trim());
                }
            }
            assertEquals(expectedExitCode, exitCode);
        } finally {
            System.setOut(previousOut);
            System.setErr(previousErr);
        }
    }

    public void testArguments() throws Exception {
        checkDriver(
                // Expected output
                "\n"
                        + "Scanning MainTest_testArguments: .\n"
                        + "res/layout/accessibility.xml:4: Error: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "res/layout/accessibility.xml:5: Error: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--error",
                    "ContentDescription",
                    "--disable",
                    "LintError",
                    getProjectDir(null, mAccessibility).getPath()
                });
    }

    public void testShowDescription() {
        checkDriver(
                // Expected output
                "NewApi\n"
                        + "------\n"
                        + "Summary: Calling new methods on older versions\n"
                        + "\n"
                        + "Priority: 6 / 10\n"
                        + "Severity: Error\n"
                        + "Category: Correctness\n"
                        + "Vendor: Android Open Source Project\n"
                        + "Contact: https://groups.google.com/g/lint-dev\n"
                        + "Feedback: https://issuetracker.google.com/issues/new?component=192708\n"
                        + "\n"
                        + "This check scans through all the Android API calls in the application and\n"
                        + "warns about any calls that are not available on all versions targeted by this\n"
                        + "application (according to its minimum SDK attribute in the manifest).\n"
                        + "\n"
                        + "If you really want to use this API and don't need to support older devices\n"
                        + "just set the minSdkVersion in your build.gradle or AndroidManifest.xml files.\n"
                        + "\n"
                        + "If your code is deliberately accessing newer APIs, and you have ensured (e.g.\n"
                        + "with conditional execution) that this code will only ever be called on a\n"
                        + "supported platform, then you can annotate your class or method with the\n"
                        + "@TargetApi annotation specifying the local minimum SDK to apply, such as\n"
                        + "@TargetApi(11), such that this check considers 11 rather than your manifest\n"
                        + "file's minimum SDK as the required API level.\n"
                        + "\n"
                        + "If you are deliberately setting android: attributes in style definitions, make\n"
                        + "sure you place this in a values-vNN folder in order to avoid running into\n"
                        + "runtime conflicts on certain devices where manufacturers have added custom\n"
                        + "attributes whose ids conflict with the new ones on later platforms.\n"
                        + "\n"
                        + "Similarly, you can use tools:targetApi=\"11\" in an XML file to indicate that\n"
                        + "the element will only be inflated in an adequate context.\n"
                        + "\n"
                        + "\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--show", "NewApi"});
    }

    public void testShowDescriptionWithUrl() {
        checkDriver(
                ""
                        // Expected output
                        + "SdCardPath\n"
                        + "----------\n"
                        + "Summary: Hardcoded reference to /sdcard\n"
                        + "\n"
                        + "Priority: 6 / 10\n"
                        + "Severity: Warning\n"
                        + "Category: Correctness\n"
                        + "Vendor: Android Open Source Project\n"
                        + "Contact: https://groups.google.com/g/lint-dev\n"
                        + "Feedback: https://issuetracker.google.com/issues/new?component=192708\n"
                        + "\n"
                        + "Your code should not reference the /sdcard path directly; instead use\n"
                        + "Environment.getExternalStorageDirectory().getPath().\n"
                        + "\n"
                        + "Similarly, do not reference the /data/data/ path directly; it can vary in\n"
                        + "multi-user scenarios. Instead, use Context.getFilesDir().getPath().\n"
                        + "\n"
                        + "More information: \n"
                        + "https://developer.android.com/training/data-storage#filesExternal\n"
                        + "\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--show", "SdCardPath"});
    }

    public void testNonexistentLibrary() {
        File fooJar = new File(getTempDir(), "foo.jar");
        checkDriver(
                "",
                "Library /TESTROOT/foo.jar does not exist.\n",

                // Expected exit code
                ERRNO_INVALID_ARGS,

                // Args
                new String[] {"--libraries", fooJar.getPath(), "prj"});
    }

    public void testMultipleProjects() throws Exception {
        File project = getProjectDir(null, jar("libs/classes.jar"));

        checkDriver(
                "",
                "The --sources, --classpath, --libraries and --resources arguments can only be used with a single project\n",

                // Expected exit code
                ERRNO_INVALID_ARGS,

                // Args
                new String[] {
                    "--libraries",
                    new File(project, "libs/classes.jar").getPath(),
                    "--disable",
                    "LintError",
                    project.getPath(),
                    project.getPath()
                });
    }

    public void testCustomResourceDirs() throws Exception {
        File project = getProjectDir(null, mAccessibility2, mAccessibility3);

        checkDriver(
                ""
                        + "\n"
                        + "Scanning MainTest_testCustomResourceDirs: ..\n"
                        + "myres1/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres1/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings\n", // Expected output
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--disable",
                    "LintError",
                    "--resources",
                    new File(project, "myres1").getPath(),
                    "--resources",
                    new File(project, "myres2").getPath(),
                    "--compile-sdk-version",
                    "15",
                    "--java-language-level",
                    "11",
                    project.getPath(),
                });
    }

    public void testPathList() throws Exception {
        File project = getProjectDir(null, mAccessibility2, mAccessibility3);

        checkDriver(
                ""
                        + "\n"
                        + "Scanning MainTest_testPathList: ..\n"
                        + "myres1/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres1/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings\n", // Expected output
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--disable",
                    "LintError",
                    "--resources",
                    // Combine two paths with a single separator here
                    new File(project, "myres1").getPath()
                            + ':'
                            + new File(project, "myres2").getPath(),
                    project.getPath(),
                });
    }

    public void testClassPath() throws Exception {
        File project = getProjectDir(null, manifest().minSdk(1), cipherTestSource, cipherTestClass);
        checkDriver(
                "\n"
                        + "Scanning MainTest_testClassPath: \n"
                        + "src/test/pkg/CipherTest1.java:11: Warning: Potentially insecure random numbers on Android 4.3 and older. Read https://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html for more info. [TrulyRandom]\n"
                        + "        cipher.init(Cipher.WRAP_MODE, key); // FLAG\n"
                        + "               ~~~~\n"
                        + "0 errors, 1 warnings\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "TrulyRandom",
                    "--classpath",
                    new File(project, "bin/classes.jar").getPath(),
                    "--disable",
                    "LintError",
                    project.getPath()
                });
    }

    public void testLibraries() throws Exception {
        File project = getProjectDir(null, manifest().minSdk(1), cipherTestSource, cipherTestClass);
        checkDriver(
                "\nScanning MainTest_testLibraries: \nNo issues found.\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "TrulyRandom",
                    "--libraries",
                    new File(project, "bin/classes.jar").getPath(),
                    "--disable",
                    "LintError",
                    project.getPath()
                });
    }

    public void testCreateBaseline() throws Exception {
        File baseline = File.createTempFile("baseline", "xml");
        //noinspection ResultOfMethodCallIgnored
        baseline.delete(); // shouldn't exist
        assertFalse(baseline.exists());
        //noinspection ConcatenationWithEmptyString
        checkDriver(
                // Expected output
                null,

                // Expected error
                ""
                        + "Created baseline file "
                        + cleanup(baseline.getPath())
                        + "\n"
                        + "\n"
                        + "Also breaking the build in case this was not intentional. If you\n"
                        + "deliberately created the baseline file, re-run the build and this\n"
                        + "time it should succeed without warnings.\n"
                        + "\n"
                        + "If not, investigate the baseline path in the lintOptions config\n"
                        + "or verify that the baseline file has been checked into version\n"
                        + "control.\n"
                        + "\n",

                // Expected exit code
                ERRNO_CREATED_BASELINE,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--baseline",
                    baseline.getPath(),
                    "--sdk-home", // SDK is needed to get version number for the baseline
                    TestUtils.getSdk().toString(),
                    "--disable",
                    "LintError",
                    getProjectDir(null, mAccessibility).getPath()
                });
        assertTrue(baseline.exists());
        //noinspection ResultOfMethodCallIgnored
        baseline.delete();
    }

    public void testUpdateBaseline() throws Exception {
        File baseline = File.createTempFile("baseline", "xml");
        Files.write(
                baseline.toPath(),
                // language=XML
                ("<issues></issues>").getBytes(),
                StandardOpenOption.TRUNCATE_EXISTING);

        checkDriver(
                // Expected output
                "\n"
                        + "Scanning MainTest_testUpdateBaseline: .\n"
                        + "res/layout/accessibility.xml:4: Information: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "res/layout/accessibility.xml:5: Information: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "0 errors, 0 warnings\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_CREATED_BASELINE,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--info",
                    "ContentDescription",
                    "--baseline",
                    baseline.getPath(),
                    "--update-baseline",
                    "--disable",
                    "LintError",
                    getProjectDir(null, mAccessibility).getPath()
                });

        // Skip the first three lines that contain just the version which can change.
        String newBaseline =
                Files.readAllLines(baseline.toPath()).stream()
                        .skip(3)
                        .collect(Collectors.joining("\n"));

        String expected =
                "    <issue\n"
                        + "        id=\"ContentDescription\"\n"
                        + "        message=\"Missing `contentDescription` attribute on image\">\n"
                        + "        <location\n"
                        + "            file=\"res/layout/accessibility.xml\"\n"
                        + "            line=\"4\"/>\n"
                        + "    </issue>\n"
                        + "\n"
                        + "    <issue\n"
                        + "        id=\"ContentDescription\"\n"
                        + "        message=\"Missing `contentDescription` attribute on image\">\n"
                        + "        <location\n"
                        + "            file=\"res/layout/accessibility.xml\"\n"
                        + "            line=\"5\"/>\n"
                        + "    </issue>\n"
                        + "\n"
                        + "</issues>";

        assertEquals(expected, newBaseline);

        baseline.delete();
    }

    /**
     * This test emulates Google3's `android_lint` setup, and catches regression caused by relative
     * path for JAR files.
     *
     * @throws Exception
     */
    public void testRelativePaths() throws Exception {
        // Project with source only
        File project = getProjectDir(null, manifest().minSdk(1), cipherTestSource);

        // Create external jar somewhere outside of project dir.
        File pwd = new File(System.getProperty("user.dir"));
        assertTrue(pwd.isDirectory());
        File classFile = cipherTestClass.createFile(pwd);

        try {
            checkDriver(
                    "\n"
                            + "Scanning MainTest_testRelativePaths: \n"
                            + "src/test/pkg/CipherTest1.java:11: Warning: Potentially insecure random numbers on Android 4.3 and older. Read https://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html for more info. [TrulyRandom]\n"
                            + "        cipher.init(Cipher.WRAP_MODE, key); // FLAG\n"
                            + "               ~~~~\n"
                            + "0 errors, 1 warnings\n",
                    "",

                    // Expected exit code
                    ERRNO_SUCCESS,

                    // Args
                    new String[] {
                        "--check",
                        "TrulyRandom",
                        "--classpath",
                        cipherTestClass.targetRelativePath,
                        "--disable",
                        "LintError",
                        project.getPath()
                    });
        } finally {
            classFile.delete();
        }
    }

    @Override
    protected Detector getDetector() {
        // Sample issue to check by the main driver
        return new AccessibilityDetector();
    }

    public void test_getCleanPath() {
        assertEquals("foo", LintCliClient.getCleanPath(new File("foo")));
        String sep = File.separator;
        assertEquals(
                "foo" + sep + "bar", LintCliClient.getCleanPath(new File("foo" + sep + "bar")));
        assertEquals(sep, LintCliClient.getCleanPath(new File(sep)));
        assertEquals(
                "foo" + sep + "bar",
                LintCliClient.getCleanPath(new File("foo" + sep + "." + sep + "bar")));
        assertEquals("bar", LintCliClient.getCleanPath(new File("foo" + sep + ".." + sep + "bar")));
        assertEquals("", LintCliClient.getCleanPath(new File("foo" + sep + "..")));
        assertEquals("foo", LintCliClient.getCleanPath(new File("foo" + sep + "bar" + sep + "..")));
        assertEquals(
                "foo" + sep + ".foo" + sep + "bar",
                LintCliClient.getCleanPath(new File("foo" + sep + ".foo" + sep + "bar")));
        assertEquals(
                "foo" + sep + "bar",
                LintCliClient.getCleanPath(new File("foo" + sep + "bar" + sep + ".")));
        assertEquals(
                "foo" + sep + "...", LintCliClient.getCleanPath(new File("foo" + sep + "...")));
        assertEquals(".." + sep + "foo", LintCliClient.getCleanPath(new File(".." + sep + "foo")));
        assertEquals(sep + "foo", LintCliClient.getCleanPath(new File(sep + "foo")));
        assertEquals(sep, LintCliClient.getCleanPath(new File(sep + "foo" + sep + "..")));
        assertEquals(
                sep + "foo",
                LintCliClient.getCleanPath(new File(sep + "foo" + sep + "bar " + sep + "..")));

        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
            assertEquals(sep + "c:", LintCliClient.getCleanPath(new File(sep + "c:")));
            assertEquals(
                    sep + "c:" + sep + "foo",
                    LintCliClient.getCleanPath(new File(sep + "c:" + sep + "foo")));
        }
    }

    public void testGradle() throws Exception {
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        source("build.gradle", ""), // placeholder; only name counts
                        // placeholder to ensure we have .class files
                        source("bin/classes/foo/bar/ApiCallTest.class", ""));
        checkDriver(
                ""
                        + "\n"
                        + "build.gradle: Error: \"MainTest_testGradle\" is a Gradle project. To correctly analyze Gradle projects, you should run \"gradlew lint\" instead. [LintError]\n"
                        + "1 errors, 0 warnings\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--check", "HardcodedText", project.getPath()});
    }

    public void testGradleKts() throws Exception {
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        source("build.gradle.kts", ""), // placeholder; only name counts
                        // placeholder to ensure we have .class files
                        source("bin/classes/foo/bar/ApiCallTest.class", ""));
        checkDriver(
                ""
                        + "\n"
                        + "build.gradle.kts: Error: \"MainTest_testGradleKts\" is a Gradle project. To correctly analyze Gradle projects, you should run \"gradlew lint\" instead. [LintError]\n"
                        + "1 errors, 0 warnings\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--check", "HardcodedText", project.getPath()});
    }

    public void testWall() throws Exception {
        File project = getProjectDir(null, java("class Test {\n    // STOPSHIP\n}"));
        checkDriver(
                ""
                        + "Scanning MainTest_testWall: ..\n"
                        + "Scanning MainTest_testWall (Phase 2): .\n"
                        + "src/Test.java:2: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                        + "    // STOPSHIP\n"
                        + "       ~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "-Wall", "--disable", "LintError,UsesMinSdkAttributes", project.getPath()
                });
    }

    public void testWerror() throws Exception {
        File project =
                getProjectDir(null, java("class Test {\n    String s = \"/sdcard/path\";\n}"));
        checkDriver(
                ""
                        + "Scanning MainTest_testWerror: ..\n"
                        + "src/Test.java:2: Error: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    String s = \"/sdcard/path\";\n"
                        + "               ~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "-Werror", "--disable", "LintError,UsesMinSdkAttributes", project.getPath()
                });
    }

    public void testNoWarn() throws Exception {
        File project =
                getProjectDir(
                        null,
                        java("" + "class Test {\n    String s = \"/sdcard/path\";\n}"),
                        xml(
                                "res/layout/test.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "</LinearLayout>\n"));
        checkDriver(
                ""
                        + "Scanning MainTest_testNoWarn: ....\n"
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "-w", "--disable", "LintError,UsesMinSdkAttributes", project.getPath()
                });
    }

    public void testWarningsAsErrors() throws Exception {
        // Regression test for 177439519
        // The scenario is that we have warningsAsErrors turned on in an override
        // configuration, and then lintConfig pointing to a lint.xml file which
        // ignores some lint checks. We want the ignored lint checks to NOT be
        // turned on on as errors. We also want any warning-severity issue not
        // otherwise mentioned to turn into errors.
        File project =
                getProjectDir(
                        null,
                        java("class Test {\n    String s = \"/sdcard/path\";\n}"),
                        xml(
                                "res/layout/foo.xml",
                                ""
                                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                                        + "</merge>\n"));
        File lintXml = new File(project, "res" + File.separator + "lint.xml");
        FilesKt.writeText(
                lintXml,
                ""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        + "<lint>\n"
                        + "    <issue id=\"HardcodedText\" severity=\"ignore\"/>\n"
                        + "</lint>",
                Charsets.UTF_8);
        checkDriver(
                ""
                        + "src/Test.java:2: Error: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    String s = \"/sdcard/path\";\n"
                        + "               ~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "-Werror",
                    "--quiet",
                    "--disable",
                    "LintError,UsesMinSdkAttributes,UnusedResources",
                    project.getPath()
                });
    }

    public void testWrongThreadOff() throws Exception {
        // Make sure the wrong thread interprocedural check is not included with -Wall
        File project =
                getProjectDir(
                        null,
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import androidx.annotation.UiThread;\n"
                                        + "import androidx.annotation.WorkerThread;\n"
                                        + "\n"
                                        + "@FunctionalInterface\n"
                                        + "public interface Runnable {\n"
                                        + "  public abstract void run();\n"
                                        + "}\n"
                                        + "\n"
                                        + "class Test {\n"
                                        + "  @UiThread static void uiThreadStatic() { unannotatedStatic(); }\n"
                                        + "  static void unannotatedStatic() { workerThreadStatic(); }\n"
                                        + "  @WorkerThread static void workerThreadStatic() {}\n"
                                        + "\n"
                                        + "  @UiThread void uiThread() { unannotated(); }\n"
                                        + "  void unannotated() { workerThread(); }\n"
                                        + "  @WorkerThread void workerThread() {}\n"
                                        + "\n"
                                        + "  @UiThread void runUi() {}\n"
                                        + "  void runIt(Runnable r) { r.run(); }\n"
                                        + "  @WorkerThread void callRunIt() {\n"
                                        + "    runIt(() -> runUi());\n"
                                        + "  }\n"
                                        + "\n"
                                        + "  public static void main(String[] args) {\n"
                                        + "    Test instance = new Test();\n"
                                        + "    instance.uiThread();\n"
                                        + "  }\n"
                                        + "}\n"),
                        SUPPORT_ANNOTATIONS_JAR);
        checkDriver(
                ""
                        + "Scanning MainTest_testWrongThreadOff: ..\n"
                        + "Scanning MainTest_testWrongThreadOff (Phase 2): .\n"
                        + "No issues found.",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "-Wall", "--disable", "LintError,UsesMinSdkAttributes", project.getPath()
                });
    }

    public void testInvalidLintXmlId() throws Exception {
        // Regression test for
        // 37070812: Lint does not fail when invalid issue ID is referenced in XML
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        xml(
                                "lint.xml",
                                ""
                                        + "<lint>\n"
                                        + "    <issue id=\"all\" severity=\"warning\" />\n"
                                        + "    <issue id=\"UnknownIssueId\" severity=\"error\" />\n"
                                        + "    <issue id=\"SomeUnknownId\" severity=\"fatal\" />\n"
                                        + "    <issue id=\"Security\" severity=\"fatal\" />\n"
                                        + "    <issue id=\"Interoperability\" severity=\"ignore\" />\n"
                                        + "    <issue id=\"IconLauncherFormat\">\n"
                                        + "        <ignore path=\"src/main/res/mipmap-anydpi-v26/ic_launcher.xml\" />\n"
                                        + "        <ignore path=\"src/main/res/drawable/ic_launcher_foreground.xml\" />\n"
                                        + "        <ignore path=\"src/main/res/drawable/ic_launcher_background.xml\" />\n"
                                        + "    </issue>"
                                        + "</lint>"),
                        // placeholder to ensure we have .class files
                        source("bin/classes/foo/bar/ApiCallTest.class", ""));
        checkDriver(
                ""
                        + "Scanning MainTest_testInvalidLintXmlId: \n"
                        + "lint.xml:4: Error: Unknown issue id \"SomeUnknownId\". Did you mean 'UnknownId' (Reference to an unknown id) ? [UnknownIssueId]\n"
                        + "    <issue id=\"SomeUnknownId\" severity=\"fatal\" />\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--check", "HardcodedText", project.getPath()});
    }

    public void testFatalOnly() throws Exception {
        // This is a lint infrastructure test to make sure we correctly include issues
        // with fatal only
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        xml(
                                "lint.xml",
                                ""
                                        + "<lint>\n"
                                        + "    <issue id=\"DuplicateDefinition\" severity=\"fatal\"/>\n"
                                        + "</lint>\n"),
                        xml(
                                "res/layout/test.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "</LinearLayout>\n"),
                        xml(
                                "res/values/duplicates.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "</resources>\n"),
                        kotlin("val path = \"/sdcard/path\""));

        // Without --fatalOnly: Both errors and warnings are reported.
        checkDriver(
                ""
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here\n"
                        + "res/values/duplicates.xml:3: Error: name has already been defined in this folder [DuplicateDefinition]\n"
                        + "    <item type=\"id\" name=\"name\" />\n"
                        + "                    ~~~~~~~~~~~\n"
                        + "    res/values/duplicates.xml:2: Previously defined here\n"
                        + "src/test.kt:1: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "val path = \"/sdcard/path\"\n"
                        + "            ~~~~~~~~~~~~\n"
                        + "res/layout/test.xml:1: Warning: The resource R.layout.test appears to be unused [UnusedResources]\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "^\n"
                        + "2 errors, 2 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--quiet",
                    "--disable",
                    "LintError,UsesMinSdkAttributes,ButtonStyle,AllowBackup",
                    project.getPath()
                });

        // WITH --fatalOnly: Only the DuplicateDefinition issue is flagged, since it is fatal.
        checkDriver(
                // Both an implicitly fatal issue (DuplicateIds) and an error severity issue
                // configured to be fatal via lint.xml (DuplicateDefinition)
                ""
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here\n"
                        + "res/values/duplicates.xml:3: Error: name has already been defined in this folder [DuplicateDefinition]\n"
                        + "    <item type=\"id\" name=\"name\" />\n"
                        + "                    ~~~~~~~~~~~\n"
                        + "    res/values/duplicates.xml:2: Previously defined here\n"
                        + "2 errors, 0 warnings",
                "",
                ERRNO_ERRORS,

                // Args
                new String[] {
                    "--quiet",
                    "--disable",
                    "LintError",
                    "--disable",
                    "UsesMinSdkAttributes",
                    "--fatalOnly",
                    "--exitcode",
                    project.getPath()
                });
    }

    public void testPrintFirstError() throws Exception {
        // Regression test for 183625575: Lint tasks doesn't output errors anymore
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        xml(
                                "lint.xml",
                                ""
                                        + "<lint>\n"
                                        + "    <issue id=\"DuplicateDefinition\" severity=\"fatal\"/>\n"
                                        + "</lint>\n"),
                        xml(
                                "res/layout/test.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "</LinearLayout>\n"),
                        xml(
                                "res/values/duplicates.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "</resources>\n"),
                        kotlin("val path = \"/sdcard/path\""));

        File html = File.createTempFile("report", "html");
        html.deleteOnExit();

        checkDriver(
                ""
                        + "Scanning MainTest_testPrintFirstError: ......\n"
                        + "Scanning MainTest_testPrintFirstError (Phase 2): ...\n"
                        + "Wrote HTML report to file://report.html\n"
                        + "Lint found 2 errors and 5 warnings. First failure:\n"
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here",
                "",
                ERRNO_ERRORS,

                // Args
                new String[] {
                    // "--quiet",
                    "--disable",
                    "LintError",
                    "--html",
                    html.getPath(),
                    "--disable",
                    "UsesMinSdkAttributes",
                    "--exitcode",
                    "--disable", // Test 182321297
                    "UnknownIssueId",
                    "--enable",
                    "SomeUnknownId",
                    project.getPath()
                },
                s -> s.replace(html.getPath(), "report.html"),
                null);
    }

    public void testValidateOutput() throws Exception {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            // This test relies on making directories not writable, then
            // running lint pointing the output to that directory
            // and checking that error messages make sense. This isn't
            // supported on Windows; calling file.setWritable(false) returns
            // false; so skip this entire test on Windows.
            return;
        }
        File project = getProjectDir(null, mAccessibility2);

        File outputDir = new File(project, "build");
        assertTrue(outputDir.mkdirs());
        assertTrue(outputDir.setWritable(true));

        checkDriver(
                "Scanning MainTest_testValidateOutput: .\n", // Expected output
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--sdk-home", // SDK is needed to get version number for the baseline
                    TestUtils.getSdk().toString(),
                    "--text",
                    new File(outputDir, "foo2.text").getPath(),
                    project.getPath(),
                });

        //noinspection ResultOfMethodCallIgnored
        boolean disabledWrite = outputDir.setWritable(false);
        assertTrue(disabledWrite);

        checkDriver(
                "", // Expected output
                "Cannot write XML output file /TESTROOT/build/foo.xml\n", // Expected error

                // Expected exit code
                ERRNO_EXISTS,

                // Args
                new String[] {
                    "--xml", new File(outputDir, "foo.xml").getPath(), project.getPath(),
                });

        checkDriver(
                "", // Expected output
                "Cannot write HTML output file /TESTROOT/build/foo.html\n", // Expected error

                // Expected exit code
                ERRNO_EXISTS,

                // Args
                new String[] {
                    "--html", new File(outputDir, "foo.html").getPath(), project.getPath(),
                });

        checkDriver(
                "", // Expected output
                "Cannot write text output file /TESTROOT/build/foo.text\n", // Expected error

                // Expected exit code
                ERRNO_EXISTS,

                // Args
                new String[] {
                    "--text", new File(outputDir, "foo.text").getPath(), project.getPath(),
                });
    }

    public void testVersion() throws Exception {
        File project = getProjectDir(null, manifest().minSdk(1));
        checkDriver(
                "lint: version " + Version.ANDROID_GRADLE_PLUGIN_VERSION + "\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--version", "--check", "HardcodedText", project.getPath()});
    }

    @Override
    protected boolean isEnabled(Issue issue) {
        return true;
    }

    @Language("XML")
    private static final String ACCESSIBILITY_XML =
            ""
                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                    + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                    + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                    + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                    + "    <Button android:text=\"Button\" android:id=\"@+id/button2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                    + "    <Button android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                    + "    <ImageButton android:importantForAccessibility=\"no\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                    + "</LinearLayout>\n";

    private final TestFile mAccessibility = xml("res/layout/accessibility.xml", ACCESSIBILITY_XML);

    private final TestFile mAccessibility2 =
            xml("myres1/layout/accessibility1.xml", ACCESSIBILITY_XML);

    private final TestFile mAccessibility3 =
            xml("myres2/layout/accessibility1.xml", ACCESSIBILITY_XML);

    @SuppressWarnings("all") // Sample code
    private TestFile cipherTestSource =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import java.security.Key;\n"
                            + "import java.security.SecureRandom;\n"
                            + "\n"
                            + "import javax.crypto.Cipher;\n"
                            + "\n"
                            + "@SuppressWarnings(\"all\")\n"
                            + "public class CipherTest1 {\n"
                            + "    public void test1(Cipher cipher, Key key) {\n"
                            + "        cipher.init(Cipher.WRAP_MODE, key); // FLAG\n"
                            + "    }\n"
                            + "\n"
                            + "    public void test2(Cipher cipher, Key key, SecureRandom random) {\n"
                            + "        cipher.init(Cipher.ENCRYPT_MODE, key, random);\n"
                            + "    }\n"
                            + "\n"
                            + "    public void setup(String transform) {\n"
                            + "        Cipher cipher = Cipher.getInstance(transform);\n"
                            + "    }\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile cipherTestClass =
            jar(
                    "bin/classes.jar",
                    base64gzip(
                            "test/pkg/CipherTest1.class",
                            ""
                                    + "H4sIAAAAAAAAAI1S227TQBA92zh2kgZSmrTlUqBAgSROa5U+BvFScYkIRSJV"
                                    + "3x13m7pNbMveoOazeCmIBz6Aj0LM2FYSgrnY2pnZ2Zk5M2f3+4+v3wDsY7cE"
                                    + "HVslPMBDFo9YbBt4bOCJgP7c9Vz1QiBXbxwLaAf+iRSodF1PHo5HfRke2f0h"
                                    + "eVa7vmMPj+3Q5X3q1NSZGwmsd5WMlBVcDKwDNzijHNrutQXy7N8TMOvdc/uj"
                                    + "fWk54SRQfhrVjp1WJJ1x6KqJ9VZO2tyD7sTHAmuZWdTqhZwIVDPSBUovLx0Z"
                                    + "KNf3IgNP0xaeCbz+7xYWXD025AfbO/FHSXthbAts/i2SkCOpxgFNkSBbQ9sb"
                                    + "WD0Vut4grlNUVCg69cMRs/tbCI3S88ehI1+5TPXKHLO7HFyGgYKBehkNNFmY"
                                    + "ZbSwI1DLugwqMEN43z+XjiIGZ64pa6l3gSe6an4mAhv1zh9ubT/z5F9kLg+k"
                                    + "6niRsj2HhmxkUZV5b/SE8/Sq+dMgmAqSRdpZpAXpfPMzxCcyllAiqcfOIpZJ"
                                    + "lpMA0tdIC1xHhaI4uYMc/YBh6q0rLM3SS6RByTolcYmtJCwtwdYKbsRlDayi"
                                    + "StG1tLM1WuvYSAGOyKeRLphaa+cKuUWESlyJEZpJ3BShMEUopAhs3cQt6mQe"
                                    + "6zbupFhvaMdd6uYXaO8WkapEQG1uFn2KpGMTdyk3T4sxf53lXlzn/k9RvT9I"
                                    + "XQQAAA=="));
}
