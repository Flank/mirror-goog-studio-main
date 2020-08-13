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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.AnnotationDetectorTest.SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP;
import static com.android.tools.lint.checks.ApiDetector.INLINED;
import static com.android.tools.lint.checks.ApiDetector.UNSUPPORTED;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.checks.infrastructure.ProjectDescription;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import org.intellij.lang.annotations.Language;

public class ApiDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new ApiDetector();
    }

    public void testXmlApi1() {
        String expected =
                ""
                        + "res/color/colors.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                        + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                        + "                                                ^\n"
                        + "res/layout/layout.xml:9: Error: View requires API level 5 (current min is 1): <QuickContactBadge> [NewApi]\n"
                        + "    <QuickContactBadge\n"
                        + "     ~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/layout.xml:15: Error: View requires API level 11 (current min is 1): <CalendarView> [NewApi]\n"
                        + "    <CalendarView\n"
                        + "     ~~~~~~~~~~~~\n"
                        + "res/layout/layout.xml:21: Error: View requires API level 14 (current min is 1): <GridLayout> [NewApi]\n"
                        + "    <GridLayout\n"
                        + "     ~~~~~~~~~~\n"
                        + "res/layout/layout.xml:22: Error: @android:attr/actionBarSplitStyle requires API level 14 (current min is 1) [NewApi]\n"
                        + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/layout.xml:23: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                        + "        bar=\"@android:color/holo_red_light\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/values/themes.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                        + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                        + "                                                ^\n"
                        + "7 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(manifest().minSdk(1), mLayout, mThemes, mThemes2)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testXmlApi2() {
        String expected =
                ""
                        + "res/layout/textureview.xml:8: Error: View requires API level 14 (current min is 1): <TextureView> [NewApi]\n"
                        + "    <TextureView\n"
                        + "     ~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        xml(
                                "res/layout/textureview.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:id=\"@+id/LinearLayout1\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <TextureView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testPropertyAnimator() {
        // Regression test for https://issuetracker.google.com/149416536
        lint().supportResourceRepository(true)
                .files(
                        manifest().minSdk(15),
                        kotlin(
                                        ""
                                                + "package test.pkg;\n"
                                                + "import android.animation.AnimatorInflater\n"
                                                + "import android.app.Activity\n"
                                                + "\n"
                                                + "class MyActivity : Activity() {\n"
                                                + "    fun test() {\n"
                                                + "        AnimatorInflater.loadAnimator(this, R.anim.blink1) // ERROR\n"
                                                + "        AnimatorInflater.loadAnimator(this, R.animator.blink2) // ERROR\n"
                                                + "        AnimatorInflater.loadAnimator(this, R.anim.blink3) // OK\n"
                                                + "    }\n"
                                                + "}")
                                .indented(),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "public class R {\n"
                                        + "    public static class anim {\n"
                                        + "        public static final int blink1 = 0x7f070031;\n"
                                        + "        public static final int blink3 = 0x7f070033;\n"
                                        + "    }\n"
                                        + "    public static class animator {\n"
                                        + "        public static final int blink2 = 0x7f070032;\n"
                                        + "    }\n"
                                        + "}\n"
                                        + ""),
                        xml(
                                        "res/anim/blink1.xml",
                                        ""
                                                + "<objectAnimator xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                                + "    android:interpolator=\"@android:anim/linear_interpolator\"\n"
                                                + "    android:duration=\"@android:integer/config_longAnimTime\"\n"
                                                + "    android:repeatCount=\"-1\"\n"
                                                + "    android:repeatMode=\"reverse\" >\n"
                                                + "\n"
                                                + "    <propertyValuesHolder\n"
                                                + "        android:propertyName=\"fillColor\"\n"
                                                + "        android:valueType=\"colorType\"\n"
                                                + "        android:valueFrom=\"?attr/color1\"\n"
                                                + "        android:valueTo=\"@android:color/white\" />\n"
                                                + "\n"
                                                + "</objectAnimator>\n")
                                .indented(),
                        xml("res/animator-v18/blink2.xml", "<propertyValuesHolder />").indented(),
                        xml("res/anim-v21/blink3.xml", "<propertyValuesHolder />").indented())
                .run()
                .expect(
                        ""
                                + "src/test/pkg/MyActivity.kt:7: Error: The resource anim.blink1 includes the tag propertyValuesHolder which causes crashes on API < 21. Consider switching to AnimatorInflaterCompat.loadAnimator to safely load the animation. [NewApi]\n"
                                + "        AnimatorInflater.loadAnimator(this, R.anim.blink1) // ERROR\n"
                                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MyActivity.kt:8: Error: The resource animator.blink2 includes the tag propertyValuesHolder which causes crashes on API < 21. Consider switching to AnimatorInflaterCompat.loadAnimator to safely load the animation. [NewApi]\n"
                                + "        AnimatorInflater.loadAnimator(this, R.animator.blink2) // ERROR\n"
                                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testNoImports() {
        // We shouldn't be flagging warnings on import statements. It's fine to import
        // whatever you want. It's *usages* that count. And those usages may be
        // guarded by SDK_INT version checks.
        // Regression test for
        //  74128292: Kotlin import flagged for InlinedApi despite constant used correctly
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.os.Build\n"
                                        + "import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR\n"
                                        + "import android.view.Window\n"
                                        + "\n"
                                        + "fun test(window: Window) {\n"
                                        + "    if (Build.VERSION.SDK_INT == 26) {\n"
                                        + "        // This attribute can only be set in code on API 26. It's in our theme XML on 27+.\n"
                                        + "        window.decorView.systemUiVisibility = SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR\n"
                                        + "    }\n"
                                        + "\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testTagWarnings() {
        String expected =
                ""
                        + "res/layout/tag.xml:12: Warning: <tag> is only used in API level 21 and higher (current min is 1) [UnusedAttribute]\n"
                        + "        <tag id=\"@+id/test\" />\n"
                        + "         ~~~\n"
                        + "0 errors, 1 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        xml(
                                "res/layout/tag.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:id=\"@+id/LinearLayout1\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:textSize=\"14dp\">\n"
                                        + "        <tag id=\"@+id/test\" />\n"
                                        + "    </TextView>\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testAttrWithoutSlash() {
        String expected =
                ""
                        + "res/layout/attribute.xml:4: Error: ?android:indicatorStart requires API level 18 (current min is 1) [NewApi]\n"
                        + "    android:enabled=\"?android:indicatorStart\"\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        xml(
                                "res/layout/attribute.xml",
                                ""
                                        + "<Button\n"
                                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:text=\"Hello\"\n"
                                        + "    android:enabled=\"?android:indicatorStart\"\n"
                                        + "    android:layout_width=\"wrap_content\"\n"
                                        + "    android:layout_height=\"wrap_content\"\n"
                                        + "    android:layout_alignParentLeft=\"true\"\n"
                                        + "    android:layout_alignParentStart=\"true\" />\n"
                                        + "\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testUnusedAttributes() {
        String expected =
                ""
                        + "res/layout/divider.xml:9: Warning: Attribute showDividers is only used in API level 11 and higher (current min is 4) [UnusedAttribute]\n"
                        + "    android:showDividers=\"middle\"\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.pkg\" >\n"
                                        + "\n"
                                        + "    <uses-sdk\n"
                                        + "        android:minSdkVersion=\"4\"\n"
                                        + "        android:targetSdkVersion=\"25\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:fullBackupContent=\"true\"\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\"\n"
                                        + "        android:roundIcon=\"@mipmap/ic_launcher_round\"\n"
                                        + "        android:supportsRtl=\"true\"\n"
                                        + "        android:theme=\"@style/AppTheme\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                        + "\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "        <service\n"
                                        + "            android:name=\"MyNavigationService\"\n"
                                        + "            android:foregroundServiceType=\"location\" />\n"
                                        + "\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>"),
                        xml(
                                "res/layout/labelfor.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:id=\"@+id/textView1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:labelFor=\"@+id/editText1\"\n"
                                        + "        android:text=\"Medium Text\"\n"
                                        + "        android:textAppearance=\"?android:attr/textAppearanceMedium\" />\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "        android:id=\"@+id/editText1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:ems=\"10\"\n"
                                        + "        android:inputType=\"textPersonName\" >\n"
                                        + "\n"
                                        + "        <requestFocus />\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:id=\"@+id/textView2\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                                        + "        android:text=\"TextView\" />\n"
                                        + "\n"
                                        + "    <AutoCompleteTextView\n"
                                        + "        android:id=\"@+id/autoCompleteTextView1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:ems=\"10\"\n"
                                        + "        android:text=\"AutoCompleteTextView\" />\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:id=\"@+id/textView3\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                                        + "        android:text=\"Large Text\"\n"
                                        + "        android:textAppearance=\"?android:attr/textAppearanceLarge\" />\n"
                                        + "\n"
                                        + "    <MultiAutoCompleteTextView\n"
                                        + "        android:id=\"@+id/multiAutoCompleteTextView1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:ems=\"10\"\n"
                                        + "        android:text=\"MultiAutoCompleteTextView\" />\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "        android:id=\"@+id/editText2\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:ems=\"10\"\n"
                                        + "        android:inputType=\"textPostalAddress\" />\n"
                                        + "\n"
                                        + "    <AutoCompleteTextView\n"
                                        + "        android:id=\"@+id/autoCompleteTextView2\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:ems=\"10\"\n"
                                        + "        android:text=\"AutoCompleteTextView\" />\n"
                                        + "\n"
                                        + "    <MultiAutoCompleteTextView\n"
                                        + "        android:id=\"@+id/multiAutoCompleteTextView2\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:ems=\"10\"\n"
                                        + "        android:text=\"MultiAutoCompleteTextView\" />\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "        android:id=\"@+id/editText20\"\n"
                                        + "        android:hint=\"Enter your address\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:ems=\"10\"\n"
                                        + "        android:inputType=\"textPostalAddress\" />\n"
                                        + "\n"
                                        + "\n"
                                        + "</LinearLayout>\n"),
                        xml(
                                "res/layout/edit_textview.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <!-- Various attributes that should be set on EditTexts, not TextViews -->\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:text=\"label\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:autoText=\"true\"\n"
                                        + "        android:bufferType=\"editable\"\n"
                                        + "        android:capitalize=\"words\"\n"
                                        + "        android:cursorVisible=\"true\"\n"
                                        + "        android:digits=\"\"\n"
                                        + "        android:editable=\"true\"\n"
                                        + "        android:editorExtras=\"@+id/foobar\"\n"
                                        + "        android:focusable=\"true\"\n"
                                        + "        android:focusableInTouchMode=\"true\"\n"
                                        + "        android:imeActionId=\"@+id/foo\"\n"
                                        + "        android:imeActionLabel=\"\"\n"
                                        + "        android:imeOptions=\"\"\n"
                                        + "        android:inputMethod=\"\"\n"
                                        + "        android:inputType=\"text\"\n"
                                        + "        android:numeric=\"\"\n"
                                        + "        android:password=\"true\"\n"
                                        + "        android:phoneNumber=\"true\"\n"
                                        + "        android:privateImeOptions=\"\" />\n"
                                        + "\n"
                                        + "    <!-- Various attributes that should be set on EditTexts, not Buttons -->\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:cursorVisible=\"true\" />\n"
                                        + "\n"
                                        + "    <CheckedTextView\n"
                                        + "        android:id=\"@+id/checkedTextView\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:cursorVisible=\"true\" />\n"
                                        + "\n"
                                        + "    <CheckBox\n"
                                        + "        android:id=\"@+id/checkbox\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:cursorVisible=\"true\" />\n"
                                        + "\n"
                                        + "    <RadioButton\n"
                                        + "        android:id=\"@+id/radioButton\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:cursorVisible=\"true\" />\n"
                                        + "\n"
                                        + "    <ToggleButton\n"
                                        + "        android:id=\"@+id/toggleButton\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:cursorVisible=\"true\" />\n"
                                        + "\n"
                                        + "\n"
                                        + "    <!-- Ok #1 -->\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:text=\"label\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:bufferType=\"spannable\"\n"
                                        + "        android:freezesText=\"true\"\n"
                                        + "        android:editable=\"false\"\n"
                                        + "        android:inputType=\"none\" />\n"
                                        + "\n"
                                        + "    <!-- Ok #2 -->\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:text=\"label\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:id=\"@+id/dynamictext\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:id=\"@+id/dynamictext\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:paddingHorizontal=\"5dp\"\n"
                                        + "        android:paddingVertical=\"5dp\"\n"
                                        + "        android:layout_marginHorizontal=\"0dp\"\n"
                                        + "        android:layout_marginVertical=\"0dp\"\n"
                                        + "        android:importantForAutofill=\"no\"\n"
                                        + "        android:autofillHints=\"auto\"\n"
                                        + "        android:autofilledHighlight=\"@drawable/exo_controls_pause\"\n"
                                        + "        android:textIsSelectable=\"true\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"),
                        xml(
                                "res/layout/divider.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:layout_marginLeft=\"16dp\"\n"
                                        + "    android:layout_marginRight=\"16dp\"\n"
                                        + "    android:divider=\"?android:dividerHorizontal\"\n"
                                        + "    android:orientation=\"horizontal\"\n"
                                        + "    android:showDividers=\"middle\"\n"
                                        + "    tools:context=\".ItemListActivity\" >\n"
                                        + "\n"
                                        + "    <!--\n"
                                        + "    This layout is a two-pane layout for the Items\n"
                                        + "    flow. See res/values-large/refs.xml and\n"
                                        + "    res/values-sw600dp/refs.xml for an example of layout aliases\n"
                                        + "    that replace the single-pane version of the layout with\n"
                                        + "    this two-pane version.\n"
                                        + "\n"
                                        + "    For more on layout aliases, see:\n"
                                        + "    http://developer.android.com/training/multiscreen/screensizes.html#TaskUseAliasFilters\n"
                                        + "    -->\n"
                                        + "\n"
                                        + "    <fragment\n"
                                        + "        android:id=\"@+id/item_list\"\n"
                                        + "        android:name=\"com.example.main.ItemListFragment\"\n"
                                        + "        android:layout_width=\"0dp\"\n"
                                        + "        android:layout_height=\"match_parent\"\n"
                                        + "        android:layout_weight=\"1\"\n"
                                        + "        tools:layout=\"@android:layout/list_content\" />\n"
                                        + "\n"
                                        + "    <FrameLayout\n"
                                        + "        android:id=\"@+id/item_detail_container\"\n"
                                        + "        android:layout_width=\"0dp\"\n"
                                        + "        android:layout_height=\"match_parent\"\n"
                                        + "        android:layout_weight=\"3\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testUnusedOnSomeVersions1() {
        String expected =
                ""
                        + "res/layout/attribute2.xml:4: Error: switchTextAppearance requires API level 14 (current min is 1), but note that attribute editTextColor is only used in API level 11 and higher [NewApi]\n"
                        + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/attribute2.xml:4: Warning: Attribute editTextColor is only used in API level 11 and higher (current min is 1) [UnusedAttribute]\n"
                        + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(manifest().minSdk(1), mAttribute2)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testUnusedThemeOnIncludeTag() {
        // Regression test for b/32879096: Add lint TargetApi warning for android:theme
        // attribute in <include> tag
        String expected =
                ""
                        + "res/layout/linear.xml:11: Warning: Attribute android:theme is only used by <include> tags in API level 23 and higher (current min is 21) [UnusedAttribute]\n"
                        + "        android:theme=\"@android:style/Theme.Holo\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        lint().files(
                        manifest().minSdk(21),
                        xml(
                                "res/layout/linear.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        layout=\"@layout/included\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:theme=\"@android:style/Theme.Holo\" />\n"
                                        + "\n"
                                        + "</LinearLayout>"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testUnusedForegroundAttribute() {
        // Regression test for b/37137262
        String expected =
                ""
                        + "res/layout/linear.xml:8: Warning: Attribute android:foreground has no effect on API levels lower than 23 (current min is 21) [UnusedAttribute]\n"
                        + "    android:foreground=\"?selectableItemBackground\"\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/linear.xml:25: Warning: Attribute android:foreground has no effect on API levels lower than 23 (current min is 21) [UnusedAttribute]\n"
                        + "  <test.pkg.MyCustomView android:foreground=\"?selectableItemBackground\"\n"
                        + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings";

        lint().files(
                        manifest().minSdk(21),
                        xml(
                                "res/layout/linear.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "  <TextView \n"
                                        + "    android:foreground=\"?selectableItemBackground\"\n"
                                        + "    android:id=\"@+id/title\"\n"
                                        + "    android:layout_width=\"wrap_content\"\n"
                                        + "    android:layout_height=\"wrap_content\"/>\n"
                                        + "\n"

                                        // Regression tests for
                                        // https://issuetracker.google.com/116404240:

                                        + "  <FrameLayout android:foreground=\"?selectableItemBackground\"\n"
                                        + "    android:layout_width=\"wrap_content\"\n"
                                        + "    android:layout_height=\"wrap_content\"/>\n"
                                        + "\n"
                                        + "  <HorizontalScrollView android:foreground=\"?selectableItemBackground\"\n"
                                        + "    android:layout_width=\"wrap_content\"\n"
                                        + "    android:layout_height=\"wrap_content\"/>\n"
                                        + "\n"
                                        + "  <test.pkg.MyFrameView android:foreground=\"?selectableItemBackground\"\n"
                                        + "    android:layout_width=\"wrap_content\"\n"
                                        + "    android:layout_height=\"wrap_content\"/>\n"
                                        + "\n"
                                        + "  <test.pkg.MyCustomView android:foreground=\"?selectableItemBackground\"\n"
                                        + "    android:layout_width=\"wrap_content\"\n"
                                        + "    android:layout_height=\"wrap_content\"/>\n"
                                        + "\n"
                                        + "  <test.pkg.MyUnknownView android:foreground=\"?selectableItemBackground\"\n"
                                        + "    android:layout_width=\"wrap_content\"\n"
                                        + "    android:layout_height=\"wrap_content\"/>\n"
                                        + "\n"
                                        + "</LinearLayout>"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.widget.FrameLayout;\n"
                                        + "\n"
                                        + "/** @noinspection ClassNameDiffersFromFileName*/ "
                                        + "public abstract class MyFrameView extends FrameLayout {\n"
                                        + "    public MyFrameView() {\n"
                                        + "        super(null);\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "/** @noinspection ClassNameDiffersFromFileName*/ "
                                        + "public abstract class MyCustomView extends android.widget.LinearLayout {\n"
                                        + "    public MyCustomView() {\n"
                                        + "        super(null);\n"
                                        + "    }\n"
                                        + "}\n"),
                        // Stub to make evaluator.findClass work from tests
                        java(
                                ""
                                        + "package android.widget;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "\n"
                                        + "/** @noinspection ClassNameDiffersFromFileName*/ "
                                        + "public abstract class FrameLayout {\n"
                                        + "    public FrameLayout(Context context) {\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testUnusedLevelListAttribute() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=214143
        String expected =
                ""
                        + "res/drawable/my_layer.xml:4: Warning: Attribute width is only used in API level 23 and higher (current min is 15) [UnusedAttribute]\n"
                        + "        android:width=\"535dp\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/drawable/my_layer.xml:5: Warning: Attribute height is only used in API level 23 and higher (current min is 15) [UnusedAttribute]\n"
                        + "        android:height=\"235dp\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings\n";
        lint().files(
                        manifest().minSdk(15),
                        xml(
                                "res/drawable/my_layer.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <item\n"
                                        + "        android:width=\"535dp\"\n"
                                        + "        android:height=\"235dp\"\n"
                                        + "        android:drawable=\"@drawable/ic_android_black_24dp\"\n"
                                        + "        android:gravity=\"center\" />\n"
                                        + "</layer-list>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testCustomDrawable() {
        String expected =
                ""
                        + "res/drawable/my_layer.xml:2: Error: Custom drawables requires API level 24 (current min is 15) [NewApi]\n"
                        + "<my.custom.drawable/>\n"
                        + " ~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        lint().files(
                        manifest().minSdk(15),
                        xml(
                                "res/drawable/my_layer.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<my.custom.drawable/>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testCustomDrawableViaClassAttribute() {
        String expected =
                ""
                        + "res/drawable/my_layer.xml:2: Error: <class> requires API level 24 (current min is 15) [NewApi]\n"
                        + "<drawable class=\"my.custom.drawable\"/>\n"
                        + "          ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        manifest().minSdk(15),
                        xml(
                                "res/drawable/my_layer.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<drawable class=\"my.custom.drawable\"/>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testRtlManifestAttribute() {
        // Treat the manifest RTL attribute in the same was as the layout start/end attributes:
        // these are known to be benign on older platforms, so don't flag it.
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.bytecode\">\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"1\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:supportsRtl='true'\n"

                                        // Ditto for the fullBackupContent attribute. If you're
                                        // targeting
                                        // 23, you'll want to use it, but it's not an error that
                                        // older
                                        // platforms aren't looking at it.

                                        + "        android:fullBackupContent='false'\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testXmlApi() {
        String expected =
                ""
                        + "res/layout/attribute2.xml:4: Error: ?android:switchTextAppearance requires API level 14 (current min is 11) [NewApi]\n"
                        + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(manifest().minSdk(11), mAttribute2)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testReportAttributeName() {
        String expected =
                ""
                        + "res/layout/layout.xml:13: Warning: Attribute layout_row is only used in API level 14 and higher (current min is 4) [UnusedAttribute]\n"
                        + "            android:layout_row=\"2\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        xml(
                                "res/layout/layout.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n"
                                        + "\n"
                                        + "    <android.support.v7.widget.GridLayout\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"match_parent\">\n"
                                        + "        <TextView\n"
                                        + "            android:text=\"@string/hello_world\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_row=\"2\"\n"
                                        + "            app:layout_column=\"1\" />\n"
                                        + "    </android.support.v7.widget.GridLayout>\n"
                                        + "</LinearLayout>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testXmlApi14() {
        //noinspection all // Sample code
        lint().files(manifest().minSdk(14), mLayout, mThemes, mThemes2)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testXmlApiIceCreamSandwich() {
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.bytecode\"\n"
                                        + "    android:versionCode=\"1\"\n"
                                        + "    android:versionName=\"1.0\" >\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"IceCreamSandwich\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <activity\n"
                                        + "            android:name=\".BytecodeTestsActivity\"\n"
                                        + "            android:label=\"@string/app_name\" >\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                        + "\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"),
                        mLayout,
                        mThemes,
                        mThemes2)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testXmlApi1TargetApi() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        xml(
                                "res/layout/layout.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:layout_width=\"fill_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\"\n"
                                        + "    tools:targetApi=\"11\" >\n"
                                        + "\n"
                                        + "    <!-- Requires API 5 -->\n"
                                        + "\n"
                                        + "    <QuickContactBadge\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "    <!-- Requires API 11 -->\n"
                                        + "\n"
                                        + "    <CalendarView\n"
                                        + "        android:layout_width=\"fill_parent\"\n"
                                        + "        android:layout_height=\"fill_parent\" />\n"
                                        + "\n"
                                        + "    <!-- Requires API 14 -->\n"
                                        + "\n"
                                        + "    <GridLayout\n"
                                        + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                                        + "        bar=\"@android:color/holo_red_light\"\n"
                                        + "        android:layout_width=\"fill_parent\"\n"
                                        + "        android:layout_height=\"fill_parent\"\n"
                                        + "        tools:targetApi=\"ICE_CREAM_SANDWICH\" >\n"
                                        + "\n"
                                        + "        <Button\n"
                                        + "            android:layout_width=\"fill_parent\"\n"
                                        + "            android:layout_height=\"fill_parent\" />\n"
                                        + "    </GridLayout>\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testXmlApiFolderVersion11() {
        String expected =
                ""
                        + "res/color-v11/colors.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                        + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                        + "                                                ^\n"
                        + "res/layout-v11/layout.xml:21: Error: View requires API level 14 (current min is 1): <GridLayout> [NewApi]\n"
                        + "    <GridLayout\n"
                        + "     ~~~~~~~~~~\n"
                        + "res/layout-v11/layout.xml:22: Error: @android:attr/actionBarSplitStyle requires API level 14 (current min is 1) [NewApi]\n"
                        + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout-v11/layout.xml:23: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                        + "        bar=\"@android:color/holo_red_light\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/values-v11/themes.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                        + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                        + "                                                ^\n"
                        + "5 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(manifest().minSdk(1), mLayout2, mThemes3, mThemes4)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testXmlApiFolderVersion14() {
        //noinspection all // Sample code
        lint().files(manifest().minSdk(1), mLayout3, mThemes5, mThemes6)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testThemeVersion() {
        String expected =
                ""
                        + "res/values/themes3.xml:3: Error: android:Theme.Holo.Light.DarkActionBar requires API level 14 (current min is 4) [NewApi]\n"
                        + "    <style name=\"AppTheme\" parent=\"android:Theme.Holo.Light.DarkActionBar\">\n"
                        + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        xml(
                                "res/values/themes3.xml",
                                ""
                                        + "<resources>\n"
                                        + "\n"
                                        + "    <style name=\"AppTheme\" parent=\"android:Theme.Holo.Light.DarkActionBar\">\n"
                                        + "        <!-- Customize your theme here. -->\n"
                                        + "    </style>\n"
                                        + "\n"
                                        + "</resources>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testApi1() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "src/foo/bar/ApiCallTest.java:33: Warning: Field requires API level 11 (current min is 1): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [InlinedApi]\n"
                        + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 1): android.app.Activity#getActionBar [NewApi]\n"
                        + "  getActionBar(); // API 11\n"
                        + "  ~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:24: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                        + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                        + "                 ~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:27: Error: Call requires API level 3 (current min is 1): android.widget.Chronometer#getOnChronometerTickListener [NewApi]\n"
                        + "  chronometer.getOnChronometerTickListener(); // API 3 \n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 1): android.widget.TextView#setTextIsSelectable [NewApi]\n"
                        + "  chronometer.setTextIsSelectable(true); // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                        + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                        + "                            ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                        + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "6 errors, 1 warnings\n";
        lint().files(manifest().minSdk(1), mApiCallTest)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testApi2() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "src/foo/bar/ApiCallTest.java:33: Warning: Field requires API level 11 (current min is 2): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [InlinedApi]\n"
                        + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 2): android.app.Activity#getActionBar [NewApi]\n"
                        + "  getActionBar(); // API 11\n"
                        + "  ~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:24: Error: Class requires API level 8 (current min is 2): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                        + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                        + "                 ~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:27: Error: Call requires API level 3 (current min is 2): android.widget.Chronometer#getOnChronometerTickListener [NewApi]\n"
                        + "  chronometer.getOnChronometerTickListener(); // API 3 \n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 2): android.widget.TextView#setTextIsSelectable [NewApi]\n"
                        + "  chronometer.setTextIsSelectable(true); // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 2): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                        + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                        + "                            ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 2): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                        + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "6 errors, 1 warnings\n";
        lint().files(manifest().minSdk(2), mApiCallTest)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testApi4() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "src/foo/bar/ApiCallTest.java:33: Warning: Field requires API level 11 (current min is 4): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [InlinedApi]\n"
                        + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 4): android.app.Activity#getActionBar [NewApi]\n"
                        + "  getActionBar(); // API 11\n"
                        + "  ~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:24: Error: Class requires API level 8 (current min is 4): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                        + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                        + "                 ~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 4): android.widget.TextView#setTextIsSelectable [NewApi]\n"
                        + "  chronometer.setTextIsSelectable(true); // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 4): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                        + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                        + "                            ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 4): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                        + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "5 errors, 1 warnings\n";
        lint().files(manifest().minSdk(4), mApiCallTest)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testApi10() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "src/foo/bar/ApiCallTest.java:33: Warning: Field requires API level 11 (current min is 10): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [InlinedApi]\n"
                        + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 10): android.app.Activity#getActionBar [NewApi]\n"
                        + "  getActionBar(); // API 11\n"
                        + "  ~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 10): android.widget.TextView#setTextIsSelectable [NewApi]\n"
                        + "  chronometer.setTextIsSelectable(true); // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 10): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                        + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                        + "                            ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 10): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                        + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "4 errors, 1 warnings\n";
        lint().files(manifest().minSdk(10), mApiCallTest)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testApi14() {
        //noinspection all // Sample code
        lint().files(manifest().minSdk(14), mApiCallTest)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testSuppressTargetApiOnFieldInitializers() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(14),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.TargetApi;\n"
                                        + "import android.os.Build;\n"
                                        + "import android.view.accessibility.AccessibilityNodeInfo;\n"
                                        + "\n"
                                        + "public class FooBar {\n"
                                        + "    @TargetApi(Build.VERSION_CODES.LOLLIPOP)\n"
                                        + "    public static int MY_CONSTANT = AccessibilityNodeInfo.ACTION_SET_TEXT;\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package android.annotation;\n"
                                        + "import static java.lang.annotation.ElementType.*;\n"
                                        + "import java.lang.annotation.*;\n"
                                        + "@Target({TYPE, METHOD, CONSTRUCTOR, FIELD})\n"
                                        + "@Retention(RetentionPolicy.CLASS)\n"
                                        + "public @interface TargetApi {\n"
                                        + "    int value();\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testInheritStatic() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "src/foo/bar/ApiCallTest5.java:16: Error: Call requires API level 11 (current min is 2): android.view.View#resolveSizeAndState [NewApi]\n"
                        + "        int measuredWidth = View.resolveSizeAndState(widthMeasureSpec,\n"
                        + "                                 ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest5.java:18: Error: Call requires API level 11 (current min is 2): android.view.View#resolveSizeAndState [NewApi]\n"
                        + "        int measuredHeight = resolveSizeAndState(heightMeasureSpec,\n"
                        + "                             ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest5.java:20: Error: Call requires API level 11 (current min is 2): android.view.View#combineMeasuredStates [NewApi]\n"
                        + "        View.combineMeasuredStates(0, 0);\n"
                        + "             ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiCallTest5.java:21: Error: Call requires API level 11 (current min is 2): android.view.View#combineMeasuredStates [NewApi]\n"
                        + "        ApiCallTest5.combineMeasuredStates(0, 0);\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "4 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(2),
                        java(
                                ""
                                        + "package foo.bar;\n"
                                        + "\n"
                                        + "import android.annotation.TargetApi;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.view.View;\n"
                                        + "\n"
                                        + "public class ApiCallTest5 extends View {\n"
                                        + "    public ApiCallTest5(Context context) {\n"
                                        + "        super(context);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressWarnings(\"unused\")\n"
                                        + "    @Override\n"
                                        + "    @TargetApi(2)\n"
                                        + "    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {\n"
                                        + "        int measuredWidth = View.resolveSizeAndState(widthMeasureSpec,\n"
                                        + "                widthMeasureSpec, 0);\n"
                                        + "        int measuredHeight = resolveSizeAndState(heightMeasureSpec,\n"
                                        + "                heightMeasureSpec, 0);\n"
                                        + "        View.combineMeasuredStates(0, 0);\n"
                                        + "        ApiCallTest5.combineMeasuredStates(0, 0);\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testInheritLocal() {
        // Test virtual dispatch in a local class which extends some other local class (which
        // in turn extends an Android API)
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest3.java:10: Error: Call requires API level 11 (current min is 1): android.app.Activity#getActionBar [NewApi]\n"
                        + "  getActionBar(); // API 11\n"
                        + "  ~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        mIntermediate,
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "/**\n"
                                        + " * Call test where the parent class is some other project class which in turn\n"
                                        + " * extends the public API\n"
                                        + " */\n"
                                        + "public class ApiCallTest3 extends Intermediate {\n"
                                        + "\tpublic void foo() {\n"
                                        + "\t\t// Virtual call\n"
                                        + "\t\tgetActionBar(); // API 11\n"
                                        + "\t}\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testViewClassLayoutReference() {
        String expected =
                ""
                        + "res/layout/view.xml:9: Error: View requires API level 5 (current min is 1): <QuickContactBadge> [NewApi]\n"
                        + "    <view\n"
                        + "     ~~~~\n"
                        + "res/layout/view.xml:16: Error: View requires API level 11 (current min is 1): <CalendarView> [NewApi]\n"
                        + "    <view\n"
                        + "     ~~~~\n"
                        + "res/layout/view.xml:24: Error: ?android:attr/dividerHorizontal requires API level 11 (current min is 1) [NewApi]\n"
                        + "        unknown=\"?android:attr/dividerHorizontal\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/view.xml:25: Error: ?android:attr/textColorLinkInverse requires API level 11 (current min is 1) [NewApi]\n"
                        + "        android:textColor=\"?android:attr/textColorLinkInverse\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "4 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        xml(
                                "res/layout/view.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"fill_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <!-- Requires API 5 -->\n"
                                        + "\n"
                                        + "    <view\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        class=\"QuickContactBadge\" />\n"
                                        + "\n"
                                        + "    <!-- Requires API 11 -->\n"
                                        + "\n"
                                        + "    <view\n"
                                        + "        android:layout_width=\"fill_parent\"\n"
                                        + "        android:layout_height=\"fill_parent\"\n"
                                        + "        class=\"CalendarView\" />\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:layout_width=\"fill_parent\"\n"
                                        + "        android:layout_height=\"fill_parent\"\n"
                                        + "        unknown=\"?android:attr/dividerHorizontal\"\n"
                                        + "        android:textColor=\"?android:attr/textColorLinkInverse\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testIOException() {
        // See http://code.google.com/p/android/issues/detail?id=35190
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest6.java:8: Error: Call requires API level 9 (current min is 1): new java.io.IOException [NewApi]\n"
                        + "        IOException ioException = new IOException(throwable);\n"
                        + "                                  ~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        mIntermediate,
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.io.IOException;\n"
                                        + "\n"
                                        + "public class ApiCallTest6 {\n"
                                        + "    public void test(Throwable throwable) {\n"
                                        + "        // IOException(Throwable) requires API 9\n"
                                        + "        IOException ioException = new IOException(throwable);\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    // Test suppressing errors -- on classes, methods etc.

    public void testSuppress() {
        String expected =
                ""
                        + "src/foo/bar/SuppressTest1.java:89: Warning: Field requires API level 11 (current min is 1): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [InlinedApi]\n"
                        + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/SuppressTest1.java:76: Error: Call requires API level 11 (current min is 1): android.app.Activity#getActionBar [NewApi]\n"
                        + "  getActionBar(); // API 11\n"
                        + "  ~~~~~~~~~~~~\n"
                        + "src/foo/bar/SuppressTest1.java:80: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                        + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                        + "                 ~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/SuppressTest1.java:83: Error: Call requires API level 3 (current min is 1): android.widget.Chronometer#getOnChronometerTickListener [NewApi]\n"
                        + "  chronometer.getOnChronometerTickListener(); // API 3\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/SuppressTest1.java:86: Error: Call requires API level 11 (current min is 1): android.widget.TextView#setTextIsSelectable [NewApi]\n"
                        + "  chronometer.setTextIsSelectable(true); // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/SuppressTest1.java:94: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                        + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                        + "                            ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/SuppressTest1.java:97: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                        + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "6 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package foo.bar;\n"
                                        + "\n"
                                        + "import org.w3c.dom.DOMError;\n"
                                        + "import org.w3c.dom.DOMErrorHandler;\n"
                                        + "import org.w3c.dom.DOMLocator;\n"
                                        + "\n"
                                        + "import android.view.ViewGroup.LayoutParams;\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.ApplicationErrorReport;\n"
                                        + "import android.app.ApplicationErrorReport.BatteryInfo;\n"
                                        + "import android.graphics.PorterDuff;\n"
                                        + "import android.graphics.PorterDuff.Mode;\n"
                                        + "import android.widget.Chronometer;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "import dalvik.bytecode.OpcodeInfo;\n"
                                        + "\n"
                                        + "public class SuppressTest1 extends Activity {\n"
                                        + "\t@SuppressLint(\"all\")\n"
                                        + "\tpublic void method1(Chronometer chronometer, DOMLocator locator) {\n"
                                        + "\t\t// Virtual call\n"
                                        + "\t\tgetActionBar(); // API 11\n"
                                        + "\n"
                                        + "\t\t// Class references (no call or field access)\n"
                                        + "\t\tDOMError error = null; // API 8\n"
                                        + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\n"
                                        + "\t\t// Method call\n"
                                        + "\t\tchronometer.getOnChronometerTickListener(); // API 3\n"
                                        + "\n"
                                        + "\t\t// Inherited method call (from TextView\n"
                                        + "\t\tchronometer.setTextIsSelectable(true); // API 11\n"
                                        + "\n"
                                        + "\t\t// Field access\n"
                                        + "\t\tint field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                                        + "\t\tint fillParent = LayoutParams.FILL_PARENT; // API 1\n"
                                        + "\t\t// This is a final int, which means it gets inlined\n"
                                        + "\t\tint matchParent = LayoutParams.MATCH_PARENT; // API 8\n"
                                        + "\t\t// Field access: non final\n"
                                        + "\t\tBatteryInfo batteryInfo = getReport().batteryInfo;\n"
                                        + "\n"
                                        + "\t\t// Enum access\n"
                                        + "\t\tMode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"NewApi\")\n"
                                        + "\tpublic void method2(Chronometer chronometer, DOMLocator locator) {\n"
                                        + "\t\t// Virtual call\n"
                                        + "\t\tgetActionBar(); // API 11\n"
                                        + "\n"
                                        + "\t\t// Class references (no call or field access)\n"
                                        + "\t\tDOMError error = null; // API 8\n"
                                        + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\n"
                                        + "\t\t// Method call\n"
                                        + "\t\tchronometer.getOnChronometerTickListener(); // API 3\n"
                                        + "\n"
                                        + "\t\t// Inherited method call (from TextView\n"
                                        + "\t\tchronometer.setTextIsSelectable(true); // API 11\n"
                                        + "\n"
                                        + "\t\t// Field access\n"
                                        + "\t\tint field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                                        + "\t\tint fillParent = LayoutParams.FILL_PARENT; // API 1\n"
                                        + "\t\t// This is a final int, which means it gets inlined\n"
                                        + "\t\tint matchParent = LayoutParams.MATCH_PARENT; // API 8\n"
                                        + "\t\t// Field access: non final\n"
                                        + "\t\tBatteryInfo batteryInfo = getReport().batteryInfo;\n"
                                        + "\n"
                                        + "\t\t// Enum access\n"
                                        + "\t\tMode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"SomethingElse\")\n"
                                        + "\tpublic void method3(Chronometer chronometer, DOMLocator locator) {\n"
                                        + "\t\t// Virtual call\n"
                                        + "\t\tgetActionBar(); // API 11\n"
                                        + "\n"
                                        + "\t\t// Class references (no call or field access)\n"
                                        + "\t\tDOMError error = null; // API 8\n"
                                        + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\n"
                                        + "\t\t// Method call\n"
                                        + "\t\tchronometer.getOnChronometerTickListener(); // API 3\n"
                                        + "\n"
                                        + "\t\t// Inherited method call (from TextView\n"
                                        + "\t\tchronometer.setTextIsSelectable(true); // API 11\n"
                                        + "\n"
                                        + "\t\t// Field access\n"
                                        + "\t\tint field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                                        + "\t\tint fillParent = LayoutParams.FILL_PARENT; // API 1\n"
                                        + "\t\t// This is a final int, which means it gets inlined\n"
                                        + "\t\tint matchParent = LayoutParams.MATCH_PARENT; // API 8\n"
                                        + "\t\t// Field access: non final\n"
                                        + "\t\tBatteryInfo batteryInfo = getReport().batteryInfo;\n"
                                        + "\n"
                                        + "\t\t// Enum access\n"
                                        + "\t\tMode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@SuppressLint({\"SomethingElse\", \"NewApi\"})\n"
                                        + "\tpublic void method4(Chronometer chronometer, DOMLocator locator) {\n"
                                        + "\t\t// Virtual call\n"
                                        + "\t\tgetActionBar(); // API 11\n"
                                        + "\n"
                                        + "\t\t// Class references (no call or field access)\n"
                                        + "\t\tDOMError error = null; // API 8\n"
                                        + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\n"
                                        + "\t\t// Method call\n"
                                        + "\t\tchronometer.getOnChronometerTickListener(); // API 3\n"
                                        + "\n"
                                        + "\t\t// Inherited method call (from TextView\n"
                                        + "\t\tchronometer.setTextIsSelectable(true); // API 11\n"
                                        + "\n"
                                        + "\t\t// Field access\n"
                                        + "\t\tint field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                                        + "\t\tint fillParent = LayoutParams.FILL_PARENT; // API 1\n"
                                        + "\t\t// This is a final int, which means it gets inlined\n"
                                        + "\t\tint matchParent = LayoutParams.MATCH_PARENT; // API 8\n"
                                        + "\t\t// Field access: non final\n"
                                        + "\t\tBatteryInfo batteryInfo = getReport().batteryInfo;\n"
                                        + "\n"
                                        + "\t\t// Enum access\n"
                                        + "\t\tMode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t// Return type\n"
                                        + "\t@SuppressLint(\"NewApi\")\n"
                                        + "\tGridLayout getGridLayout() { // API 14\n"
                                        + "\t\treturn null;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"all\")\n"
                                        + "\tprivate ApplicationErrorReport getReport() {\n"
                                        + "\t\treturn null;\n"
                                        + "\t}\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package foo.bar;\n"
                                        + "\n"
                                        + "import org.w3c.dom.DOMLocator;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.ApplicationErrorReport;\n"
                                        + "import android.widget.Chronometer;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "@SuppressLint(\"all\")\n"
                                        + "public class SuppressTest2 extends Activity {\n"
                                        + "\tpublic void method(Chronometer chronometer, DOMLocator locator) {\n"
                                        + "\t\tgetActionBar(); // API 11\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t// Return type\n"
                                        + "\tGridLayout getGridLayout() { // API 14\n"
                                        + "\t\treturn null;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\tprivate ApplicationErrorReport getReport() {\n"
                                        + "\t\treturn null;\n"
                                        + "\t}\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package foo.bar;\n"
                                        + "\n"
                                        + "import org.w3c.dom.DOMLocator;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.ApplicationErrorReport;\n"
                                        + "import android.widget.Chronometer;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "@SuppressLint(\"NewApi\")\n"
                                        + "public class SuppressTest3 extends Activity {\n"
                                        + "\tpublic void method(Chronometer chronometer, DOMLocator locator) {\n"
                                        + "\t\tgetActionBar(); // API 11\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t// Return type\n"
                                        + "\tGridLayout getGridLayout() { // API 14\n"
                                        + "\t\treturn null;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\tprivate ApplicationErrorReport getReport() {\n"
                                        + "\t\treturn null;\n"
                                        + "\t}\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package foo.bar;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.ApplicationErrorReport;\n"
                                        + "import android.app.ApplicationErrorReport.BatteryInfo;\n"
                                        + "\n"
                                        + "public class SuppressTest4 extends Activity {\n"
                                        + "\tpublic void method() {\n"
                                        + "\n"
                                        + "\t\t// These annotations within the method do not end up\n"
                                        + "\t\t// in the bytecode, so they have no effect. We need a\n"
                                        + "\t\t// lint annotation check to find these.\n"
                                        + "\n"
                                        + "\t\t@SuppressLint(\"NewApi\")\n"
                                        + "\t\tApplicationErrorReport report = null;\n"
                                        + "\n"
                                        + "\t\t@SuppressLint(\"NewApi\")\n"
                                        + "\t\tBatteryInfo batteryInfo = report.batteryInfo;\n"
                                        + "\t}\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testSuppressInnerClasses() {
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest4.java:9: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n"
                        + "        new GridLayout(null, null, 0);\n"
                        + "        ~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest4.java:38: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n"
                        + "            new GridLayout(null, null, 0);\n"
                        + "            ~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ApiCallTest4 {\n"
                                        + "    public void foo() {\n"
                                        + "        new GridLayout(null, null, 0);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    void foo2() {\n"
                                        + "        // Inner class suppressed via a method in outer class\n"
                                        + "        new Runnable() {\n"
                                        + "            @Override\n"
                                        + "            public void run() {\n"
                                        + "                new GridLayout(null, null, 0);\n"
                                        + "            }\n"
                                        + "        };\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    private class InnerClass1 {\n"
                                        + "        void foo() {\n"
                                        + "            new GridLayout(null, null, 0);\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        private class InnerInnerClass1 {\n"
                                        + "            public void foo() {\n"
                                        + "                new GridLayout(null, null, 0);\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private class InnerClass2 {\n"
                                        + "        public void foo() {\n"
                                        + "            new GridLayout(null, null, 0);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testFieldWithinMethodCall() {
        String expected =
                ""
                        + "src/p1/p2/FieldWithinCall.java:7: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                        + "    int hash = PorterDuff.Mode.OVERLAY.hashCode();\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                "package p1.p2;\n"
                                        + "\n"
                                        + "import android.graphics.PorterDuff;\n"
                                        + "\n"
                                        + "class FieldWithinCall {\n"
                                        + "  public void test() {\n"
                                        // + "    Object o = PorterDuff.Mode.OVERLAY;\n"
                                        + "    int hash = PorterDuff.Mode.OVERLAY.hashCode();\n"
                                        + "  }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testApiTargetAnnotation() {
        String expected =
                ""
                        + "src/foo/bar/ApiTargetTest.java:13: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                        + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                        + "                 ~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiTargetTest.java:25: Error: Class requires API level 8 (current min is 4): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                        + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                        + "                 ~~~~~~~~~~~~~~~\n"
                        + "src/foo/bar/ApiTargetTest.java:39: Error: Class requires API level 8 (current min is 7): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                        + "   Class<?> clz = DOMErrorHandler.class; // API 8\n"
                        + "                  ~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package foo.bar;\n"
                                        + "\n"
                                        + "import org.w3c.dom.DOMErrorHandler;\n"
                                        + "\n"
                                        + "import android.annotation.TargetApi;\n"
                                        + "\n"
                                        + "// Test using the @TargetApi annotation to temporarily override\n"
                                        + "// the required API levels\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ApiTargetTest {\n"
                                        + "\tpublic void test1() {\n"
                                        + "\t\t// No annotation: should generate warning if manifest SDK < 8\n"
                                        + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t// Temporarily setting method min sdk to 12\n"
                                        + "\t@TargetApi(12)\n"
                                        + "\tpublic void test2() {\n"
                                        + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t// Temporarily setting method min sdk to 14\n"
                                        + "\t@TargetApi(4)\n"
                                        + "\tpublic void test3() {\n"
                                        + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t// Temporarily setting class min sdk to 12\n"
                                        + "\t@TargetApi(value=11)\n"
                                        + "\tpublic static class LocalClass {\n"
                                        + "\t\tpublic void test4() {\n"
                                        + "\t\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\t\t}\n"
                                        + "\n"
                                        + "\t\t// Overriding class min sdk: this should generate\n"
                                        + "\t\t// an API warning again\n"
                                        + "\t\t@TargetApi(7)\n"
                                        + "\t\tpublic void test5() {\n"
                                        + "\t\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                                        + "\t\t}\n"
                                        + "\t}\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testTargetAnnotationInner() {
        String expected =
                ""
                        + "src/test/pkg/ApiTargetTest2.java:32: Error: Call requires API level 14 (current min is 3): new android.widget.GridLayout [NewApi]\n"
                        + "                        new GridLayout(null, null, 0);\n"
                        + "                        ~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.TargetApi;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "// Test using the @TargetApi annotation on inner classes and anonymous inner classes\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ApiTargetTest2 {\n"
                                        + "    @TargetApi(value=14)\n"
                                        + "    void foo2() {\n"
                                        + "        new Runnable() {\n"
                                        + "            @Override\n"
                                        + "            public void run() {\n"
                                        + "                new GridLayout(null, null, 0);\n"
                                        + "            }\n"
                                        + "\n"
                                        + "            void foo3() {\n"
                                        + "                new Runnable() {\n"
                                        + "                    @Override\n"
                                        + "                    public void run() {\n"
                                        + "                        new GridLayout(null, null, 0);\n"
                                        + "                    }\n"
                                        + "                };\n"
                                        + "            }\n"
                                        + "\n"
                                        + "            @TargetApi(value=3)\n"
                                        + "            void foo4() {\n"
                                        + "                new Runnable() {\n"
                                        + "                    @Override\n"
                                        + "                    public void run() {\n"
                                        + "                        // This should be marked as an error since the effective target API is 3 here\n"
                                        + "                        new GridLayout(null, null, 0);\n"
                                        + "                    }\n"
                                        + "                };\n"
                                        + "            }\n"
                                        + "\n"
                                        + "        };\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testSuper() {
        // See http://code.google.com/p/android/issues/detail?id=36384
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest7.java:8: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n"
                        + "        super(message, cause); // API 9\n"
                        + "        ~~~~~\n"
                        + "src/test/pkg/ApiCallTest7.java:12: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n"
                        + "        super.toString(); throw new IOException((Throwable) null); // API 9\n"
                        + "                                ~~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.io.IOException;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"serial\")\n"
                                        + "public class ApiCallTest7 extends IOException {\n"
                                        + "    public ApiCallTest7(String message, Throwable cause) {\n"
                                        + "        super(message, cause); // API 9\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void fun() throws IOException {\n"
                                        + "        super.toString(); throw new IOException((Throwable) null); // API 9\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testEnums() {
        // See http://code.google.com/p/android/issues/detail?id=36951
        String expected =
                ""
                        + "src/test/pkg/TestEnum.java:61: Error: Enum for switch requires API level 11 (current min is 4): android.renderscript.Element.DataType [NewApi]\n"
                        + "        switch (type) {\n"
                        + "                ~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.graphics.Bitmap.CompressFormat;\n"
                                        + "import android.graphics.PorterDuff;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"incomplete-switch\")\n"
                                        + "public class TestEnum {\n"
                                        + "    public static void test1(final CompressFormat format) {\n"
                                        + "        switch (format) {\n"
                                        + "            case JPEG: {\n"
                                        + "                System.out.println(\"jpeg\");\n"
                                        + "                break;\n"
                                        + "            }\n"
                                        + "            default: {\n"
                                        + "                System.out.println(\"Default\");\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public static void test2(final PorterDuff.Mode mode) {\n"
                                        + "        switch (mode) {\n"
                                        + "            case CLEAR: {\n"
                                        + "                System.out.println(\"clear\");\n"
                                        + "            }\n"
                                        + "            case OVERLAY: {\n"
                                        + "                System.out.println(\"add\");\n"
                                        + "                break;\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // Second usage: should also complain here\n"
                                        + "        switch (mode) {\n"
                                        + "            case CLEAR: {\n"
                                        + "                System.out.println(\"clear\");\n"
                                        + "            }\n"
                                        + "            case OVERLAY: {\n"
                                        + "                System.out.println(\"add\");\n"
                                        + "                break;\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    public static void test3(PorterDuff.Mode mode) {\n"
                                        + "        // Third usage: no complaint because it's suppressed\n"
                                        + "        switch (mode) {\n"
                                        + "            case CLEAR: {\n"
                                        + "                System.out.println(\"clear\");\n"
                                        + "            }\n"
                                        + "            case OVERLAY: {\n"
                                        + "                System.out.println(\"add\");\n"
                                        + "                break;\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public static void test4(final android.renderscript.Element.DataType type) {\n"
                                        + "        // Switch usage where the whole underlying enum requires a higher API level:\n"
                                        + "        // test customized error message\n"
                                        + "        switch (type) {\n"
                                        + "            case RS_FONT: {\n"
                                        + "                System.out.println(\"font\");\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    // hook up to lint test task
    @Override
    public String getSuperClass(Project project, String name) {
        // For testInterfaceInheritance
        //noinspection IfCanBeSwitch
        if (name.equals("android/database/sqlite/SQLiteStatement")) {
            return "android/database/sqlite/SQLiteProgram";
        } else if (name.equals("android/database/sqlite/SQLiteProgram")) {
            return "android/database/sqlite/SQLiteClosable";
        } else if (name.equals("android/database/sqlite/SQLiteClosable")) {
            return "java/lang/Object";
        }
        return null;
    }

    public void testInterfaceInheritance() {
        // See http://code.google.com/p/android/issues/detail?id=38004
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.database.sqlite.SQLiteStatement;\n"
                                        + "\n"
                                        + "public class CloseTest {\n"
                                        + "    public void close(SQLiteStatement statement) {\n"
                                        + "        statement.close();\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testInnerClassPositions() {
        // See http://code.google.com/p/android/issues/detail?id=38113
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.text.style.LeadingMarginSpan;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ApiCallTest8 {\n"
                                        + "    public void test() {\n"
                                        + "        LeadingMarginSpan.LeadingMarginSpan2 span = null;        \n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testManifestReferences() {
        String expected =
                ""
                        + "AndroidManifest.xml:15: Error: @android:style/Theme.Holo requires API level 11 (current min is 4) [NewApi]\n"
                        + "            android:theme=\"@android:style/Theme.Holo\" >\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.bytecode\"\n"
                                        + "    android:versionCode=\"1\"\n"
                                        + "    android:versionName=\"1.0\" >\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"4\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <activity\n"
                                        + "            android:name=\".BytecodeTestsActivity\"\n"
                                        + "            android:label=\"@string/app_name\"\n"
                                        + "            android:theme=\"@android:style/Theme.Holo\" >\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                        + "\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testSuppressFieldAnnotations() {
        // See http://code.google.com/p/android/issues/detail?id=38626
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest9.java:9: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                        + "    private GridLayout field1 = new GridLayout(null);\n"
                        + "                                ~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest9.java:12: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                        + "    private static GridLayout field2 = new GridLayout(null);\n"
                        + "                                       ~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "/** Test suppress on fields */\n"
                                        + "public class ApiCallTest9 {\n"
                                        + "    // Actual initialization code lives in the synthetic method <init>\n"
                                        + "    private GridLayout field1 = new GridLayout(null);\n"
                                        + "\n"
                                        + "    // Actual initialization code lives in the synthetic method <clinit>\n"
                                        + "    private static GridLayout field2 = new GridLayout(null);\n"
                                        + "\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    private GridLayout field3 = new GridLayout(null);\n"
                                        + "\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    private static GridLayout field4 = new GridLayout(null);\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testIgnoreTestSources() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                "test/test/pkg/UnitTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "public class UnitTest {\n"
                                        + "    private GridLayout field1 = new GridLayout(null);\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testUnignoreTestSources() {
        lint().files(
                        manifest().minSdk(4),
                        java(
                                "src/test/java/test/pkg/UnitTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "public class UnitTest {\n"
                                        + "    private GridLayout field1 = new GridLayout(null);\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        checkTestSources true\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(
                        ""
                                + "src/test/java/test/pkg/UnitTest.java:6: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                                + "    private GridLayout field1 = new GridLayout(null);\n"
                                + "                                ~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testTestSourcesInEditor() {
        lint().files(
                        manifest().minSdk(4),
                        java(
                                "src/test/java/test/pkg/UnitTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "public class UnitTest {\n"
                                        + "    private GridLayout field1 = new GridLayout(null);\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        checkTestSources false\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .incremental("src/test/java/test/pkg/UnitTest.java")
                .run()
                .expectClean();
    }

    public void testDensity() {
        // 120162341: Lint detection of Configuration.densityDpi field is strange when minSdk < 17
        lint().files(
                        manifest().minSdk(8),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.res.Configuration;\n"
                                        + "\n"
                                        + "public class ConfigTest {\n"
                                        + "    public void test(Configuration configuration) {\n"
                                        + "        System.out.println(configuration.densityDpi);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/ConfigTest.java:7: Error: Field requires API level 17 (current min is 8): android.content.res.Configuration#densityDpi [NewApi]\n"
                                + "        System.out.println(configuration.densityDpi);\n"
                                + "                           ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void test38195() {
        // See http://code.google.com/p/android/issues/detail?id=38195
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest9.java:7: Error: Call requires API level 9 (current min is 4): java.lang.String#isEmpty [NewApi]\n"
                        + "        boolean s = \"\".isEmpty(); \n"
                        + "                       ~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest9.java:8: Error: Call requires API level 9 (current min is 4): new java.sql.SQLException [NewApi]\n"
                        + "        throw new SQLException(\"error on upgrade: \", e); \n"
                        + "              ~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest9.java:11: Error: Call requires API level 16 (current min is 4): new android.database.SQLException [NewApi]\n"
                        + "        throw new android.database.SQLException(\"error on upgrade: \", e); \n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg; \n"
                                        + "\n"
                                        + "import java.sql.SQLException; \n"
                                        + "\n"
                                        + "public class ApiCallTest9 { \n"
                                        + "    public void test(Exception e) { \n"
                                        + "        boolean s = \"\".isEmpty(); \n"
                                        + "        throw new SQLException(\"error on upgrade: \", e); \n"
                                        + "    } \n"
                                        + "    public void test2(Exception e) { \n"
                                        + "        throw new android.database.SQLException(\"error on upgrade: \", e); \n"
                                        + "    } \n"
                                        + "} \n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testAllowLocalMethodsImplementingInaccessible() {
        // See http://code.google.com/p/android/issues/detail?id=39030
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest10.java:40: Error: Call requires API level 14 (current min is 4): android.view.View#dispatchHoverEvent [NewApi]\n"
                        + "        dispatchHoverEvent(null);\n"
                        + "        ~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Build;\n"
                                        + "import android.view.MotionEvent;\n"
                                        + "import android.view.View;\n"
                                        + "import android.view.accessibility.AccessibilityEvent;\n"
                                        + "\n"
                                        + "public class ApiCallTest10 extends View {\n"
                                        + "    public ApiCallTest10() {\n"
                                        + "        super(null, null, 0);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {\n"
                                        + "            onPopulateAccessibilityEvent(event); // Shouldn't warn here: method\n"
                                        + "                                                 // exists locally\n"
                                        + "            return true;\n"
                                        + "        }\n"
                                        + "        return super.dispatchPopulateAccessibilityEvent(event);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {\n"
                                        + "        super.onPopulateAccessibilityEvent(event); // Not flagged: calling same mehod\n"
                                        + "        // Additional override code here:\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected boolean dispatchGenericFocusedEvent(MotionEvent event) {\n"
                                        + "        return super.dispatchGenericFocusedEvent(event); // Not flagged: calling same mehod\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected boolean dispatchHoverEvent(int event) {\n"
                                        + "        return false;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test1() {\n"
                                        + "        // Should flag this, because the local method has the wrong signature\n"
                                        + "        dispatchHoverEvent(null);\n"
                                        + "\n"
                                        + "        // Shouldn't flag this, local method makes it available\n"
                                        + "        dispatchGenericFocusedEvent(null);\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testOverrideUnknownTarget() {
        //noinspection all // Sample code
        lint().files(manifest().minSdk(4), mApiCallTest11)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testOverride() {
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest11.java:13: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.ApiCallTest11#getActionBar [Override]\n"
                        + "    public ActionBar getActionBar() {\n"
                        + "                     ~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest11.java:17: Error: This method is not overriding anything with the current build target, but will in API level 17 (current target is 3): test.pkg.ApiCallTest11#isDestroyed [Override]\n"
                        + "    public boolean isDestroyed() {\n"
                        + "                   ~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest11.java:39: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.ApiCallTest11.MyLinear#setDividerDrawable [Override]\n"
                        + "        public void setDividerDrawable(Drawable dividerDrawable) {\n"
                        + "                    ~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n";
        lint().files(manifest().minSdk(4), projectProperties().compileSdk(3), mApiCallTest11)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testDateFormat() {
        // See http://code.google.com/p/android/issues/detail?id=40876
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest12.java:18: Error: Call requires API level 9 (current min is 4): java.text.DateFormatSymbols#getInstance [NewApi]\n"
                        + "  new SimpleDateFormat(\"yyyy-MM-dd\", DateFormatSymbols.getInstance());\n"
                        + "                                                       ~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest12.java:23: Error: The pattern character 'L' requires API level 9 (current min is 4) : \"yyyy-MM-dd LL\" [NewApi]\n"
                        + "  new SimpleDateFormat(\"yyyy-MM-dd LL\", Locale.US);\n"
                        + "                                  ~~\n"
                        + "src/test/pkg/ApiCallTest12.java:25: Error: The pattern character 'c' requires API level 9 (current min is 4) : \"cc yyyy-MM-dd\" [NewApi]\n"
                        + "  SimpleDateFormat format = new SimpleDateFormat(\"cc yyyy-MM-dd\");\n"
                        + "                                                 ~~\n"
                        + "3 errors, 0 warnings\n";
        lint().files(manifest().minSdk(4), projectProperties().compileSdk(19), mApiCallTest12)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testDateFormatOk() {
        lint().files(manifest().minSdk(10), projectProperties().compileSdk(19), mApiCallTest12)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testDateFormatApi24() {
        lint().files(
                        manifest().minSdk(8),
                        kotlin(
                                ""
                                        + "import android.os.Build\n"
                                        + "import java.text.SimpleDateFormat\n"
                                        + "import java.util.*\n"
                                        + "\n"
                                        + "fun test(): String {\n"
                                        + "    return SimpleDateFormat(\n"
                                        + "        \"'test'cc-LL-YY-uu-XX-yy\",\n"
                                        + "        Locale.US\n"
                                        + "    ).format(Date())\n"
                                        + "}\n"
                                        + "\n"
                                        + "fun testSuppressed(): String {\n"
                                        + "    return if (Build.VERSION.SDK_INT > 24) {\n"
                                        + "        SimpleDateFormat(\n"
                                        + "            \"'test'cc-LL-YY-uu-XX-yy\",\n"
                                        + "            Locale.US\n"
                                        + "        ).format(Date())\n"
                                        + "    } else {\n"
                                        + "        \"?\";\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(
                        ""
                                + "src/test.kt:7: Error: The pattern character 'L' requires API level 9 (current min is 8) : \"'test'cc-LL-YY-uu-XX-yy\" [NewApi]\n"
                                + "        \"'test'cc-LL-YY-uu-XX-yy\",\n"
                                + "                  ~~\n"
                                + "src/test.kt:7: Error: The pattern character 'X' requires API level 24 (current min is 8) : \"'test'cc-LL-YY-uu-XX-yy\" [NewApi]\n"
                                + "        \"'test'cc-LL-YY-uu-XX-yy\",\n"
                                + "                           ~~\n"
                                + "src/test.kt:7: Error: The pattern character 'Y' requires API level 24 (current min is 8) : \"'test'cc-LL-YY-uu-XX-yy\" [NewApi]\n"
                                + "        \"'test'cc-LL-YY-uu-XX-yy\",\n"
                                + "                     ~~\n"
                                + "src/test.kt:7: Error: The pattern character 'c' requires API level 9 (current min is 8) : \"'test'cc-LL-YY-uu-XX-yy\" [NewApi]\n"
                                + "        \"'test'cc-LL-YY-uu-XX-yy\",\n"
                                + "               ~~\n"
                                + "src/test.kt:7: Error: The pattern character 'u' requires API level 24 (current min is 8) : \"'test'cc-LL-YY-uu-XX-yy\" [NewApi]\n"
                                + "        \"'test'cc-LL-YY-uu-XX-yy\",\n"
                                + "                        ~~\n"
                                + "5 errors, 0 warnings");
    }

    public void testJavaConstants() {
        String expected =
                ""
                        + "src/test/pkg/ApiSourceCheck.java:30: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                        + "        int x = MEASURED_STATE_MASK;\n"
                        + "                ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:33: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                        + "        int y = android.view.View.MEASURED_STATE_MASK;\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:36: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                        + "        int z = View.MEASURED_STATE_MASK;\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:37: Warning: Field requires API level 14 (current min is 1): android.view.View#FIND_VIEWS_WITH_TEXT [InlinedApi]\n"
                        + "        int find2 = View.FIND_VIEWS_WITH_TEXT; // requires API 14\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:40: Warning: Field requires API level 12 (current min is 1): android.app.ActivityManager#MOVE_TASK_NO_USER_ACTION [InlinedApi]\n"
                        + "        int w = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:41: Warning: Field requires API level 14 (current min is 1): android.view.View#FIND_VIEWS_WITH_CONTENT_DESCRIPTION [InlinedApi]\n"
                        + "        int find1 = ZoomButton.FIND_VIEWS_WITH_CONTENT_DESCRIPTION; // requires\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:44: Warning: Field requires API level 9 (current min is 1): android.view.View#OVER_SCROLL_ALWAYS [InlinedApi]\n"
                        + "        int overScroll = OVER_SCROLL_ALWAYS; // requires API 9\n"
                        + "                         ~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:47: Warning: Field requires API level 16 (current min is 1): android.view.View#IMPORTANT_FOR_ACCESSIBILITY_AUTO [InlinedApi]\n"
                        + "        int auto = IMPORTANT_FOR_ACCESSIBILITY_AUTO; // requires API 16\n"
                        + "                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:54: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                        + "        return (child.getMeasuredWidth() & View.MEASURED_STATE_MASK)\n"
                        + "                                           ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:55: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_HEIGHT_STATE_SHIFT [InlinedApi]\n"
                        + "                | ((child.getMeasuredHeight() >> View.MEASURED_HEIGHT_STATE_SHIFT) & (View.MEASURED_STATE_MASK >> View.MEASURED_HEIGHT_STATE_SHIFT));\n"
                        + "                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:55: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_HEIGHT_STATE_SHIFT [InlinedApi]\n"
                        + "                | ((child.getMeasuredHeight() >> View.MEASURED_HEIGHT_STATE_SHIFT) & (View.MEASURED_STATE_MASK >> View.MEASURED_HEIGHT_STATE_SHIFT));\n"
                        + "                                                                                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:55: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                        + "                | ((child.getMeasuredHeight() >> View.MEASURED_HEIGHT_STATE_SHIFT) & (View.MEASURED_STATE_MASK >> View.MEASURED_HEIGHT_STATE_SHIFT));\n"
                        + "                                                                                      ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:90: Warning: Field requires API level 8 (current min is 1): android.R.id#custom [InlinedApi]\n"
                        + "        int custom = android.R.id.custom; // API 8\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:94: Warning: Field requires API level 19 (current min is 1): android.Manifest.permission#BLUETOOTH_PRIVILEGED [InlinedApi]\n"
                        + "        String setPointerSpeed = permission.BLUETOOTH_PRIVILEGED;\n"
                        + "                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:95: Warning: Field requires API level 19 (current min is 1): android.Manifest.permission#BLUETOOTH_PRIVILEGED [InlinedApi]\n"
                        + "        String setPointerSpeed2 = Manifest.permission.BLUETOOTH_PRIVILEGED;\n"
                        + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:120: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                        + "        int y = View.MEASURED_STATE_MASK; // Not OK\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:121: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                        + "        testBenignUsages(View.MEASURED_STATE_MASK); // Not OK\n"
                        + "                         ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck.java:51: Error: Field requires API level 14 (current min is 1): android.view.View#ROTATION_X [NewApi]\n"
                        + "        Object rotationX = ZoomButton.ROTATION_X; // Requires API 14\n"
                        + "                           ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 17 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        projectProperties().compileSdk(19),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.util.Property;\n"
                                        + "import android.view.View;\n"
                                        + "import static android.view.View.MEASURED_STATE_MASK;\n"
                                        + "import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;\n"
                                        + "import android.view.*;\n"
                                        + "import android.annotation.*;\n"
                                        + "import android.app.*;\n"
                                        + "import android.widget.*;\n"
                                        + "import static android.widget.ZoomControls.*;\n"
                                        + "import android.Manifest.permission;\n"
                                        + "import android.Manifest;\n"
                                        + "\n"
                                        + "/** Various tests for source-level checks */\n"
                                        + "final class ApiSourceCheck extends LinearLayout {\n"
                                        + "    public ApiSourceCheck(android.content.Context context) {\n"
                                        + "        super(context);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    /**\n"
                                        + "     * Return only the state bits of {@link #getMeasuredWidthAndState()} and\n"
                                        + "     * {@link #getMeasuredHeightAndState()}, combined into one integer. The\n"
                                        + "     * width component is in the regular bits {@link #MEASURED_STATE_MASK} and\n"
                                        + "     * the height component is at the shifted bits\n"
                                        + "     * {@link #MEASURED_HEIGHT_STATE_SHIFT}>>{@link #MEASURED_STATE_MASK}.\n"
                                        + "     */\n"
                                        + "    public static int m1(View child) {\n"
                                        + "        // from static import of field\n"
                                        + "        int x = MEASURED_STATE_MASK;\n"
                                        + "\n"
                                        + "        // fully qualified name field access\n"
                                        + "        int y = android.view.View.MEASURED_STATE_MASK;\n"
                                        + "\n"
                                        + "        // from explicitly imported class\n"
                                        + "        int z = View.MEASURED_STATE_MASK;\n"
                                        + "        int find2 = View.FIND_VIEWS_WITH_TEXT; // requires API 14\n"
                                        + "\n"
                                        + "        // from wildcard import of package\n"
                                        + "        int w = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                                        + "        int find1 = ZoomButton.FIND_VIEWS_WITH_CONTENT_DESCRIPTION; // requires\n"
                                        + "                                                                    // API 14\n"
                                        + "        // from static wildcard import\n"
                                        + "        int overScroll = OVER_SCROLL_ALWAYS; // requires API 9\n"
                                        + "\n"
                                        + "        // Inherited field from ancestor class (View)\n"
                                        + "        int auto = IMPORTANT_FOR_ACCESSIBILITY_AUTO; // requires API 16\n"
                                        + "\n"
                                        + "        // object field reference: ensure that we don't get two errors\n"
                                        + "        // (one from source scan, the other from class scan)\n"
                                        + "        Object rotationX = ZoomButton.ROTATION_X; // Requires API 14\n"
                                        + "\n"
                                        + "        // different type of expression than variable declaration\n"
                                        + "        return (child.getMeasuredWidth() & View.MEASURED_STATE_MASK)\n"
                                        + "                | ((child.getMeasuredHeight() >> View.MEASURED_HEIGHT_STATE_SHIFT) & (View.MEASURED_STATE_MASK >> View.MEASURED_HEIGHT_STATE_SHIFT));\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    private void testSuppress1() {\n"
                                        + "        // Checks suppress on surrounding method\n"
                                        + "        int w = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void testSuppress2() {\n"
                                        + "        // Checks suppress on surrounding declaration statement\n"
                                        + "        @SuppressLint(\"NewApi\")\n"
                                        + "        int w, z = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @TargetApi(17)\n"
                                        + "    private void testTargetApi1() {\n"
                                        + "        // Checks @TargetApi on surrounding method\n"
                                        + "        int w, z = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)\n"
                                        + "    private void testTargetApi2() {\n"
                                        + "        // Checks @TargetApi with codename\n"
                                        + "        int w, z = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @TargetApi(JELLY_BEAN_MR1)\n"
                                        + "    private void testTargetApi3() {\n"
                                        + "        // Checks @TargetApi with codename\n"
                                        + "        int w, z = ActivityManager.MOVE_TASK_NO_USER_ACTION;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void checkOtherFields() {\n"
                                        + "        // Look at fields that aren't capitalized\n"
                                        + "        int custom = android.R.id.custom; // API 8\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void innerclass() {\n"
                                        + "        String setPointerSpeed = permission.BLUETOOTH_PRIVILEGED;\n"
                                        + "        String setPointerSpeed2 = Manifest.permission.BLUETOOTH_PRIVILEGED;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void test() {\n"
                                        + "        // Make sure that local variable references which look like fields,\n"
                                        + "        // even imported ones, aren't taken as invalid references\n"
                                        + "        int OVER_SCROLL_ALWAYS = 1, IMPORTANT_FOR_ACCESSIBILITY_AUTO = 2;\n"
                                        + "        int x = OVER_SCROLL_ALWAYS;\n"
                                        + "        int y = IMPORTANT_FOR_ACCESSIBILITY_AUTO;\n"
                                        + "        findViewById(IMPORTANT_FOR_ACCESSIBILITY_AUTO); // yes, nonsensical\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void testBenignUsages(int x) {\n"
                                        + "        // Certain types of usages (such as switch/case constants) are okay\n"
                                        + "        switch (x) {\n"
                                        + "            case View.MEASURED_STATE_MASK: { // OK\n"
                                        + "                break;\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "        if (x == View.MEASURED_STATE_MASK) { // OK\n"
                                        + "        }\n"
                                        + "        if (false || x == View.MEASURED_STATE_MASK) { // OK\n"
                                        + "        }\n"
                                        + "        if (x >= View.MEASURED_STATE_MASK) { // OK\n"
                                        + "        }\n"
                                        + "        int y = View.MEASURED_STATE_MASK; // Not OK\n"
                                        + "        testBenignUsages(View.MEASURED_STATE_MASK); // Not OK\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testStyleDeclaration() {
        String expected =
                ""
                        + "res/values/styles2.xml:5: Error: android:actionBarStyle requires API level 11 (current min is 10) [NewApi]\n"
                        + "        <item name=\"android:actionBarStyle\">...</item>\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(manifest().minSdk(10), projectProperties().compileSdk(19), mStyles2)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testStyleDeclarationInV9() {
        String expected =
                ""
                        + "res/values-v9/styles2.xml:5: Error: android:actionBarStyle requires API level 11 (current min is 10) [NewApi]\n"
                        + "        <item name=\"android:actionBarStyle\">...</item>\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/values-v9: Warning: This folder configuration (v9) is unnecessary; minSdkVersion is 10. Merge all the resources in this folder into values. [ObsoleteSdkInt]\n"
                        + "1 errors, 1 warnings\n";
        lint().files(manifest().minSdk(10), projectProperties().compileSdk(19), mStyles2_class)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testStyleDeclarationInV11() {
        lint().files(manifest().minSdk(10), projectProperties().compileSdk(19), mStyles2_class2)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testStyleDeclarationInV14() {
        lint().files(manifest().minSdk(10), projectProperties().compileSdk(19), mStyles2_class3)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testMovedConstants() {
        String expected =
                ""
                        + "src/test/pkg/ApiSourceCheck2.java:10: Warning: Field requires API level 11 (current min is 1): android.widget.AbsListView#CHOICE_MODE_MULTIPLE_MODAL [InlinedApi]\n"
                        + "        int mode2 = AbsListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiSourceCheck2.java:14: Warning: Field requires API level 11 (current min is 1): android.widget.AbsListView#CHOICE_MODE_MULTIPLE_MODAL [InlinedApi]\n"
                        + "        int mode6 = ListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        projectProperties().compileSdk(19),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.widget.AbsListView;\n"
                                        + "import android.widget.ListView;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ApiSourceCheck2 {\n"
                                        + "    public void test() {\n"
                                        + "        int mode1 = AbsListView.CHOICE_MODE_MULTIPLE;\n"
                                        + "        int mode2 = AbsListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                                        + "        int mode3 = AbsListView.CHOICE_MODE_NONE;\n"
                                        + "        int mode4 = AbsListView.CHOICE_MODE_SINGLE;\n"
                                        + "        int mode5 = ListView.CHOICE_MODE_MULTIPLE;\n"
                                        + "        int mode6 = ListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                                        + "        int mode7 = ListView.CHOICE_MODE_NONE;\n"
                                        + "        int mode8 = ListView.CHOICE_MODE_SINGLE;\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testMovedMethod() {
        // Regression test for https://issuetracker.google.com/37133935
        // View#setForeground incorrectly requires API 23
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.graphics.drawable.Drawable;\n"
                                        + "import android.support.annotation.NonNull;\n"
                                        + "import android.widget.FrameLayout;\n"
                                        + "\n"
                                        + "public class CustomFrameLayout extends FrameLayout {\n"
                                        + "    public CustomFrameLayout(@NonNull Context context) {\n"
                                        + "        super(context);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static void test(CustomFrameLayout layout, Drawable drawable) {\n"
                                        + "        layout.setForeground(drawable);\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testInheritCompatLibrary() {
        String expected =
                ""
                        + "src/test/pkg/MyActivityImpl.java:8: Error: Call requires API level 11 (current min is 1): android.app.Activity#isChangingConfigurations [NewApi]\n"
                        + "  boolean isChanging = super.isChangingConfigurations();\n"
                        + "                             ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/MyActivityImpl.java:12: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.MyActivityImpl#isChangingConfigurations [Override]\n"
                        + " public boolean isChangingConfigurations() {\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        projectProperties().compileSdk(3),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.support.v4.app.FragmentActivity;\n"
                                        + "\n"
                                        + "public class MyActivityImpl extends FragmentActivity {\n"
                                        + "\tpublic void test() {\n"
                                        + "\t\tboolean isChanging = super.isChangingConfigurations();\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic boolean isChangingConfigurations() {\n"
                                        + "\t\treturn super.isChangingConfigurations();\n"
                                        + "\t}\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package android.support.v4.app;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class FragmentActivity extends Activity {\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testImplements() {
        String expected =
                ""
                        + "src/test/pkg/ApiCallTest13.java:8: Error: Class requires API level 14 (current min is 4): android.widget.GridLayout [NewApi]\n"
                        + "public class ApiCallTest13 extends GridLayout implements\n"
                        + "                                   ~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest13.java:9: Error: Class requires API level 11 (current min is 4): android.view.View.OnLayoutChangeListener [NewApi]\n"
                        + "  View.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {\n"
                        + "                                           ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest13.java:9: Error: Class requires API level 11 (current min is 4): android.view.View.OnSystemUiVisibilityChangeListener [NewApi]\n"
                        + "  View.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {\n"
                        + "  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ApiCallTest13.java:12: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                        + "  super(context);\n"
                        + "  ~~~~~\n"
                        + "4 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        projectProperties().compileSdk(19),
                        java(
                                "src/test/pkg/ApiCallTest13.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "import android.view.View;\n"
                                        + "import android.view.View.OnLayoutChangeListener;\n"
                                        + "import android.widget.GridLayout;\n"
                                        + "\n"
                                        + "public class ApiCallTest13 extends GridLayout implements\n"
                                        + "\t\tView.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {\n"
                                        + "\n"
                                        + "\tpublic ApiCallTest13(Context context) {\n"
                                        + "\t\tsuper(context);\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic void onSystemUiVisibilityChange(int visibility) {\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic void onLayoutChange(View v, int left, int top, int right,\n"
                                        + "\t\t\tint bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {\n"
                                        + "\t}\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testFieldSuppress() {
        // See https://code.google.com/p/android/issues/detail?id=52726
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.media.MediaRouter;\n"
                                        + "import android.media.MediaRouter.RouteInfo;\n"
                                        + "import android.media.MediaRouter.SimpleCallback;\n"
                                        + "\n"
                                        + "public class ApiCallTest14 {\n"
                                        + "\t@SuppressLint(\"NewApi\")\n"
                                        + "\tpublic SimpleCallback cb = new SimpleCallback() {\n"
                                        + "\t\t@Override\n"
                                        + "\t\tpublic void onRoutePresentationDisplayChanged(MediaRouter router,\n"
                                        + "\t\t\t\tRouteInfo route) {\n"
                                        + "\t\t\t// do something\n"
                                        + "\t\t}\n"
                                        + "\t};\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"NewApi\")\n"
                                        + "\tprivate SimpleCallback cb2 = new SimpleCallback() {\n"
                                        + "\t\t@Override\n"
                                        + "\t\tpublic void onRoutePresentationDisplayChanged(MediaRouter router,\n"
                                        + "\t\t\t\tRouteInfo route) {\n"
                                        + "\t\t\t// do something\n"
                                        + "\t\t}\n"
                                        + "\t};\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"NewApi\")\n"
                                        + "\tprivate static final SimpleCallback cb3 = new SimpleCallback() {\n"
                                        + "\t\t@Override\n"
                                        + "\t\tpublic void onRoutePresentationDisplayChanged(MediaRouter router,\n"
                                        + "\t\t\t\tRouteInfo route) {\n"
                                        + "\t\t\t// do something\n"
                                        + "\t\t}\n"
                                        + "\t};\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testDefaultMethods() {
        if (createClient().getHighestKnownApiLevel() < 24) {
            // This test only works if you have at least Android N installed
            return;
        }

        // Default methods require minSdkVersion >= N
        String expected =
                ""
                        + "src/main/java/test/pkg/InterfaceMethodTest.java:6: Error: Default method requires API level 24 (current min is 15): InterfaceMethodTest#method2 [NewApi]\n"
                        + "    default void method2() {\n"
                        + "    ^\n"
                        + "src/main/java/test/pkg/InterfaceMethodTest.java:9: Error: Static interface method requires API level 24 (current min is 15): InterfaceMethodTest#method3 [NewApi]\n"
                        + "    static void method3() {\n"
                        + "    ^\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                "src/main/java/test/pkg/InterfaceMethodTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public interface InterfaceMethodTest {\n"
                                        + "    void someMethod();\n"
                                        + "    default void method2() {\n"
                                        + "        System.out.println(\"test\");\n"
                                        + "    }\n"
                                        // Regression test for http//b.android.com/300016
                                        + "    static void method3() {\n"
                                        + "        System.out.println(\"test\");\n"
                                        + "    }\n"
                                        + "}"),
                        gradleVersion231)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testDefaultMethodsOk() {
        // Default methods require minSdkVersion=N
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(24),
                        java(
                                "src/test/pkg/InterfaceMethodTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public interface InterfaceMethodTest {\n"
                                        + "    void someMethod();\n"
                                        + "    default void method2() {\n"
                                        + "        System.out.println(\"test\");\n"
                                        + "    }\n"
                                        + "    static void method3() {\n"
                                        + "        System.out.println(\"test\");\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testRepeatableAnnotations() {
        if (createClient().getHighestKnownApiLevel() < 24) {
            // This test only works if you have at least Android N installed
            return;
        }

        // Repeatable annotations require minSdkVersion >= N
        String expected =
                ""
                        + "src/main/java/test/pkg/MyAnnotation.java:5: Error: Repeatable annotation requires API level 24 (current min is 15) [NewApi]\n"
                        + "@Repeatable(android.annotation.SuppressLint.class)\n"
                        + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                "src/main/java/test/pkg/MyAnnotation.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.lang.annotation.Repeatable;\n"
                                        + "\n"
                                        + "@Repeatable(android.annotation.SuppressLint.class)\n"
                                        + "public @interface MyAnnotation {\n"
                                        + "    int test() default 1;\n"
                                        + "}"),
                        gradleVersion231)
                .allowCompilationErrors(true)
                .allowSystemErrors(false)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testAnonymousInherited() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=172621
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "import android.view.ViewTreeObserver;\n"
                                        + "import android.widget.ListView;\n"
                                        + "\n"
                                        + "public class Test extends ListView {\n"
                                        + "\n"
                                        + "    public Test(Context context, AttributeSet attrs) {\n"
                                        + "        super(context, attrs);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void doSomething() {\n"
                                        + "        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {\n"
                                        + "            @Override\n"
                                        + "            public boolean onPreDraw() {\n"
                                        + "                setSelectionFromTop(0, 0);\n"
                                        + "                return true;\n"
                                        + "            }\n"
                                        + "         });\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testUpdatedDescriptions() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=78495
        // Without this fix, the required API level for getString would be 21 instead of 12
        String expected =
                ""
                        + "src/test/pkg/Test.java:5: Error: Class requires API level 11 (current min is 1): android.app.Fragment [NewApi]\n"
                        + "public class Test extends Fragment {\n"
                        + "                          ~~~~~~~~\n"
                        + "src/test/pkg/Test.java:11: Error: Call requires API level 12 (current min is 1): android.os.Bundle#getString [NewApi]\n"
                        + "            mCurrentPhotoPath = savedInstanceState.getString(\"mCurrentPhotoPath\", \"\");\n"
                        + "                                                   ~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.app.Fragment;\n"
                                        + "import android.os.Bundle;\n"
                                        + "\n"
                                        + "public class Test extends Fragment {\n"
                                        + "    private String mCurrentPhotoPath = \"\";\n"
                                        + "    @Override\n"
                                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        if (savedInstanceState != null) {\n"
                                        + "            mCurrentPhotoPath = savedInstanceState.getString(\"mCurrentPhotoPath\", \"\");\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testListView() {
        // Regression test for 56236: AbsListView#getChoiceMode incorrectly requires API 11
        String expected =
                ""
                        + "src/p1/p2/Test.java:22: Error: Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode [NewApi]\n"
                        + "      if (this.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                        + "               ~~~~~~~~~~~~~\n"
                        + "src/p1/p2/Test.java:24: Error: Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode [NewApi]\n"
                        + "      if (getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                        + "          ~~~~~~~~~~~~~\n"
                        + "src/p1/p2/Test.java:26: Error: Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode [NewApi]\n"
                        + "      if (super.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                        + "                ~~~~~~~~~~~~~\n"
                        + "src/p1/p2/Test.java:29: Error: Call requires API level 11 (current min is 1): android.widget.AbsListView#getChoiceMode [NewApi]\n"
                        + "      if (view.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                        + "               ~~~~~~~~~~~~~\n"
                        + "4 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package p1.p2;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "import android.widget.AbsListView;\n"
                                        + "import android.widget.ListAdapter;\n"
                                        + "import android.widget.ListView;\n"
                                        + "\n"
                                        + "public class Test {\n"
                                        + "  private class MyAbsListView extends AbsListView {\n"
                                        + "    private MyAbsListView(Context context, AttributeSet attrs, int defStyle) {\n"
                                        + "      super(context, attrs, defStyle);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public ListAdapter getAdapter() {\n"
                                        + "      return null;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void setSelection(int i) {\n"
                                        + "      if (this.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "      if (getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "      if (super.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "      AbsListView view = (AbsListView) getEmptyView();\n"
                                        + "      if (view.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "    }\n"
                                        + "  }\n"
                                        + "\n"
                                        + "  private class MyListView extends ListView {\n"
                                        + "    private MyListView(Context context, AttributeSet attrs, int defStyle) {\n"
                                        + "      super(context, attrs, defStyle);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public ListAdapter getAdapter() {\n"
                                        + "      return null;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void setSelection(int i) {\n"
                                        + "      if (this.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "      if (getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "      if (super.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "      ListView view = (ListView) getEmptyView();\n"
                                        + "      if (view.getChoiceMode() != ListView.CHOICE_MODE_NONE) {\n"
                                        + "      }\n"
                                        + "    }\n"
                                        + "  }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testMovedField() {
        // Constant moved up to super interface in API 29; see b/154635330
        String expected =
                ""
                        + "src/test/pkg/Test.java:7: Warning: Field requires API level 29 (current min is 1): android.provider.MediaStore.MediaColumns#BUCKET_DISPLAY_NAME [InlinedApi]\n"
                        + "        System.out.println(media.BUCKET_DISPLAY_NAME); // ERROR - req 29\n"
                        + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/Test.java:9: Warning: Field requires API level 3 (current min is 1): android.provider.MediaStore.Video.VideoColumns#BUCKET_DISPLAY_NAME [InlinedApi]\n"
                        + "        System.out.println(video.BUCKET_DISPLAY_NAME); // ERROR - req 3\n"
                        + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings";
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.provider.MediaStore.Images.ImageColumns;\n"
                                        + "import android.provider.MediaStore.MediaColumns;\n"
                                        + "import android.provider.MediaStore.Video.VideoColumns;\n"
                                        + "public class Test {\n"
                                        + "    public void test(MediaColumns media, ImageColumns image, VideoColumns video) {\n"
                                        + "        System.out.println(media.BUCKET_DISPLAY_NAME); // ERROR - req 29\n"
                                        + "        System.out.println(image.BUCKET_DISPLAY_NAME); // OK\n"
                                        + "        System.out.println(video.BUCKET_DISPLAY_NAME); // ERROR - req 3\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testMovedField2() {
        // Regression test for https://issuetracker.google.com/139695984
        // The baseIntent field moved up from ActivityManager.RecentTaskInfo
        // into new super class TaskInfo; resolve will point to the new
        // field in the new class and get flagged, but if you're referencing
        // the field via ActivityManager.taskInfo there's no problem.
        // But if we explicitly access it via the new TaskInfo class it will
        // crash and should be flagged.
        lint().files(
                        manifest().minSdk(21),
                        gradle(
                                ""
                                        + "apply plugin: 'com.android.application'\n"
                                        + "\n"
                                        + "android {\n"
                                        + "    compileSdkVersion '29'\n"
                                        + "    defaultConfig {\n"
                                        + "        minSdkVersion 21\n"
                                        + "        targetSdkVersion 29\n"
                                        + "    }\n"
                                        + "}\n"),
                        kotlin(
                                ""
                                        + "package com.example.myapplication\n"
                                        + "\n"
                                        + "import android.app.ActivityManager\n"
                                        + "\n"
                                        + "fun testRecentTaskInfo(activityManager: ActivityManager) {\n"
                                        + "    // In running tasks all these fields are available since API 1\n"
                                        + "    activityManager.appTasks.first()?.taskInfo?.let {\n"
                                        + "        val baseIntent = it.baseIntent // OK\n"
                                        + "        val baseActivity = it.baseActivity // since 23\n"
                                        + "        val numActivities = it.numActivities // since 23\n"
                                        + "        val topActivity = it.topActivity // since 23\n"
                                        + "        val affiliatedTaskId = it.affiliatedTaskId // since 21\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "\n"
                                        + "fun testRunningInfo(activityManager: ActivityManager) {\n"
                                        + "    // In running tasks all these fields are available since API 1\n"
                                        + "    activityManager.getRunningTasks(4).first()?.let {\n"
                                        + "        val baseActivity = it.baseActivity // OK\n"
                                        + "        val numActivities = it.numActivities // OK\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(
                        ""
                                + "src/main/kotlin/com/example/myapplication/test.kt:9: Error: Field requires API level 23 (current min is 21): android.app.ActivityManager.RecentTaskInfo#baseActivity [NewApi]\n"
                                + "        val baseActivity = it.baseActivity // since 23\n"
                                + "                           ~~~~~~~~~~~~~~~\n"
                                + "src/main/kotlin/com/example/myapplication/test.kt:10: Error: Field requires API level 23 (current min is 21): android.app.ActivityManager.RecentTaskInfo#numActivities [NewApi]\n"
                                + "        val numActivities = it.numActivities // since 23\n"
                                + "                            ~~~~~~~~~~~~~~~~\n"
                                + "src/main/kotlin/com/example/myapplication/test.kt:11: Error: Field requires API level 23 (current min is 21): android.app.ActivityManager.RecentTaskInfo#topActivity [NewApi]\n"
                                + "        val topActivity = it.topActivity // since 23\n"
                                + "                          ~~~~~~~~~~~~~~\n"
                                + "3 errors, 0 warnings");
    }

    public void testKotlinVirtualDispatch() {
        // Regression test for https://issuetracker.google.com/64528052
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.os.Bundle\n"
                                        + "\n"
                                        + "fun test() {\n"
                                        + "    Bundle().apply {\n"
                                        + "        putString(\"\",\"\")\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testSuspendFunctions() {
        // Regression test for b/141622949 along with some additional scenarios from
        // failing metalava tests
        lint().files(
                        manifest().minSdk(19),
                        kotlin(
                                ""
                                        + "@file:Suppress(\"NOTHING_TO_INLINE\", \"RedundantVisibilityModifier\", \"unused\")\n"
                                        + "package test.pkg\n"
                                        + "import android.content.Context\n"
                                        + "import android.net.ConnectivityManager\n"
                                        + "class MainActivity : android.app.Activity() {\n"
                                        + "    val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager\n"
                                        + "    fun works1() = cm.activeNetwork\n"
                                        + "    private fun works2() = cm.activeNetwork\n"
                                        + "    suspend fun works3() = cm.activeNetwork\n"
                                        + "    private suspend fun fails() = cm.activeNetwork\n"
                                        + "\n"
                                        + "    inline fun <T> a(t: T) = cm.activeNetwork\n"
                                        + "    inline fun <reified T> b(t: T) = cm.activeNetwork\n"
                                        + "    private inline fun <reified T> c(t: T) = cm.activeNetwork\n"
                                        + "    internal inline fun <reified T> d(t: T) = cm.activeNetwork\n"
                                        + "    public inline fun <reified T> e(t: T) = cm.activeNetwork\n"
                                        + "    inline fun <reified T> T.f(t: T) = cm.activeNetwork\n"
                                        + "}"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/MainActivity.kt:7: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    fun works1() = cm.activeNetwork\n"
                                + "                      ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:8: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    private fun works2() = cm.activeNetwork\n"
                                + "                              ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:9: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    suspend fun works3() = cm.activeNetwork\n"
                                + "                              ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:10: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    private suspend fun fails() = cm.activeNetwork\n"
                                + "                                     ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:12: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    inline fun <T> a(t: T) = cm.activeNetwork\n"
                                + "                                ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:13: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    inline fun <reified T> b(t: T) = cm.activeNetwork\n"
                                + "                                        ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:14: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    private inline fun <reified T> c(t: T) = cm.activeNetwork\n"
                                + "                                                ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:15: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    internal inline fun <reified T> d(t: T) = cm.activeNetwork\n"
                                + "                                                 ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:16: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    public inline fun <reified T> e(t: T) = cm.activeNetwork\n"
                                + "                                               ~~~~~~~~~~~~~\n"
                                + "src/test/pkg/MainActivity.kt:17: Error: Call requires API level 23 (current min is 19): android.net.ConnectivityManager#getActiveNetwork [NewApi]\n"
                                + "    inline fun <reified T> T.f(t: T) = cm.activeNetwork\n"
                                + "                                          ~~~~~~~~~~~~~\n"
                                + "10 errors, 0 warnings");
    }

    public void testReifiedFunctions() {
        // Regression test for
        // https://youtrack.jetbrains.com/issue/KT-34316
        lint().files(
                        manifest().minSdk(19),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "import android.content.Context\n"
                                        + "inline fun <reified T> Context.systemService1() = getSystemService(T::class.java)\n"
                                        + "inline fun Context.systemService2() = getSystemService(String::class.java)"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/.java).kt:3: Error: Call requires API level 23 (current min is 19): android.content.Context#getSystemService [NewApi]\n"
                                + "inline fun <reified T> Context.systemService1() = getSystemService(T::class.java)\n"
                                + "                                                  ~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/.java).kt:4: Error: Call requires API level 23 (current min is 19): android.content.Context#getSystemService [NewApi]\n"
                                + "inline fun Context.systemService2() = getSystemService(String::class.java)\n"
                                + "                                      ~~~~~~~~~~~~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testThisCall() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=93158
        // Make sure we properly resolve super classes in Class.this.call()
        String expected =
                ""
                        + "src/p1/p2/Class.java:8: Error: Call requires API level 3 (current min is 1): android.app.Activity#hasWindowFocus [NewApi]\n"
                        + "    if (activity.hasWindowFocus()) {\n"
                        + "                 ~~~~~~~~~~~~~~\n"
                        + "src/p1/p2/Class.java:15: Error: Call requires API level 3 (current min is 1): android.app.Activity#hasWindowFocus [NewApi]\n"
                        + "        if (hasWindowFocus()) {\n"
                        + "            ~~~~~~~~~~~~~~\n"
                        + "src/p1/p2/Class.java:19: Error: Call requires API level 3 (current min is 1): android.app.Activity#hasWindowFocus [NewApi]\n"
                        + "        if (Class.super.hasWindowFocus()) {\n"
                        + "                        ~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package p1.p2;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.Service;\n"
                                        + "\n"
                                        + "public class Class extends Activity {\n"
                                        + "  public void test(final Activity activity, WebView webView) {\n"
                                        + "    if (activity.hasWindowFocus()) {\n"
                                        + "      return;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    webView.setWebChromeClient(new WebChromeClient() {\n"
                                        + "      @Override\n"
                                        + "      public void onProgressChanged(WebView view, int newProgress) {\n"
                                        + "        if (hasWindowFocus()) {\n"
                                        + "          return;\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Class.super.hasWindowFocus()) {\n"
                                        + "          return;\n"
                                        + "        }\n"
                                        + "        foo();\n"
                                        + "      }\n"
                                        + "    });\n"
                                        + "  }\n"
                                        + "\n"
                                        + "  public void foo() {\n"
                                        + "  }\n"
                                        + "\n"
                                        + "  private static abstract class WebView extends Service {\n"
                                        + "    public abstract void setWebChromeClient(WebChromeClient client);\n"
                                        + "  }\n"
                                        + "\n"
                                        + "  private static abstract class WebChromeClient {\n"
                                        + "    public abstract void onProgressChanged(WebView view, int newProgress);\n"
                                        + "  }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testReflectiveOperationException() {
        String expected =
                ""
                        + "src/test/pkg/Java7API.java:8: Error: Exception requires API level 19 (current min is 1): java.lang.ReflectiveOperationException [NewApi]\n"
                        + "        } catch (ReflectiveOperationException e) {\n"
                        + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        lint().files(manifest().minSdk(1), mJava7API)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testReflectiveOperationExceptionOk() {
        lint().files(manifest().minSdk(19), mJava7API)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testRipple() {
        String expected =
                ""
                        + "res/drawable/ripple.xml:1: Error: <ripple> requires API level 21 (current min is 14) [NewApi]\n"
                        + "<ripple\n"
                        + " ~~~~~~\n"
                        + "1 errors, 0 warnings";
        lint().files(manifest().minSdk(14), mRipple)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testRippleOk1() {
        // minSdkVersion satisfied
        lint().files(manifest().minSdk(21), mRipple)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testRippleOk2() {
        // -vNN location satisfied
        lint().files(manifest().minSdk(4), mRipple2)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testVector() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "res/drawable/vector.xml:1: Error: <vector> requires API level 21 (current min is 4) or building with Android Gradle plugin 1.4 or higher [NewApi]\n"
                        + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                        + " ~~~~~~\n"
                        + "1 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(manifest().minSdk(4), mVector)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testVector_withGradleSupport() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        xml("src/main/" + mVector.targetRelativePath, mVector.contents),
                        gradle(
                                ""
                                        + "buildscript {\n"
                                        + "    dependencies {\n"
                                        + "        classpath 'com.android.tools.build:gradle:1.4.0-alpha1'\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testAnimatedSelector() {
        String expected =
                ""
                        + "res/drawable/animated_selector.xml:1: Error: <animated-selector> requires API level 21 (current min is 14) [NewApi]\n"
                        + "<animated-selector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + " ~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(14),
                        xml(
                                "res/drawable/animated_selector.xml",
                                ""
                                        + "<animated-selector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:constantSize=\"true\">\n"
                                        + "    <item android:state_enabled=\"false\" android:id=\"@+id/off\">\n"
                                        + "        <nine-patch\n"
                                        + "            android:src=\"@drawable/btn_switch_to_on_mtrl_00001\"\n"
                                        + "            android:gravity=\"center\"\n"
                                        + "            android:tintMode=\"multiply\"\n"
                                        + "            android:tint=\"?attr/colorSwitchThumbNormal\" />\n"
                                        + "    </item>\n"
                                        + "    <item\n"
                                        + "        android:state_checked=\"true\"\n"
                                        + "        android:id=\"@+id/on\">\n"
                                        + "        <nine-patch\n"
                                        + "            android:src=\"@drawable/btn_switch_to_on_mtrl_00012\"\n"
                                        + "            android:gravity=\"center\"\n"
                                        + "            android:tintMode=\"multiply\"\n"
                                        + "            android:tint=\"?attr/colorControlActivated\" />\n"
                                        + "    </item>\n"
                                        + "    <item android:id=\"@+id/off\">\n"
                                        + "        <nine-patch\n"
                                        + "            android:src=\"@drawable/btn_switch_to_on_mtrl_00001\"\n"
                                        + "            android:gravity=\"center\"\n"
                                        + "            android:tintMode=\"multiply\"\n"
                                        + "            android:tint=\"?attr/colorSwitchThumbNormal\" />\n"
                                        + "    </item>\n"
                                        + "    <transition\n"
                                        + "        android:fromId=\"@+id/off\"\n"
                                        + "        android:toId=\"@+id/on\">\n"
                                        + "        <animation-list>\n"
                                        + "            <item android:duration=\"15\">\n"
                                        + "                <nine-patch android:src=\"@drawable/btn_switch_to_on_mtrl_00001\" android:gravity=\"center\" android:tintMode=\"multiply\" android:tint=\"?attr/colorSwitchThumbNormal\" />\n"
                                        + "            </item>\n"
                                        + "            <item android:duration=\"15\">\n"
                                        + "                <nine-patch android:src=\"@drawable/btn_switch_to_on_mtrl_00002\" android:gravity=\"center\" android:tintMode=\"multiply\" android:tint=\"?attr/colorSwitchThumbNormal\" />\n"
                                        + "            </item>\n"
                                        + "        </animation-list>\n"
                                        + "    </transition>\n"
                                        + "    <transition android:fromId=\"@+id/on\" android:toId=\"@+id/off\">\n"
                                        + "        <animation-list>\n"
                                        + "            <item android:duration=\"15\">\n"
                                        + "                <nine-patch android:src=\"@drawable/btn_switch_to_off_mtrl_00001\" android:gravity=\"center\" android:tintMode=\"multiply\" android:tint=\"?attr/colorControlActivated\" />\n"
                                        + "            </item>\n"
                                        + "            <item android:duration=\"15\">\n"
                                        + "                <nine-patch android:src=\"@drawable/btn_switch_to_off_mtrl_00002\" android:gravity=\"center\" android:tintMode=\"multiply\" android:tint=\"?attr/colorControlActivated\" />\n"
                                        + "            </item>\n"
                                        + "        </animation-list>\n"
                                        + "    </transition>\n"
                                        + "</animated-selector>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testAnimatedVector() {
        String expected =
                ""
                        + "res/drawable/animated_vector.xml:1: Error: <animated-vector> requires API level 21 (current min is 14) [NewApi]\n"
                        + "<animated-vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + " ~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(14),
                        xml(
                                "res/drawable/animated_vector.xml",
                                ""
                                        + "<animated-vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:drawable=\"@drawable/vector_drawable_progress_bar_large\" >\n"
                                        + "    <target\n"
                                        + "        android:name=\"progressBar\"\n"
                                        + "        android:animation=\"@anim/progress_indeterminate_material\" />\n"
                                        + "    <target\n"
                                        + "        android:name=\"root\"\n"
                                        + "        android:animation=\"@anim/progress_indeterminate_rotation_material\" />\n"
                                        + "</animated-vector>\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testGradient() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(14),
                        xml(
                                "src/main/res/drawable/gradient.xml",
                                ""
                                        + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "        xmlns:aapt=\"http://schemas.android.com/aapt\"\n"
                                        + "        android:height=\"76dp\"\n"
                                        + "        android:width=\"76dp\"\n"
                                        + "        android:viewportHeight=\"48\"\n"
                                        + "        android:viewportWidth=\"48\"\n"
                                        + "        android:tint=\"?attr/colorControlActivated\">\n"
                                        + "\n"
                                        + "    <clip-path android:pathData=\"M10,10h40v30h-40z\"/>\n"
                                        + "\n"
                                        + "    <group\n"
                                        + "            android:name=\"root\"\n"
                                        + "            android:translateX=\"24.0\"\n"
                                        + "            android:translateY=\"24.0\" >\n"
                                        + "        <path android:pathData=\"M10,10h40v30h-40z\">\n"
                                        + "            <aapt:attr name=\"android:fillColor\">\n"
                                        + "                <gradient android:startY=\"10\" android:startX=\"10\" android:endY=\"40\" android:endX=\"10\">\n"
                                        + "                    <item android:offset=\"0\" android:color=\"#FFFF0000\"/>\n"
                                        + "                    <item android:offset=\"1\" android:color=\"#FFFFFF00\"/>\n"
                                        + "                </gradient>\n"
                                        + "            </aapt:attr>\n"
                                        + "        </path>\n"
                                        + "    </group>\n"
                                        + "\n"
                                        + "</vector>\n"),
                        gradle(
                                "apply plugin: 'com.android.application'\n"
                                        + "dependencies {\n"
                                        + "    compile 'com.android.support:appcompat-v7:+'\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testPaddingStart() {
        String expected =
                ""
                        + "res/layout/padding_start.xml:14: Error: Attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                        + "            android:paddingStart=\"20dp\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/padding_start.xml:21: Error: Attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                        + "            android:paddingStart=\"20dp\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/padding_start.xml:28: Error: Attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                        + "            android:paddingStart=\"20dp\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n";
        lint().files(manifest().minSdk(4), mPadding_start)
                .client(
                        new com.android.tools.lint.checks.infrastructure.TestLintClient() {
                            @NonNull
                            @Override
                            protected Project createProject(
                                    @NonNull File dir, @NonNull File referenceDir) {
                                Project fromSuper = super.createProject(dir, referenceDir);
                                Project spy = spy(fromSuper);
                                when(spy.getBuildToolsRevision()).thenReturn(null);
                                return spy;
                            }
                        })
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testPaddingStartNotApplicable() {
        lint().files(manifest().minSdk(4), mPadding_start2)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testPaddingStartWithOldBuildTools() {
        String expected =
                ""
                        + "res/layout/padding_start.xml:14: Error: Upgrade buildToolsVersion from 22.2.1 to at least 23.0.1; if not, attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                        + "            android:paddingStart=\"20dp\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/padding_start.xml:21: Error: Upgrade buildToolsVersion from 22.2.1 to at least 23.0.1; if not, attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                        + "            android:paddingStart=\"20dp\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/padding_start.xml:28: Error: Upgrade buildToolsVersion from 22.2.1 to at least 23.0.1; if not, attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                        + "            android:paddingStart=\"20dp\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n";
        lint().files(manifest().minSdk(4), mPadding_start)
                .client(
                        new com.android.tools.lint.checks.infrastructure.TestLintClient() {
                            @NonNull
                            @Override
                            protected Project createProject(
                                    @NonNull File dir, @NonNull File referenceDir) {
                                Revision revision = new Revision(22, 2, 1);
                                Project fromSuper = super.createProject(dir, referenceDir);
                                Project spy = spy(fromSuper);
                                when(spy.getBuildToolsRevision()).thenReturn(revision);
                                return spy;
                            }
                        })
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testPaddingStartWithNewBuildTools() {
        lint().files(manifest().minSdk(4), mPadding_start)
                .client(
                        new com.android.tools.lint.checks.infrastructure.TestLintClient() {
                            @NonNull
                            @Override
                            protected Project createProject(
                                    @NonNull File dir, @NonNull File referenceDir) {
                                Revision revision = new Revision(23, 0, 2);
                                Project fromSuper = super.createProject(dir, referenceDir);
                                Project spy = spy(fromSuper);
                                when(spy.getBuildToolsRevision()).thenReturn(revision);
                                return spy;
                            }
                        })
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testSwitch() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.TargetApi;\n"
                                        + "import android.graphics.Bitmap;\n"
                                        + "import android.os.Build;\n"
                                        + "\n"
                                        + "public class TargetApiTest {\n"
                                        + "    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)\n"
                                        + "    public static String getCompressFormatMimeType(Bitmap.CompressFormat format) {\n"
                                        + "        switch (format) {\n"
                                        + "            case JPEG:\n"
                                        + "                return \"image/jpeg\";\n"
                                        + "            case PNG:\n"
                                        + "                return \"image/png\";\n"
                                        + "            case WEBP:\n"
                                        + "                return \"image/webp\";\n"
                                        + "        }\n"
                                        + "        // Unreachable\n"
                                        + "        throw new IllegalArgumentException(\"Unexpected CompressFormat: \" + format);\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testGravity() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.view.Gravity;\n"
                                        + "import android.widget.TextView;\n"
                                        + "\n"
                                        + "public class GravityTest extends Activity {\n"
                                        + "    @SuppressLint(\"RtlHardcoded\")\n"
                                        + "    public void test() {\n"
                                        + "        TextView textView = new TextView(this);\n"
                                        + "        textView.setGravity(Gravity.LEFT);\n"
                                        + "        textView.setGravity(Gravity.RIGHT);\n"
                                        + "        textView.setGravity(Gravity.START);\n"
                                        + "        textView.setGravity(Gravity.END);\n"
                                        + "        textView.setGravity(Gravity.END);\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testSuperCall() {
        String expected =
                ""
                        + "src/test/pkg/SuperCallTest.java:20: Error: Call requires API level 21 (current min is 19): android.service.wallpaper.WallpaperService.Engine#onApplyWindowInsets [NewApi]\n"
                        + "            super.onApplyWindowInsets(insets); // Error\n"
                        + "                  ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SuperCallTest.java:27: Error: Call requires API level 21 (current min is 19): android.service.wallpaper.WallpaperService.Engine#onApplyWindowInsets [NewApi]\n"
                        + "            onApplyWindowInsets(insets); // Error: not overridden\n"
                        + "            ~~~~~~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(19),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.service.wallpaper.WallpaperService;\n"
                                        + "import android.view.WindowInsets;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"UnusedDeclaration\")\n"
                                        + "public class SuperCallTest extends WallpaperService {\n"
                                        + "    @Override\n"
                                        + "    public Engine onCreateEngine() {\n"
                                        + "        return new MyEngine1();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private class MyEngine1 extends WallpaperService.Engine {\n"
                                        + "        @Override\n"
                                        + "        public void onApplyWindowInsets(WindowInsets insets) {\n"
                                        + "            super.onApplyWindowInsets(insets); // OK\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        public void notSameMethod(WindowInsets insets) {\n"
                                        + "            super.onApplyWindowInsets(insets); // Error\n"
                                        + "            onApplyWindowInsets(insets); // OK: overridden. This should arguably be an error.\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private class MyEngine2 extends Engine {\n"
                                        + "        public void notSameMethod(WindowInsets insets) {\n"
                                        + "            onApplyWindowInsets(insets); // Error: not overridden\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testSuperClassInLibrary() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=97006
        // 97006: Gradle lint does not recognize Context.getDrawable() as API 21+
        String expected =
                ""
                        + "src/test/pkg/MyFragment.java:10: Error: Call requires API level 21 (current min is 14): android.content.Context#getDrawable [NewApi]\n"
                        + "        getActivity().getDrawable(R.color.my_color);\n"
                        + "                      ~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().pkg("foo.main").minSdk(14),
                        projectProperties()
                                .property("android.library.reference.1", "../LibraryProject"),
                        java(
                                ""
                                        + "package foo.main;\n"
                                        + "\n"
                                        + "public class MainCode {\n"
                                        + "    static {\n"
                                        + "        System.out.println(R.string.string2);\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package foo.main;\n"
                                        + "public class R {\n"
                                        + "    public static class color {\n"
                                        + "        public static final int my_color = 0x7f070031;\n"
                                        + "    }\n"
                                        + "    public static class string {\n"
                                        + "        public static final int string2 = 0x7f070032;\n"
                                        + "    }\n"
                                        + "}\n"
                                        + ""),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.support.v4.app.Fragment;\n"
                                        + "\n"
                                        + "public class MyFragment extends Fragment {\n"
                                        + "    public MyFragment() {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test() {\n"
                                        + "        getActivity().getDrawable(R.color.my_color);\n"
                                        + "    }\n"
                                        + "}\n"),
                        manifest()
                                .pkg("foo.library")
                                .minSdk(14)
                                .to("../LibraryProject/AndroidManifest.xml"),
                        source(
                                "../LibraryProject/project.properties",
                                ""
                                        + "# This file is automatically generated by Android Tools.\n"
                                        + "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!\n"
                                        + "#\n"
                                        + "# This file must be checked in Version Control Systems.\n"
                                        + "#\n"
                                        + "# To customize properties used by the Ant build system use,\n"
                                        + "# \"ant.properties\", and override values to adapt the script to your\n"
                                        + "# project structure.\n"
                                        + "\n"
                                        + "# Project target.\n"
                                        + "target=android-14\n"
                                        + "android.library=true\n"),
                        java(
                                ""
                                        + "package foo.library;\n"
                                        + "\n"
                                        + "public class LibraryCode {\n"
                                        + "    static {\n"
                                        + "        System.out.println(R.string.string1);\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package foo.library;\n"
                                        + "public class R {\n"
                                        + "    public static class string {\n"
                                        + "        public static final int string1 = 0x7f070033;\n"
                                        + "    }\n"
                                        + "}\n"
                                        + ""),
                        xml(
                                "../LibraryProject/res/values/strings.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources>\n"
                                        + "\n"
                                        + "    <string name=\"app_name\">LibraryProject</string>\n"
                                        + "    <string name=\"string1\">String 1</string>\n"
                                        + "    <string name=\"string2\">String 2</string>\n"
                                        + "    <string name=\"string3\">String 3</string>\n"
                                        + "\n"
                                        + "</resources>\n"),
                        base64gzip(
                                "../LibraryProject/libs/fragment_support.jar",
                                ""
                                        + "H4sIAAAAAAAAAAvwZmYRYeAAQuV+PTcGJMDJwMLg6xriqOvp56b/7xQDAzND"
                                        + "gDc7B0iKCaokAKdmESCGa/Z19PN0cw0O0fN1++x75rSPt67eRV5vXa1zZ85v"
                                        + "DjK4YvzgaZGel6+Op+/F0lUsnDNeSh6ZPVMrw0JM5MkSrYpnqq8zPxV9LGIE"
                                        + "217vbnHcBmi2DdR2LrBttztRbQc5MjEvpSg/M0UftyoBJFXFpQUF+UUleFQL"
                                        + "Y1FdZoKkoaoXVYM4dg2JBQX6iHBD16SKW5NbUWJ6bmpeiV5yTmJx8dTAWG8m"
                                        + "RxHbzbt37VqdGRbm/3C2/EWHL2Ecgj9Uvgb8sFvX3fvi3aY+7r+rm5ReLJS/"
                                        + "0N/1bs2swMajB/4+mlN8vuD+vfg4Rpnk+42tc218QnLf3LqRfML/nK2q8Mp3"
                                        + "ElKxgjyPAvh+lIi5Jq6pKDrkKvLRT6dN2D7A+4Wm8zcftqCtkmeqFh9PiG1h"
                                        + "nr/wytTGesvZX/7+cPty3GRlibN/T7XStF3xPLql2st5tpb+zui3/nr11MPE"
                                        + "v+fFtq6e8yNl1+H1Vmt/1f97FdO++cZn66BqfT1Gd+WJHUw5T7hOKJwu2OA2"
                                        + "ceEazRqdJsX9r/pr/CNNd7W6bk802MIRqnZ1iaehv+zyMxrps84n8IdIFGik"
                                        + "XV7IOT0pkW9BjE+rWUDUhNstZia7blY1bUyyfnlFzU8vdMW5QG7W2KmXeWd/"
                                        + "0bLvsTm043/akb9XT4R13foHTk4bjytmOjEyMNQxISdm9EjRJRwpjsklmWWZ"
                                        + "JZWQyOn1P+h12EGg9vKyzt6LXS/YKy59e9BUsZlD6YvIni1f9vmKzb2UdrhM"
                                        + "+MNmG065X4x1Uyw63y5VT39g+P5sNfPnv5+3sha0b5aIneNi0Wug0JKsd8Ne"
                                        + "aJf5YUvFim07FXmC9M54b42TML/cPVXj+cqzX7LWyZrXqLP96zVvSVzVfSfy"
                                        + "3NVlWt6v96x6P1XP27TwV2ku1/8pletu7rcXeLRybUya2p0n+vy8271jX7Mw"
                                        + "VrW8rpdTjXkz92yV/mMu2cotmpOmTW5aaHC1aIH5L5baExccVST+dVoI+ZQV"
                                        + "yxdK2+fFntQHBZrMcv2zt4BB4swICjRGJhEG1DIAVjqAChBUgFKcoGtFztoi"
                                        + "KNpscRQmIBO4GHAXAQiwG6VAwK1LAEXXQyzxjk+3MIpufkYcBQbCAGxFBgI4"
                                        + "YDcAUoAgwg5kCHI6VkUxpBS3IagFCrqRyLGsi2IkFzOJ2SHAm5UNEiccDKpA"
                                        + "F9qD0wUAV7HJDXoGAAA="))
                .allowCompilationErrors(true)
                .allowSystemErrors(false)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testTargetInLambda() {
        // Regression test for
        // 226364: TargetAPI annotation on method doesn't apply to lambda
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.TargetApi;\n"
                                        + "import android.os.Build;\n"
                                        + "import android.widget.TextView;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class MyLambdaTest {\n"
                                        + "    @TargetApi(Build.VERSION_CODES.LOLLIPOP)\n"
                                        + "    public void test(TextView textView) {\n"
                                        + "        test(new Runnable() {\n"
                                        + "            @Override\n"
                                        + "            public void run() {\n"
                                        + "                textView.setLetterSpacing(1f);\n"
                                        + "            }\n"
                                        + "        });\n"
                                        + "        test(() -> textView.setLetterSpacing(1f));\n"
                                        + "        textView.setLetterSpacing(1f);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test(Runnable runnable) {\n"
                                        + "        runnable.run();\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testConditionalAroundException() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=220968
        // 220968: Build version check not work on exception
        // (and https://code.google.com/p/android/issues/detail?id=209129)

        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(19), // prior to 19, version check does not prevent crash
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.hardware.camera2.CameraAccessException;\n"
                                        + "import android.hardware.camera2.CameraManager;\n"
                                        + "import android.os.Build;\n"
                                        + "\n"
                                        + "public class VersionConditionals7 extends Activity {\n"
                                        + "    public void testCamera() {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);\n"
                                        + "            try {\n"
                                        + "                int length = manager.getCameraIdList().length;\n"
                                        + "            } catch (CameraAccessException e) { // OK\n"
                                        + "                e.printStackTrace();\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testConditionalAroundExceptionSuppress() {
        // Regression test for https://issuetracker.google.com/140154274
        lint().files(
                        manifest().minSdk(17),
                        kotlin(
                                ""
                                        + "@file:Suppress(\"unused\")\n"
                                        + "\n"
                                        + "package com.example.myapplication\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint\n"
                                        + "import android.content.Context\n"
                                        + "import android.hardware.camera2.CameraAccessException\n"
                                        + "import android.hardware.camera2.CameraManager\n"
                                        + "import android.os.Build\n"
                                        + "\n"
                                        + "class CameraTest {\n"
                                        + "    fun hasCamera(context: Context): Boolean {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            val camerasIds: Array<String>\n"
                                        + "            try {\n"
                                        + "                val cameraManager =\n"
                                        + "                    context.getSystemService(Context.CAMERA_SERVICE) as CameraManager\n"
                                        + "                camerasIds = cameraManager.cameraIdList\n"
                                        + "                return camerasIds.isNotEmpty()\n"
                                        + "            } catch (@SuppressLint(\"NewApi\") e: CameraAccessException) {\n"
                                        + "                e.printStackTrace()\n"
                                        + "            }\n"
                                        + "\n"
                                        + "        }\n"
                                        + "        val numberOfCameras = android.hardware.Camera.getNumberOfCameras()\n"
                                        + "        return numberOfCameras > 0\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.hardware.camera2.CameraAccessException;\n"
                                        + "import android.hardware.camera2.CameraManager;\n"
                                        + "import android.os.Build;\n"
                                        + "\n"
                                        + "public class CameraTest2 {\n"
                                        + "    boolean hasCamera(Context context) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            String[] camerasIds;\n"
                                        + "            try {\n"
                                        + "                CameraManager cameraManager =\n"
                                        + "                        (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);\n"
                                        + "                if (cameraManager == null) {\n"
                                        + "                    return false;\n"
                                        + "                }\n"
                                        + "                camerasIds = cameraManager.getCameraIdList();\n"
                                        + "                return camerasIds.length > 0;\n"
                                        + "            } catch (@SuppressLint(\"NewApi\") CameraAccessException e) {\n"
                                        + "                e.printStackTrace();\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "        int numberOfCameras = android.hardware.Camera.getNumberOfCameras();\n"
                                        + "        return numberOfCameras > 0;\n"
                                        + "    }\n"
                                        + "}\n")
                                .indented())
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testMethodReferences() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=219413
        String expected =
                ""
                        + "src/test/pkg/Class.java:7: Error: Method reference requires API level 17 (current min is 4): TextView::getCompoundPaddingEnd [NewApi]\n"
                        + "        System.out.println(TextView::getCompoundPaddingEnd);\n"
                        + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.widget.TextView;\n"
                                        + "\n"
                                        + "@SuppressWarnings({\"unused\",\"WeakerAccess\"})\n"
                                        + "public class Class {\n"
                                        + "    protected void test(TextView textView) {\n"
                                        + "        System.out.println(TextView::getCompoundPaddingEnd);\n"
                                        + "    }\n"
                                        + "}"))
                .allowCompilationErrors(true)
                .allowSystemErrors(false)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testLambdas() {
        String expected =
                ""
                        + "src/test/pkg/LambdaTest.java:9: Error: Call requires API level 23 (current min is 1): android.view.View#performContextClick [NewApi]\n"
                        + "    private View.OnTouchListener myListener = (v, event) -> v.performContextClick();\n"
                        + "                                                              ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/LambdaTest.java:12: Error: Call requires API level 24 (current min is 1): java.util.Map#forEach [NewApi]\n"
                        + "        map.forEach((t, u) -> Log.i(\"tag\", t + u));\n"
                        + "            ~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.util.Log;\n"
                                        + "import android.view.View;\n"
                                        + "\n"
                                        + "import java.util.Map;\n"
                                        + "\n"
                                        + "public class LambdaTest {\n"
                                        + "    private View.OnTouchListener myListener = (v, event) -> v.performContextClick();\n"
                                        + "\n"
                                        + "    public void apiCheck(Map<String,String> map) {\n"
                                        + "        map.forEach((t, u) -> Log.i(\"tag\", t + u));\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testVirtualMethods() {
        // Regression test for b/32430124
        String expected =
                ""
                        + "src/test/pkg/SupportLibTest.java:19: Error: Call requires API level 21 (current min is 4): android.graphics.drawable.Drawable#inflate [NewApi]\n"
                        + "        drawable1.inflate(resources, parser, attrs, theme); // ERROR\n"
                        + "                  ~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.res.Resources;\n"
                                        + "import android.graphics.drawable.Drawable;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "\n"
                                        + "import org.xmlpull.v1.XmlPullParser;\n"
                                        + "import org.xmlpull.v1.XmlPullParserException;\n"
                                        + "\n"
                                        + "import java.io.IOException;\n"
                                        + "\n"
                                        + "public class SupportLibTest {\n"
                                        + "    public void test(Resources resources,\n"
                                        + "                         XmlPullParser parser,\n"
                                        + "                         AttributeSet attrs,\n"
                                        + "                         Resources.Theme theme,\n"
                                        + "                         Drawable drawable1,\n"
                                        + "                         MyDrawable drawable2) throws IOException, XmlPullParserException {\n"
                                        + "        drawable1.inflate(resources, parser, attrs, theme); // ERROR\n"
                                        + "        drawable2.inflate(resources, parser, attrs, theme); // OK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private abstract static class MyDrawable extends Drawable {\n"
                                        + "\n"
                                        + "        @Override\n"
                                        + "        public void inflate(Resources r, XmlPullParser parser,\n"
                                        + "                            AttributeSet attrs, Resources.Theme theme)\n"
                                        + "                throws XmlPullParserException, IOException {\n"
                                        + "            super.inflate(r, parser, attrs, theme);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    @SuppressWarnings({"MethodMayBeStatic", "ConstantConditions", "ClassNameDiffersFromFileName"})
    public void testCastChecks() {
        // When editing a file we place the error on the first line of the file instead
        String expected =
                ""
                        + "src/test/pkg/CastTest.java:15: Error: Cast from Cursor to Closeable requires API level 16 (current min is 14) [NewApi]\n"
                        + "        Closeable closeable = (java.io.Closeable) cursor; // Requires 16\n"
                        + "                              ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/CastTest.java:21: Error: Cast from KeyCharacterMap to Parcelable requires API level 16 (current min is 14) [NewApi]\n"
                        + "        Parcelable parcelable2 = (Parcelable)map; // Requires API 16\n"
                        + "                                 ~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/CastTest.java:27: Error: Class requires API level 19 (current min is 14): android.animation.Animator.AnimatorPauseListener [NewApi]\n"
                        + "        AnimatorPauseListener listener = (AnimatorPauseListener)adapter;\n"
                        + "                                          ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/CastTest.java",
                                ""
                                        + "import android.animation.Animator.AnimatorPauseListener;\n"
                                        + "import android.animation.AnimatorListenerAdapter;\n"
                                        + "import android.database.Cursor;\n"
                                        + "import android.database.CursorWindow;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "import android.view.KeyCharacterMap;\n"
                                        + "\n"
                                        + "import java.io.Closeable;\n"
                                        + "import java.io.IOException;\n"
                                        + "\n"
                                        + "@SuppressWarnings({\"RedundantCast\", \"unused\"})\n"
                                        + "public class CastTest {\n"
                                        + "    public void test(Cursor cursor) throws IOException {\n"
                                        + "        cursor.close();\n"
                                        + "        Closeable closeable = (java.io.Closeable) cursor; // Requires 16\n"
                                        + "        closeable.close();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test(CursorWindow window, KeyCharacterMap map) {\n"
                                        + "        Parcelable parcelable1 = (Parcelable)window; // OK\n"
                                        + "        Parcelable parcelable2 = (Parcelable)map; // Requires API 16\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressWarnings(\"UnnecessaryLocalVariable\")\n"
                                        + "    public void test(AnimatorListenerAdapter adapter) {\n"
                                        + "        // Uh oh - what if the cast isn't needed anymore\n"
                                        + "        AnimatorPauseListener listener = (AnimatorPauseListener)adapter;\n"
                                        + "    }\n"
                                        + "}"),
                        manifest().minSdk(14))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    @SuppressWarnings({
        "MethodMayBeStatic",
        "ConstantConditions",
        "ClassNameDiffersFromFileName",
        "UnnecessaryLocalVariable"
    })
    public void testImplicitCastTest() {
        // When editing a file we place the error on the first line of the file instead
        String expected =
                ""
                        + "src/test/pkg/ImplicitCastTest.java:14: Error: Cast from Cursor to Closeable requires API level 16 (current min is 14) [NewApi]\n"
                        + "        Closeable closeable = c;\n"
                        + "                              ~\n"
                        + "src/test/pkg/ImplicitCastTest.java:26: Error: Cast from Cursor to Closeable requires API level 16 (current min is 14) [NewApi]\n"
                        + "        closeable = c;\n"
                        + "                    ~\n"
                        + "src/test/pkg/ImplicitCastTest.java:36: Error: Cast from ParcelFileDescriptor to Closeable requires API level 16 (current min is 14) [NewApi]\n"
                        + "        safeClose(pfd);\n"
                        + "                  ~~~\n"
                        + "src/test/pkg/ImplicitCastTest.java:47: Error: Cast from AccelerateDecelerateInterpolator to BaseInterpolator requires API level 22 (current min is 14) [NewApi]\n"
                        + "        android.view.animation.BaseInterpolator base = interpolator;\n"
                        + "                                                       ~~~~~~~~~~~~\n"
                        + "4 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/ImplicitCastTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.database.Cursor;\n"
                                        + "import android.os.ParcelFileDescriptor;\n"
                                        + "\n"
                                        + "import java.io.Closeable;\n"
                                        + "import java.io.IOException;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ImplicitCastTest {\n"
                                        + "    // https://code.google.com/p/android/issues/detail?id=174535\n"
                                        + "    @SuppressWarnings(\"UnnecessaryLocalVariable\")\n"
                                        + "    public void testImplicitCast(Cursor c) {\n"
                                        + "        Closeable closeable = c;\n"
                                        + "        try {\n"
                                        + "            closeable.close();\n"
                                        + "        } catch (IOException e) {\n"
                                        + "            e.printStackTrace();\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Like the above, but with assignment instead of initializer\n"
                                        + "    public void testImplicitCast2(Cursor c) {\n"
                                        + "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n"
                                        + "        Closeable closeable;\n"
                                        + "        closeable = c;\n"
                                        + "        try {\n"
                                        + "            closeable.close();\n"
                                        + "        } catch (IOException e) {\n"
                                        + "            e.printStackTrace();\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // https://code.google.com/p/android/issues/detail?id=191120\n"
                                        + "    public void testImplicitCast(ParcelFileDescriptor pfd) {\n"
                                        + "        safeClose(pfd);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static void safeClose(Closeable closeable) {\n"
                                        + "        try {\n"
                                        + "            closeable.close();\n"
                                        + "        } catch (IOException ignore) {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void testImplicitCast(android.view.animation.AccelerateDecelerateInterpolator interpolator) {\n"
                                        + "        android.view.animation.BaseInterpolator base = interpolator;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "}\n"),
                        manifest().minSdk(14))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    @SuppressWarnings("all") // sample code
    public void testResourceReference() {
        String expected =
                ""
                        + "src/test/pkg/TestResourceReference.java:5: Warning: Field requires API level 21 (current min is 10): android.R.interpolator#fast_out_linear_in [InlinedApi]\n"
                        + "        int id = android.R.interpolator.fast_out_linear_in;\n"
                        + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        lint().files(
                        manifest().minSdk(10),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class TestResourceReference {\n"
                                        + "    protected void test() {\n"
                                        + "        int id = android.R.interpolator.fast_out_linear_in;\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testSupportLibraryCalls() {
        // See issue 196925
        String expected =
                ""
                        + "src/test/pkg/SupportLibraryApiTest.java:22: Error: Call requires API level 21 (current min is 14): android.view.View#setBackgroundTintList [NewApi]\n"
                        + "        button.setBackgroundTintList(colors); // ERROR\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(14),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.res.ColorStateList;\n"
                                        + "import android.support.design.widget.FloatingActionButton;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "import android.widget.ImageButton;\n"
                                        + "\n"
                                        + "public class SupportLibraryApiTest extends FloatingActionButton {\n"
                                        + "     public SupportLibraryApiTest(Context context, AttributeSet attrs, int defStyleAttr) {\n"
                                        + "        super(context, attrs, defStyleAttr);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test1(ColorStateList colors) {\n"
                                        + "        setBackgroundTintList(colors); // OK: FAB overrides ImageButton with lower minSDK\n"
                                        + "        this.setBackgroundTintList(colors); // OK: FAB overrides ImageButton with lower minSDK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test2(FloatingActionButton fab, ImageButton button,\n"
                                        + "                    ColorStateList colors) {\n"
                                        + "        fab.setBackgroundTintList(colors); // OK: FAB overrides ImageButton with lower minSDK\n"
                                        + "        button.setBackgroundTintList(colors); // ERROR\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package android.support.design.widget;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.res.ColorStateList;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "import android.widget.ImageButton;\n"
                                        + "\n"
                                        + "// JUST A UNIT TESTING STUB!\n"
                                        + "public abstract class FloatingActionButton extends ImageButton {\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    public FloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {\n"
                                        + "        super(context, attrs, defStyleAttr, defStyleRes);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void setBackgroundTintList(ColorStateList tint) {\n"
                                        + "        super.setBackgroundTintList(tint);\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    @SuppressWarnings("all") // sample code
    public void testEnumInitialization() {
        String expected =
                ""
                        + "src/test/pkg/ApiDetectorTest2.java:8: Warning: Field requires API level 19 (current min is 15): android.location.LocationManager#MODE_CHANGED_ACTION [InlinedApi]\n"
                        + "    LOCATION_MODE_CHANGED(LocationManager.MODE_CHANGED_ACTION) {\n"
                        + "                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                "src/test/pkg/ApiDetectorTest2.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.location.LocationManager;\n"
                                        + "\n"
                                        + "@SuppressWarnings({\"FieldCanBeLocal\", \"unused\"})\n"
                                        + "public class ApiDetectorTest2 {\n"
                                        + "public enum HealthChangeHandler {\n"
                                        + "    LOCATION_MODE_CHANGED(LocationManager.MODE_CHANGED_ACTION) {\n"
                                        + "        @Override public String toString() { return super.toString(); }\n"
                                        + "};\n"
                                        + "\n"
                                        + "    HealthChangeHandler(String mode) {\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testRequiresApiAsTargetApi() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                "src/test/pkg/ApiDetectorTest2.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.location.LocationManager;\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "\n"
                                        + "@SuppressWarnings({\"FieldCanBeLocal\", \"unused\"})\n"
                                        + "public class ApiDetectorTest2 {\n"
                                        + "public enum HealthChangeHandler {\n"
                                        + "    @RequiresApi(api=19)\n"
                                        + "    LOCATION_MODE_CHANGED(LocationManager.MODE_CHANGED_ACTION) {\n"
                                        + "        @Override String toString() { return super.toString(); }\n"
                                        + "};\n"
                                        + "\n"
                                        + "    HealthChangeHandler(String mode) {\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "}"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testRequiresApi() {
        String expected =
                ""
                        + "src/test/pkg/TestRequiresApi.java:8: Error: Call requires API level 19 (current min is 15): requiresKitKat [NewApi]\n"
                        + "        requiresKitKat(); // ERROR - requires 19\n"
                        + "        ~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestRequiresApi.java:9: Error: Call requires API level 21 (current min is 15): LollipopClass [NewApi]\n"
                        + "        LollipopClass lollipopClass = new LollipopClass();\n"
                        + "                                      ~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestRequiresApi.java:10: Error: Call requires API level 21 (current min is 15): requiresLollipop [NewApi]\n"
                        + "        lollipopClass.requiresLollipop(); // ERROR - requires 21\n"
                        + "                      ~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestRequiresApi.java:28: Error: Call requires API level 22 (current min is 15): requiresLollipop [NewApi]\n"
                        + "        requiresLollipop(); // ERROR\n"
                        + "        ~~~~~~~~~~~~~~~~\n"
                        + "4 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                "src/test/pkg/TestRequiresApi.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "import android.os.Build;\n"
                                        + "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n"
                                        + "public class TestRequiresApi {\n"
                                        + "    public void caller() {\n"
                                        + "        requiresKitKat(); // ERROR - requires 19\n"
                                        + "        LollipopClass lollipopClass = new LollipopClass();\n"
                                        + "        lollipopClass.requiresLollipop(); // ERROR - requires 21\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(19)\n"
                                        + "    public void requiresKitKat() {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(21)\n"
                                        + "    public class LollipopClass {\n"
                                        + "        LollipopClass() {\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        public void requiresLollipop() {\n"
                                        + "            requiresKitKat(); // OK\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void something() {\n"
                                        + "        requiresLollipop(); // ERROR\n"
                                        + "        if (Build.VERSION.SDK_INT >= 22) {\n"
                                        + "            requiresLollipop(); // OK\n"
                                        + "        }\n"
                                        + "        if (Build.VERSION.SDK_INT < 22) {\n"
                                        + "            return;\n"
                                        + "        }\n"
                                        + "        requiresLollipop(); // OK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(22)\n"
                                        + "    public void requiresLollipop() {\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testRequiresApiInheritance() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                "package android.support.v7.app;\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "\n"
                                        + "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n"
                                        + "public class RequiresApiTest {\n"
                                        + "    public void test() {\n"
                                        + "        new ParentClass().foo1(); // ERROR\n"
                                        + "        new ChildClass().foo1(); // OK\n"
                                        + "        new ChildClass().foo2(); // OK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(16)\n"
                                        + "    public class ParentClass {\n"
                                        + "        @RequiresApi(18)\n"
                                        + "        void foo1() {\n"
                                        + "        }\n"
                                        + "        public ParentClass() { }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public class ChildClass extends ParentClass {\n"
                                        + "        @Override\n"
                                        + "        void foo1() {\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        void foo2() {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(
                        ""
                                + "src/android/support/v7/app/RequiresApiTest.java:8: Error: Call requires API level 16 (current min is 1): ParentClass [NewApi]\n"
                                + "        new ParentClass().foo1(); // ERROR\n"
                                + "        ~~~~~~~~~~~~~~~\n"
                                + "src/android/support/v7/app/RequiresApiTest.java:8: Error: Call requires API level 18 (current min is 1): foo1 [NewApi]\n"
                                + "        new ParentClass().foo1(); // ERROR\n"
                                + "                          ~~~~\n"
                                + "2 errors, 0 warnings\n");
    }

    public void testRequiresApiOnFields() {
        // Regression test for issue 37124805
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "import android.util.Log;\n"
                                        + "\n"
                                        + "public class RequiresApiFieldTest {\n"
                                        + "    @RequiresApi(24)\n"
                                        + "    private int Method24() {\n"
                                        + "        return 42;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(24)\n"
                                        + "    private static final int Field24 = 42;\n"
                                        + "\n"
                                        + "    private void ReferenceMethod24() {\n"
                                        + "        Log.d(\"zzzz\", \"ReferenceField24: \" + Method24());\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void ReferenceField24() {\n"
                                        + "        Log.d(\"zzzz\", \"ReferenceField24: \" + Field24);\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/RequiresApiFieldTest.java:16: Error: Call requires API level 24 (current min is 15): Method24 [NewApi]\n"
                                + "        Log.d(\"zzzz\", \"ReferenceField24: \" + Method24());\n"
                                + "                                             ~~~~~~~~\n"
                                + "src/test/pkg/RequiresApiFieldTest.java:20: Error: Call requires API level 24 (current min is 15): Field24 [NewApi]\n"
                                + "        Log.d(\"zzzz\", \"ReferenceField24: \" + Field24);\n"
                                + "                                             ~~~~~~~\n"
                                + "2 errors, 0 warnings\n");
    }

    public void testDrawableThemeReferences() {
        // Regression test for
        // https://code.google.com/p/android/issues/detail?id=199597
        // Make sure that theme references in drawable XML files are checked
        String expected =
                ""
                        + "res/drawable/my_drawable.xml:3: Error: Using theme references in XML drawables requires API level 21 (current min is 9) [NewApi]\n"
                        + "    <item android:drawable=\"?android:windowBackground\"/>\n"
                        + "          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/drawable/my_drawable.xml:4: Error: Using theme references in XML drawables requires API level 21 (current min is 9) [NewApi]\n"
                        + "    <item android:drawable=\"?android:selectableItemBackground\"/>\n"
                        + "          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(9),
                        xml(
                                "res/drawable/my_drawable.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <item android:drawable=\"?android:windowBackground\"/>\n"
                                        + "    <item android:drawable=\"?android:selectableItemBackground\"/>\n"
                                        + "</layer-list>"),
                        xml(
                                "res/drawable-v21/my_drawable.xml",
                                "" // OK
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <item android:drawable=\"?android:windowBackground\"/>\n"
                                        + "    <item android:drawable=\"?android:selectableItemBackground\"/>\n"
                                        + "</layer-list>"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testNonAndroidProjects() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=228481
        // Don't flag API violations in plain java modules if there are no dependent
        // Android modules pointing to it
        //noinspection all // Sample code
        lint().projects(
                        project(
                                        java(
                                                ""
                                                        + "package com.example;\n"
                                                        + "\n"
                                                        + "import java.io.FileReader;\n"
                                                        + "import java.io.IOException;\n"
                                                        + "import java.util.Properties;\n"
                                                        + "\n"
                                                        + "public class MyClass {\n"
                                                        + "  public static void foo() throws IOException {\n"
                                                        + "    FileReader reader=new FileReader(\"../local.properties\");\n"
                                                        + "    Properties props=new Properties();\n"
                                                        + "\n"
                                                        + "    props.load(reader);\n"
                                                        + "    reader.close();\n"
                                                        + "  }\n"
                                                        + "}\n"))
                                .type(ProjectDescription.Type.JAVA))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    // bug 198295: Add a test for a case that crashes ApiDetector due to an
    // invalid parameterIndex causing by a varargs method invocation.
    public void testMethodWithPrimitiveAndVarargs() {
        // In case of a crash, there is an assertion failure in tearDown()
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(14),
                        java(
                                "src/test/pkg/LogHelper.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class LogHelper {\n"
                                        + "\n"
                                        + "    public static void log(String tag, Object... args) {\n"
                                        + "    }\n"
                                        + "}"),
                        java(
                                "src/test/pkg/Browser.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class Browser {\n"
                                        + "    \n"
                                        + "    public void onCreate() {\n"
                                        + "        LogHelper.log(\"TAG\", \"arg1\", \"arg2\", 1, \"arg4\", this /*non primitive*/);\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testMethodInvocationWithGenericTypeArgs() {
        // Test case for https://code.google.com/p/android/issues/detail?id=198439
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/Loader.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public abstract class Loader<P> {\n"
                                        + "    private P mParam;\n"
                                        + "\n"
                                        + "    public abstract void loadInBackground(P val);\n"
                                        + "\n"
                                        + "    public void load() {\n"
                                        + "        // Invoke a method that takes a generic type.\n"
                                        + "        loadInBackground(mParam);\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testFontFamilyWithAppCompat() {
        lint().files(
                        manifest().minSdk(1),
                        xml(
                                "src/main/res/layout/foo.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:id=\"@+id/LinearLayout1\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "    <TextView\n"
                                        + "        android:fontFamily=\"@font/my_font\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"/>\n"
                                        + "</LinearLayout>\n"),
                        gradle(
                                "apply plugin: 'com.android.application'\n"
                                        + "dependencies {\n"
                                        + "    compile 'com.android.support:appcompat-v7:+'\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(
                        "src/main/res/layout/foo.xml:8: Warning: Attribute fontFamily is only used in API level 16 and higher (current min is 1) Did you mean app:fontFamily ? [UnusedAttribute]\n"
                                + "        android:fontFamily=\"@font/my_font\"\n"
                                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings");
    }

    @SuppressWarnings("all") // sample code
    public void testInlinedConstantConditional() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=205925
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/MainActivity.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.os.Build;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.UserManager;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        setContentView(R.layout.activity_main);\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {\n"
                                        + "            UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    @SuppressWarnings("all") // sample code
    public void testSdkSuppress() {
        // Regression test for b/31799926 and 35968791
        lint().files(
                        java(
                                "src/test/pkg/MainActivity.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.os.Build;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.UserManager;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    @android.support.test.filters.SdkSuppress(minSdkVersion = 17)\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        setContentView(R.layout.activity_main);\n"
                                        + "\n"
                                        + "        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @androidx.test.filters.SdkSuppress(minSdkVersion = 17)\n"
                                        + "    public void test() {\n"
                                        + "        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testMultiCatch() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=198854
        // Check disjointed exception types

        String expected =
                ""
                        + "src/test/pkg/MultiCatch.java:12: Error: Exception requires API level 18 (current min is 1): android.media.UnsupportedSchemeException [NewApi]\n"
                        + "        } catch (MediaDrm.MediaDrmStateException | UnsupportedSchemeException e) {\n"
                        + "                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/MultiCatch.java:12: Error: Exception requires API level 21 (current min is 1): android.media.MediaDrm.MediaDrmStateException [NewApi]\n"
                        + "        } catch (MediaDrm.MediaDrmStateException | UnsupportedSchemeException e) {\n"
                        + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/MultiCatch.java:18: Error: Exception requires API level 21 (current min is 1): android.media.MediaDrm.MediaDrmStateException [NewApi]\n"
                        + "        } catch (MediaDrm.MediaDrmStateException\n"
                        + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/MultiCatch.java:19: Error: Exception requires API level 18 (current min is 1): android.media.UnsupportedSchemeException [NewApi]\n"
                        + "                  | UnsupportedSchemeException e) {\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/MultiCatch.java:25: Error: Multi-catch with these reflection exceptions requires API level 19 (current min is 1) because they get compiled to the common but new super type ReflectiveOperationException. As a workaround either create individual catch statements, or catch Exception. [NewApi]\n"
                        + "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n"
                        + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "5 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/MultiCatch.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.media.MediaDrm;\n"
                                        + "import android.media.UnsupportedSchemeException;\n"
                                        + "\n"
                                        + "import java.lang.reflect.InvocationTargetException;\n"
                                        + "\n"
                                        + "public class MultiCatch {\n"
                                        + "    public void test() {\n"
                                        + "        try {\n"
                                        + "            method1();\n"
                                        + "        } catch (MediaDrm.MediaDrmStateException | UnsupportedSchemeException e) {\n"
                                        + "            e.printStackTrace();\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        try {\n"
                                        + "            method2();\n"
                                        + "        } catch (MediaDrm.MediaDrmStateException\n"
                                        + "                  | UnsupportedSchemeException e) {\n"
                                        + "            e.printStackTrace();\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        try {\n"
                                        + "            String.class.getMethod(\"trim\").invoke(\"\");\n"
                                        + "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n"
                                        + "            e.printStackTrace();\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void method1() throws MediaDrm.MediaDrmStateException, UnsupportedSchemeException {\n"
                                        + "    }\n"
                                        + "    public void method2() throws MediaDrm.MediaDrmStateException, UnsupportedSchemeException {\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testExceptionHandlingDalvik() {
        // Regression test for 131349148: Dalvik: java.lang.VerifyError
        lint().files(
                        java(
                                "src/test/pkg/CatchTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.hardware.camera2.CameraAccessException;\n"
                                        + "import android.media.MediaDrmResetException;\n"
                                        + "import android.os.Build;\n"
                                        + "\n"
                                        + "import android.annotation.TargetApi;\n"
                                        + "import android.support.annotation.RequiresApi;"
                                        + "\n"
                                        + "@SuppressWarnings({\"unused\", \"WeakerAccess\"})\n"
                                        + "public class CatchTest {\n"
                                        + "    public class C0 {\n"
                                        + "        public void test() {\n"
                                        + "            try {\n"
                                        + "                thrower();\n"
                                        + "            } catch (CameraAccessException e) { // ERROR: Requires 21\n"
                                        + "                logger(e.toString());\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public class C1 {\n"
                                        + "        public void test() {\n"
                                        + "            try {\n"
                                        + "                thrower();\n"
                                        + "            } catch (MediaDrmResetException | CameraAccessException e) { // ERROR: Requires 23 & 21\n"
                                        + "                logger(e.toString());\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public class C2 {\n"
                                        + "        public void test() {\n"
                                        + "            if (Build.VERSION.SDK_INT >= 23) { // Not adequate\n"
                                        + "                try {\n"
                                        + "                    thrower();\n"
                                        + "                } catch (CameraAccessException e) { // ERROR: Requires 23; version check not enough\n"
                                        + "                    logger(e.toString());\n"
                                        + "                }\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public class C3 {\n"
                                        + "        @TargetApi(21)\n"
                                        + "        public void test() {\n"
                                        + "            try {\n"
                                        + "                thrower();\n"
                                        + "            } catch (CameraAccessException e) { // ERROR: Requires 23; @TargetApi on method not enough\n"
                                        + "                logger(e.toString());\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(23) // OK\n"
                                        + "    public class C4 {\n"
                                        + "        public void test() {\n"
                                        + "            try {\n"
                                        + "                thrower();\n"
                                        + "            } catch (CameraAccessException | MediaDrmResetException e) { // OK: Class requires 21\n"
                                        + "                logger(e.toString());\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @TargetApi(23) // Not enough; we want @RequiresApi instead to communicate outward\n"
                                        + "    public class C4 {\n"
                                        + "        public void test() {\n"
                                        + "            try {\n"
                                        + "                thrower();\n"
                                        + "            } catch (CameraAccessException | MediaDrmResetException e) { // ERROR: Wrong class annotation\n"
                                        + "                logger(e.toString());\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private void logger(String e) {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void thrower() throws CameraAccessException, MediaDrmResetException {\n"
                                        + "        if (Build.VERSION.SDK_INT >= 21) {\n"
                                        + "            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/CatchTest.java:15: Error: Exception requires API level 21 (current min is 1): android.hardware.camera2.CameraAccessException [NewApi]\n"
                                + "            } catch (CameraAccessException e) { // ERROR: Requires 21\n"
                                + "                     ~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CatchTest.java:25: Error: Exception requires API level 21 (current min is 1): android.hardware.camera2.CameraAccessException [NewApi]\n"
                                + "            } catch (MediaDrmResetException | CameraAccessException e) { // ERROR: Requires 23 & 21\n"
                                + "                                              ~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CatchTest.java:25: Error: Exception requires API level 23 (current min is 1): android.media.MediaDrmResetException [NewApi]\n"
                                + "            } catch (MediaDrmResetException | CameraAccessException e) { // ERROR: Requires 23 & 21\n"
                                + "                     ~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CatchTest.java:36: Error: Exception requires API level 21 (current min is 1): android.hardware.camera2.CameraAccessException, and having a surrounding/preceding version check does not help since prior to API level 19, just loading the class will cause a crash. Consider marking the surrounding class with RequiresApi(19) to ensure that the class is never loaded except when on API 19 or higher. [NewApi]\n"
                                + "                } catch (CameraAccessException e) { // ERROR: Requires 23; version check not enough\n"
                                + "                         ~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CatchTest.java:48: Error: Exception requires API level 21 (current min is 21): android.hardware.camera2.CameraAccessException, and having a surrounding/preceding version check does not help since prior to API level 19, just loading the class will cause a crash. Consider marking the surrounding class with RequiresApi(19) to ensure that the class is never loaded except when on API 19 or higher. [NewApi]\n"
                                + "            } catch (CameraAccessException e) { // ERROR: Requires 23; @TargetApi on method not enough\n"
                                + "                     ~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CatchTest.java:70: Error: Exception requires API level 21 (current min is 23): android.hardware.camera2.CameraAccessException, and having a surrounding/preceding version check does not help since prior to API level 19, just loading the class will cause a crash. Consider marking the surrounding class with RequiresApi(19) to ensure that the class is never loaded except when on API 19 or higher. [NewApi]\n"
                                + "            } catch (CameraAccessException | MediaDrmResetException e) { // ERROR: Wrong class annotation\n"
                                + "                     ~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CatchTest.java:70: Error: Exception requires API level 23 (current min is 23): android.media.MediaDrmResetException, and having a surrounding/preceding version check does not help since prior to API level 19, just loading the class will cause a crash. Consider marking the surrounding class with RequiresApi(19) to ensure that the class is never loaded except when on API 19 or higher. [NewApi]\n"
                                + "            } catch (CameraAccessException | MediaDrmResetException e) { // ERROR: Wrong class annotation\n"
                                + "                                             ~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "7 errors, 0 warnings");
    }

    @SuppressWarnings("all") // Sample code
    public void testConcurrentHashMapUsage() {
        ApiLookup lookup = ApiLookup.get(createClient());
        String expected =
                ""
                        + "src/test/pkg/MapUsage.java:7: Error: The type of the for loop iterated value is java.util.concurrent.ConcurrentHashMap.KeySetView<java.lang.String,java.lang.Object>, which requires API level 24 (current min is 1); to work around this, add an explicit cast to (Map) before the keySet call. [NewApi]\n"
                        + "        for (String key : map.keySet()) {\n"
                        + "                          ~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/MapUsage.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.util.concurrent.ConcurrentHashMap;\n"
                                        + "\n"
                                        + "public class MapUsage {\n"
                                        + "    public void dumpKeys(ConcurrentHashMap<String, Object> map) {\n"
                                        + "        for (String key : map.keySet()) {\n"
                                        + "            System.out.println(key);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}"),
                        java(
                                "src/java/util/concurrent/ConcurrentHashMap.java",
                                ""
                                        + "package java.util.concurrent;\n"
                                        + "\n"
                                        + "import java.io.Serializable;\n"
                                        + "import java.util.AbstractMap;\n"
                                        + "import java.util.Set;\n"
                                        + "import java.util.concurrent.ConcurrentMap;\n"
                                        + "\n"
                                        + "public abstract class ConcurrentHashMap<K,V> extends AbstractMap<K,V>\n"
                                        + "        implements ConcurrentMap<K,V>, Serializable {\n"
                                        + "\n"
                                        + "    public abstract KeySetView<K,V> keySet();\n"
                                        + "\n"
                                        + "    public static abstract class KeySetView<K,V> implements Set<K>, java.io.Serializable {\n"
                                        + "    }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    @SuppressWarnings("all") // sample code
    public void testObsoleteVersionCheck() {
        String expected =
                ""
                        + "src/test/pkg/TestVersionCheck.java:7: Warning: Unnecessary; SDK_INT is always >= 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT >= 21) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:8: Warning: Unnecessary; SDK_INT is always >= 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT > 21) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:9: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT < 21) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:10: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT <= 21) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:11: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT == 21) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:13: Warning: Unnecessary; SDK_INT is always >= 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT >= 22) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:14: Warning: Unnecessary; SDK_INT is always >= 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT > 22) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:15: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT < 22) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:16: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT <= 22) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:17: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT == 22) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestVersionCheck.java:21: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                        + "        if (Build.VERSION.SDK_INT < 23) { }\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 11 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(23),
                        java(
                                "src/test/pkg/TestVersionCheck.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Build;\n"
                                        + "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n"
                                        + "public class TestVersionCheck {\n"
                                        + "    public void something() {\n"
                                        + "        if (Build.VERSION.SDK_INT >= 21) { }\n"
                                        + "        if (Build.VERSION.SDK_INT > 21) { }\n"
                                        + "        if (Build.VERSION.SDK_INT < 21) { }\n"
                                        + "        if (Build.VERSION.SDK_INT <= 21) { }\n"
                                        + "        if (Build.VERSION.SDK_INT == 21) { }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= 22) { }\n"
                                        + "        if (Build.VERSION.SDK_INT > 22) { }\n"
                                        + "        if (Build.VERSION.SDK_INT < 22) { }\n"
                                        + "        if (Build.VERSION.SDK_INT <= 22) { }\n"
                                        + "        if (Build.VERSION.SDK_INT == 22) { }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= 23) { }\n"
                                        + "        if (Build.VERSION.SDK_INT > 23) { }\n"
                                        + "        if (Build.VERSION.SDK_INT < 23) { }\n"
                                        + "        if (Build.VERSION.SDK_INT <= 23) { }\n"
                                        + "        if (Build.VERSION.SDK_INT == 23) { }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= 24) { }\n"
                                        + "        if (Build.VERSION.SDK_INT > 24) { }\n"
                                        + "        if (Build.VERSION.SDK_INT < 24) { }\n"
                                        + "        if (Build.VERSION.SDK_INT <= 24) { }\n"
                                        + "        if (Build.VERSION.SDK_INT == 24) { }\n"
                                        + "\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testKotlinFileSuppress() {
        // Regression test for https://issuetracker.google.com/72509076
        lint().files(
                        kotlin(
                                ""
                                        + "@file:RequiresApi(21)\n"
                                        + "\n"
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi\n"
                                        + "import android.widget.Toolbar\n"
                                        + "\n"
                                        + "fun Toolbar.hideOverflowMenu2() = hideOverflowMenu()"))
                .run()
                .expectClean();
    }

    public void testMapGetOrDefault() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=235665
        String expected =
                ""
                        + "src/test/pkg/MapApiTest.java:8: Error: Call requires API level 24 (current min is 1): java.util.Map#getOrDefault [NewApi]\n"
                        + "        map.getOrDefault(\"foo\", \"bar\");\n"
                        + "            ~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.util.Map;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"Since15\")\n"
                                        + "public class MapApiTest  {\n"
                                        + "    public void test(Map<String,String> map) {\n"
                                        + "        map.getOrDefault(\"foo\", \"bar\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected)
                .expectFixDiffs("Data for src/test/pkg/MapApiTest.java line 7:   Integer : 24");
    }

    public void testObsoleteFolder() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=236018
        @Language("XML")
        String stringsXml =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <string name=\"home_title\">Home Sample</string>\n"
                        + "</resources>\n";
        String expected =
                ""
                        + "res/layout-v5: Warning: This folder configuration (v5) is unnecessary; minSdkVersion is 12. Merge all the resources in this folder into layout. [ObsoleteSdkInt]\n"
                        + "res/values-land-v5: Warning: This folder configuration (v5) is unnecessary; minSdkVersion is 12. Merge all the resources in this folder into values-land. [ObsoleteSdkInt]\n"
                        + "res/values-v5: Warning: This folder configuration (v5) is unnecessary; minSdkVersion is 12. Merge all the resources in this folder into values. [ObsoleteSdkInt]\n"
                        + "0 errors, 3 warnings\n";
        lint().files(
                        manifest().minSdk(12),
                        xml("res/values/strings.xml", stringsXml),
                        xml("res/values-v5/strings.xml", stringsXml),
                        xml("res/values-land-v5/strings.xml", stringsXml),
                        xml("res/values-v21/strings.xml", stringsXml),
                        xml("res/values-land/strings.xml", stringsXml),
                        xml("res/layout/my_activity.xml", "<merge/>"),
                        xml("res/layout-v5/my_activity.xml", "<merge/>"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testVectorDrawableCompat() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=222654
        String expected =
                ""
                        + "src/test/pkg/VectorTest.java:17: Error: Call requires API level 21 (current min is 15): android.graphics.drawable.Drawable#setTint [NewApi]\n"
                        + "        vector3.setTint(0xFFFFFF); // ERROR\n"
                        + "                ~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.res.Resources;\n"
                                        + "import android.graphics.drawable.Drawable;\n"
                                        + "import android.support.graphics.drawable.VectorDrawableCompat;\n"
                                        + "\n"
                                        + "public class VectorTest {\n"
                                        + "    public void test(Resources resources) {\n"
                                        + "        VectorDrawableCompat vector = VectorDrawableCompat.create(resources, 0, null);\n"
                                        + "        vector.setTint(0xFFFFFF); // OK\n"
                                        + "\n"
                                        + "        VectorDrawableCompat vector2 = VectorDrawableCompat.createFromXmlInner(resources, null,\n"
                                        + "                null, null);\n"
                                        + "        vector2.setTint(0xFFFFFF); // OK\n"
                                        + "\n"
                                        + "        Drawable vector3 = Drawable.createFromPath(null);\n"
                                        + "        vector3.setTint(0xFFFFFF); // ERROR\n"
                                        + "    }\n"
                                        + "}\n"))
                .allowCompilationErrors(true)
                .allowSystemErrors(false)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testInnerClassAccess() {
        // "Calling new methods on older version" doesn't work with inner classes
        // Regression test for https://code.google.com/p/android/issues/detail?id=228035
        String expected =
                ""
                        + "src/pkg/my/myapplication/Fragment.java:8: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getContext [NewApi]\n"
                        + "            Context c1 = getContext();\n"
                        + "                         ~~~~~~~~~~\n"
                        + "src/pkg/my/myapplication/Fragment.java:9: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getContext [NewApi]\n"
                        + "            Context c2 = Fragment.this.getContext();\n"
                        + "                                       ~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package pkg.my.myapplication;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "\n"
                                        + "public class Fragment extends android.app.Fragment {\n"
                                        + "    class MyClass {\n"
                                        + "        public void test() {\n"
                                        + "            Context c1 = getContext();\n"
                                        + "            Context c2 = Fragment.this.getContext();\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testMethodWithInterfaceAlternative() {
        // Make sure we correctly handle the case where you ensure that a method exists
        // at runtime (e.g. is always overridden) by using an interface
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "\n"
                                        + "public class MyActivity extends Activity implements LifecycleAware {\n"
                                        + "    private void verifyUserCanBeMessaged(Bundle intentExtras) {\n"
                                        + "        if (isDestroyed() || isFinishing()) {\n"
                                        + "            return;\n"
                                        + "        }\n"
                                        + "        // ...\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Test scenario where the qualifier is non-null\n"
                                        + "    private void verifyUserCanBeMessaged(MyActivity myActivity, Bundle intentExtras) {\n"
                                        + "        if (myActivity.isDestroyed() || myActivity.isFinishing()) {\n"
                                        + "            return;\n"
                                        + "        }\n"
                                        + "        // ...\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public interface LifecycleAware {\n"
                                        + "    boolean isDestroyed();\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testMethodWithInterfaceAlternative2() {
        // Slight variation on testMethodWithInterfaceAlternative where
        // we extend a class which implements the interface
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "\n"
                                        + "public class MyActivity extends BaseFragmentActivity {\n"
                                        + "    private void verifyUserCanBeMessaged(Bundle intentExtras) {\n"
                                        + "        if (isDestroyed() || isFinishing()) {\n"
                                        + "            return;\n"
                                        + "        }\n"
                                        + "        // ...\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Test scenario where the qualifier is non-null\n"
                                        + "    private void verifyUserCanBeMessaged(MyActivity myActivity, Bundle intentExtras) {\n"
                                        + "        if (myActivity.isDestroyed() || myActivity.isFinishing()) {\n"
                                        + "            return;\n"
                                        + "        }\n"
                                        + "        // ...\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class BaseFragmentActivity extends Activity implements LifecycleAware {\n"
                                        + "    boolean mDestroyed;\n"
                                        + "    \n"
                                        + "    @Override\n"
                                        + "    public boolean isDestroyed() {\n"
                                        + "        return mDestroyed;\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public interface LifecycleAware {\n"
                                        + "    boolean isDestroyed();\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testKotlinPropertySyntax() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        kotlin(
                                "package test.pkg\n"
                                        + "@Suppress(\"UsePropertyAccessSyntax\")\n"
                                        + "fun testApiCheck(calendar: java.util.Calendar) {\n"
                                        + "    calendar.weekYear\n"
                                        + "    calendar.getWeekYear()\n"
                                        + "}    \n"))
                .run()
                .expect(
                        "src/test/pkg/test.kt:4: Error: Call requires API level 24 (current min is 1): java.util.Calendar#getWeekYear [NewApi]\n"
                                + "    calendar.weekYear\n"
                                + "             ~~~~~~~~\n"
                                + "src/test/pkg/test.kt:5: Error: Call requires API level 24 (current min is 1): java.util.Calendar#getWeekYear [NewApi]\n"
                                + "    calendar.getWeekYear()\n"
                                + "             ~~~~~~~~~~~\n"
                                + "2 errors, 0 warnings\n");
    }

    public void test69534659() {
        // Regression test for issue 69534659
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        kotlin(
                                "package test.pkg\n"
                                        + "\n"
                                        + "import android.view.View\n"
                                        + "\n"
                                        + "class Foo {\n"
                                        + "    var <T : View> Iterable<T>.visibility: Int\n"
                                        + "    set(value) = forEach { it.visibility = value }\n"
                                        + "    get() = throw RuntimeException(\"Not supported\")\n"
                                        + "\n"
                                        + "}\n"
                                        + "\n"
                                        + "abstract class MyIterable : Iterable<String> {\n"
                                        + "    fun test() {\n"
                                        + "        forEach { println(it[0]) }\n"
                                        + "    }\n"
                                        + "}"),
                        kotlin(
                                ""
                                        + "import android.util.Log\n"
                                        + "import io.realm.Realm\n"
                                        + "import io.realm.RealmModel\n"
                                        + "import io.realm.kotlin.where\n"
                                        + "\n"
                                        + "class Test {\n"
                                        + "    fun method(realm: Realm) {\n"
                                        + "        realm.where<RealmModel>()\n"
                                        + "                .findAll()\n"
                                        + "                .forEach {\n"
                                        + "                    Log.i(\"tag\", it.toString())\n"
                                        + "                }\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testForEach2() {
        // Regression test for 70965444: False positives for Map#forEach
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(21),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.util.List;\n"
                                        + "import java.util.function.Consumer;\n"
                                        + "\n"
                                        + "public class JavaForEach {\n"
                                        + "    public void test(List<String> list) {\n"
                                        + "        list.forEach(new Consumer<String>() {\n"
                                        + "            @Override\n"
                                        + "            public void accept(String s) {\n"
                                        + "                System.out.println(s);\n"
                                        + "            }\n"
                                        + "        });\n"
                                        + "    }\n"
                                        + "}\n"),
                        kotlin(
                                ""
                                        + "import android.webkit.WebResourceRequest\n"
                                        + "import android.webkit.WebResourceResponse\n"
                                        + "import android.webkit.WebView\n"
                                        + "import android.webkit.WebViewClient\n"
                                        + "\n"
                                        + "class MyWebViewClient() : WebViewClient() {\n"
                                        + "    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {\n"
                                        + "        val header: Map<String, String> = request.requestHeaders\n"
                                        + "        // Lint reports this Map.forEach as java.util.Map's but it's kotlin.collections.Map's!\n"
                                        + "        header.forEach { (key, value) ->\n"
                                        + "            TODO(\"addHeader(key, value)\")\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        return TODO(\"Somethi9ng\")\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/JavaForEach.java:8: Error: Call requires API level 24 (current min is 21): java.lang.Iterable#forEach [NewApi]\n"
                                + "        list.forEach(new Consumer<String>() {\n"
                                + "             ~~~~~~~\n"
                                + "src/test/pkg/JavaForEach.java:8: Error: Class requires API level 24 (current min is 21): java.util.function.Consumer [NewApi]\n"
                                + "        list.forEach(new Consumer<String>() {\n"
                                + "                         ~~~~~~~~~~~~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testCastsToSelf() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.util.List;\n"
                                        + "import java.util.function.Function;\n"
                                        + "import java.util.stream.Collectors;\n"
                                        + "\n"
                                        + "public class Used {\n"
                                        + "    List<Object> test(List<String> list) {\n"
                                        + "        return list.stream().map((Function<String, Object>) s -> \n"
                                        + "                fromNullable(s)).collect(Collectors.toList());\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    void tes2t(List<String> list) {\n"
                                        + "        list.stream().map(new Function<String, Object>() {\n"
                                        + "            @Override\n"
                                        + "            public Object apply(String s) {\n"
                                        + "                return fromNullable(s);\n"
                                        + "            }\n"
                                        + "        });\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static String fromNullable(String o) {\n"
                                        + "        return o;\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/Used.java:9: Error: Call requires API level 24 (current min is 15): java.util.Collection#stream [NewApi]\n"
                                + "        return list.stream().map((Function<String, Object>) s -> \n"
                                + "                    ~~~~~~\n"
                                + "src/test/pkg/Used.java:9: Error: Call requires API level 24 (current min is 15): java.util.stream.Stream#map [NewApi]\n"
                                + "        return list.stream().map((Function<String, Object>) s -> \n"
                                + "                             ~~~\n"
                                + "src/test/pkg/Used.java:9: Error: Cast to Function requires API level 24 (current min is 15) [NewApi]\n"
                                + "        return list.stream().map((Function<String, Object>) s -> \n"
                                + "                                 ^\n"
                                + "src/test/pkg/Used.java:10: Error: Call requires API level 24 (current min is 15): java.util.stream.Collectors#toList [NewApi]\n"
                                + "                fromNullable(s)).collect(Collectors.toList());\n"
                                + "                                                    ~~~~~~\n"
                                + "src/test/pkg/Used.java:10: Error: Call requires API level 24 (current min is 15): java.util.stream.Stream#collect [NewApi]\n"
                                + "                fromNullable(s)).collect(Collectors.toList());\n"
                                + "                                 ~~~~~~~\n"
                                + "src/test/pkg/Used.java:10: Error: Cast to Collector requires API level 24 (current min is 15) [NewApi]\n"
                                + "                fromNullable(s)).collect(Collectors.toList());\n"
                                + "                                         ~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/Used.java:14: Error: Call requires API level 24 (current min is 15): java.util.Collection#stream [NewApi]\n"
                                + "        list.stream().map(new Function<String, Object>() {\n"
                                + "             ~~~~~~\n"
                                + "src/test/pkg/Used.java:14: Error: Call requires API level 24 (current min is 15): java.util.stream.Stream#map [NewApi]\n"
                                + "        list.stream().map(new Function<String, Object>() {\n"
                                + "                      ~~~\n"
                                + "src/test/pkg/Used.java:14: Error: Class requires API level 24 (current min is 15): java.util.function.Function [NewApi]\n"
                                + "        list.stream().map(new Function<String, Object>() {\n"
                                + "                              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "9 errors, 0 warnings");
    }

    public void testKotlinArgumentsInConstructorDelegation() {
        // Regression test for https://issuetracker.google.com/69948867
        // NewApi doesn't work for calls in arguments of constructor delegation
        lint().files(
                        manifest().minSdk(15),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.content.Context\n"
                                        + "import android.graphics.drawable.Drawable\n"
                                        + "import android.text.style.ImageSpan\n"
                                        + "\n"
                                        + "class SomeClass(val drawable: Drawable?) {\n"
                                        + "    constructor(context: Context, resourceId: Int) : this(context.getDrawable(resourceId)) {\n"
                                        + "        SomeClass(context.getDrawable(resourceId))\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "\n"
                                        + "class AnotherClass(context: Context, id: Int): ImageSpan(context.getDrawable(id)!!) {\n"
                                        + "    init {\n"
                                        + "        val x = context.getDrawable(id)\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        "src/test/pkg/SomeClass.kt:8: Error: Call requires API level 21 (current min is 15): android.content.Context#getDrawable [NewApi]\n"
                                + "    constructor(context: Context, resourceId: Int) : this(context.getDrawable(resourceId)) {\n"
                                + "                                                                  ~~~~~~~~~~~\n"
                                + "src/test/pkg/SomeClass.kt:9: Error: Call requires API level 21 (current min is 15): android.content.Context#getDrawable [NewApi]\n"
                                + "        SomeClass(context.getDrawable(resourceId))\n"
                                + "                          ~~~~~~~~~~~\n"
                                + "src/test/pkg/SomeClass.kt:13: Error: Call requires API level 21 (current min is 15): android.content.Context#getDrawable [NewApi]\n"
                                + "class AnotherClass(context: Context, id: Int): ImageSpan(context.getDrawable(id)!!) {\n"
                                + "                                                                 ~~~~~~~~~~~\n"
                                + "src/test/pkg/SomeClass.kt:15: Error: Call requires API level 21 (current min is 15): android.content.Context#getDrawable [NewApi]\n"
                                + "        val x = context.getDrawable(id)\n"
                                + "                        ~~~~~~~~~~~\n"
                                + "4 errors, 0 warnings");
    }

    public void testInstanceOf() {
        // 69736645: "NewApi" not detected in instanceof
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.security.keystore.KeyPermanentlyInvalidatedException;\n"
                                        + "\n"
                                        + "/** @noinspection ConstantConditions, UnnecessaryReturnStatement, ClassNameDiffersFromFileName */ "
                                        + "public class ApiTest {\n"
                                        + "    public static void test(Throwable throwable) {\n"
                                        + "        if (throwable instanceof KeyPermanentlyInvalidatedException) {\n"
                                        + "            return;\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"),
                        kotlin(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.security.keystore.KeyPermanentlyInvalidatedException\n"
                                        + "\n"
                                        + "fun test(throwable: Throwable) {\n"
                                        + "    if (throwable is KeyPermanentlyInvalidatedException) {\n"
                                        + "        return\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        "src/test/pkg/ApiTest.java:7: Error: Class requires API level 23 (current min is 15): android.security.keystore.KeyPermanentlyInvalidatedException [NewApi]\n"
                                + "        if (throwable instanceof KeyPermanentlyInvalidatedException) {\n"
                                + "                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/test.kt:6: Error: Class requires API level 23 (current min is 15): android.security.keystore.KeyPermanentlyInvalidatedException [NewApi]\n"
                                + "    if (throwable is KeyPermanentlyInvalidatedException) {\n"
                                + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testLazyProperties() {
        // Regression test for https://issuetracker.google.com/65728903
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.app.Activity\n"
                                        + "import test.pkg.R\n"
                                        + "\n"
                                        + "class MainActivity2 : Activity() {\n"
                                        + "    val illegalColor1 by lazy {\n"
                                        + "        resources.getColor(R.color.primary_text_default_material_light, null)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    val illegalColor2 = resources.getColor(R.color.primary_text_default_material_light, null)\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "public class R {\n"
                                        + "    public static class color {\n"
                                        + "        public static final int primary_text_default_material_light = 0x7f070031;\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/MainActivity2.kt:8: Error: Call requires API level 23 (current min is 1): android.content.res.Resources#getColor [NewApi]\n"
                                + "        resources.getColor(R.color.primary_text_default_material_light, null)\n"
                                + "                  ~~~~~~~~\n"
                                + "src/test/pkg/MainActivity2.kt:11: Error: Call requires API level 23 (current min is 1): android.content.res.Resources#getColor [NewApi]\n"
                                + "    val illegalColor2 = resources.getColor(R.color.primary_text_default_material_light, null)\n"
                                + "                                  ~~~~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testSupportLibrary() {
        // Check that we don't flag support library calls; they share the same prefix
        // as the android APIs, but generally are backports (or are annotated with @RequiresApi)
        // so are safe to call on the overall platform minSdkVersion.

        String expected =
                ""
                        + "src/test/pkg/SupportLibTest.java:8: Error: Call requires API level 24 (current min is 1): android.app.Activity#isInPictureInPictureMode [NewApi]\n"
                        + "        isInPictureInPictureMode();      // API 24\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SupportLibTest.java:9: Error: Call requires API level 24 (current min is 1): android.app.Activity#isInPictureInPictureMode [NewApi]\n"
                        + "        this.isInPictureInPictureMode(); // API 24\n"
                        + "             ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SupportLibTest.java:10: Error: Call requires API level 24 (current min is 1): android.app.Activity#isInPictureInPictureMode [NewApi]\n"
                        + "        super.isInPictureInPictureMode(); // API 24\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SupportLibTest.java:17: Error: Call requires API level 24 (current min is 1): android.app.Activity#isInPictureInPictureMode [NewApi]\n"
                        + "        activity1.isInPictureInPictureMode(); // API 24\n"
                        + "                  ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SupportLibTest.java:18: Error: Call requires API level 24 (current min is 1): android.app.Activity#enterPictureInPictureMode [NewApi]\n"
                        + "        activity1.enterPictureInPictureMode(); // API 24\n"
                        + "                  ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SupportLibTest.java:21: Error: Call requires API level 24 (current min is 1): android.app.Activity#isInPictureInPictureMode [NewApi]\n"
                        + "        activity2.isInPictureInPictureMode(); // API 24\n"
                        + "                  ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "6 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package android.support.v7.app;\n"
                                        + "\n"
                                        + "public abstract class MyActivityParent extends android.app.Activity {\n"
                                        + "    public void enterPictureInPictureMode() {\n"
                                        + "        // OK on all API levels\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.support.v7.app.MyActivityParent;\n"
                                        + "\n"
                                        + "public class SupportLibTest extends MyActivityParent {\n"
                                        + "    public void test(Activity activity1, MyActivityParent activity2) {\n"
                                        + "        isInPictureInPictureMode();      // API 24\n"
                                        + "        this.isInPictureInPictureMode(); // API 24\n"
                                        + "        super.isInPictureInPictureMode(); // API 24\n"
                                        + "\n"
                                        + "        enterPictureInPictureMode();      // OK\n"
                                        + "        this.enterPictureInPictureMode(); // OK\n"
                                        + "        super.enterPictureInPictureMode(); // OK\n"
                                        + "\n"
                                        + "        activity1.getMenuInflater(); // OK\n"
                                        + "        activity1.isInPictureInPictureMode(); // API 24\n"
                                        + "        activity1.enterPictureInPictureMode(); // API 24\n"
                                        + "\n"
                                        + "        activity2.getMenuInflater(); // OK\n"
                                        + "        activity2.isInPictureInPictureMode(); // API 24\n"
                                        + "        activity2.enterPictureInPictureMode(); //OK\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testBrokenVirtualDispatch() {
        // Regression test for 65549795: NewApi violations not detected on android.view.View
        String expected =
                ""
                        + "src/com/google/android/apps/common/testing/lint/BrokenNewApi.java:11: Error: Call requires API level 25 (current min is 4): android.view.View#setRevealOnFocusHint [NewApi]\n"
                        + "    setRevealOnFocusHint(true); // API 25\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/com/google/android/apps/common/testing/lint/BrokenNewApi.java:12: Error: Call requires API level 11 (current min is 4): android.view.View#setPivotY [NewApi]\n"
                        + "    setPivotY(1.0f); // api 11\n"
                        + "    ~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package com.google.android.apps.common.testing.lint;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "import android.view.View;\n"
                                        + "\n"
                                        + "public class BrokenNewApi extends View {\n"
                                        + "\n"
                                        + "  public BrokenNewApi(Context c) {\n"
                                        + "    super(c);\n"
                                        + "    // these should be picked up by lint but are not.\n"
                                        + "    setRevealOnFocusHint(true); // API 25\n"
                                        + "    setPivotY(1.0f); // api 11\n"
                                        + "  }\n"
                                        + "}"))
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testCheckThroughAnonymousClass() {
        // Regression test for 76458979: NewApi false positive: anonymous class contents inside an
        // SDK_INT check

        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(4),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Build;\n"
                                        + "import android.view.View;\n"
                                        + "import android.view.View.OnApplyWindowInsetsListener;\n"
                                        + "import android.view.WindowInsets;\n"
                                        + "\n"
                                        + "public class NewApiTest {\n"
                                        + "    public static void test(View v, final OnApplyWindowInsetsListener listener) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= 21) {\n"
                                        + "            v.setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {\n"
                                        + "                @Override\n"
                                        + "                public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {\n"
                                        + "                    listener.onApplyWindowInsets(view, null);\n"
                                        + "                    return null;\n"
                                        + "                }\n"
                                        + "            });\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testCastTypeCheck() {
        // Regression test for 35381581:  Check Class API target
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "import android.os.UserManager;\n"
                                        + "\n"
                                        + "/** @noinspection ClassNameDiffersFromFileName, MethodMayBeStatic */ "
                                        + "public class Check {\n"
                                        + "    public void test(Context context) {\n"
                                        + "        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "\n"))
                .run()
                .expect(
                        "src/test/pkg/Check.java:8: Warning: Field requires API level 17 (current min is 1): android.content.Context#USER_SERVICE [InlinedApi]\n"
                                + "        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);\n"
                                + "                                                                         ~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/Check.java:8: Error: Class requires API level 17 (current min is 1): android.os.UserManager [NewApi]\n"
                                + "        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);\n"
                                + "                                   ~~~~~~~~~~~\n"
                                + "1 errors, 1 warnings\n");
    }

    public void test70784223() {
        // Regression test for 70784223: Linter doesn't detect API level check correctly using
        // Kotlin
        //noinspection all // Sample code
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.app.NotificationChannel\n"
                                        + "import android.app.NotificationManager\n"
                                        + "import android.content.Context\n"
                                        + "import android.os.Build\n"
                                        + "\n"
                                        + "fun test(context: Context) {\n"
                                        + "    val channelName = context.getString(R.string.app_name)\n"
                                        + "\n"
                                        + "    if (Build.VERSION.SDK_INT > 26) {\n"
                                        + "        val name = \"Something\"\n"
                                        + "        val channel = NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_HIGH)\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testKotlinClassLiteral() {
        // Regression test for 117517492: ApiDetector does not work for class literals in Kotlin.
        lint().files(
                        manifest().minSdk(1),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import org.w3c.dom.DOMErrorHandler\n"
                                        + "\n"
                                        + "fun test() {\n"
                                        + "    val clz = DOMErrorHandler::class // API 8\n"
                                        + "}\n"))
                .run()
                .expect(
                        "src/test/pkg/}.kt:6: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                                + "    val clz = DOMErrorHandler::class // API 8\n"
                                + "              ~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void test69788053() {
        // Regression test for issue 69788053:
        // Lint NewApi check doesn't work with inner classes that extend another class
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Fragment;\n"
                                        + "import android.os.AsyncTask;\n"
                                        + "\n"
                                        + "public class MyFragment extends Fragment {\n"
                                        + "    class MyGoodClass {\n"
                                        + "        private void hello() {\n"
                                        + "            getContext(); // Expect warning from lint\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    class MyBadClass extends AsyncTask<Void, Void, Void> {\n"
                                        + "        private void hello() {\n"
                                        + "            getContext(); // Expects warning from lint\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        @Override\n"
                                        + "        protected Void doInBackground(Void... voids) {\n"
                                        + "            return null;\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        "src/test/pkg/MyFragment.java:9: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getContext [NewApi]\n"
                                + "            getContext(); // Expect warning from lint\n"
                                + "            ~~~~~~~~~~\n"
                                + "src/test/pkg/MyFragment.java:15: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getContext [NewApi]\n"
                                + "            getContext(); // Expects warning from lint\n"
                                + "            ~~~~~~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testTypeUseAnnotations() {
        // Regression test for issue 118489828: type use annotations are allowed
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.lang.annotation.ElementType;\n"
                                        + "import java.lang.annotation.Repeatable;\n"
                                        + "import java.lang.annotation.Target;\n"
                                        + "\n"
                                        + "public class TypeUseAnnotations {\n"
                                        + "    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE}) // OK\n"
                                        + "    @interface MyTypeUseAnnotation {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test() {\n"
                                        + "        System.out.println(ElementType.TYPE_PARAMETER); // ERROR\n"
                                        + "    }\n"
                                        + "}\n"),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "class TypeUseAnnotations {\n"
                                        + "    @Target(AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.TYPE)\n"
                                        + "    internal annotation class MyTypeUseAnnotation\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/TypeUseAnnotations.java:13: Error: Field requires API level 26 (current min is 15): java.lang.annotation.ElementType#TYPE_PARAMETER [NewApi]\n"
                                + "        System.out.println(ElementType.TYPE_PARAMETER); // ERROR\n"
                                + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void test110576964() {
        // Regression test for
        // 110576964: android:layout_marginEnd requires API level 17 (current min is 15)

        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        xml(
                                "res/values/styles.xml",
                                ""
                                        + "<resources>\n"
                                        + "<style name=\"CallToActionButtonStyle\">\n"
                                        + "  <item name=\"android:layout_marginEnd\">@dimen/content_margin_xxl</item>\n"
                                        + "  <item name=\"android:layout_marginLeft\">@dimen/content_margin_xxl</item>\n"
                                        + "  <item name=\"android:layout_marginRight\">@dimen/content_margin_xxl</item>\n"
                                        + "  <item name=\"android:layout_marginStart\">@dimen/content_margin_xxl</item>\n"
                                        + "</style>\n"
                                        + "</resources>"),
                        xml(
                                "res/layout/divider.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:layout_marginLeft=\"16dp\"\n"
                                        + "    android:layout_marginStart=\"16dp\">\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expectClean();
    }

    public void test110576968() {
        // Regression test for issue related to 110576968:
        // We weren't enforcing @RequireApi on calls to default constructors
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Build;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class WorkManagerTest {\n"
                                        + "\n"
                                        + "    public void test() {\n"
                                        + "        SystemJobScheduler scheduler = new SystemJobScheduler(); // ERROR\n"
                                        + "    }\n"
                                        + "}"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "\n"
                                        + "@RequiresApi(23)\n"
                                        + "public class SystemJobScheduler {\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/WorkManagerTest.java:9: Error: Call requires API level 23 (current min is 15): SystemJobScheduler [NewApi]\n"
                                + "        SystemJobScheduler scheduler = new SystemJobScheduler(); // ERROR\n"
                                + "                                       ~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void test118555413() {
        // Regression test for issue 118555413: NPE enforcing @RequiresApi on classes
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Build;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class WorkManagerTest {\n"
                                        + "\n"
                                        + "    public void test() {\n"
                                        + "        SystemJobScheduler[] schedulers = new SystemJobScheduler[100]; // ERROR\n"
                                        + "    }\n"
                                        + "}"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "\n"
                                        + "@RequiresApi(23)\n"
                                        + "public class SystemJobScheduler {\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/WorkManagerTest.java:9: Error: Call requires API level 23 (current min is 15): SystemJobScheduler [NewApi]\n"
                                + "        SystemJobScheduler[] schedulers = new SystemJobScheduler[100]; // ERROR\n"
                                + "                                          ~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testIgnoreAttributesWithinVector() {
        lint().files(
                        xml(
                                "res/drawable/ic_drawable.xml",
                                ""
                                        + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:aapt=\"http://schemas.android.com/aapt\">\n"
                                        + "<item>\n"
                                        + "<aapt:attr name=\"android:drawable\">\n"
                                        + "<vector android:width=\"24dp\" android:height=\"24dp\" android:viewportHeight=\"24.0\" android:viewportWidth=\"24.0\">\n"
                                        + "<path android:pathData=\"M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-0.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z\" android:strokeColor=\"@color/colorAccent\" android:strokeWidth=\"1\"/>\n"
                                        + "</vector>\n"
                                        + "</aapt:attr>\n"
                                        + "</item>\n"
                                        + "</selector>"))
                .run()
                .expect(
                        ""
                                + "res/drawable/ic_drawable.xml:4: Error: <vector> requires API level 21 (current min is 1) or building with Android Gradle plugin 1.4 or higher [NewApi]\n"
                                + "<vector android:width=\"24dp\" android:height=\"24dp\" android:viewportHeight=\"24.0\" android:viewportWidth=\"24.0\">\n"
                                + " ~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void test117793069() {
        // Comprehensive Kotlin API check test, originally part of the Kotlin plugin
        // Regression test for issue 117793069
        lint().files(
                        kotlin(
                                "src/test/pkg/ApiCallTest.kt",
                                ""
                                        + "package test.pkg\n"
                                        + "import android.animation.RectEvaluator\n"
                                        + "import android.annotation.SuppressLint\n"
                                        + "import android.annotation.TargetApi\n"
                                        + "import org.w3c.dom.DOMError\n"
                                        + "import org.w3c.dom.DOMErrorHandler\n"
                                        + "import org.w3c.dom.DOMLocator\n"
                                        + "\n"
                                        + "import android.view.View\n"
                                        + "import android.view.ViewGroup\n"
                                        + "import android.view.ViewGroup.LayoutParams\n"
                                        + "import android.app.Activity\n"
                                        + "import android.app.ApplicationErrorReport\n"
                                        + "import android.graphics.drawable.VectorDrawable\n"
                                        + "import android.graphics.Path\n"
                                        + "import android.graphics.PorterDuff\n"
                                        + "import android.graphics.Rect\n"
                                        + "import android.os.Build\n"
                                        + "import android.widget.*\n"
                                        + "import dalvik.bytecode.OpcodeInfo\n"
                                        + "\n"
                                        + "import android.os.Build.VERSION\n"
                                        + "import android.os.Build.VERSION.SDK_INT\n"
                                        + "import android.os.Build.VERSION_CODES\n"
                                        + "import android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH\n"
                                        + "import android.os.Build.VERSION_CODES.JELLY_BEAN\n"
                                        + "import android.os.Bundle\n"
                                        + "import android.os.Parcelable\n"
                                        + "import android.system.ErrnoException\n"
                                        + "import android.widget.TextView\n"
                                        + "import android.support.annotation.RequiresApi\n"
                                        + "\n"
                                        + "@Suppress(\"SENSELESS_COMPARISON\", \"UNUSED_EXPRESSION\", \"UsePropertyAccessSyntax\", \"UNUSED_VARIABLE\", \"unused\", \"UNUSED_PARAMETER\", \"DEPRECATION\", \"USELESS_CAST\")\n"
                                        + "class ApiCallTest: Activity() {\n"
                                        + "\n"
                                        + "    fun method(chronometer: Chronometer, locator: DOMLocator) {\n"
                                        + "        chronometer./*Call requires API level 16 (current min is 1): android.view.View#setBackground*/setBackground/**/(null)\n"
                                        + "\n"
                                        + "        // Ok\n"
                                        + "        Bundle().getInt(\"\")\n"
                                        + "\n"
                                        + "        /*Field requires API level 16 (current min is 1): android.view.View#SYSTEM_UI_FLAG_FULLSCREEN*/View.SYSTEM_UI_FLAG_FULLSCREEN/**/\n"
                                        + "\n"
                                        + "        // Virtual call\n"
                                        + "        /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/getActionBar/**/() // API 11\n"
                                        + "        /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/actionBar/**/ // API 11\n"
                                        + "\n"
                                        + "        // Class references (no call or field access)\n"
                                        + "        val error: DOMError? = null // API 8\n"
                                        + "        val clz = /*Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler*/DOMErrorHandler::class/**/ // API 8\n"
                                        + "\n"
                                        + "        // Method call\n"
                                        + "        chronometer./*Call requires API level 3 (current min is 1): android.widget.Chronometer#getOnChronometerTickListener*/onChronometerTickListener/**/ // API 3\n"
                                        + "\n"
                                        + "        // Inherited method call (from TextView\n"
                                        + "        chronometer./*Call requires API level 11 (current min is 1): android.widget.TextView#setTextIsSelectable*/setTextIsSelectable/**/(true) // API 11\n"
                                        + "\n"
                                        + "        /*Class requires API level 14 (current min is 1): android.widget.GridLayout*/GridLayout::class/**/\n"
                                        + "\n"
                                        + "        // Field access\n"
                                        + "        val field = /*Field requires API level 11 (current min is 1): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE*/OpcodeInfo.MAXIMUM_VALUE/**/ // API 11\n"
                                        + "\n"
                                        + "\n"
                                        + "        val fillParent = LayoutParams.FILL_PARENT // API 1\n"
                                        + "        // This is a final int, which means it gets inlined\n"
                                        + "        val matchParent = LayoutParams.MATCH_PARENT // API 8\n"
                                        + "        // Field access: non final\n"
                                        + "        val batteryInfo = /*Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo*/report!!.batteryInfo/**/\n"
                                        + "\n"
                                        + "        // Enum access\n"
                                        + "        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            val mode = /*Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY*/PorterDuff.Mode.OVERLAY/**/ // API 11\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test(rect: Rect) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            RectEvaluator(rect); // OK\n"
                                        + "        }\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            if (rect != null) {\n"
                                        + "                RectEvaluator(rect); // OK\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test2(rect: Rect) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            RectEvaluator(rect); // OK\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test3(rect: Rect) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {\n"
                                        + "            /*Call requires API level 18 (current min is 1): android.animation.RectEvaluator()*/RectEvaluator()/**/; // ERROR\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test4(rect: Rect) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n"
                                        + "            System.out.println(\"Something\");\n"
                                        + "            RectEvaluator(rect); // OK\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 21 (current min is 1): android.animation.RectEvaluator()*/RectEvaluator(rect)/**/; // ERROR\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test5(rect: Rect) {\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {\n"
                                        + "            /*Call requires API level 21 (current min is 1): android.animation.RectEvaluator()*/RectEvaluator(rect)/**/; // ERROR\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 21 (current min is 1): android.animation.RectEvaluator()*/RectEvaluator(rect)/**/; // ERROR\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test(priority: Boolean, layout: ViewGroup) {\n"
                                        + "        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT >= ICE_CREAM_SANDWICH) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        } else {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= 14) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // Nested conditionals\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {\n"
                                        + "            if (priority) {\n"
                                        + "                /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "            } else {\n"
                                        + "                /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "            }\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // Nested conditionals 2\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {\n"
                                        + "            if (priority) {\n"
                                        + "                GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "            } else {\n"
                                        + "                GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "            }\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test2(priority: Boolean) {\n"
                                        + "        if (android.os.Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (android.os.Build.VERSION.SDK_INT >= 16) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (android.os.Build.VERSION.SDK_INT >= 13) {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/./*Call requires API level 14 (current min is 1): android.widget.GridLayout#getOrientation*/getOrientation/**/(); // Flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT >= JELLY_BEAN) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        } else {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (Build.VERSION.SDK_INT >= 16) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {\n"
                                        + "            GridLayout(null).getOrientation(); // Not flagged\n"
                                        + "        } else {\n"
                                        + "            /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(null)/**/; // Flagged\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test(textView: TextView) {\n"
                                        + "        if (textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/()) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "        if (textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT >= JELLY_BEAN && textView.isSuggestionsEnabled()) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT >= JELLY_BEAN && textView.isSuggestionsEnabled) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT >= JELLY_BEAN && (textView.text != \"\" || textView.isSuggestionsEnabled)) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < JELLY_BEAN && (textView.text != \"\" || textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/)) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < JELLY_BEAN && textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/()) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < JELLY_BEAN && textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < JELLY_BEAN || textView.isSuggestionsEnabled) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT > JELLY_BEAN || textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "\n"
                                        + "        // getActionBar() API 11\n"
                                        + "        if (SDK_INT <= 10 || getActionBar() == null) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < 10 || /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/getActionBar/**/() == null) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < 11 || getActionBar() == null) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT != 11 || getActionBar() == null) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT != 12 || /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/getActionBar/**/() == null) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT <= 11 || getActionBar() == null) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < 12 || getActionBar() == null) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT <= 12 || getActionBar() == null) {\n"
                                        + "            //NO ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT < 9 || /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/getActionBar/**/() == null) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (SDK_INT <= 9 || /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/getActionBar/**/() == null) {\n"
                                        + "            //ERROR\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun testReturn() {\n"
                                        + "        if (SDK_INT < 11) {\n"
                                        + "            return\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // No Error\n"
                                        + "        val actionBar = getActionBar()\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun testThrow() {\n"
                                        + "        if (SDK_INT < 11) {\n"
                                        + "            throw IllegalStateException()\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // No Error\n"
                                        + "        val actionBar = getActionBar()\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun testError() {\n"
                                        + "        if (SDK_INT < 11) {\n"
                                        + "            error(\"Api\")\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // No Error\n"
                                        + "        val actionBar = getActionBar()\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun testWithoutAnnotation(textView: TextView) {\n"
                                        + "        if (textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/()) {\n"
                                        + "\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (textView./*Call requires API level 14 (current min is 1): android.widget.TextView#isSuggestionsEnabled*/isSuggestionsEnabled/**/) {\n"
                                        + "\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(JELLY_BEAN)\n"
                                        + "    fun testWithTargetApiAnnotation(textView: TextView) {\n"
                                        + "        if (textView.isSuggestionsEnabled()) {\n"
                                        + "            //NO ERROR, annotation\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (textView.isSuggestionsEnabled) {\n"
                                        + "            //NO ERROR, annotation\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressLint(\"NewApi\")\n"
                                        + "    fun testWithSuppressLintAnnotation(textView: TextView) {\n"
                                        + "        if (textView.isSuggestionsEnabled()) {\n"
                                        + "            //NO ERROR, annotation\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (textView.isSuggestionsEnabled) {\n"
                                        + "            //NO ERROR, annotation\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun testCatch() {\n"
                                        + "        try {\n"
                                        + "\n"
                                        + "        } catch (e: /*Exception requires API level 21 (current min is 1): android.system.ErrnoException*/ErrnoException/**/) {\n"
                                        + "\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun testOverload() {\n"
                                        + "        // this overloaded addOval available only on API Level 21\n"
                                        + "        Path()./*Call requires API level 21 (current min is 1): android.graphics.Path#addOval*/addOval/**/(0f, 0f, 0f, 0f, Path.Direction.CW)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // KT-14737 False error with short-circuit evaluation\n"
                                        + "    fun testShortCircuitEvaluation() {\n"
                                        + "        /*Call requires API level 21 (current min is 1): android.content.Context#getDrawable*/getDrawable/**/(0) // error here as expected\n"
                                        + "        if(Build.VERSION.SDK_INT >= 23\n"
                                        + "           && null == getDrawable(0)) // error here should not occur\n"
                                        + "        {\n"
                                        + "            getDrawable(0) // no error here as expected\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // KT-1482 Kotlin Lint: \"Calling new methods on older versions\" does not report call on receiver in extension function\n"
                                        + "    private fun Bundle.caseE1a() { /*Call requires API level 18 (current min is 1): android.os.Bundle#getBinder*/getBinder/**/(\"\") }\n"
                                        + "\n"
                                        + "    private fun Bundle.caseE1c() { this./*Call requires API level 18 (current min is 1): android.os.Bundle#getBinder*/getBinder/**/(\"\") }\n"
                                        + "\n"
                                        + "    private fun caseE1b(bundle: Bundle) { bundle./*Call requires API level 18 (current min is 1): android.os.Bundle#getBinder*/getBinder/**/(\"\") }\n"
                                        + "\n"
                                        + "    // KT-12023 Kotlin Lint: Cast doesn't trigger minSdk error\n"
                                        + "    fun testCast(layout: ViewGroup) {\n"
                                        + "        if (layout is LinearLayout) {}  // OK API 1\n"
                                        + "        layout as? LinearLayout         // OK API 1\n"
                                        + "        layout as LinearLayout          // OK API 1\n"
                                        + "\n"
                                        + "        if (layout !is /*Class requires API level 14 (current min is 1): android.widget.GridLayout*/GridLayout/**/) {}\n"
                                        + "        layout as? /*Class requires API level 14 (current min is 1): android.widget.GridLayout*/GridLayout/**/\n"
                                        + "        layout as /*Class requires API level 14 (current min is 1): android.widget.GridLayout*/GridLayout/**/\n"
                                        + "\n"
                                        + "        val grid = layout as? /*Class requires API level 14 (current min is 1): android.widget.GridLayout*/GridLayout/**/\n"
                                        + "        val linear = layout as LinearLayout // OK API 1\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(21)\n"
                                        + "    class MyVectorDrawable : VectorDrawable()\n"
                                        + "\n"
                                        + "    fun testTypes() {\n"
                                        + "        /*Call requires API level 14 (current min is 1): android.widget.GridLayout()*/GridLayout(this)/**/\n"
                                        + "        val c = /*Class requires API level 21 (current min is 1): android.graphics.drawable.VectorDrawable*/VectorDrawable::class/**/.java\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun testCallWithApiAnnotation(textView: TextView) {\n"
                                        + "        /*Call requires API level 21 (current min is 1): MyVectorDrawable*/MyVectorDrawable()/**/\n"
                                        + "        /*Call requires API level 16 (current min is 1): testWithTargetApiAnnotation*/testWithTargetApiAnnotation/**/(textView)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    companion object : Activity() {\n"
                                        + "        fun test() {\n"
                                        + "            /*Call requires API level 21 (current min is 1): android.content.Context#getDrawable*/getDrawable/**/(0)\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Return type\n"
                                        + "    internal // API 14\n"
                                        + "    val gridLayout: GridLayout?\n"
                                        + "        get() = null\n"
                                        + "\n"
                                        + "    private val report: ApplicationErrorReport?\n"
                                        + "        get() = null\n"
                                        + "}\n"
                                        + "\n"
                                        + "object O: Activity() {\n"
                                        + "    fun test() {\n"
                                        + "        /*Call requires API level 21 (current min is 1): android.content.Context#getDrawable*/getDrawable/**/(0)\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "\n"
                                        + "fun testJava8() {\n"
                                        + "    // Error, Api 24, Java8\n"
                                        + "    mutableListOf(1, 2, 3)./*Call requires API level 24 (current min is 1): java.util.Collection#removeIf*/removeIf/**/ {\n"
                                        + "        true\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Ok, Kotlin\n"
                                        + "    mutableListOf(1, 2, 3).removeAll {\n"
                                        + "        true\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Error, Api 24, Java8\n"
                                        + "    mapOf(1 to 2)./*Call requires API level 24 (current min is 1): java.util.Map#forEach*/forEach/**/ { key, value -> key + value }\n"
                                        + "\n"
                                        + "    // Ok, Kotlin\n"
                                        + "    mapOf(1 to 2).forEach { (key, value) -> key + value }\n"
                                        + "}\n"
                                        + "\n"
                                        + "interface WithDefault {\n"
                                        + "    // Should be ok\n"
                                        + "    fun methodWithBody() {\n"
                                        + "        return\n"
                                        + "    }\n"
                                        + "}"),
                        mSupportClasspath,
                        mSupportJar)
                .run()
                .expectInlinedMessages(false);
    }

    public void testInfixCall() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi\n"
                                        + "\n"
                                        + "class MyClass2 {\n"
                                        + "    @RequiresApi(21)\n"
                                        + "    infix fun something(other: MyClass2) {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    companion object {\n"
                                        + "        fun test(a: MyClass2, b: MyClass2) {\n"
                                        + "            a something b\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}"),
                        mSupportClasspath,
                        mSupportJar)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/MyClass2.kt:12: Error: Call requires API level 21 (current min is 15): something [NewApi]\n"
                                + "            a something b\n"
                                + "            ~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testCheckSuper() {
        // Regression test for issue 135460230
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                ""
                                        + "import android.content.Context;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "import android.view.ViewGroup;\n"
                                        + "\n"
                                        + "public abstract class ViewPager extends ViewGroup {\n"
                                        + "\n"
                                        + "    public ViewPager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {\n"
                                        + "        super(context, attrs, defStyleAttr, defStyleRes);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public CharSequence getAccessibilityClassName() {\n"
                                        + "        return super.getAccessibilityClassName();\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .run()
                .expect(
                        ""
                                + "src/ViewPager.java:8: Error: Call requires API level 21 (current min is 15): new android.view.ViewGroup [NewApi]\n"
                                + "        super(context, attrs, defStyleAttr, defStyleRes);\n"
                                + "        ~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testSourceJars() {
        // Make sure that resolving files through srcjars is working properly

        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        jar(
                                "libs/library.srcjar",
                                java(
                                        ""
                                                + "package test.pkg.library;\n"
                                                + "\n"
                                                + "import android.support.annotation.RequiresApi;\n"
                                                + "import android.os.Build;\n"
                                                + "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n"
                                                + "public class Library {\n"
                                                + "    @RequiresApi(19)\n"
                                                + "    public void requiresKitKat() {\n"
                                                + "    }\n"
                                                + "}\n")),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import test.pkg.library.Library;\n"
                                        + "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n"
                                        + "public class TestRequiresApi {\n"
                                        + "    public void caller() {\n"
                                        + "        new Library().requiresKitKat(); // ERROR - requires 19\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/TestRequiresApi.java:7: Error: Call requires API level 19 (current min is 15): requiresKitKat [NewApi]\n"
                                + "        new Library().requiresKitKat(); // ERROR - requires 19\n"
                                + "                      ~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testSourceJarsKotlin() {
        // Make sure that resolving files through srcjars is working properly, including
        // sources in srcjars only provided as Kotlin sources, not as compiled libraries

        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        jar(
                                "libs/library.srcjar",
                                kotlin(
                                        ""
                                                + "package test.pkg\n"
                                                + "\n"
                                                + "import android.support.annotation.RequiresApi\n"
                                                + "\n"
                                                + "class Library {\n"
                                                + "    @RequiresApi(19)\n"
                                                + "    fun requiresKitKat() {\n"
                                                + "    }\n"
                                                + "}\n"
                                                + " ")),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import test.pkg.library.Library\n"
                                        + "\n"
                                        + "class TestRequiresApi {\n"
                                        + "    fun caller() {\n"
                                        + "        Library().requiresKitKat() // ERROR - requires 19\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/TestRequiresApi.kt:7: Error: Call requires API level 19 (current min is 15): requiresKitKat [NewApi]\n"
                                + "        Library().requiresKitKat() // ERROR - requires 19\n"
                                + "                  ~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testFutureApi() {
        // This test only works while there is a preview API
        if (SdkVersionInfo.HIGHEST_KNOWN_STABLE_API == SdkVersionInfo.HIGHEST_KNOWN_API) {
            return;
        }
        String preview = SdkVersionInfo.getCodeName(SdkVersionInfo.HIGHEST_KNOWN_API);
        String expected =
                ""
                        + "src/test/pkg/TestRequiresApi.java:8: Error: Call requires API level "
                        + preview
                        + " (current min is 15): requiresPreview [NewApi]\n"
                        + "        requiresPreview();\n"
                        + "        ~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(15),
                        java(
                                "src/test/pkg/TestRequiresApi.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "import android.os.Build;\n"
                                        + "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n"
                                        + "public class TestRequiresApi {\n"
                                        + "    public void caller() {\n"
                                        + "        requiresPreview();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi("
                                        + (SdkVersionInfo.HIGHEST_KNOWN_STABLE_API + 1)
                                        + ")\n"
                                        + "    public void requiresPreview() {\n"
                                        + "    }\n"
                                        + "}\n"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expect(expected);
    }

    public void testKT_37200() {
        // Regression test for https://youtrack.jetbrains.com/issue/KT-37200
        lint().files(
                        manifest().minSdk(15),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "inline fun <reified F> ViewModelContext.viewModelFactory(): F {\n"
                                        + "    return activity as? F ?: throw IllegalStateException(\"Boo!\")\n"
                                        + "}\n"
                                        + "\n"
                                        + "sealed class ViewModelContext {\n"
                                        + "    abstract val activity: Number\n"
                                        + "}\n"),
                        kotlin(
                                ""
                                        + "inline fun <reified A : Activity, T : Any> ActivityScenario<A>.withActivity(\n"
                                        + "    crossinline block: A.() -> T\n"
                                        + "): T {\n"
                                        + "    lateinit var value: T\n"
                                        + "    var err: Throwable? = null\n"
                                        + "    onActivity { activity ->\n"
                                        + "        try {\n"
                                        + "            value = block(activity)\n"
                                        + "        } catch (t: Throwable) {\n"
                                        + "            err = t\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "    err?.let { throw it }\n"
                                        + "    return value\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package androidx.test.core.app;\n"
                                        + "import android.app.Activity;\n"
                                        + "import java.io.Closeable;\n"
                                        + "public class ActivityScenario<A extends Activity> implements Closeable {\n"
                                        + ""
                                        + "}"),
                        mSupportClasspath,
                        mSupportJar)
                .checkMessage(this::checkReportedError)
                .run()
                .expectClean();
    }

    public void testTargetApiInCustomJar() {
        String libraryJarPath = "jar/jar/binks.jar";
        String base64JarData =
                ""
                        + "H4sIAAAAAAAAAAvwZmbhYmDgYAACRRsGJMDJwMLg6xriqOvp56b/7xQDQwCK"
                        + "UpaLMTuKgSwQFgFiuFJfRz9PN9fgED1fN9/EvMy01OIS3bDUouLM/DwrBUM9"
                        + "A14u56LUxJLUFF2nSiuFpMSq1BxerpDEovTUEl2fxKTUHCsFff3UioLUoszc"
                        + "1LySxBz90mKgdv3EosTs1OIM/azEskQgUQTCVjmZeSXxJUAr4nMyk3i5eLkC"
                        + "cPqHBYhB2nCr4ICqgKkSYeDg4GBgRFMljKTKKTMvu1gvOSexuLjV/7QXs6GI"
                        + "7cvg41nZtQ6C4u9Wcac/9hNcpSDCNjVql//yhffKfI75LPv/1KvgIfuPwEdn"
                        + "Q44cP9Ry5/jO73eqP33794nrActuxlM3ViXv7pNtvCWZWfSc6em7hx4LijLV"
                        + "vBZITgy7/vNwxM2FMuo9W5SFT6eo6P78I7xmR/Wpmu8+ty7s5770+c2UiSHH"
                        + "ii1DbY7xhj5j+ca2s+zLgl9VjpKGu5dvLbw08aLNQmlP241taonTlq/cqDcl"
                        + "/mnBArniTVqvlx5ZvJp75pNVZ+Jvir/IWKxcdOrUk2/fjz0498QsLPzJVLbW"
                        + "TY9nxpxg01rBteb/BZ0Ln7YaTKySuteTrlVm4cBZx3+Dyz8p+rFOiv6N8P55"
                        + "ivMsNBae2xbWsDoi8XaEWYGYyA2p40ub7hz8JOLysffGA+68lNQ/Zr8yjNZ/"
                        + "tV//c7r6hsgVWfXAhMXO0a+2dJs1IwPDF0ZQQmNk4mLAnSpRAVoaRdWKnkoR"
                        + "QBtHmsVnOarVF+AJCrcODhQdn1ASGCOTCAMiiSEHgDCKLglGrAkuwJuVDSTN"
                        + "CoRqQCVTmEA8AKV33pzQAwAA";

        TestFile customLibraryJar = base64gzip(libraryJarPath, base64JarData);
        TestFile classPath = classpath(SUPPORT_JAR_PATH, libraryJarPath);

        lint().files(
                        manifest().minSdk(10),
                        java(
                                "package test.pkg;\n"
                                        + "\n"
                                        + "import jar.jar.Binks;\n"
                                        + "import android.support.annotation.RequiresApi;\n"
                                        + "\n"
                                        + "public class CheckJarAnnotations {\n"
                                        + "    public static void test() {\n"
                                        + "        Binks.packageLintTest(); // Should Fail\n"
                                        + "        Binks.nonLiteralPackageLintTest(); // Should Fail\n"
                                        + "        classLintTest(); // Should Fail\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @RequiresApi(29)\n"
                                        + "    private void classLintTest() {}\n"
                                        + "}\n"),
                        classPath,
                        SUPPORT_ANNOTATIONS_JAR,
                        customLibraryJar)
                .run()
                .expect(
                        "src/test/pkg/CheckJarAnnotations.java:8: Error: Call requires API level 29 (current min is 10): packageLintTest [NewApi]\n"
                                + "        Binks.packageLintTest(); // Should Fail\n"
                                + "              ~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CheckJarAnnotations.java:9: Error: Call requires API level 29 (current min is 10): nonLiteralPackageLintTest [NewApi]\n"
                                + "        Binks.nonLiteralPackageLintTest(); // Should Fail\n"
                                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/CheckJarAnnotations.java:10: Error: Call requires API level 29 (current min is 10): classLintTest [NewApi]\n"
                                + "        classLintTest(); // Should Fail\n"
                                + "        ~~~~~~~~~~~~~\n"
                                + "3 errors, 0 warnings");
    }

    public void testKotlinStdlibPlatformDependent() {
        // Regression test for https://issuetracker.google.com/77187996
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "fun test1(map: MutableMap<String, String>) {\n"
                                        + "    map.getOrDefault(\"a\", \"b\")\n"
                                        + "    map.remove(\"a\", \"b\")\n"
                                        + "}"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/test.kt:4: Error: Call requires API level 24 (current min is 1): java.util.Map#getOrDefault [NewApi]\n"
                                + "    map.getOrDefault(\"a\", \"b\")\n"
                                + "        ~~~~~~~~~~~~\n"
                                + "src/test/pkg/test.kt:5: Error: Call requires API level 24 (current min is 1): java.util.Map#remove [NewApi]\n"
                                + "    map.remove(\"a\", \"b\")\n"
                                + "        ~~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testVersionCheckWithExit() {
        lint().files(
                        manifest().minSdk(21),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.os.Build\n"
                                        + "import android.telephony.SmsManager\n"
                                        + "import android.util.Log\n"
                                        + "\n"
                                        + "fun test() {\n"
                                        + "    val defaultSmsManager = SmsManager.getDefault()\n"
                                        + "    if (Build.VERSION.SDK_INT < 22) {\n"
                                        + "        val subscriptionId = defaultSmsManager.subscriptionId // ERROR: requires 22\n"
                                        + "        Log.d(\"AppLog\", \"subscriptionId:$subscriptionId\")\n"
                                        + "        return\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/test.kt:10: Error: Call requires API level 22 (current min is 21): android.telephony.SmsManager#getSubscriptionId [NewApi]\n"
                                + "        val subscriptionId = defaultSmsManager.subscriptionId // ERROR: requires 22\n"
                                + "                                               ~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void test150198810() {
        // Regression test for https://issuetracker.google.com/150198810
        lint().files(
                        manifest().minSdk(21),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.os.Build\n"
                                        + "import android.telephony.SmsManager\n"
                                        + "import android.util.Log\n"
                                        + "\n"
                                        + "fun test() {\n"
                                        + "    val defaultSmsManager = SmsManager.getDefault()\n"
                                        + "    if (Build.VERSION.SDK_INT < 22) {\n"
                                        + "        return\n"
                                        + "    }\n"
                                        + "    val subscriptionId = defaultSmsManager.subscriptionId\n"
                                        + "    Log.d(\"AppLog\", \"subscriptionId:$subscriptionId\")\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    @Override
    protected boolean ignoreSystemErrors() {
        //noinspection SimplifiableIfStatement
        if (getName().equals("testMissingApiDatabase")) {
            return false;
        }
        return super.ignoreSystemErrors();
    }

    @Override
    protected void checkReportedError(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @NonNull Location location,
            @NonNull String message,
            @Nullable LintFix fixData) {
        if (issue == UNSUPPORTED || issue == INLINED) {
            if (message.startsWith("The SDK platform-tools version (")) {
                return;
            }
            if (message.startsWith("Type annotations")) {
                return;
            }
            if (message.startsWith("Upgrade buildToolsVersion from ")) {
                return;
            }
            assertTrue(fixData instanceof LintFix.DataMap);
            LintFix.DataMap map = (LintFix.DataMap) fixData;
            Integer apiLevel = map.get(Integer.class);
            assertNotNull(apiLevel);
            int requiredVersion = apiLevel;
            assertTrue(
                    "Could not extract message tokens from \"" + message + "\"",
                    requiredVersion >= 1 && requiredVersion <= SdkVersionInfo.HIGHEST_KNOWN_API);
        }
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mApiCallTest =
            java(
                    ""
                            + "package foo.bar;\n"
                            + "\n"
                            + "import org.w3c.dom.DOMError;\n"
                            + "import org.w3c.dom.DOMErrorHandler;\n"
                            + "import org.w3c.dom.DOMLocator;\n"
                            + "\n"
                            + "import android.view.ViewGroup.LayoutParams;\n"
                            + "import android.app.Activity;\n"
                            + "import android.app.ApplicationErrorReport;\n"
                            + "import android.app.ApplicationErrorReport.BatteryInfo;\n"
                            + "import android.graphics.PorterDuff;\n"
                            + "import android.graphics.PorterDuff.Mode;\n"
                            + "import android.widget.Chronometer;\n"
                            + "import android.widget.GridLayout;\n"
                            + "import dalvik.bytecode.OpcodeInfo;\n"
                            + "\n"
                            + "public class ApiCallTest extends Activity {\n"
                            + "\tpublic void method(Chronometer chronometer, DOMLocator locator) {\n"
                            + "\t\t// Virtual call\n"
                            + "\t\tgetActionBar(); // API 11\n"
                            + "\n"
                            + "\t\t// Class references (no call or field access)\n"
                            + "\t\tDOMError error = null; // API 8\n"
                            + "\t\tClass<?> clz = DOMErrorHandler.class; // API 8\n"
                            + "\n"
                            + "\t\t// Method call\n"
                            + "\t\tchronometer.getOnChronometerTickListener(); // API 3 \n"
                            + "\n"
                            + "\t\t// Inherited method call (from TextView\n"
                            + "\t\tchronometer.setTextIsSelectable(true); // API 11\n"
                            + "\n"
                            + "\t\t// Field access\n"
                            + "\t\tint field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                            + "\t\tint fillParent = LayoutParams.FILL_PARENT; // API 1\n"
                            + "\t\t// This is a final int, which means it gets inlined\n"
                            + "\t\tint matchParent = LayoutParams.MATCH_PARENT; // API 8\n"
                            + "\t\t// Field access: non final\n"
                            + "\t\tBatteryInfo batteryInfo = getReport().batteryInfo;\n"
                            + "\n"
                            + "\t\t// Enum access\n"
                            + "\t\tMode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                            + "\t}\n"
                            + "\n"
                            + "\t// Return type\n"
                            + "\tGridLayout getGridLayout() { // API 14\n"
                            + "\t\treturn null;\n"
                            + "\t}\n"
                            + "\n"
                            + "\tprivate ApplicationErrorReport getReport() {\n"
                            + "\t\treturn null;\n"
                            + "\t}\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mApiCallTest11 =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.annotation.SuppressLint;\n"
                            + "import android.app.ActionBar;\n"
                            + "import android.app.Activity;\n"
                            + "import android.content.Context;\n"
                            + "import android.graphics.drawable.Drawable;\n"
                            + "import android.widget.LinearLayout;\n"
                            + "\n"
                            + "public class ApiCallTest11 extends Activity {\n"
                            + "\tMyActivity mActionBarHost;\n"
                            + "\n"
                            + "    public ActionBar getActionBar() {\n"
                            + "        return mActionBarHost.getActionBar();\n"
                            + "    }\n"
                            + "\n"
                            + "    public boolean isDestroyed() {\n"
                            + "        return true;\n"
                            + "    }\n"
                            + "\n"
                            + "    @SuppressLint(\"Override\")\n"
                            + "    public void finishAffinity() {\n"
                            + "    }\n"
                            + "\n"
                            + "    private class MyLinear extends LinearLayout {\n"
                            + "        private Drawable mDividerDrawable;\n"
                            + "\n"
                            + "        public MyLinear(Context context) {\n"
                            + "            super(context);\n"
                            + "        }\n"
                            + "\n"
                            + "       /**\n"
                            + "         * Javadoc here\n"
                            + "         *\n"
                            + "         *\n"
                            + "         *\n"
                            + "         *\n"
                            + "         */\n"
                            + "        public void setDividerDrawable(Drawable dividerDrawable) {\n"
                            + "            mDividerDrawable = dividerDrawable;\n"
                            + "        }\n"
                            + "    }\n"
                            + "\n"
                            + "    private class MyActivity {\n"
                            + "        public ActionBar getActionBar() {\n"
                            + "            return null;\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n"
                            + "\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mApiCallTest12 =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.annotation.SuppressLint;\n"
                            + "import android.annotation.TargetApi;\n"
                            + "import android.os.Build;\n"
                            + "\n"
                            + "import java.text.DateFormatSymbols;\n"
                            + "import java.text.SimpleDateFormat;\n"
                            + "import java.util.Locale;\n"
                            + "\n"
                            + "@SuppressWarnings({ \"unused\", \"javadoc\" })\n"
                            + "@SuppressLint(\"SimpleDateFormat\")\n"
                            + "public class ApiCallTest12 {\n"
                            + "\tpublic void test() {\n"
                            + "\t\t// Normal SimpleDateFormat calls\n"
                            + "\t\tnew SimpleDateFormat();\n"
                            + "\t\tnew SimpleDateFormat(\"yyyy-MM-dd\");\n"
                            + "\t\tnew SimpleDateFormat(\"yyyy-MM-dd\", DateFormatSymbols.getInstance());\n"
                            + "\t\tnew SimpleDateFormat(\"yyyy-MM-dd\", Locale.US);\n"
                            + "\t\tnew SimpleDateFormat(\"MMMM\", Locale.US);\n"
                            + "\n"
                            + "\t\t// Flag format strings requiring API 9\n"
                            + "\t\tnew SimpleDateFormat(\"yyyy-MM-dd LL\", Locale.US);\n"
                            + "\n"
                            + "\t\tSimpleDateFormat format = new SimpleDateFormat(\"cc yyyy-MM-dd\");\n"
                            + "\n"
                            + "\t\t// Escaped text\n"
                            + "\t\tnew SimpleDateFormat(\"MM-dd 'My Location'\", Locale.US);\n"
                            + "\t}\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mAttribute2 =
            xml(
                    "res/layout/attribute2.xml",
                    ""
                            + "<ExitText\n"
                            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:text=\"Hello\"\n"
                            + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
                            + "    android:layout_width=\"wrap_content\"\n"
                            + "    android:layout_height=\"wrap_content\" />\n"
                            + "\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mIntermediate =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.Activity;\n"
                            + "import android.widget.Button;\n"
                            + "\n"
                            + "/** Local activity */\n"
                            + "public abstract class Intermediate extends Activity {\n"
                            + "\n"
                            + "\t/** Local Custom view */\n"
                            + "\tpublic abstract static class IntermediateCustomV extends Button {\n"
                            + "\t\tpublic IntermediateCustomV() {\n"
                            + "\t\t\tsuper(null);\n"
                            + "\t\t}\n"
                            + "\t}\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mJava7API =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "public class Java7API {\n"
                            + "    public Object testReflection(String name) {\n"
                            + "        try {\n"
                            + "            Class<?> clazz = Class.forName(name);\n"
                            + "            return clazz.newInstance();\n"
                            + "        } catch (ReflectiveOperationException e) {\n"
                            + "            e.printStackTrace();\n"
                            + "        }\n"
                            + "        return null;\n"
                            + "    }\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mLayout =
            xml(
                    "res/layout/layout.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"fill_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <!-- Requires API 5 -->\n"
                            + "\n"
                            + "    <QuickContactBadge\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\" />\n"
                            + "\n"
                            + "    <!-- Requires API 11 -->\n"
                            + "\n"
                            + "    <CalendarView\n"
                            + "        android:layout_width=\"fill_parent\"\n"
                            + "        android:layout_height=\"fill_parent\" />\n"
                            + "\n"
                            + "    <!-- Requires API 14 -->\n"
                            + "\n"
                            + "    <GridLayout\n"
                            + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                            + "        bar=\"@android:color/holo_red_light\"\n"
                            + "        android:layout_width=\"fill_parent\"\n"
                            + "        android:layout_height=\"fill_parent\" >\n"
                            + "\n"
                            + "        <Button\n"
                            + "            android:layout_width=\"fill_parent\"\n"
                            + "            android:layout_height=\"fill_parent\" />\n"
                            + "    </GridLayout>\n"
                            + "\n"
                            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mLayout2 =
            xml(
                    "res/layout-v11/layout.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"fill_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <!-- Requires API 5 -->\n"
                            + "\n"
                            + "    <QuickContactBadge\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\" />\n"
                            + "\n"
                            + "    <!-- Requires API 11 -->\n"
                            + "\n"
                            + "    <CalendarView\n"
                            + "        android:layout_width=\"fill_parent\"\n"
                            + "        android:layout_height=\"fill_parent\" />\n"
                            + "\n"
                            + "    <!-- Requires API 14 -->\n"
                            + "\n"
                            + "    <GridLayout\n"
                            + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                            + "        bar=\"@android:color/holo_red_light\"\n"
                            + "        android:layout_width=\"fill_parent\"\n"
                            + "        android:layout_height=\"fill_parent\" >\n"
                            + "\n"
                            + "        <Button\n"
                            + "            android:layout_width=\"fill_parent\"\n"
                            + "            android:layout_height=\"fill_parent\" />\n"
                            + "    </GridLayout>\n"
                            + "\n"
                            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mLayout3 =
            xml(
                    "res/layout-v14/layout.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"fill_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <!-- Requires API 5 -->\n"
                            + "\n"
                            + "    <QuickContactBadge\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\" />\n"
                            + "\n"
                            + "    <!-- Requires API 11 -->\n"
                            + "\n"
                            + "    <CalendarView\n"
                            + "        android:layout_width=\"fill_parent\"\n"
                            + "        android:layout_height=\"fill_parent\" />\n"
                            + "\n"
                            + "    <!-- Requires API 14 -->\n"
                            + "\n"
                            + "    <GridLayout\n"
                            + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                            + "        bar=\"@android:color/holo_red_light\"\n"
                            + "        android:layout_width=\"fill_parent\"\n"
                            + "        android:layout_height=\"fill_parent\" >\n"
                            + "\n"
                            + "        <Button\n"
                            + "            android:layout_width=\"fill_parent\"\n"
                            + "            android:layout_height=\"fill_parent\" />\n"
                            + "    </GridLayout>\n"
                            + "\n"
                            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mPadding_start =
            xml(
                    "res/layout/padding_start.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "              xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "              android:layout_width=\"match_parent\"\n"
                            + "              android:layout_height=\"match_parent\"\n"
                            + "              android:paddingStart=\"20dp\"\n"
                            + "              android:orientation=\"vertical\"\n"
                            + "              tools:ignore=\"RtlCompat,RtlSymmetry,HardcodedText\">\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:text=\"Test\"\n"
                            + "            android:paddingStart=\"20dp\"\n"
                            + "            android:paddingEnd=\"20dp\"/>\n"
                            + "\n"
                            + "    <EditText\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:text=\"Test\"\n"
                            + "            android:paddingStart=\"20dp\"\n"
                            + "            android:paddingEnd=\"20dp\"/>\n"
                            + "\n"
                            + "    <my.custom.view\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:text=\"Test\"\n"
                            + "            android:paddingStart=\"20dp\"\n"
                            + "            android:paddingEnd=\"20dp\"/>\n"
                            + "\n"
                            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mPadding_start2 =
            xml(
                    "res/layout-v17/padding_start.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "              xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "              android:layout_width=\"match_parent\"\n"
                            + "              android:layout_height=\"match_parent\"\n"
                            + "              android:paddingStart=\"20dp\"\n"
                            + "              android:orientation=\"vertical\"\n"
                            + "              tools:ignore=\"RtlCompat,RtlSymmetry,HardcodedText\">\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:text=\"Test\"\n"
                            + "            android:paddingStart=\"20dp\"\n"
                            + "            android:paddingEnd=\"20dp\"/>\n"
                            + "\n"
                            + "    <EditText\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:text=\"Test\"\n"
                            + "            android:paddingStart=\"20dp\"\n"
                            + "            android:paddingEnd=\"20dp\"/>\n"
                            + "\n"
                            + "    <my.custom.view\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:text=\"Test\"\n"
                            + "            android:paddingStart=\"20dp\"\n"
                            + "            android:paddingEnd=\"20dp\"/>\n"
                            + "\n"
                            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mRipple =
            xml(
                    "res/drawable/ripple.xml",
                    ""
                            + "<ripple\n"
                            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:tint=\"#ffffffff\"\n"
                            + "    android:tintMode=\"src_over\"\n"
                            + "    >\n"
                            + "    <item>\n"
                            + "        <shape>\n"
                            + "            <solid android:color=\"#d4ffffff\" />\n"
                            + "            <corners android:radius=\"20dp\" />\n"
                            + "        </shape>\n"
                            + "    </item>\n"
                            + "</ripple>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mRipple2 =
            xml(
                    "res/drawable-v21/ripple.xml",
                    ""
                            + "<ripple\n"
                            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:tint=\"#ffffffff\"\n"
                            + "    android:tintMode=\"src_over\"\n"
                            + "    >\n"
                            + "    <item>\n"
                            + "        <shape>\n"
                            + "            <solid android:color=\"#d4ffffff\" />\n"
                            + "            <corners android:radius=\"20dp\" />\n"
                            + "        </shape>\n"
                            + "    </item>\n"
                            + "</ripple>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mStyles2 =
            xml(
                    "res/values/styles2.xml",
                    ""
                            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
                            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
                            + "         unless this is in a -v11 (or better) resource folder -->\n"
                            + "        <item name=\"android:actionBarStyle\">...</item>\n"
                            + "        <item name=\"android:textColor\">#999999</item>\n"
                            + "    </style>\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mStyles2_class =
            xml(
                    "res/values-v9/styles2.xml",
                    ""
                            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
                            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
                            + "         unless this is in a -v11 (or better) resource folder -->\n"
                            + "        <item name=\"android:actionBarStyle\">...</item>\n"
                            + "        <item name=\"android:textColor\">#999999</item>\n"
                            + "    </style>\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mStyles2_class2 =
            xml(
                    "res/values-v11/styles2.xml",
                    ""
                            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
                            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
                            + "         unless this is in a -v11 (or better) resource folder -->\n"
                            + "        <item name=\"android:actionBarStyle\">...</item>\n"
                            + "        <item name=\"android:textColor\">#999999</item>\n"
                            + "    </style>\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mStyles2_class3 =
            xml(
                    "res/values-v14/styles2.xml",
                    ""
                            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
                            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
                            + "         unless this is in a -v11 (or better) resource folder -->\n"
                            + "        <item name=\"android:actionBarStyle\">...</item>\n"
                            + "        <item name=\"android:textColor\">#999999</item>\n"
                            + "    </style>\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mThemes =
            xml(
                    "res/values/themes.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "    <style name=\"Theme\" parent=\"android:Theme\"/>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test\" parent=\"android:style/Theme.Light\">\n"
                            + "        <item name=\"android:windowNoTitle\">true</item>\n"
                            + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                            + "        <!-- Requires API 14 -->\n"
                            + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test.Transparent\">\n"
                            + "        <item name=\"android:windowBackground\">@android:color/transparent</item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mThemes2 =
            xml(
                    "res/color/colors.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "    <style name=\"Theme\" parent=\"android:Theme\"/>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test\" parent=\"android:style/Theme.Light\">\n"
                            + "        <item name=\"android:windowNoTitle\">true</item>\n"
                            + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                            + "        <!-- Requires API 14 -->\n"
                            + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test.Transparent\">\n"
                            + "        <item name=\"android:windowBackground\">@android:color/transparent</item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mThemes3 =
            xml(
                    "res/values-v11/themes.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "    <style name=\"Theme\" parent=\"android:Theme\"/>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test\" parent=\"android:style/Theme.Light\">\n"
                            + "        <item name=\"android:windowNoTitle\">true</item>\n"
                            + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                            + "        <!-- Requires API 14 -->\n"
                            + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test.Transparent\">\n"
                            + "        <item name=\"android:windowBackground\">@android:color/transparent</item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mThemes4 =
            xml(
                    "res/color-v11/colors.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "    <style name=\"Theme\" parent=\"android:Theme\"/>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test\" parent=\"android:style/Theme.Light\">\n"
                            + "        <item name=\"android:windowNoTitle\">true</item>\n"
                            + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                            + "        <!-- Requires API 14 -->\n"
                            + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test.Transparent\">\n"
                            + "        <item name=\"android:windowBackground\">@android:color/transparent</item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mThemes5 =
            xml(
                    "res/values-v14/themes.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "    <style name=\"Theme\" parent=\"android:Theme\"/>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test\" parent=\"android:style/Theme.Light\">\n"
                            + "        <item name=\"android:windowNoTitle\">true</item>\n"
                            + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                            + "        <!-- Requires API 14 -->\n"
                            + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test.Transparent\">\n"
                            + "        <item name=\"android:windowBackground\">@android:color/transparent</item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mThemes6 =
            xml(
                    "res/color-v14/colors.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "    <style name=\"Theme\" parent=\"android:Theme\"/>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test\" parent=\"android:style/Theme.Light\">\n"
                            + "        <item name=\"android:windowNoTitle\">true</item>\n"
                            + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                            + "        <!-- Requires API 14 -->\n"
                            + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "    <style name=\"Theme.Test.Transparent\">\n"
                            + "        <item name=\"android:windowBackground\">@android:color/transparent</item>\n"
                            + "    </style>\n"
                            + "\n"
                            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile tryWithResources =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import java.io.BufferedReader;\n"
                            + "import java.io.FileReader;\n"
                            + "import java.io.IOException;\n"
                            + "\n"
                            + "public class TryWithResources {\n"
                            + "    public String testTryWithResources(String path) throws IOException {\n"
                            + "        try (BufferedReader br = new BufferedReader(new FileReader(path))) {\n"
                            + "            return br.readLine();\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile multiCatch =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import java.io.IOException;\n"
                            + "import java.lang.reflect.InvocationTargetException;\n"
                            + "\n"
                            + "public class MultiCatch {\n"
                            + "    public void testMultiCatch() {\n"
                            + "        try {\n"
                            + "            Class.forName(\"java.lang.Integer\").getMethod(\"toString\").invoke(null);\n"
                            + "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n"
                            + "            e.printStackTrace();\n"
                            + "        } catch (ClassNotFoundException e) {\n"
                            + "            e.printStackTrace();\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mVector =
            xml(
                    "res/drawable/vector.xml",
                    ""
                            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                            + "\n"
                            + "    <size\n"
                            + "        android:height=\"64dp\"\n"
                            + "        android:width=\"64dp\" />\n"
                            + "\n"
                            + "    <viewport\n"
                            + "        android:viewportHeight=\"24\"\n"
                            + "        android:viewportWidth=\"24\" />\n"
                            + "\n"
                            + "</vector>\n");

    private TestFile gradleVersion231 =
            gradle(
                    ""
                            + "buildscript {\n"
                            + "    repositories {\n"
                            + "        jcenter()\n"
                            + "    }\n"
                            + "    dependencies {\n"
                            + "        classpath 'com.android.tools.build:gradle:2.3.1'\n"
                            + "    }\n"
                            + "}");

    public static final String SUPPORT_JAR_PATH = "libs/support-annotations.jar";
    private final TestFile mSupportJar =
            base64gzip(SUPPORT_JAR_PATH, SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP);
    private final TestFile mSupportClasspath = classpath(SUPPORT_JAR_PATH);
}
