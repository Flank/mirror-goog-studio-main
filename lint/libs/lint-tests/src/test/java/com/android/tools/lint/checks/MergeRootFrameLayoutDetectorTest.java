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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class MergeRootFrameLayoutDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new MergeRootFrameLayoutDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    public void testMergeRefFromJava() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/simple.xml:3: Warning: This <FrameLayout> can be replaced with a <merge> tag [MergeRootFrame]\n"
                + "<FrameLayout\n"
                + "^\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                    mSimple,
                    java(""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import foo.bar.R;\n"
                            + "import android.app.Activity;\n"
                            + "import android.os.Bundle;\n"
                            + "\n"
                            + "public class ImportFrameActivity extends Activity {\n"
                            + "    @Override\n"
                            + "    public void onCreate(Bundle savedInstanceState) {\n"
                            + "        super.onCreate(savedInstanceState);\n"
                            + "        setContentView(R.layout.simple);\n"
                            + "    }\n"
                            + "}\n")
                    ));
    }

    public void testMergeRefFromInclude() throws Exception {
        assertEquals(""
                + "res/layout/simple.xml:3: Warning: This <FrameLayout> can be replaced with a <merge> tag [MergeRootFrame]\n"
                + "<FrameLayout\n"
                + "^\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                    mSimple,
                    mSimpleinclude
                    ));
    }

    public void testMergeRefFromIncludeSuppressed() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",
                lintProject(
                        xml("res/layout/simple.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "\n"
                            + "<FrameLayout\n"
                            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    tools:ignore=\"MergeRootFrame\" />\n"),
                        mSimpleinclude
                        ));
    }

    public void testNotIncluded() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(mSimple));
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mSimple = xml("res/layout/simple.xml", ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "\n"
            + "<FrameLayout\n"
            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\" />\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mSimpleinclude = xml("res/layout/simpleinclude.xml", ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:orientation=\"vertical\" >\n"
            + "\n"
            + "    <include\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        layout=\"@layout/simple\" />\n"
            + "\n"
            + "    <Button\n"
            + "        android:id=\"@+id/button1\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"Button\" />\n"
            + "\n"
            + "    <Button\n"
            + "        android:id=\"@+id/button2\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"Button\" />\n"
            + "\n"
            + "</LinearLayout>\n");
}
