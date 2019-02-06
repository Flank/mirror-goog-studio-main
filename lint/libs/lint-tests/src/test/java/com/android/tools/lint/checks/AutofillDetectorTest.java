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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

public class AutofillDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new AutofillDetector();
    }

    public void testBelow26WithoutAutofillHints() {
        lint().files(
                        manifest().targetSdk(25),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }

    public void testWithAutofillHints() {
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:autofillHints=\"username\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }

    public void testWithoutAutofillHints() {
        String expected =
                ""
                        + "res/layout/autofill.xml:6: Warning: Missing autofillHints attribute [Autofill]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "0 errors, 1 warnings";
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected)
                .verifyFixes()
                .window(2)
                .expectFixDiffs(
                        ""
                                + "Fix for res/layout/autofill.xml line 6: Set autofillHints:\n"
                                + "@@ -11 +11\n"
                                + "          android:layout_width=\"match_parent\"\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "+         android:autofillHints=\"|\"\n"
                                + "          android:hint=\"hint\"\n"
                                + "          android:inputType=\"password\" >\n"
                                + "Fix for res/layout/autofill.xml line 6: Set importantForAutofill=\"no\":\n"
                                + "@@ -12 +12\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "          android:hint=\"hint\"\n"
                                + "+         android:importantForAutofill=\"no\"\n"
                                + "          android:inputType=\"password\" >\n"
                                + "  \n");
    }

    public void testImportantForAutofillNo() {
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:importantForAutofill=\"no\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }

    public void testImportantForAutofillNoExcludeDescendants() {
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:importantForAutofill=\"noExcludeDescendants\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }

    // Test that setting importantForAutoFill to "yes" on a view overrides parent's
    // attribute to exclude descendants from autofill.
    public void testImportantForAutofillYes() {
        String expected =
                ""
                        + "res/layout/autofill.xml:7: Warning: Missing autofillHints attribute [Autofill]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "0 errors, 1 warnings";
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:importantForAutofill=\"noExcludeDescendants\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:importantForAutofill=\"yes\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected)
                .verifyFixes()
                .window(2)
                .expectFixDiffs(
                        ""
                                + "Fix for res/layout/autofill.xml line 7: Set autofillHints:\n"
                                + "@@ -12 +12\n"
                                + "          android:layout_width=\"match_parent\"\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "+         android:autofillHints=\"|\"\n"
                                + "          android:hint=\"hint\"\n"
                                + "          android:importantForAutofill=\"yes\"\n"
                                + "Fix for res/layout/autofill.xml line 7: Set importantForAutofill=\"no\":\n"
                                + "@@ -13 +13\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "          android:hint=\"hint\"\n"
                                + "-         android:importantForAutofill=\"yes\"\n"
                                + "+         android:importantForAutofill=\"no\"\n"
                                + "          android:inputType=\"password\" >\n"
                                + "  \n");
    }

    // Test that setting importantForAutoFill to "yesExcluideDescendants" on a view overrides
    // parent's attribute to exclude descendants from autofill.
    public void testChildYesExcludeDescendants() {
        String expected =
                ""
                        + "res/layout/autofill.xml:7: Warning: Missing autofillHints attribute [Autofill]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "0 errors, 1 warnings";
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:importantForAutofill=\"noExcludeDescendants\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "              android:importantForAutofill=\"yesExcludeDescendants\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected);
    }

    // Tests that if a parent is importantForAutofill="no", the child is still responsible for
    // handling autofill.
    public void testParentImportantForAutofillNo() {
        String expected =
                ""
                        + "res/layout/autofill.xml:7: Warning: Missing autofillHints attribute [Autofill]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "0 errors, 1 warnings";
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:importantForAutofill=\"no\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected)
                .verifyFixes()
                .window(2)
                .expectFixDiffs(
                        ""
                                + "Fix for res/layout/autofill.xml line 7: Set autofillHints:\n"
                                + "@@ -12 +12\n"
                                + "          android:layout_width=\"match_parent\"\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "+         android:autofillHints=\"|\"\n"
                                + "          android:hint=\"hint\"\n"
                                + "          android:inputType=\"password\" >\n"
                                + "Fix for res/layout/autofill.xml line 7: Set importantForAutofill=\"no\":\n"
                                + "@@ -13 +13\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "          android:hint=\"hint\"\n"
                                + "+         android:importantForAutofill=\"no\"\n"
                                + "          android:inputType=\"password\" >\n"
                                + "  \n");
        ;
    }

    // Tests that if a parent is importantForAutofill="noExcludeDescendants", the child is not
    // responsible for handling autofill.
    public void testParentImportantForAutofillNoExcludesDescendants() {
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:importantForAutofill=\"noExcludeDescendants\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }

    // Tests that if a parent is importantForAutofill="yesExcludeDescendants", the child is not
    // responsible for handling autofill.
    public void testParentImportantForAutofillYesExcludeDescendants() {
        lint().files(
                        manifest().targetSdk(26),
                        xml(
                                "res/layout/autofill.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:importantForAutofill=\"yesExcludeDescendants\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/usernameField\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:hint=\"hint\"\n"
                                        + "            android:inputType=\"password\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }
}
