/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.UnusedResourceDetector
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location.Companion.create
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

class WarningTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return UnusedResourceDetector()
    }

    override fun isEnabled(issue: Issue): Boolean {
        return true
    }

    fun testComparator() {
        val projectDir = getProjectDir(
            null,  // Rename .txt files to .java
            java(
                """
                package my.pgk;

                class Test {
                   private static String s = " R.id.button1 \" "; // R.id.button1 should not be considered referenced
                   static {
                       System.out.println(R.id.button2);
                       char c = '"';
                       System.out.println(R.id.linearLayout1);
                   }
                }
                """
            ).indented(),
            java(
                """
                package my.pkg;
                public final class R {
                    public static final class attr {
                    }
                    public static final class drawable {
                        public static final int ic_launcher=0x7f020000;
                    }
                    public static final class id {
                        public static final int button1=0x7f050000;
                        public static final int button2=0x7f050004;
                        public static final int imageView1=0x7f050003;
                        public static final int include1=0x7f050005;
                        public static final int linearLayout1=0x7f050001;
                        public static final int linearLayout2=0x7f050002;
                    }
                    public static final class layout {
                        public static final int main=0x7f030000;
                        public static final int other=0x7f030001;
                    }
                    public static final class string {
                        public static final int app_name=0x7f040001;
                        public static final int hello=0x7f040000;
                    }
                }
                """
            ).indented(),
            manifest().minSdk(14),
            xml(
                "res/layout/accessibility.xml",
                """

                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/newlinear" android:orientation="vertical" android:layout_width="match_parent" android:layout_height="match_parent">
                    <Button android:text="Button" android:id="@+id/button1" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                    <ImageView android:id="@+id/android_logo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    <ImageButton android:importantForAccessibility="yes" android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    <Button android:text="Button" android:id="@+id/button2" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                    <Button android:id="@+android:id/summary" android:contentDescription="@string/label" />
                    <ImageButton android:importantForAccessibility="no" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                </LinearLayout>
                """
            ).indented()
        )
        val warningsHolder =
            AtomicReference<List<Warning>>()
        val lintClient: TestLintClient = object : TestLintClient() {
            override fun analyze(files: List<File>): String {
                val lintRequest = LintRequest(this, files)
                lintRequest.setScope(getLintScope(files))
                driver = LintDriver(
                    CustomIssueRegistry(),
                    this,
                    lintRequest
                )
                configureDriver(driver)
                driver.analyze()
                warningsHolder.set(warnings)
                return "<unused>"
            }
        }
        val files = listOf(projectDir)
        lintClient.analyze(files)
        val warnings = warningsHolder.get()
        var prev: Warning? = null
        for (warning in warnings) {
            if (prev != null) {
                val equals = warning.equals(prev)
                assertEquals(equals, prev.equals(warning))
                val compare = warning.compareTo(prev)
                assertEquals(equals, compare == 0)
                assertEquals(-compare, prev.compareTo(warning))
            }
            prev = warning
        }
        Collections.sort(warnings)
        var prev2 = prev
        prev = null
        for (warning in warnings) {
            if (prev != null && prev2 != null) {
                assertTrue(warning.compareTo(prev) > 0)
                assertTrue(prev.compareTo(prev2) > 0)
                assertTrue(warning.compareTo(prev2) > 0)
                assertTrue(prev.compareTo(warning) < 0)
                assertTrue(prev2.compareTo(prev) < 0)
                assertTrue(prev2.compareTo(warning) < 0)
            }
            prev2 = prev
            prev = warning
        }

        // Regression test for https://issuetracker.google.com/146824833
        val warning1 = warnings[0]
        val location1 = warning1.location
        val location2 =
            create(
                location1.file,
                DefaultPosition(
                    location1.start!!.line,
                    location1.start!!.column + 1,
                    location1.start!!.offset + 1
                ),
                DefaultPosition(
                    location1.end!!.line,
                    location1.end!!.column + 1,
                    location1.end!!.offset + 1
                )
            )
        val warning2 = Warning(
            lintClient,
            warning1.issue,
            warning1.message,
            warning1.severity,
            warning1.project,
            location2,
            warning1.fileContents,
            warning1.errorLine,
            warning1.fix,
            warning1.displayPath
        )

        // Make position on same line but shifted one char to the right; should not equal!
        assertTrue(warning2.compareTo(warning1) > 0)
        assertTrue(warning1.compareTo(warning2) < 0)
        val secondary1 =
            create(
                location1.file,
                location1.start!!,
                location1.end
            )
        var secondary2 =
            create(
                location1.file,
                location1.start!!,
                location1.end
            )
        location1.secondary = secondary1
        warning2.location.secondary = secondary2
        assertTrue(warning2.compareTo(warning1) > 0)
        assertTrue(warning1.compareTo(warning2) < 0)
        secondary2 = create(
            File(location1.file.parentFile, "_before"),
            location1.start!!,
            location1.end
        )
        warning2.location.secondary = secondary2
        assertTrue(warning2.compareTo(warning1) > 0)
        assertTrue(warning1.compareTo(warning2) < 0)
    }

    override fun allowCompilationErrors(): Boolean {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true
    }
}
