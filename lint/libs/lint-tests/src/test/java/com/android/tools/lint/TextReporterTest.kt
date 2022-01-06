/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants.DOT_TXT
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.checks.DuplicateResourceDetector
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.InteroperabilityDetector
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Detector

class TextReporterTest : AbstractCheckTest() {
    fun testBasic() {
        lint().files(
            xml(
                "res/menu/menu.xml",
                """
                    <menu xmlns:android="http://schemas.android.com/apk/res/android" >
                        <item
                            android:id="@+id/item1"
                            android:icon="@drawable/icon1"
                            android:title="My title 1">
                        </item>
                        <item
                            android:id="@+id/item2"
                            android:icon="@drawable/icon2"
                            android:showAsAction="ifRoom"
                            android:title="My title 2">
                        </item>
                    </menu>
                    """
            ).indented(),
            xml(
                "res/values/duplicate-strings.xml",
                """
                    <resources>
                        <string name="app_name">App Name</string>
                        <string name="hello_world">Hello world!</string>
                        <string name="app_name">App Name 1</string>
                        <string name="app_name2">App Name 2</string>

                    </resources>
                    """
            ).indented()
        ).issues(HardcodedValuesDetector.ISSUE, DuplicateResourceDetector.ISSUE).run().expectText(
            """
            res/values/duplicate-strings.xml:4: Error: app_name has already been defined in this folder [DuplicateDefinition]
                <string name="app_name">App Name 1</string>
                        ~~~~~~~~~~~~~~~
                res/values/duplicate-strings.xml:2: Previously defined here
            res/menu/menu.xml:5: Warning: Hardcoded string "My title 1", should use @string resource [HardcodedText]
                    android:title="My title 1">
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/menu/menu.xml:11: Warning: Hardcoded string "My title 2", should use @string resource [HardcodedText]
                    android:title="My title 2">
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 2 warnings
                """,
            LintCliFlags().apply { isShowEverything = true }
        )
    }

    fun testWithExplanations() {
        // Temporarily switch HardcodedValuesDetector.ISSUE to a custom
        // registry with an example vendor to test output of vendor info
        // (which we normally omit for built-in checks)
        HardcodedValuesDetector.ISSUE.vendor = createTestVendor()

        // Some sample errors with secondary locations, multiple incidents of each
        // type to make sure we display the explanations only once per issue type etc
        try {
            lint().files(
                xml(
                    "res/menu/menu.xml",
                    """
                    <menu xmlns:android="http://schemas.android.com/apk/res/android" >
                        <item
                            android:id="@+id/item1"
                            android:icon="@drawable/icon1"
                            android:title="My title 1">
                        </item>
                        <item
                            android:id="@+id/item2"
                            android:icon="@drawable/icon2"
                            android:showAsAction="ifRoom"
                            android:title="My title 2">
                        </item>
                    </menu>
                    """
                ).indented(),
                xml(
                    "res/values/duplicate-strings.xml",
                    """
                    <resources>
                        <string name="app_name">App Name</string>
                        <string name="hello_world">Hello world!</string>
                        <string name="app_name">App Name 1</string>
                        <string name="app_name2">App Name 2</string>

                    </resources>
                    """
                ).indented()
            ).issues(HardcodedValuesDetector.ISSUE, DuplicateResourceDetector.ISSUE).run().expectText(
                """
                res/values/duplicate-strings.xml:4: Error: app_name has already been defined in this folder [DuplicateDefinition]
                    <string name="app_name">App Name 1</string>
                            ~~~~~~~~~~~~~~~
                    res/values/duplicate-strings.xml:2: Previously defined here

                   Explanation for issues of type "DuplicateDefinition":
                   You can define a resource multiple times in different resource folders;
                   that's how string translations are done, for example. However, defining the
                   same resource more than once in the same resource folder is likely an
                   error, for example attempting to add a new resource without realizing that
                   the name is already used, and so on.

                res/menu/menu.xml:5: Warning: Hardcoded string "My title 1", should use @string resource [HardcodedText from mylibrary-1.0]
                        android:title="My title 1">
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/menu/menu.xml:11: Warning: Hardcoded string "My title 2", should use @string resource [HardcodedText from mylibrary-1.0]
                        android:title="My title 2">
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~

                   Explanation for issues of type "HardcodedText":
                   Hardcoding text attributes directly in layout files is bad for several
                   reasons:

                   * When creating configuration variations (for example for landscape or
                   portrait) you have to repeat the actual text (and keep it up to date when
                   making changes)

                   * The application cannot be translated to other languages by just adding
                   new translations for existing string resources.

                   There are quickfixes to automatically extract this hardcoded string into a
                   resource lookup.

                   Vendor: AOSP Unit Tests
                   Identifier: mylibrary-1.0
                   Contact: lint@example.com
                   Feedback: https://example.com/lint/file-new-bug.html

                1 errors, 2 warnings
                """,
                LintCliFlags().apply { isExplainIssues = true }
            )
        } finally {
            HardcodedValuesDetector.ISSUE.vendor = BuiltinIssueRegistry().vendor
        }
    }

    fun testDescribeOptions() {
        lint().files(
            java(
                """
                package other.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Test2 {
                    public Float error4;
                    /** @deprecated */
                    public Float error5;
                }
                """
            ).indented(),
        ).issues(InteroperabilityDetector.PLATFORM_NULLNESS).run().expectText(
            """
            src/other/pkg/Test2.java:5: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Float error4;
                       ~~~~~
            src/other/pkg/Test2.java:7: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Float error5;
                       ~~~~~

               Explanation for issues of type "UnknownNullness":
               To improve referencing this code from Kotlin, consider adding explicit
               nullness information here with either @NonNull or @Nullable.

               https://developer.android.com/kotlin/interop#nullability_annotations

               Available options:

               **ignore-deprecated** (default is false):
               Whether to ignore classes and members that have been annotated with `@Deprecated`.

               Normally this lint check will flag all unannotated elements, but by setting this option to `true` it will skip any deprecated elements.

               To configure this option, use a `lint.xml` file with an <option> like this:

               ```xml
               <lint>
                   <issue id="UnknownNullness">
                       <option name="ignore-deprecated" value="false" />
                   </issue>
               </lint>
               ```

            0 errors, 2 warnings
            """,
            LintCliFlags().apply { isExplainIssues = true }
        )
    }

    private fun TestLintResult.expectText(expected: String, flags: LintCliFlags) {
        expectReported(
            expected, DOT_TXT, { client, file ->
                Reporter.createTextReporter(client, flags, file, file.bufferedWriter(), true)
            }
        )
    }

    override fun getDetector(): Detector = HardcodedValuesDetector()
}

fun createTestVendor(): Vendor {
    return Vendor(
        vendorName = "AOSP Unit Tests",
        contact = "lint@example.com",
        feedbackUrl = "https://example.com/lint/file-new-bug.html",
        identifier = "mylibrary-1.0"
    )
}
