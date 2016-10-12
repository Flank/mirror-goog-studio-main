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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

import com.android.annotations.NonNull;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;

import java.io.File;

/**
 * <b>NOTE: This is not a final API; if you rely on this be prepared to adjust your code for the
 * next tools release.</b>
 */
@SuppressWarnings("javadoc")
public class UnpackedNativeCodeDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new UnpackedNativeCodeDetector();
    }

    @Override
    protected TestLintClient createClient() {
        return new TestLintClient() {
            @NonNull
            @Override
            protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                return new Project(this, dir, referenceDir) {

                    private final GradleVersion GRADLE_VERSION = GradleVersion.parse("2.2.0");

                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Override
                    public int getBuildSdk() {
                        return 23;
                    }

                    @Override
                    public GradleVersion getGradleModelVersion() {
                        return GRADLE_VERSION;
                    }
                };
            }
        };
    }

    /**
     * Test that a manifest without extractNativeLibs produces warnings for Runtime.loadLibrary
     */
    public void testRuntimeLoadLibrary() throws Exception {
        String expected = ""
                + "AndroidManifest.xml:4: Warning: Missing attribute "
                + "android:extractNativeLibs=\"false\" on the "
                + "<application> tag. [UnpackedNativeCode]\n"
                + "    <application android:allowBackup=\"true\">\n"
                + "    ^\n"
                + "0 errors, 1 warnings\n";

        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest package=\"com.example.android.custom-lint-rules\"\n"
                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                        + "\n"
                        + "    <application android:allowBackup=\"true\">\n"
                        + "    </application>\n"
                        + "</manifest>"),
                java("src/test/pkg/Load.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.lang.Runtime;\n"
                        + "\n"
                        + "public class Load {\n"
                        + "    public static void foo() {\n"
                        + "            Runtime.getRuntime().loadLibrary(\"hello\"); \n"
                        + "    }\n"
                        + "}\n"));
        assertEquals(expected, result);
    }

    /**
     * Test that a manifest without extractNativeLibs produces warnings for System.loadLibrary
     */
    public void testSystemLoadLibrary() throws Exception {
        String expected = ""
                + "AndroidManifest.xml:4: Warning: Missing attribute android:extractNativeLibs=\"false\" on the <application> tag. [UnpackedNativeCode]\n"
                + "    <application>\n"
                + "    ^\n"
                + "0 errors, 1 warnings\n";

        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest package=\"com.example.android.custom-lint-rules\"\n"
                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                        + "\n"
                        + "    <application>\n"
                        + "    </application>\n"
                        + "</manifest>"),
                java("src/test/pkg/Load.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.lang.System;\n"
                        + "\n"
                        + "public class Load {\n"
                        + "    public static void foo() {\n"
                        + "            System.loadLibrary(\"hello\"); \n"
                        + "    }\n"
                        + "}\n"));
        assertEquals(expected, result);
    }

    /**
     * Test that a manifest with extractNativeLibs has no warnings.
     */
    public void testHasExtractNativeLibs() throws Exception {
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest package=\"com.example.android.custom-lint-rules\"\n"
                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:extractNativeLibs=\"false\">\n"
                        + "    </application>\n"
                        + "</manifest>"),
                java("src/test/pkg/Load.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.lang.Runtime;\n"
                        + "\n"
                        + "public class Load {\n"
                        + "    public static void foo() {\n"
                        + "            Runtime.getRuntime().loadLibrary(\"hello\"); // ok\n"
                        + "    }\n"
                        + "}\n"));
        assertEquals(expected, result);
    }

    /**
     * Test that supperssing the lint check using tools:ignore works.
     */
    public void testSuppress() throws Exception {
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest package=\"com.example.android.custom-lint-rules\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                        + "\n"
                        + "    <application tools:ignore=\"UnpackedNativeCode\">\n"
                        + "    </application>\n"
                        + "</manifest>"),
                java("src/test/pkg/Load.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.lang.Runtime;\n"
                        + "\n"
                        + "public class Load {\n"
                        + "    public static void foo() {\n"
                        + "            Runtime.getRuntime().loadLibrary(\"hello\"); // ok\n"
                        + "    }\n"
                        + "}\n"));
        assertEquals(expected, result);
    }

}
