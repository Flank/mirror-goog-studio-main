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
import static com.android.tools.lint.detector.api.TextFormat.TEXT;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.checks.infrastructure.ProjectDescription;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import org.intellij.lang.annotations.Language;
import org.junit.Ignore;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class ApiDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new ApiDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Tests a given set of test files intended for the API check both with
     * the PSI-based java check and with the bytecode based checks
     */
    protected void checkApiCheck(
            @NonNull String expected,
            @Nullable String expectedBytecode,
            @NonNull TestFile... files) throws Exception {
        checkApiCheck(expected, expectedBytecode, false, true, null, files);
    }

    protected void checkApiCheck(
            @NonNull String expected,
            @Nullable String expectedBytecode,
            boolean allowCompilationErrors,
            boolean allowSystemErrors,
            @Nullable com.android.tools.lint.checks.infrastructure.TestLintClient lintClient,
            @NonNull TestFile... files) throws Exception {
        // First check with PSI/UAST
        lint().projects(project(files).name(getName()))
                .allowCompilationErrors(allowCompilationErrors)
                .allowSystemErrors(allowSystemErrors)
                .client(lintClient)
                .run()
                .expect(expected);


        // Then check with bytecode
        if (expectedBytecode == null) {
            expectedBytecode = expected;
        }
        lint().projects(project(files).name(getName()))
                // This is how we check with bytecode: simulate symbol resolution errors
                .forceSymbolResolutionErrors()
                .allowCompilationErrors(allowCompilationErrors)
                .allowSystemErrors(allowSystemErrors)
                .client(lintClient)
                .run()
                .expect(expectedBytecode);
    }

    public void testXmlApi1() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/color/colors.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                + "                                                ^\n"
                + "res/layout/layout.xml:9: Error: View requires API level 5 (current min is 1): <QuickContactBadge> [NewApi]\n"
                + "    <QuickContactBadge\n"
                + "    ^\n"
                + "res/layout/layout.xml:15: Error: View requires API level 11 (current min is 1): <CalendarView> [NewApi]\n"
                + "    <CalendarView\n"
                + "    ^\n"
                + "res/layout/layout.xml:21: Error: View requires API level 14 (current min is 1): <GridLayout> [NewApi]\n"
                + "    <GridLayout\n"
                + "    ^\n"
                + "res/layout/layout.xml:22: Error: @android:attr/actionBarSplitStyle requires API level 14 (current min is 1) [NewApi]\n"
                + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/layout.xml:23: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                + "        bar=\"@android:color/holo_red_light\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values/themes.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                + "                                                ^\n"
                + "7 errors, 0 warnings\n",

            lintProject(
                manifest().minSdk(1),
                mLayout,
                mThemes,
                mThemes2
                ));
    }

    public void testXmlApi2() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/textureview.xml:8: Error: View requires API level 14 (current min is 1): <TextureView> [NewApi]\n"
                + "    <TextureView\n"
                + "    ^\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        manifest().minSdk(1),
                        xml("res/layout/textureview.xml", ""
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
                            + "</LinearLayout>\n")
                ));
    }

    public void testTag() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/tag.xml:12: Warning: <tag> is only used in API level 21 and higher (current min is 1) [UnusedAttribute]\n"
                + "        <tag id=\"@+id/test\" />\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        manifest().minSdk(1),
                        xml("res/layout/tag.xml", ""
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
                            + "</LinearLayout>\n")
                ));
    }

    public void testAttrWithoutSlash() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/attribute.xml:4: Error: ?android:indicatorStart requires API level 18 (current min is 1) [NewApi]\n"
                + "    android:enabled=\"?android:indicatorStart\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        manifest().minSdk(1),
                        xml("res/layout/attribute.xml", ""
                            + "<Button\n"
                            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:text=\"Hello\"\n"
                            + "    android:enabled=\"?android:indicatorStart\"\n"
                            + "    android:layout_width=\"wrap_content\"\n"
                            + "    android:layout_height=\"wrap_content\"\n"
                            + "    android:layout_alignParentLeft=\"true\"\n"
                            + "    android:layout_alignParentStart=\"true\" />\n"
                            + "\n")
                ));
    }

    public void testUnusedAttributes() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/divider.xml:9: Warning: Attribute showDividers is only used in API level 11 and higher (current min is 4) [UnusedAttribute]\n"
                + "    android:showDividers=\"middle\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"test.pkg\" >\n"
                            + "\n"
                            + "    <uses-sdk\n"
                            + "        android:minSdkVersion=\"4\"\n"
                            + "        android:targetSdkVersion=\"25\" />\n"
                            + "\n"
                            + "    <application\n"
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
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>"),
                        xml("res/layout/labelfor.xml", ""
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
                        xml("res/layout/edit_textview.xml", ""
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
                            + "        android:textIsSelectable=\"true\" />\n"
                            + "\n"
                            + "</LinearLayout>\n"),
                        xml("res/layout/divider.xml", ""
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
                            + "    master/detail flow. See res/values-large/refs.xml and\n"
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
                            + "        android:name=\"com.example.master.ItemListFragment\"\n"
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
                            + "</LinearLayout>\n")
                ));
    }

    public void testUnusedOnSomeVersions1() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/attribute2.xml:4: Error: switchTextAppearance requires API level 14 (current min is 1), but note that attribute editTextColor is only used in API level 11 and higher [NewApi]\n"
                + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/attribute2.xml:4: Warning: Attribute editTextColor is only used in API level 11 and higher (current min is 1) [UnusedAttribute]\n"
                + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 1 warnings\n",

                lintProject(
                        manifest().minSdk(1),
                        mAttribute2
                ));
    }

    public void testUnusedThemeOnIncludeTag() throws Exception {
        // Regression test for b/32879096: Add lint TargetApi warning for android:theme
        // attribute in <include> tag
        String expected = ""
                + "res/layout/linear.xml:11: Warning: Attribute android:theme is only used by <include> tags in API level 23 and higher (current min is 21) [UnusedAttribute]\n"
                + "        android:theme=\"@android:style/Theme.Holo\" />\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        lint().files(
                manifest().minSdk(21),
                xml("res/layout/linear.xml", ""
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
                .run()
                .expect(expected);
    }

    public void testUnusedLevelListAttribute() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=214143
        assertEquals(""
                + "res/drawable/my_layer.xml:4: Warning: Attribute width is only used in API level 23 and higher (current min is 15) [UnusedAttribute]\n"
                + "        android:width=\"535dp\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/drawable/my_layer.xml:5: Warning: Attribute height is only used in API level 23 and higher (current min is 15) [UnusedAttribute]\n"
                + "        android:height=\"235dp\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",
                lintProject(
                        manifest().minSdk(15),
                        xml("res/drawable/my_layer.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <item\n"
                                + "        android:width=\"535dp\"\n"
                                + "        android:height=\"235dp\"\n"
                                + "        android:drawable=\"@drawable/ic_android_black_24dp\"\n"
                                + "        android:gravity=\"center\" />\n"
                                + "</layer-list>\n")
                ));
    }

    public void testCustomDrawable() throws Exception {
        assertEquals(""
                + "res/drawable/my_layer.xml:2: Error: Custom drawables requires API level 24 (current min is 15) [NewApi]\n"
                + "<my.custom.drawable/>\n"
                + "~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(15),
                        xml("res/drawable/my_layer.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<my.custom.drawable/>\n")
                ));
    }

    public void testCustomDrawableViaClassAttribute() throws Exception {
        assertEquals(""
                + "res/drawable/my_layer.xml:2: Error: <class> requires API level 24 (current min is 15) [NewApi]\n"
                + "<drawable class=\"my.custom.drawable\"/>\n"
                + "          ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(15),
                        xml("res/drawable/my_layer.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<drawable class=\"my.custom.drawable\"/>\n")
                ));
    }

    public void testRtlManifestAttribute() throws Exception {
        // Treat the manifest RTL attribute in the same was as the layout start/end attributes:
        // these are known to be benign on older platforms, so don't flag it.
        assertEquals("No warnings.",
                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"test.bytecode\">\n"
                                + "\n"
                                + "    <uses-sdk android:minSdkVersion=\"1\" />\n"
                                + "\n"
                                + "    <application\n"
                                + "        android:supportsRtl='true'\n"

                                // Ditto for the fullBackupContent attribute. If you're targeting
                                // 23, you'll want to use it, but it's not an error that older
                                // platforms aren't looking at it.

                                + "        android:fullBackupContent='false'\n"
                                + "        android:icon=\"@drawable/ic_launcher\"\n"
                                + "        android:label=\"@string/app_name\" >\n"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>\n")

                )
        );
    }

    public void testXmlApi() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/attribute2.xml:4: Error: ?android:switchTextAppearance requires API level 14 (current min is 11) [NewApi]\n"
                + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        manifest().minSdk(11),
                        mAttribute2
                ));
    }

    public void testReportAttributeName() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/layout.xml:13: Warning: Attribute layout_row is only used in API level 14 and higher (current min is 4) [UnusedAttribute]\n"
                + "            android:layout_row=\"2\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        manifest().minSdk(4),
                        xml("res/layout/layout.xml", ""
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
                            + "</LinearLayout>\n")
                ));
    }

    public void testXmlApi14() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

                lintProject(
                    manifest().minSdk(14),
                    mLayout,
                    mThemes,
                    mThemes2
                    ));
    }

    public void testXmlApiIceCreamSandwich() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

                lintProject(
                        xml("AndroidManifest.xml", ""
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
                        mThemes2
                ));
    }

    public void testXmlApi1TargetApi() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            lintProject(
                manifest().minSdk(1),
                xml("res/layout/layout.xml", ""
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
                            + "</LinearLayout>\n")
                ));
    }

    public void testXmlApiFolderVersion11() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/color-v11/colors.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                + "                                                ^\n"
                + "res/layout-v11/layout.xml:21: Error: View requires API level 14 (current min is 1): <GridLayout> [NewApi]\n"
                + "    <GridLayout\n"
                + "    ^\n"
                + "res/layout-v11/layout.xml:22: Error: @android:attr/actionBarSplitStyle requires API level 14 (current min is 1) [NewApi]\n"
                + "        foo=\"@android:attr/actionBarSplitStyle\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout-v11/layout.xml:23: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                + "        bar=\"@android:color/holo_red_light\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values-v11/themes.xml:9: Error: @android:color/holo_red_light requires API level 14 (current min is 1) [NewApi]\n"
                + "        <item name=\"android:windowBackground\">  @android:color/holo_red_light </item>\n"
                + "                                                ^\n"
                + "5 errors, 0 warnings\n",

            lintProject(
                manifest().minSdk(1),
                mLayout2,
                mThemes3,
                mThemes4
                ));
    }

    public void testXmlApiFolderVersion14() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

                lintProject(
                    manifest().minSdk(1),
                    mLayout3,
                    mThemes5,
                    mThemes6
                    ));
    }

    public void testThemeVersion() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/values/themes3.xml:3: Error: android:Theme.Holo.Light.DarkActionBar requires API level 14 (current min is 4) [NewApi]\n"
                + "    <style name=\"AppTheme\" parent=\"android:Theme.Holo.Light.DarkActionBar\">\n"
                + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(4),
                        xml("res/values/themes3.xml", ""
                            + "<resources>\n"
                            + "\n"
                            + "    <style name=\"AppTheme\" parent=\"android:Theme.Holo.Light.DarkActionBar\">\n"
                            + "        <!-- Customize your theme here. -->\n"
                            + "    </style>\n"
                            + "\n"
                            + "</resources>\n")
                ));
    }

    public void testApi1() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
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
                + "6 errors, 1 warnings\n",

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
                + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 1): android.widget.Chronometer#setTextIsSelectable [NewApi]\n"
                + "  chronometer.setTextIsSelectable(true); // API 11\n"
                + "              ~~~~~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 1): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n"
                + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                + "                         ~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                + "              ~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                + "                              ~~~~~~~\n"
                + "7 errors, 1 warnings\n",
                classpath(),
                manifest().minSdk(1),
                mApiCallTest,
                mApiCallTest2
            );
    }

    public void testApi2() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
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
                + "6 errors, 1 warnings\n",

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
                + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 2): android.widget.Chronometer#setTextIsSelectable [NewApi]\n"
                + "  chronometer.setTextIsSelectable(true); // API 11\n"
                + "              ~~~~~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 2): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n"
                + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                + "                         ~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 2): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                + "              ~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 2): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                + "                              ~~~~~~~\n"
                + "7 errors, 1 warnings\n",

                classpath(),
                manifest().minSdk(2),
                mApiCallTest,
                mApiCallTest2
            );
    }

    public void testApi4() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
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
                + "5 errors, 1 warnings\n",

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
                + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 4): android.widget.Chronometer#setTextIsSelectable [NewApi]\n"
                + "  chronometer.setTextIsSelectable(true); // API 11\n"
                + "              ~~~~~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 4): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n"
                + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                + "                         ~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 4): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                + "              ~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 4): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                + "                              ~~~~~~~\n"
                + "6 errors, 1 warnings\n",

                classpath(),
                manifest().minSdk(4),
                mApiCallTest,
                mApiCallTest2
        );
    }

    public void testApi10() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
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
                + "4 errors, 1 warnings\n",

                ""
                + "src/foo/bar/ApiCallTest.java:33: Warning: Field requires API level 11 (current min is 10): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [InlinedApi]\n"
                + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:20: Error: Call requires API level 11 (current min is 10): android.app.Activity#getActionBar [NewApi]\n"
                + "  getActionBar(); // API 11\n"
                + "  ~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:30: Error: Call requires API level 11 (current min is 10): android.widget.Chronometer#setTextIsSelectable [NewApi]\n"
                + "  chronometer.setTextIsSelectable(true); // API 11\n"
                + "              ~~~~~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:33: Error: Field requires API level 11 (current min is 10): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n"
                + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                + "                         ~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:38: Error: Field requires API level 14 (current min is 10): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                + "              ~~~~~~~~~~~\n"
                + "src/foo/bar/ApiCallTest.java:41: Error: Field requires API level 11 (current min is 10): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                + "                              ~~~~~~~\n"
                + "5 errors, 1 warnings\n",

                classpath(),
                manifest().minSdk(10),
                mApiCallTest,
                mApiCallTest2
        );
    }

    public void testApi14() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                null,

                classpath(),
                manifest().minSdk(14),
                mApiCallTest,
                mApiCallTest2
        );
    }

    public void testInheritStatic() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
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
                + "4 errors, 0 warnings\n",
                null,

                classpath(),
                manifest().minSdk(2),
                java(""
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
                            + "}\n"),
                base64gzip("bin/classes/foo/bar/ApiCallTest5.class", ""
                            + "H4sIAAAAAAAAAHVR224SURRdBwYoU1ouLa2Vsd5rq4mTGPWlhqQhMU6CPgiB"
                            + "58NwAkeHGTJzoMaP8F980vjgB/hRxjUDRU30Yfbt7L3W2nt+/Pz2HcATPCgh"
                            + "J9A0KjHu/P3EvZjrjgyCPvNnJVgCdRmO40iP3aVWl+6ARqD4QofatAWc0+7V"
                            + "sx+FRoXG7aT+gzk/GwhYnWisbORRrqCAokC1q0P1ZjEbqbgvR4ESaHQjXwYD"
                            + "Ges0XxctM9WJwGH3n7LOBUr+ikXg6L8CBMpR+FrJZBETsnDqeamk1ttFaPRM"
                            + "eeFSJ5psF2EYGWl0FJLxeIMmN2W3L+OJMlRAyMJSBguV5+ly6V6HFVzDkcBe"
                            + "rJIoWKqe/kjAcY+T5CyS0zvzbHCVtNup4AaOeW0/mo14iLW4VXuy1pi1OwK1"
                            + "Sz0203VLb658AeHxd0yVnkzNX/Wd2RpomI4I7F7lr7JeAbsXLWJfvdTpcet/"
                            + "3vLxO7mUuE3KdCduxYh/irbErM08R198+OgLtj5n7zatnVXL7LSxzehg1YUK"
                            + "djKUInZRJYaFGup8a7C2zXyP376HZgb+iZQFeqfVyn/FQdtx6K4/t/J0rWFq"
                            + "bw5/U56QDoSjfs7ViNDAFgFt+ir2idlEi0JSOU9XuBs5Dm5lK6bRHUY5Tjdx"
                            + "l1Geszu4x8ji+/2M7eQXBKMMexwDAAA=")
        );
    }

    public void testInheritLocal() throws Exception {
        // Test virtual dispatch in a local class which extends some other local class (which
        // in turn extends an Android API)
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest3.java:10: Error: Call requires API level 11 (current min is 1): android.app.Activity#getActionBar [NewApi]\n"
                + "  getActionBar(); // API 11\n"
                + "  ~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                null,

                classpath(),
                manifest().minSdk(1),
                mIntermediate,
                java(""
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
                            + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest3.class", ""
                            + "H4sIAAAAAAAAAG1OTU/CQBScpV+6VhGUGI/egINNxJvGBElMTBovEjwv7Yqr"
                            + "pdssi//LiyYe/AH+KONrJYWDe5h5896b2ff98/kF4AydAA2GjpULGxUvs2hY"
                            + "qJHIsjHpQQB3c3SbW2nmMlXCSgb/UuXKXjE43d6EwR3pVHI42A7hwWdoxiqX"
                            + "d8v5VJqxmGbkaMc6EdlEGFXqVdO1T2rBcBT/e8EFxT9qzcGwH6KFNkM4k3aY"
                            + "WKXza2EYjru9WOSp0SqNRFFE9Yis/F4vTSJvVPlRazP39Fm8CpxQrIPyNaii"
                            + "qwkDUhExI/b6H9h6q8ac0K+aAXYIw78F4l1ihr3afL4y+/13NB/Wbk5cokf+"
                            + "dYJfJxxUm4e/Qfv6ZZkBAAA="),
                base64gzip("bin/classes/test/pkg/Intermediate.class", ""
                            + "H4sIAAAAAAAAAG1PTUvDQBB906aJjdHaD/EseLAeDHitCBIQCsGL0vs2WXQ1"
                            + "2ZTdTcGf5Unw4A/wR4mT6KGH7LBv5r03DDPfP59fAK4wC9AjHDtpXbx5fYqX"
                            + "2klTylwJJwN4hJnQualUHovNJr7NnNoq90bwr5VW7obQP5+vCF5S5TJEH8MI"
                            + "A/iEUaq0vK/LtTSPYl1IwiStMlGshFEN/xc996ws4STtXGBBCB+q2mTyTjXd"
                            + "413z8kVsBSFaai1NUghrpQ0wIcw7Z53tkqS2rip572mH6p2Cz0LziIPPYQyY"
                            + "xS0HBhcf2HvnooeQ0W/FAPuM0V8D54PWP2xxhCPOIXtj/lNv+AsUyylNfwEA"
                            + "AA==")
        );
    }

    public void testViewClassLayoutReference() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/view.xml:9: Error: View requires API level 5 (current min is 1): <QuickContactBadge> [NewApi]\n"
                + "    <view\n"
                + "    ^\n"
                + "res/layout/view.xml:16: Error: View requires API level 11 (current min is 1): <CalendarView> [NewApi]\n"
                + "    <view\n"
                + "    ^\n"
                + "res/layout/view.xml:24: Error: ?android:attr/dividerHorizontal requires API level 11 (current min is 1) [NewApi]\n"
                + "        unknown=\"?android:attr/dividerHorizontal\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/view.xml:25: Error: ?android:attr/textColorLinkInverse requires API level 11 (current min is 1) [NewApi]\n"
                + "        android:textColor=\"?android:attr/textColorLinkInverse\" />\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "4 errors, 0 warnings\n",

            lintProject(
                    manifest().minSdk(1),
                    xml("res/layout/view.xml", ""
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
                            + "</LinearLayout>\n")
                ));
    }

    public void testIOException() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=35190
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest6.java:8: Error: Call requires API level 9 (current min is 1): new java.io.IOException [NewApi]\n"
                + "        IOException ioException = new IOException(throwable);\n"
                + "                                  ~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                ""
                + "src/test/pkg/ApiCallTest6.java:8: Error: Call requires API level 9 (current min is 1): new java.io.IOException [NewApi]\n"
                + "        IOException ioException = new IOException(throwable);\n"
                + "        ~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(1),
                mIntermediate,
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.io.IOException;\n"
                        + "\n"
                        + "public class ApiCallTest6 {\n"
                        + "    public void test(Throwable throwable) {\n"
                        + "        // IOException(Throwable) requires API 9\n"
                        + "        IOException ioException = new IOException(throwable);\n"
                        + "    }\n"
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest6.class", ""
                        + "H4sIAAAAAAAAAG2Qy04CMRSG/8plmAHlJoKudAeaOIkLNxgTQzQxGWUhIXFZ"
                        + "xgaK45QMRX0tVySa+AA+lPF0GEET2vRc2vN/Pe3X9/sngBPsWdhgqGkx1e7k"
                        + "ceheTGSHB0GP8lMLaYbSmD9zN+Dh0O0OxsLXDNkzGUp9zpBqtvoM6Y56EA5S"
                        + "sAvIIMtQ9GQobmdPAxH1+CAQDBVP+Tzo80iaPNlM65GcMtS9tZe3TQEFDI2m"
                        + "t+qhN4rUi9G3W30LZYZqfCaVe929fPXFREsVOiihapopMtj6V0HPXAtiyEu1"
                        + "1C6r/iOpyrlTs8gXV9Kgyn97PTYCHIB+BGZsUEQ/QdaizCXPyGcO58i9xccO"
                        + "2exiE3myhSQuYJM8wxaKidgjpIE6HyjdH81RuVkRHPJADjZNQ9lfVCYUE22j"
                        + "Rhyb1g7qcVuNWL37A0Vnst8AAgAA")
                );
    }

    // Test suppressing errors -- on classes, methods etc.

    public void testSuppress() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
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
                + "6 errors, 1 warnings\n",

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
                + "src/foo/bar/SuppressTest1.java:86: Error: Call requires API level 11 (current min is 1): android.widget.Chronometer#setTextIsSelectable [NewApi]\n"
                + "  chronometer.setTextIsSelectable(true); // API 11\n"
                + "              ~~~~~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/SuppressTest1.java:89: Error: Field requires API level 11 (current min is 1): dalvik.bytecode.OpcodeInfo#MAXIMUM_VALUE [NewApi]\n"
                + "  int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n"
                + "                         ~~~~~~~~~~~~~\n"
                + "src/foo/bar/SuppressTest1.java:94: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                + "  BatteryInfo batteryInfo = getReport().batteryInfo;\n"
                + "              ~~~~~~~~~~~\n"
                + "src/foo/bar/SuppressTest1.java:97: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "  Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n"
                + "                              ~~~~~~~\n"
                + "src/foo/bar/SuppressTest4.java:19: Error: Field requires API level 14 (current min is 1): android.app.ApplicationErrorReport#batteryInfo [NewApi]\n"
                + "  BatteryInfo batteryInfo = report.batteryInfo;\n"
                + "              ~~~~~~~~~~~\n"
                + "8 errors, 1 warnings\n",

                classpath(),
                manifest().minSdk(1),
                java(""
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
                base64gzip("bin/classes/foo/bar/SuppressTest1.class", ""
                            + "H4sIAAAAAAAAAMVWbVMbVRR+bggsCaEIBSy0KG0ReZMtLaWUIG3KW6PLiwRC"
                            + "Wx2dZXMTtiy76+4GxNEvfvHvaKeDM37wB/hf/AuOzy6BIG2o4zBDZnLvPfee"
                            + "+5znnHvOSf78+/c/ANzFMwUxgc5A+oHq7pTUXNl1Pen769wYUxAXaNftgueY"
                            + "BVV3XTVjBOaeGRwINEybthnMCNQNDOYF4rNOQSZRh0QK9WgQaNFMWy6Xd7ek"
                            + "t65vWVKgTXMM3crrnhnKlc14sG36Ate0tzNICyi7Mth2CmMCDwa0Yy77ZqEk"
                            + "A3V223NshwrSS2uOV1L37xlqwdlV51aWQmuB46VDdtfXynZg7sqsvWf6Jg1n"
                            + "bNsJ9MB0bBqvwurVbTVwHMs/YUNvApKp39OtMmnX6ZaVhEBHCp14XyBFNmFs"
                            + "HPuJ7gl0DQxqZ8MWHaUVdJHOGarznud4T3nBkl4S19Gj4IZAd21nU/gAHwrc"
                            + "4PaKfWp/3TR2NNMPpC3JYvIUizcx+mreTIccbqZwC7cFrvoyWJffBVk/Jy1p"
                            + "BJVnG3gxmE/gI3ysoJ9UC7q1Z+6oWweBNJgI6oobTlm76KQwgEGB5qXMs+zS"
                            + "xtI3+Yy2MS8gsmH4hlMYwScCCXJbk67jBQL9Z2PnupZpRI8SxelIL52ACibo"
                            + "HYFb79ZOMdPvCTRt6QF9PQh5Cdz5D2b6nlRv0OR9PFAwIdB7fLPk6e62afjq"
                            + "KpWlN1cuFvuW6HkKk3jI3F3Jz69pmeckqb3rCrOryag+CF/3vGwntnWU4GHx"
                            + "1Eh95qsMfWGBn1WJfKRCnWF9L9CqvdT3dNXS7ZI6a+m+H14tmtIqCCSLpmWt"
                            + "6p60+ThNu3pgbB9L8V3yjsBPF/aBe1zc7Wdhp4dmqhV9l01kWe5nXPNki2/U"
                            + "nAs93Dbt0rzly5OTcZ4wBoueWdD0A6dM6z1vpnf1mGaSOafsGXLBjJrPv9rK"
                            + "aMiLVZu1me4RM+kryLEyz3kl+hu+kwI2lPH/U1fsCzXPcJPlUIfwo3DFFspR"
                            + "oaRyFpzrh35D469cxJDk2BBttqGJY+pIgXMzZ4EraKHWe1xfodTKb9uXED6u"
                            + "RoB/VRDUoUO0b4rltmtT8eFDdG8Oxw/R+wp9E/WxiYbYhEKDQ68xOtX4CuNT"
                            + "idB0XXRxNDLaSYNdBOym1ENjvejAbUp9lPopseoxTN2RSLt4ZLJCMVxNIR3R"
                            + "VjGNT0lJ4Y0ZPKKNJmI8RgZxonXgCXHqidmGWa4aiNyCOa6USGJZo5FWBObx"
                            + "EAksVMJxhLFIDIGnNcKRvchwjJHkOAHvU5qksTSpz9CBR6SeIemQ/AJ1Fy89"
                            + "HJ/VCMfnFxkOjSRXCLhKKUdjeVJ/TgdekPpXJP016erU3br0cGgn4WithiPG"
                            + "cFxwgpTI8yUBdyjZtPct2Qf0oUz2++R9QMY/UPfHS44IsITlc+plpNKPYuKX"
                            + "M83op1PNKFZhHgt/389pRrXRfn4r2ko0ruKLqPnFWG5rWH/chQ3+Y9lsSPwD"
                            + "d6IkUtcKAAA="),
                java(""
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
                base64gzip("bin/classes/foo/bar/SuppressTest2.class", ""
                            + "H4sIAAAAAAAAAIVSy27UQBCs9r6IsyHkQR4kIQmHaDdCWAoHDouQQnhKBqQk"
                            + "Wg6cJvZod8DrscbjjfJXcAGJAx/ARyHaXoPDKoAld497qqtqpv39x9dvAA5w"
                            + "pwWHsGJlar3kw8A7yZLEyDQ95cJBC3XCsohDo1XoiSTxDgOrxspeEJoPVazs"
                            + "I0Kt0+0T6kc6lC5qmGmjgSZh3lexfJ2NzqQ5FWeRJCz6OhBRXxiVf5fFuh2q"
                            + "lLDmX+2gx0IjaYc6JDzo+L+snKtwIK13NDQ61rwvTc/XZuCd3w+8UI+8J29e"
                            + "5WJWm16374Kw0MYilghtbsvPoOPHwhDWO11/+njFFuvOBhU7YfNf2oRWNJHL"
                            + "T/IXI4Q57ntuVOiLC51ZwtYl9ZK12mb8DFeOZaINY/emnSZJpJiY7T41RpsJ"
                            + "jpvcE52ZQD5TxZX/cZn33ouxIGwcZ7FVI/kyHqtU8RQO41jbgoonUV2yqMqe"
                            + "1TpKf4+GR5tLNcYiylilJqIIu3zLNeRPnVf8C3Bs8ZfHmTg39r/g2ideOHA5"
                            + "NotiG7NFLACc5zgTrmO+bH5Rkjb3P+PG26rb5QzGNRiZM+xMUCVDvlrGzYK1"
                            + "iRWsFug1rJesd0tLDn2c8rN0yY9Tsjm4hY3/dq5e2blZxC3cLtwStvndeQdK"
                            + "sfsT2t9aJ4IDAAA="),
                java(""
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
                base64gzip("bin/classes/foo/bar/SuppressTest3.class", ""
                            + "H4sIAAAAAAAAAIVSTW/TQBB94yQOdVNa2tIP2kLLoUoqhCV64BCEFMqnZIrU"
                            + "VuHAaRuvkgXHa63Xifqv4AISB34APwoxdgwpUQFLnlnPvnnv7Y6///j6DcAD"
                            + "3K3DIaxZmVo/+dD3T7MkMTJNz7hwWEeVsCri0GgV+iJJ/E7PqpGyFwT3kYqV"
                            + "fUyoNFtdQvVIh9JDBXMN1OASFgMVy+NseC7NmTiPJGE50D0RdYVR+XdZrNqB"
                            + "SgkbwdUO2iw0lHagQ8LDZvDLyliFfWn9o4HRseZ9adqBNn1/fNjzQz30n755"
                            + "nYtZbdqtrgfCjQaWsUJocFt+Bh0/EYaw2WwFs8crtlh3vjdlJ2z/S5tQjyZy"
                            + "+Un+YoSwwH0vjAoDcaEzS9i5pF6yTrcZP8eVE5low9j9WadJEikmZrvPjNFm"
                            + "guMm71Rnpiefq+LK/7jM++/FSBC2TrLYqqF8FY9UqngKnTjWtqDiSUwvWUzL"
                            + "vtU6Sn+PhkebS9VGIspYxT2W406isMcXXUH+VHnFfwHHOn/5nIlz7eALrn3i"
                            + "hQOPo1sUG5gvYgHgvMCZcB2LZfPLktQ9+Iylt9NujzMYV2NkzrA7QZUM+WoV"
                            + "NwtWF2tYL9Ab2CxZ75WWHPo442flkh+nZHNwC1v/7Vy/snO7iDu4Xbgl3OF3"
                            + "9x0oxd5PIVcbdIUDAAA="),
                java(""
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
                            + "}\n"),
                base64gzip("bin/classes/foo/bar/SuppressTest4.class", ""
                            + "H4sIAAAAAAAAAI1QW0sCQRT+xtt6S03N7K3Chy7QRvRWBBUFweZDiu/j7lRT"
                            + "urPMjoI/KwiCHvoB/ajoOEoiBDUP58z5zvm+c/n8ev8AcIQNBwmGhhGxcaPn"
                            + "B7cziiIt4rhLwLGDFEOdh4FWMnB5FLnnvpFjaSYMmVMZSnPGkNzZ7TGkLlUg"
                            + "8kgiV0QaGYayJ0PRHg37Qnd5fyAYqp7y+aDHtZzGczBlHmXM0PR+n+CEGg2F"
                            + "eVRBDhVUHawybC8NFEUD6XMjVXiltdJ3IlLaFFFDnaHQ58YIPbkJ7xXDofc3"
                            + "sXWxYEx7a4sytP7Bpfp8R420L66l3Xdpk4MnPubYAh0M05egHx2KrEORS56R"
                            + "T++9Ifti03myGQtmUSBbnBWQXyHPUPoht+eiWebtv6J8u+CXLF6hqEaVdauz"
                            + "Oaud6yRIZQ0Nq8SmV7ODrdtM8xvqaNrhJgIAAA==")
        );
    }

    public void testSuppressInnerClasses() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest4.java:9: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n"
                + "        new GridLayout(null, null, 0);\n"
                + "        ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest4.java:38: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n"
                + "            new GridLayout(null, null, 0);\n"
                + "            ~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                ""
                + "src/test/pkg/ApiCallTest4.java:9: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n"
                + "        new GridLayout(null, null, 0);\n"
                + "            ~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest4.java:38: Error: Call requires API level 14 (current min is 1): new android.widget.GridLayout [NewApi]\n"
                + "            new GridLayout(null, null, 0);\n"
                + "                ~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(1),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest4.class", ""
                        + "H4sIAAAAAAAAAI1RXWsTQRQ9s/nYdBubthprUrVWq0YfHBpEChEhBJTAUsGU"
                        + "vvg0yY5x6nYm7M6m+rN8KljwB/ijxDvbmEaJ4Cx7z9y5d849d+6Pn9++A2jj"
                        + "sQ+PoW5lavnk05h3J6on4viI/Oc+igzrJ2IqeCz0mL8dnsiRZSi/VFrZVwyF"
                        + "1pNjhmLPRDJAAStVlFBmqIVKy8PsdCiTIzGMJcNmaEYiPhaJcv7ssGg/qpRh"
                        + "K1xavEP0H4zxsc7QEDpKjIr4mYrG0vI3iYpC8cVkNkANm67sdYaDVvg7b2S0"
                        + "ldrynsPPtjMPZFbFvGttooaZlQNpO/28BarUZth+l2mrTmVfT1WqSGRXa2OF"
                        + "VUaT0N05i5gf80E2mSQyTallS5JLUxFn1Fv5UJ5RNz6a1ODS/vb2AzRw22m/"
                        + "w9Bs/eMVnLpgYLJkJF8r92obi+FnbjgM1b7WMunFIk1l6uM+aV1e8ypvn2F1"
                        + "wfPx8D8utf+41MYuaEhwq0A7mj1ZnzxOyAhLT89R+UobDwHZcn7oY5Vs9TKB"
                        + "8Bohw9r88gGKeSy4QI2xwjk2rhgCQmCFvmCBJZixADeIxUOddmsUuUn/1nuw"
                        + "FLdy5hcUc/mVCzRI2fbfvDXKqi/wVma8Hu7mdgf3CJvUbAOXyz3AA4rsET6C"
                        + "9wvhnCqWVgMAAA=="),
                base64gzip("bin/classes/test/pkg/ApiCallTest4$1.class", ""
                        + "H4sIAAAAAAAAAHVRXUvcQBQ9Y1LjxtSNa/2s9aPdh1XBqPggbCnI0hYhtlAX"
                        + "32eT6To2zkgysfqzfBBBwR/gjxLvxNaWsh0Y5t5zz5x75s79w80dgC288zDE"
                        + "MG1EYaLTH/1o91R2eJZ1Kd9ubnpwGcJjfsajjKt+9LV3LBLjYZih8Qf9VirF"
                        + "e5lgGDZHsmhukGA8ULFNlPdSSfOBYa71H87KIYPb0amogeFlAA8jPhzUA9QQ"
                        + "MjgtS6jHUokv5UlP5N2n3o1YJzw75Lm0+S/QtYYYZge3am6SIScvlYdpInGV"
                        + "5lqm0U+Z9oWJPucyjfmFLo2PKcza9nMMO634Ny/Ryghloo49z037uVAamUW7"
                        + "xuSyVxpxIEx7z3r2D3SZJ+KTtM7G/zaybodJj/qokkwXUvX3hTnSqYdFhsmB"
                        + "1gMs22G437XeYgj2lBJ5J+NFIQos0dwc2i/AwtCOj356iHYNPqGjFO1QbhF/"
                        + "de0Kweo1xi4rzrjFqoq9XUeDooBii07gVYVPInxWcZ9qt5hizLnGzL8qDeo5"
                        + "MUDFweuKOY83dLpYIM9vqzqreLQeAVVq5b+iAgAA"),
                base64gzip("bin/classes/test/pkg/ApiCallTest4$InnerClass1.class", ""
                        + "H4sIAAAAAAAAAIVSXWsTQRQ9N5tm7bq2aWxtm/oVm0pUcLX0oRARQkAJLBVM"
                        + "6YtPk+wYp93Oht3ZVH+WD1JQ8Af4o8Q725pGibjD3rkfhzPn3pkfP79+B7CL"
                        + "hy5KhIaRmQnGJ6OgM1ZdEceHHO81e1rLtBuLLHvuokyoHouJCGKhR8GbwbEc"
                        + "GkLFfFBZ8xlhPZzL0WbIC6WVeUmot/6BeXREKHeTSC6CcN3HAioeHNzw4WKJ"
                        + "4LQsYDlUWh7kpwOZHopBLAm1MBmK+EikysaXybIVRNief9RsSyzNeZ8kLtYI"
                        + "m0JHaaKi4ExFI2mC16mKQvEpyY2HVaxbIRuE/Vb4GzdMtJHaBF27fzTtaSE3"
                        + "Kg46xqRqkBvZl6bds+q9fpKnQ/lKWY0rs5Ke2qEStt7m2qhT2dMTlSlupaN1"
                        + "YoRRieZ2GlN+MU0H/Xw8TmWW8WAMN7MwEXHO7JUDecYHEPyrZmXmokFYmzsU"
                        + "nvkfN90k7P53ehf+TIKfx98p3Of7dPidUbVqL5W9Ei8X1zi/yNFeEQPe4ydf"
                        + "2JzD/1xgltlWGANsoMrWL3wPK6jBfjexdMmwj/JF7RtWiZxz3Lpi8AruOuO2"
                        + "5rA42CyQda6i4LvN/513oAx3cY9zNa4S9/CA9232dlD6Bfo0hm04AwAA"),
                base64gzip("bin/classes/test/pkg/ApiCallTest4$InnerClass2.class", ""
                        + "H4sIAAAAAAAAAHVR20rDQBA909TGxthWrfc7Fq0KRsQHoSKUgiIEfVD6vm3W"
                        + "uhoTSTZePssHERT8AD9K3I1V+1AZODszezh7Zvbj8/UdwA4WTWQIy5LH0rm9"
                        + "7jj1W9Fgvn+u6t3KcRDwqOGzON4xkSWUrtgdc3wWdJzT1hVvS0JOXoq4sk2Y"
                        + "dPtq1BRlXwRCHhBmqv9w1puEbCP0eB6EIRsDyFkwMGzDRIFgVDWh6IqAnyQ3"
                        + "LR6ds5bPCaNu2GZ+k0VC191mVhsirPR/qnckZc24CEMT44RpFnhRKDznXngd"
                        + "Lp2jSHguewwTaaGMSW1kirBXdX947TCQPJBOQ58PsvZ7kUjhO3UpI9FKJD/j"
                        + "snas3VtnYRK1+aHQHkd6LW3ppRLsP2c8NjFPGO87gVpQzwxYUhsz1E9SqaTX"
                        + "prKMChODqp9X1W5aA9bG5rOCF9hPKaeoMKc4QAUlhXaaWxjBqFbDGApdhT1k"
                        + "v+/eUCYyXjDxp2Cl2quKt9ZHJYPpFGcwm3ZJxRwWkPkCe2CCCX4CAAA="),
                base64gzip("bin/classes/test/pkg/ApiCallTest4$InnerClass1$InnerInnerClass1.class",
                        ""
                                + "H4sIAAAAAAAAAI2SXUvcQBSG35NsNxqjrut3q121IquCQdkLYYsgC4oQ6oXi"
                                + "/exmXMfGRJKJHz/LCxFa8Af4o0rPRKtLK2wJnDnnzMM775nJ068fjwC2sOTA"
                                + "ImxpmWn/8nvX371ULRFFx1w3lg/iWKatSGTZ5nPe03BQIlTOxZXwIxF3/cP2"
                                + "uexoQlmfqWx5k/Al6CvaZPyripXeIazU/4NfPSGUWkkoB0EY8vABZRc2hj04"
                                + "GCHYdQOMBiqW3/KLtkyPRTuShGqQdER0IlJl6pdmyRglNPof+8/s7Ns+TRIH"
                                + "k4RZEYdpokL/WoVdqf39VIWBuE1y7WIC08bZDGG7HvzhOkmsZaz9lllvdPN1"
                                + "I9cq8ne1TlU71/JI6uaBGcc9SvK0I/eUMT3W63HD3D7Be3MmMwfzhMW+Izmo"
                                + "ESbfxfhie0B+47+nxwJfvs1/D1Uq5gU4s/hzMMD9Qa4aRQ24a+v3HB7g3RXM"
                                + "KMcyM8A8Khy9IncxhqpRwzhGXhS2UXre+4kJIvsBU28KbqFdY27hHRULs0X8"
                                + "iE+8Vjmbw2cmLabmsAjrN4oLFtT6AgAA")
        );
    }

    public void testFieldWithinMethodCall() throws Exception {
        assertEquals(""
                + "src/p1/p2/FieldWithinCall.java:7: Error: Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "    int hash = PorterDuff.Mode.OVERLAY.hashCode();\n"
                + "               ~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        manifest().minSdk(1),
                        java("package p1.p2;\n"
                                + "\n"
                                + "import android.graphics.PorterDuff;\n"
                                + "\n"
                                + "class FieldWithinCall {\n"
                                + "  public void test() {\n"
                                //+ "    Object o = PorterDuff.Mode.OVERLAY;\n"
                                + "    int hash = PorterDuff.Mode.OVERLAY.hashCode();\n"
                                + "  }\n"
                                + "}\n")
                ));
    }

    public void testApiTargetAnnotation() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/foo/bar/ApiTargetTest.java:13: Error: Class requires API level 8 (current min is 1): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                + "                 ~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiTargetTest.java:25: Error: Class requires API level 8 (current min is 4): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                + "  Class<?> clz = DOMErrorHandler.class; // API 8\n"
                + "                 ~~~~~~~~~~~~~~~\n"
                + "src/foo/bar/ApiTargetTest.java:39: Error: Class requires API level 8 (current min is 7): org.w3c.dom.DOMErrorHandler [NewApi]\n"
                + "   Class<?> clz = DOMErrorHandler.class; // API 8\n"
                + "                  ~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n",
                null,

                classpath(),
                manifest().minSdk(1),
                java(""
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
                            + "}\n"),
                base64gzip("bin/classes/foo/bar/ApiTargetTest.class", ""
                            + "H4sIAAAAAAAAAJVRS28TMRD+vJtk6ZK+0hD6gJagHkoPWLRHqkpVARFpaSWI"
                            + "enc2VnDZ2JHXCYJ/xQmJAz+AH4UYO2mrokhVV7LHMzvfw+M/f3/9BnCA5wki"
                            + "hpaTpeOjLwN+MlJdYQfSdamSoMKwcikmghdCD/h571LmjqF2pLRyxwzx3osL"
                            + "hsqp6csUMRbqqKLGsJwpLc/Gw560XdErJEMjM7koLoRVPp8VK+6zKhnWs/nq"
                            + "rxmq/s+rBCsMW8YO+NfDnPfNkL85//DWWmPfC90vpCUjefGdYTW78XpaiLIk"
                            + "itZt5W+jK/Xm/81H+8dXkgek93GsnRrKjp6oUhHiRGvjhFNGk+ftjJStUX0u"
                            + "rst8ap3u4GkmohjLmGZcn3Ee+oTmmX4yY5vLdyrM5daNX3pHDPWO1tIGT7JM"
                            + "8IyhPX9Cu+FuoZF4bxK0QSOB/yp0ojehPaGMU2QUq/s/8eAHHSKktNdCMcVD"
                            + "b3faQHGRIsPSNfiMYuQpG8uZx8YBm4baImVLAd+a9szwMWFW0aB9bcY9rTSD"
                            + "kUfEHREChGR4TGu9g407xdYoa95bbHOe2NadYhuUbd5TLMKTMNun2A48DDu0"
                            + "2lj4B1mQdJZ3AwAA"),
                base64gzip("bin/classes/foo/bar/ApiTargetTest$LocalClass.class", ""
                            + "H4sIAAAAAAAAAJVRX08TQRD/ba/tQakCFRBFi40mlj64CeKThISAxiYnJNr0"
                            + "fXvd1MXrbrO3rZFvxZMJD3wAP5RxdttCICbES25nZzLz+zP7+8/lFYBdNGIU"
                            + "GBpO5o6Pvg/44Uh1hB1I16HKq8SkIjvKRJ7HKDKsnImJ4JnQA37aO5OpYyjv"
                            + "K63cAUPU3OkyFI9MX1YQYbGKEsoMy4nS8mQ87EnbEb1MMtQCaFdY5fNZsei+"
                            + "qZzhZXKvkPcMJd+0F2OFYcvYAf/xNuV9M+THp58/WGvsJ6H7mbSkKc3OGVaT"
                            + "G9lziI3bIn6O5kLW7jbvtw7mlO+I78tYOzWUbT1RuaKJQ62NE04ZTfLrCTFb"
                            + "o/pcXJf51AXZ8TATkY1lRJuPGSpfzdim8qMKW7nl940X4duWGKptraUNWiQ9"
                            + "wzaJ//eSCPFmTWiA/MN/Ed3oLeiMKeMUGcVS6xcWLuhSQIXOcijWsURnddpA"
                            + "8QFFhofXwycUCxSLteXkIkD72UqovaCsEeY3pj2zeU+/ihqdj2bY08paELJO"
                            + "2AWaIKuUPaZ/s40n95K9pqz5n2QRnga7W3fpnuF5APZ66uRj8S+gHMc9HgMA"
                            + "AA==")
        );
    }

    public void testTargetAnnotationInner() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiTargetTest2.java:32: Error: Call requires API level 14 (current min is 3): new android.widget.GridLayout [NewApi]\n"
                + "                        new GridLayout(null, null, 0);\n"
                + "                        ~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                ""
                + "src/test/pkg/ApiTargetTest2.java:32: Error: Call requires API level 14 (current min is 3): new android.widget.GridLayout [NewApi]\n"
                + "                        new GridLayout(null, null, 0);\n"
                + "                            ~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(1),
                java(""
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
                            + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiTargetTest2.class", ""
                            + "H4sIAAAAAAAAAHWQXUsCQRSG3/FrbbU0rcyMIIiwLlryopsiECEQloIS70ed"
                            + "bGydkd1Z/1dXQUE/oB8VnR2ljwsX5pyZ8/Gcfc/n19sHgBYaDlIMNSMi482e"
                            + "x157Jns8HAvTo0jLQYahPOFz7gVcjb27wUQMDUPuSipprhnSzZM+Q6ajR8JF"
                            + "GmtFZJFjKPlSidt4OhBhjw8CwVDx9ZAHfR7K5L0MZsyTjBjq/orxl1TyqHWL"
                            + "oXEfKyOnoqvmMpLU3FZKG26kVgQ48LkahVqOPP4T9hYcAhIlO+dBLNKkeMPB"
                            + "Fk1cMfDo3EUVO4mKGsN+c+WPJardBx2HQ3EjEynV/wVnyc4Yil2lRNgJeBSJ"
                            + "CIeghSH5UnSjRZF16OWRZ+Szp6/Iv9i0SzZng3kUyBYXBeTXLWCDsimU6Fag"
                            + "TJnOZhcVS7uwdGp8R5WA279Al3zS4OD4DzS/hKawa20de7aWNNlR+AaTPM2j"
                            + "LAIAAA=="),
                base64gzip("bin/classes/test/pkg/ApiTargetTest2$1.class", ""
                            + "H4sIAAAAAAAAAH2Sb28SQRDGn+VoDw4sSLV/sa0WkVLtFTSmCcaEEDUkVBNL"
                            + "+n6BlW697jV3e6gfyxemiU38AH4o4+zRopLiXTY7NzP322dm5+ev7z8A1FGz"
                            + "kWBY1SLU7vnHods8l10eDIXukqdeonCSIX/KR9z1uBq673qnoq9tzDMU/njf"
                            + "R0rxnicY5vWJDEv7hOzMYDYo6YVUUr9kKFZmZu0cMyRb/kCkwXArCxspBxZy"
                            + "WaSRZ7AqJiHXkUq8jc56IuiOzy90/D73jnkgzfeVM2lEMazPOqxUI1FWECkb"
                            + "y6Scq0Hgy4H7SQ4ow30TyEGHf/Ej7WAJq0bAGsNBpXOd1/eVFkq7LbN/1o1J"
                            + "INLSc5taB7IXaXEkdKMdl/XB95/auEeKZgoq1RwUsWkO22LYmNknkn6NfEY8"
                            + "uggtz0RbjWQoqfamUr7mWvqK6t+YCOMTtztmEZRaMDfiXiQsGgzLxqP/yqs7"
                            + "KGOTwTnyo6AvXkvT58V/0/bMgNAlvVJ9zw+lGh4KfeIPbDxhWJ6BzsI112vq"
                            + "qTNk20qJoOXxMBQhtmgSLFpzYPm8GQgSmqSVhkPeDFkHSNALONXdb8hWL7Dw"
                            + "lb4SuG18ccT8nUGBrCzZxruIO7H/LvITSnIcu8QSY9YFVqYpC3Rm7gYKsD6h"
                            + "PKdME0tdokhKNqYZBapg5S9GasK4T4wEHpCVocg2rVIbD6ehZYJWpqFFgm7f"
                            + "ALWwE2dWsRs37TH2sE/WWtzQ8VO82svj7Tfu3a3nIwQAAA=="),
                base64gzip("bin/classes/test/pkg/ApiTargetTest2$1$2.class", ""
                            + "H4sIAAAAAAAAAH1RXU8UMRQ9ZQbGHUcZFgX5WkD3YcXECRseSNaQkA0QkkET"
                            + "2fDenalLcWhJp+PHz/LBkGjCD/BHGW93FQJxaXJzb889PT29/fX7xxWANl4E"
                            + "mGBYsqK0ycXHQbJ7IXvcDITtEdJubjbbAXyG+Ix/4knB1SB51z8TmQ0wxVC/"
                            + "Qd9XSvF+IRim7Kksm5skmo5V7RDtjVTS7jA0WvfwXp4w+F2dixoYHkUI8CCE"
                            + "h+kINcQMXssRplOpxNvqvC9Mb+ShnuqMFyfcSLf/C/rOGMPK+OuabTLmmUoF"
                            + "mGdY4Co3WubJZ5kTJzkwMk/5V13ZEHNYcBYWGbZb6T9eppUVyiZdl7/YznWj"
                            + "srJIdq01sl9ZcSxs59D5Do91ZTKxL5272dtmXrvR0tP2VFboUqrBkbCnOg+w"
                            + "SsbGPiDCuhuL/0HrLYboUClhugUvS1FijSboUUyCxbEbJP3/BEUNIaEPqdqm"
                            + "vUPCjVffEW1c4vG3IWfGYcOOO72MOlUR1Q6dxZMh/hTxtYo/6v3EHGPeJZ7d"
                            + "VVmjO9f/o+JhachcxgplHw1iPqeqTlgDo8VG6Q+20o4FwAIAAA=="),
                base64gzip("bin/classes/test/pkg/ApiTargetTest2$1$1.class", ""
                            + "H4sIAAAAAAAAAH1R0U4UMRQ9ZUaGHUcZF1hFZAHdhwUSJwsvJGtMyEYNyaiJ"
                            + "bHjvztSlOLak00H8LB8MiSR8AB9lvJ1VjMa1TXNvzz09Pb29/v7tCsAOngSY"
                            + "YVixorTJ6Ydxsn8qh9yMhR0SstPpdXoBfIb4hJ/xpOBqnLwdnYjMBphlaP5G"
                            + "31VK8VEhGGbtsSw7PRJNp6r2ifZMKmmfM7S7/+FtHjH4A52LBhjuRAgwF8LD"
                            + "fIQGYgav6wjzqVTiTfVxJMxw4qGZ6owXR9xIt/8J+s4Yw+r062pjnqlUgPsM"
                            + "y1zlRss8+SRz4iSvjMxT/llXNkQLy87CQ4a9bvqLl2llhbLJwMVz278pVFYW"
                            + "yb61Ro4qKw6F7R843+GhrkwmXkrnbuFPM09da+lpL1RW6FKq8Wthj3UeYI2M"
                            + "TX1AhA3XFv+91rsM0YFSwgwKXpaixDp10KN1CyyOXSPp/2doNRASepuyPdo7"
                            + "JNza/opo6wJ3v9Scew6rK+50kyYQUe7QBSzW+BLiGxV/UrtEizHvAg/+Vlmi"
                            + "O1v/UPGwUjMfYZWijzZ5fkxZk7A2JoNNwg8lm7K6wAIAAA==")
        );
    }

    public void testSuper() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=36384
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest7.java:8: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n"
                + "        super(message, cause); // API 9\n"
                + "        ~~~~~\n"
                + "src/test/pkg/ApiCallTest7.java:12: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n"
                + "        super.toString(); throw new IOException((Throwable) null); // API 9\n"
                + "                                ~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                ""
                + "src/test/pkg/ApiCallTest7.java:8: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n"
                + "        super(message, cause); // API 9\n"
                + "        ~~~~~\n"
                + "src/test/pkg/ApiCallTest7.java:12: Error: Call requires API level 9 (current min is 4): new java.io.IOException [NewApi]\n"
                + "        super.toString(); throw new IOException((Throwable) null); // API 9\n"
                + "                                    ~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(4),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest7.class", ""
                        + "H4sIAAAAAAAAAHVQW0sCQRg942112zJvaVfqTS1a6KXACEIKBKkHxehxtG2d"
                        + "Wndld63+Vk9CQb33o6JvRhFBm4f5LnO+75wzP78fXwBOsKchwlAIrSA0h8+2"
                        + "eTkUde44bapPNcQYck/8hZvCMxu3V289axgKz2VInAtXhBcM1XJTARzu2mYr"
                        + "9IVr1+Y67b7vvfKuY9UqHYZY3XuwdESRMhBHgiHdFK51Mxp0Lb8tUQzZptfj"
                        + "Tof7QtbTZizsi4Ch2Fwqs8agDawg4LaaX5DDEO/xUUCPhaXKGKKPI/IULUuN"
                        + "+sxlIJVuGCiixJAMvck6hny5skgisVvS1TZDqfzvD+gtb+T3rGshbWXmXRzL"
                        + "ERyAdECeCGX0R3RrVDWoK/ta9fBojOS7Auh06xSBJAFTWKFsfwKDgVW1RsMa"
                        + "0rREZuvIqLVZ5CjmqRdTdJLgjKJErVXHKNx9InrPxtj8nvEk1KuhOIwJcsrB"
                        + "sKNQu39xzOqnVAIAAA==")
        );
    }

    public void testEnums() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=36951
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/TestEnum.java:61: Error: Enum for switch requires API level 11 (current min is 4): android.renderscript.Element.DataType [NewApi]\n"
                + "        switch (type) {\n"
                + "                ~~~~\n"
                + "1 errors, 0 warnings\n",

                // Bytecode analysis mistakenly picks up the constants; we're allowed have enum
                // paths that aren't taken!
                ""
                + "src/test/pkg/TestEnum.java:26: Error: Enum value requires API level 11 (current min is 4): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "            case OVERLAY: {\n"
                + "                 ~~~~~~~\n"
                + "src/test/pkg/TestEnum.java:37: Error: Enum value requires API level 11 (current min is 4): android.graphics.PorterDuff.Mode#OVERLAY [NewApi]\n"
                + "            case OVERLAY: {\n"
                + "                 ~~~~~~~\n"
                + "src/test/pkg/TestEnum.java:61: Error: Enum for switch requires API level 11 (current min is 4): android.renderscript.Element.DataType [NewApi]\n"
                + "        switch (type) {\n"
                + "        ^\n"
                + "3 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(4),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/TestEnum.class", ""
                        + "H4sIAAAAAAAAAM1WCXhcVRk9N8u8yfSmTdO9TSm0U9qEkrRNumha2kkySYfO"
                        + "xsy0TUlleE1ektfO5iypBTeUKrihoCAoWhWsgkpJtVSECi6oqOCuKLgrCu47"
                        + "LuD/33nvZVJK2w++z8/ke++fc+7yn/vfc997Dz5993EAq0WVhiqB6UWjUGzL"
                        + "7R1pS9APf6aU1lAj0LBHH9PbUnpmpC2ye48xWBTo8MZ3BBLdW5IJX1fQ79Uz"
                        + "Q/msOeQdyeu5UXOw4O0yi2k95+3OpnN5o1DozebTOg2rGggItJ5mbDSbLxr5"
                        + "ntLwsDeUHTIE2k8+IG9khox8YTBv5opef8pIG5mit0cv6on9ORrl2mBmzOIF"
                        + "AtXLm7cL1HTTXB5Uo16iDh6BaUEzY4RL6d1GPqHvTtGIxmB2UE9t1/MmY4us"
                        + "KY6aBYEZwWfVplOglslVAuctD1qq2uxltJ20BJ3N2z0QmCVRi9k0+fLmgYAH"
                        + "czFfwzyBc89sEokFaBLQsvkhM6On1AoDdTgLZ2tYNGm74vsLRSMtcQ4WU69s"
                        + "ibZgVlA1m9m2aN7MFOPFvKGnO93wkpo9OWPEg3OxXMMyWvJJOko0o4VS55hK"
                        + "ZWi65cGKdEWiR2iNbqygTj3GsF5KUU7XsLX/Z1wmgfp4UR/cG9Jz1j6oUq8W"
                        + "WHqSOU5wjFXkdgkNsz1Yw9VdK3D26ca5sZ7yDKYMPe/Gi6lg+tAQVSWtTLj4"
                        + "9Gktke0CC2KlTNFMG4HMmFkwSb8vk8kW9aKZzZCXznGm0h26LV7KqQKQK3n5"
                        + "tWN6qsQuDhv7fDnTmrpDoGVi/ZUHoO3EA2AVwS/h5iL0cRG2UPnOaLAbF9LC"
                        + "h7OZIh8AdZyWnWHaOkoaZnu72NdRiYsQo2Wo5RT4pDQPnKkJ6miChMQ2tJKK"
                        + "C6P+PiZ2SPQzUR0NK3yxxIDqsMPfFdXwEoH5E4YMZ+OlwdFe00gN+fP5bF6D"
                        + "i/Vdys5wsTN2s75BdtXJZJ24wXU0wpAYxgbK7+vpYTwqYTKu7Q76fTFm9kqk"
                        + "mHH1+GJb/WGmMhJZNagnnmD8Uok8YzfhpC8RiTJJ57pUHkdkQI3bJ/EypjSm"
                        + "ItvU2MskLnfGRrb7Vc5XSLxSdQwG+rYkyklfLXGF6hjaFkwEosGdTL5W4krV"
                        + "kUcGfYp7ncTrVeJ4d8xfHnu1xBuU4Hism/GbJN6s5iLsCL5G4q3lcUSWBV8r"
                        + "cZ2anilL8DskrnfG2oLfKXGjStAfifGWvIt96mKf3sxb8h42esWWnM5zfTgo"
                        + "8T4EKXNXJEI7EWbuAxK3MOfuDUZ8iWT7aiY/KHGoglzbweSHJW5j0hPyJWKB"
                        + "/uTqftX3IxIfraTb+9uZvkPicCXd0a8mGZc4wnRNOBL2M/EJiaNM1MfiSV8w"
                        + "GOn2JQIRpeyYxCfVDNTiD/pD/nCC6U9J3KMWQXRvpMwdl/i0zYX88S3M3S/x"
                        + "GeZmEBeNRfpivlCyN+brs+f5nMTnuX16RXvMF09w7fvwBYkvcmtDRWs8EYkp"
                        + "zQ9KfPnEobRnCX8/t35V4iFbd9wXigbLM35N4utM1zHdHQtElYpvSnzLVp7Y"
                        + "GVXTf0fiu6pnPNAX9vckV61l9hGJ71ey5Z16VOKxSra8VT+S+LHaP4tdz+RP"
                        + "JX7G5JRt4UkT/0Lil5P58tS/kvi1KoHDd5T/ufFJid9Mblyj/ldx4+8kfs+N"
                        + "Uysa1ybXcNMfJf40OVlZ8V8k/qqK5vD0nvHEs6X8oNFr8sut3v6maOVnl4AM"
                        + "ZDJGvjulFwpGQcM/BeY8xzOThEx+amr4D71/TvEoI3/yw0wTdOqaTnW++BFj"
                        + "nTD6gKCjSl+K1Q0efrQDFDUrujnSXfB3Fd2nEGoDTw/UttwFeSf9qMJUursU"
                        + "6cY0ustyBzRgOsU6NGKGNbhEvblt5THMbDmKOa23Qf0toktY18xxLGxcchRL"
                        + "D2EK/zyPfnKeapVnNs0LEtNEWbyoxzLK0kbzT+RdifPRqoSyhuomj0YSVmKV"
                        + "JeFeS4J5DKtJQkfr7WUJK9RSylcTXdzXy/nXUX6OL6L4fMZMaF9M9eEVeOnl"
                        + "toK+9Drow24dbUA/luBS+jgbRUvFOkx0YoO1DtoIr6Yt0XgpG2kpVbiA2KnU"
                        + "bxNdmwcgCvCp9GPW8ta8MKmNqFGpvZRsBQntwPoKaWsqpFGJvUpXF7qtEuuW"
                        + "BipXD2notXe5Se1c+VK7HJiUc6ry4UbqtonydlXkW4qtCFr5alDVpDW4+Svb"
                        + "SncjjeNeG8cR2vkAag7vOIbI8Xs8W1vGESeT1UQO0TcEge0Eam2wk4CrDHYe"
                        + "QegwTdKAeZR8FxbSXjRTbEE71lKsfpbDqywxZ0HbouGSZ9jgFPm2C1udG8nU"
                        + "bJlVK8oyRf04krZM3ZI5RDvlyBwh4MjcQ8BlgzQBzQY5Am4bFAg0OP3GGDkd"
                        + "9zNyer6cUZ2NXsXIY6PXMJpiowOMpI2uYlRvozcymmqjtzCaZqO3MWqw0dsZ"
                        + "TbfRDYwanbInD5PTnqvs67CZLLULPQghSvEi+hy8hGKSjkaKYhr7cBnFy3EA"
                        + "V1G8mj5Qrqd4A30u3ELxVtyBcYpH6MzfR/F+ev09RPFhPIJHKT6Gx/EExSfp"
                        + "Gf4Pik+JauE65XabWHqq7Z64BV/ojZzjtp1Tvc1yzqFx3GQ7592Wc95L58tx"
                        + "zvsJOM65lYDjiA8RcAxxOwHHDx8jMOGcOxk5HT/OyOl5FyPHOXczcpxzLyPH"
                        + "OfcxcpzzWUaOcx5g5DjnS4wc53yFkeOchxk5zvkGo0YbfZvRDBt9j9FMG/2A"
                        + "0Swb/ZDRbBv9hNEcG/2c0VwbPc5ono2eYDTfRr9ltMBGf2DUZKM/M1roePqm"
                        + "w+Tk/zNPC01MFzMpzhKLxGKKS8T5YiXFVaJTXEBxkwiIIMWQ2CEupjggDDFK"
                        + "0RQFMUZxn7hCXEnxgLhGXEvxOnGzOHjKs3IQnf+js/I8b6T0b0rv36lSwHxa"
                        + "y1w8hX9tnkfvt3/jaYp9eEaIzfP+C6YC4z5MEwAA")
        );
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

    public void testInterfaceInheritance() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38004
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                null,

                classpath(),
                manifest().minSdk(4),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.database.sqlite.SQLiteStatement;\n"
                        + "\n"
                        + "public class CloseTest {\n"
                        + "    public void close(SQLiteStatement statement) {\n"
                        + "        statement.close();\n"
                        + "    }\n"
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/CloseTest.class", ""
                        + "H4sIAAAAAAAAAI1RwU7CQBB9W0oLtQIioldPgho38Yp6IfHUaAyE+7bd4GJp"
                        + "tV38Ly+aePAD/CjjbKl68MIe5s282XkzO/v59f4B4BwHLiyGrpaF5o8Pcz5O"
                        + "skJOKXJhM3QW4lnwRKRzfhsuZKQZnAuVKn3FUBsMZwz2OIulhxqaPupwGNqB"
                        + "SuXNahnKfCrCRJJ4kEUimYlcmbgibX2vCoZe8L/ziKEemYDhdBCINM4zFfNY"
                        + "aBGKQvLiKVFa8sldQDDRQsulTPVoOPOwg10XXYajDYt8tMzIzeKHYBhu3JDB"
                        + "m2SrPJLXyjyo9Tv/mVkaDkErgjkWebQasi5FnJAR1o/f0Hgp0x5ZZ01ii6xf"
                        + "+T62CUka7ar4ktAyuZNXdP6KvZKkH0OjFOivL1UCxuthr+zbL2v2vwFtusFD"
                        + "/wEAAA==")
        );
    }

    public void testInnerClassPositions() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38113
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                null,

                classpath(),
                manifest().minSdk(4),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.text.style.LeadingMarginSpan;\n"
                        + "\n"
                        + "@SuppressWarnings(\"unused\")\n"
                        + "public class ApiCallTest8 {\n"
                        + "    public void test() {\n"
                        + "        LeadingMarginSpan.LeadingMarginSpan2 span = null;        \n"
                        + "    }\n"
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest8.class", ""
                        + "H4sIAAAAAAAAAG1PzUrDQBicTdKkjdX+iHoWPKgHF3pSKoIUPK16aOl9kyxx"
                        + "a9yEZCv6WJ4EDz6ADyV+SYsKuoeZ/WZndvg+Pt/eAYywHcBh2LGqsry4T/ll"
                        + "oScyy2Y0nwbwGPoL+Sh5Jk3Kb6OFii2Df66NthcM7uHRnMGb5IkK4aLTRQs+"
                        + "Q09oo26WD5EqZzLKFMNQ5LHM5rLU9bwWPXunK4Y98W/5uDbQhagqpGE4E9Ik"
                        + "Za4TbtWT5ZV9zhQXSibapNeyTLWZku/gjzKin8JpvixjdaXr3sHvmpN6PeyD"
                        + "lkF9HLrREoQBTZyYEbeOX9F+aZ5DQr8RfWwQdlcG4k1ihq3v8JjYIXaZ+ImG"
                        + "jdQm7DTx3ZVlHa/be+g3rYNmHn4BpgEZzqoBAAA=")
        );
    }

    public void testManifestReferences() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "AndroidManifest.xml:15: Error: @android:style/Theme.Holo requires API level 11 (current min is 4) [NewApi]\n"
                + "            android:theme=\"@android:style/Theme.Holo\" >\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

            lintProject(
                    classpath(),
                    xml("AndroidManifest.xml", ""
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
                            + "</manifest>\n")
                ));
    }

    public void testSuppressFieldAnnotations() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38626
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest9.java:9: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                + "    private GridLayout field1 = new GridLayout(null);\n"
                + "                                ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest9.java:12: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                + "    private static GridLayout field2 = new GridLayout(null);\n"
                + "                                       ~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                ""
                + "src/test/pkg/ApiCallTest9.java:9: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                + "    private GridLayout field1 = new GridLayout(null);\n"
                + "            ~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest9.java:12: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                + "    private static GridLayout field2 = new GridLayout(null);\n"
                + "                   ~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(4),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest9.class", ""
                        + "H4sIAAAAAAAAAHWRXU8TQRSG32mXFpaFfkhFoIoV+fKCjegNKSEhTTAmG0ws"
                        + "aWK8mu4OdWCZbXZni/4sYwyJJv4Af5ThzEhrL+Ri9rxzzpznPbPz+8+PXwD2"
                        + "sV1GgaGhRab94eXAPx7KDo/jM9oflOEwVC/4iPsxVwP/Xf9ChJqhdC5FHL1k"
                        + "WAu4itJERv61jAZC+29SGQX8S5Lr9vjY/li8ovPvc6XllXirRjKT/VgcK5Vo"
                        + "rmWiMobWhMYnab+bD4epyLJAKsOcGfE4F4Q8Fdc06pj9mmH2MIylkvqIobiz"
                        + "22NwOkkkyqgzrNw7pYsaljw08JBIh3ftzZ3JIGGitFDa75j4Wbd3e3NgeOSh"
                        + "jJJRqx4WUGKo0HjiNL/qi/SM070Y6kES8rjHU2n2NumiiCfGrGJan3qY+Qt5"
                        + "5mHWQBz9SdJvWA7++xh0ebeb5GkoTqQxqE0X98wjoUWwIr2qg4Jhk3LNoBQL"
                        + "xoGqc6QXKbq05j+CZfBIL9xbM32LqFCmStW2pQPLP1H7wG7w4Nu0XPlqjdas"
                        + "bYG+HuZRR9NmQYjGBNSlERnFxy9uaI0R37E+pVv/cHU6DrqJQzNWCdIk3bQG"
                        + "FoINPLcGm7Zj6xakAjwp3AIAAA==")
        );
    }

    public void testIgnoreTestSources() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                "No warnings.",

                classpath(),
                manifest().minSdk(4),
                java("test/test/pkg/UnitTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.annotation.SuppressLint;\n"
                        + "import android.widget.GridLayout;\n"
                        + "\n"
                        + "public class UnitTest {\n"
                        + "    private GridLayout field1 = new GridLayout(null);\n"
                        + "}\n")
        );
    }

    public void test38195() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=38195
        //noinspection all // Sample code
        assertEquals(""
                + "bin/classes/TestLint.class: Error: Call requires API level 16 (current min is 4): new android.database.SQLException [NewApi]\n"
                + "bin/classes/TestLint.class: Error: Call requires API level 9 (current min is 4): java.lang.String#isEmpty [NewApi]\n"
                + "bin/classes/TestLint.class: Error: Call requires API level 9 (current min is 4): new java.sql.SQLException [NewApi]\n"
                + "3 errors, 0 warnings\n",

            lintProject(
                    classpath(),
                    manifest().minSdk(4),
                    /*
                        Compiled from "TestLint.java"
                        public class test.pkg.TestLint extends java.lang.Object{
                        public test.pkg.TestLint();
                          Code:
                           0:   aload_0
                           1:   invokespecial   #8; //Method java/lang/Object."<init>":()V
                           4:   return

                        public void test(java.lang.Exception)   throws java.lang.Exception;
                          Code:
                           0:   ldc #19; //String
                           2:   invokevirtual   #21; //Method java/lang/String.isEmpty:()Z
                           5:   istore_2
                           6:   new #27; //class java/sql/SQLException
                           9:   dup
                           10:  ldc #29; //String error on upgrade:
                           12:  aload_1
                           13:  invokespecial   #31; //Method java/sql/SQLException."<init>":
                                                       (Ljava/lang/String;Ljava/lang/Throwable;)V
                           16:  athrow

                        public void test2(java.lang.Exception)   throws java.lang.Exception;
                          Code:
                           0:   new #39; //class android/database/SQLException
                           3:   dup
                           4:   ldc #29; //String error on upgrade:
                           6:   aload_1
                           7:   invokespecial   #41; //Method android/database/SQLException.
                                               "<init>":(Ljava/lang/String;Ljava/lang/Throwable;)V
                           10:  athrow
                        }
                     */
                    base64gzip("bin/classes/TestLint.class", ""
                            + "H4sIAAAAAAAAAJVRTW8TMRB903xs2Gxpm5JSKIUtLTRtgJV6JHwcUDlFIJSo"
                            + "Er05WZO6SrzB6xT4WVyoRCV650chxpsQUpQLPnje2M9vZp5//vr+A8ABHnlY"
                            + "INRjGUml05GOXYjSgTA2NupMRh+M6A2ktlFbpraptPWQJyyfijMR9YXuRW87"
                            + "p7JrCcVnSiv7gpCr7R0R8q+SWPrI4VqAAoqEJX4s34wGHWnaotOXhEoz6Yr+"
                            + "kTDK5ZPDvD1RKeFx8z9aarhnjAnrtebfzg4/d+XQqkQ3XEP+NE09VAirc4gl"
                            + "3CDAxxrWPdy8MmbLGqV7AW7hNsFT6eFgaL9kwx57uEOoZtT0Yz9qvWvOCN7l"
                            + "OaUxiQkTHY6GPSNi+TT0sYHQGbNF2J/teVymMXPSPjHJJ+dNNgWxRdW5M/Id"
                            + "+0bHhILz4sBDjbApdGwSFUexsKIjUnmlOx+7CNmZVjIyXflaOf8X/5j6xBXB"
                            + "FnhGuJVjxB/Ju8dZxJGtQmH/HKWvDBbYNmTXfIgy78EEB1jkSLiOJWYtM85z"
                            + "tpLJtBg7+ZXK6jdUn19g431ls36Oe5dTSZ8jmFxEKZMNxw8msg7dxzYLFvl2"
                            + "Bw+YTXg4p9RLxk6pfIHdcZG9y3/6DrICa2PatEB5UoB/KuPXfwNoQbjpPQMA"
                            + "AA==")
                ));
    }

    public void testAllowLocalMethodsImplementingInaccessible() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=39030
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest10.java:40: Error: Call requires API level 14 (current min is 4): android.view.View#dispatchHoverEvent [NewApi]\n"
                + "        dispatchHoverEvent(null);\n"
                + "        ~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                null,

                classpath(),
                manifest().minSdk(4),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest10.class", ""
                        + "H4sIAAAAAAAAAK2SXU8TQRSG32mXLtQtLQiVT1FA7IeywIUx1mCwgjYWNJaU"
                        + "hAvNdjuRkXW36c5W/Tnec6MXkHjhD/BHGc8s21KrXJi4F2fPzJzznDPnnR8/"
                        + "v30HsIENHTGGrOS+NFvHb82tlihbjrNP6/U1HRrDmOU2255omh3BP5h1MgyJ"
                        + "h8IVcpMhnsvXGbSy1+RJxDFiYAhJhvu5ajfL9lzJXWmW1f+jLPUOAikcc0vK"
                        + "tmgEkte4LFUUK10VLt8L3jd4e99qOJxhvOrZllO32kKto01NHgmfYar6985L"
                        + "DItN4bcsaR+99FqBY0m+Zdvc90VDOEJ+2u5QUwwbF42G17P6Y8w/M0r5wxGM"
                        + "Y0LHVSreTfV883EgnOZyfftVrfJiz8Aksgx67cnzN5W9fQZWSYLiDUxjhmHO"
                        + "c/9vT3U1+zkDaWQYhvg5xvxHCkOqJi37eNdqhTNWzCmG2e4Yn3KXt4W949mB"
                        + "z5tRr/MDve56Unhud1QKsWzgJhYZZi4PJIm7RZ55Hd6O2Fqukj+k+yiF19X8"
                        + "CgZWsKi8ZYZkzQvaNt8R4RP5TfzVd1bHYjAqLnVcdizf576ONYbMoGCkUSQZ"
                        + "NUmvGepLkDcUWp1W96CRBwwXGIufYfgL+TFcIZukv7I6rQzyjPM4pDBKf6bU"
                        + "iBivKVLFTn7FWGb0M5KF4imuaSeF4hlmT+ggHgLHqRQobZgSU7RKYyIEZ8+T"
                        + "I7Dy5nGd0AthJkuTS+8qqrUZ1Uoo+I3BbqfpZrN90EQPmoigmpIrYj3oZy2d"
                        + "9FiJ8KYLl3JuhZwV3O7NkIVRsfggY6mPEesxYsTI0jlDrqfDI9pVGakCO0X+"
                        + "QNniwcX1RkPxVHiRkHf6BElF2BjtKnsXq+E46NXAxDpGfgEYC5BVDAUAAA==")
        );
    }

    public void testOverrideUnknownTarget() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                null,

                classpath(),
                manifest().minSdk(4),
                mApiCallTest11,
                mApiCallTest11_class
        );
    }

    public void testOverride() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest11.java:13: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.ApiCallTest11#getActionBar [Override]\n"
                + "    public ActionBar getActionBar() {\n"
                + "                     ~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest11.java:17: Error: This method is not overriding anything with the current build target, but will in API level 17 (current target is 3): test.pkg.ApiCallTest11#isDestroyed [Override]\n"
                + "    public boolean isDestroyed() {\n"
                + "                   ~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest11.java:39: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.ApiCallTest11.MyLinear#setDividerDrawable [Override]\n"
                + "        public void setDividerDrawable(Drawable dividerDrawable) {\n"
                + "                    ~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n",
                null,

                classpath(),
                manifest().minSdk(4),
                projectProperties().compileSdk(3),
                mApiCallTest11,
                mApiCallTest11_class,
                base64gzip("bin/classes/test/pkg/ApiCallTest11$MyLinear.class", ""
                        + "H4sIAAAAAAAAAI1T207bQBA9E1IbggvhUgoltFBFIoEKQ9U3KqQqqFUlty9F"
                        + "eV/sVVgwdrTeQPksHlClPvAB/SjE7ObSG6kqy2vPzJlz5ozsH3ffbwG8xksf"
                        + "JcILIwsTds864buuaok0PeJ4b6/+6SpSmRTaR5mwKrJE5yoJL1XSkSbslyJx"
                        + "lfcMoXp+qC5UIvWhFpfiOJWEejTs6GjRPVFxESaDYjhE7RM8c6KK+i5hOXp4"
                        + "DIt5qzJlDghvGuNAI7E4z4zMTNiyz69mv9kmlFt5IqdAeBzAx2QFE5gNMIUq"
                        + "odb4Z+estfm5d34s9VHf1nyUxyJtC61sPEiWrQnCxpjpRptkL37cpyesjFVm"
                        + "lUKavza62fiflTbb1umzAI/gsYHkT5bKl7ynY/leOTe/zblzKi4EIfiYZVK3"
                        + "UlEUsvCxTlh62BdhcugM6yw6wR9ViS9WBqpVu2uXId51hc9pjj5wbHHTW9s3"
                        + "CLZefcPMtQPN8TnjSjUmWOOG55jnaIlrDMcCFgH39sTlCE+xPCA9cLKAZ0lX"
                        + "fvJVXLbBfM1fuLwRl4dVvomzNdezxppwo/JfgQ2U7gGb5f+5KgMAAA=="),
                base64gzip("bin/classes/test/pkg/ApiCallTest11$MyActivity.class", ""
                        + "H4sIAAAAAAAAAHVRwU7CQBB9W5BKrVJREeSikQMooWK8aUyQxMSk6kHCfYEN"
                        + "Lta2aSsJf6UHY+LBD/CjjLMNUTSQTWbnzbx5M7P7+fX+AeAYZR0aw14sotgO"
                        + "HoZ2K5Bt7rodws1m5XrS6sdyLOOJjjSDNeJjbrvcG9q3vZHoxwyZ+F5GlSOG"
                        + "ojNf45Q4Z9KT8TlDubqIVOsypNv+QGTBsGJiCRkDKaya0LHGkKoqQs6Rnrh5"
                        + "euyJsMN7rmDIO36fu10eSoWnwbQaiWF/Qa+ZpWg2cyhiBX3vgocMpWrN4d4g"
                        + "9OXA5kFg/6SIatz5T2FfXMqk8x/NhnoYErvyPBG2XR5FItJRYijMn4HEfqfA"
                        + "Lu2cot9glqUWJ0+jo2OZ4llCJwkGjIPDVzJvMF8STo5shjhAHRZZM/ENrCOv"
                        + "1LCBzalCnW6V09jzv8rGTKU2rdSwldgCthM9VVvEDrRvG3ATRzYCAAA=")
        );
    }

    public void testDateFormat() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=40876
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiCallTest12.java:18: Error: Call requires API level 9 (current min is 4): java.text.DateFormatSymbols#getInstance [NewApi]\n"
                + "  new SimpleDateFormat(\"yyyy-MM-dd\", DateFormatSymbols.getInstance());\n"
                + "                                                       ~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest12.java:23: Error: The pattern character 'L' requires API level 9 (current min is 4) : \"yyyy-MM-dd LL\" [NewApi]\n"
                + "  new SimpleDateFormat(\"yyyy-MM-dd LL\", Locale.US);\n"
                + "                                   ~\n"
                + "src/test/pkg/ApiCallTest12.java:25: Error: The pattern character 'c' requires API level 9 (current min is 4) : \"cc yyyy-MM-dd\" [NewApi]\n"
                + "  SimpleDateFormat format = new SimpleDateFormat(\"cc yyyy-MM-dd\");\n"
                + "                                                  ~\n"
                + "3 errors, 0 warnings\n",

                ""
                + "src/test/pkg/ApiCallTest12.java:18: Error: Call requires API level 9 (current min is 4): java.text.DateFormatSymbols#getInstance [NewApi]\n"
                + "  new SimpleDateFormat(\"yyyy-MM-dd\", DateFormatSymbols.getInstance());\n"
                + "                                                       ~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest12.java:23: Error: The pattern character 'L' requires API level 9 (current min is 4) : \"yyyy-MM-dd LL\" [NewApi]\n"
                + "  new SimpleDateFormat(\"yyyy-MM-dd LL\", Locale.US);\n"
                + "                        ^\n"
                + "src/test/pkg/ApiCallTest12.java:25: Error: The pattern character 'c' requires API level 9 (current min is 4) : \"cc yyyy-MM-dd\" [NewApi]\n"
                + "  SimpleDateFormat format = new SimpleDateFormat(\"cc yyyy-MM-dd\");\n"
                + "                                                  ^\n"
                + "3 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(4),
                projectProperties().compileSdk(19),
                mApiCallTest12,
                mApiCallTest12_class
        );
    }

    public void testDateFormatOk() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                null,

                classpath(),
                manifest().minSdk(10),
                projectProperties().compileSdk(19),
                mApiCallTest12,
                mApiCallTest12_class
        );
    }

    public void testJavaConstants() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiSourceCheck.java:5: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "import static android.view.View.MEASURED_STATE_MASK;\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
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
                + "1 errors, 18 warnings\n",
                ""
                + "src/test/pkg/ApiSourceCheck.java:5: Warning: Field requires API level 11 (current min is 1): android.view.View#MEASURED_STATE_MASK [InlinedApi]\n"
                + "import static android.view.View.MEASURED_STATE_MASK;\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
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
                + "src/test/pkg/ApiSourceCheck.java:51: Error: Field requires API level 14 (current min is 1): android.widget.ZoomButton#ROTATION_X [NewApi]\n"
                + "        Object rotationX = ZoomButton.ROTATION_X; // Requires API 14\n"
                + "                                      ~~~~~~~~~~\n"
                + "1 errors, 18 warnings\n",
                classpath(),
                manifest().minSdk(1),
                projectProperties().compileSdk(19),
                java(""
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
                    + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiSourceCheck.class", ""
                    + "H4sIAAAAAAAAAJ2Tf1MaRxjHn+X4IYYYPBOj1SaaSiLaStWUtAFJEbG5FIUB"
                    + "ovkxU3rCCpvAHXN3aMwfmb6g/tP+kc70j7yAvou+kabfPRAN6jiTubm93b1n"
                    + "P893v8/uP//9/YGIVmgrQB5GNx1uO7H263os3RYls2NVeabBq68D5GU0pRs1"
                    + "yxS12KGo1bkTywmD61ZOPzI7DiN/UhjCSTGans8dB1ZNw+GGE8vI7xsnEd1h"
                    + "5M2YNT5MCgVD5CM/o2uSs91p7XGrrO81OSM1Z1b15o5uCTnuTXqdhrAZTeYu"
                    + "kJhgFKh288ioiyQw8rSWGY2fiDwQ/DC2gyYR1ZSPMCNI12k8QDeAGdjxC9Ns"
                    + "rXccxzRCdJMmGA0X8+V0WctvV57BvD6y44hmrGCZbW45R4lh+oKmAzTFaPRM"
                    + "zhB9SbcYhUHf4rrdsXhtV9ScBiNlPqrJlTMhmpUho6dCHnNRb2CbvmpDNGuM"
                    + "xs7ZDCP2Bq+G9wjvW0TvC6O2gv5hrw8fhs0DbpWqltlswmK945iMgpbp6I4w"
                    + "jWeyFK/0Az3W1I16LL/3ilelg1dlCUqddtvitr3sSkVhp4odwxEtrhkHwhao"
                    + "WdoweiDUbbYvUe9Px44ZOAGS6zvQmx2U2r/ND1HcgURQPiLHZd2CFfiPzLfO"
                    + "o/YDEgqqOTq47AxnFf5X5RHKOw1ubQrerNkK89AilFQ7tmO24JMwDG5Vm7pt"
                    + "D9GPjOZ6eZdQ4pawbeRdKmXLlUJe2y5ni5VSIZvdwNm2uVMwsTuY3Oa89qmh"
                    + "JccSRh0bDw+ErcjjDonDxGgzRD/RY0YhWTFZ2fUjrSYPsBY9t+pqfkfmzxTz"
                    + "uVwlndtNPy8xmtG2CvliOb1drmzmi5V0JpMtlbR1LaeVn1fST8t57LB7lzaF"
                    + "vGxjn96tJamZvoUa6SiR1IW7izaAUYo8eIj8C4t/0dAf6HkQIaPkrIrIMbqC"
                    + "3ng3ikJ01aX4aYSugRGkMI32WP8ig1y7rKpJVV1T1ZQ37vXFfb64X4kHlPjQ"
                    + "nzT2MLjwniZV9R0+t8Pht6Mf6d1vv/fTriAB0S2AZpFiDvgIZuaRKgoxX9MN"
                    + "itEEreLixfH3AUZxV95+NzHdoa/wVWgJfyOQ5adFuoueB6R5uoeeAl4E/Qh5"
                    + "Qb0DbgSbVOk2LaDnR4ZprIlgQxM0iYwRGkK26/QNmEFwYrDSg0wEAxj0Mlp9"
                    + "Scym+64FCYylUYovOejlGtrUKS+VnpceLIm6Yj30nVunY5DigtYGQRm0GxeA"
                    + "7rl79cCVY5VXMPtAqtTo+8vIGtonl5B/+CxyHm3hEvLDzyI/RbtzCTnRtzXZ"
                    + "q49XTZ4p0AusfXmK5O2RFKxZ6xUo1Sdt9SQF1Ec59dHWCWzEnf4FbQXH6VcX"
                    + "ONMN7QO9lKZ193gylHPdFbnRR1sY+fANe5O+tanUdNy7MP2esrsnOcbd/xyc"
                    + "fTDqOJgNHGbh5rrfXdu3YQRlleLldp64hvjBmHOvQhD57rpXgdHPbnTuf8hV"
                    + "2BtVCAAA")
        );
    }

    public void testStyleDeclaration() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/values/styles2.xml:5: Error: android:actionBarStyle requires API level 11 (current min is 10) [NewApi]\n"
                + "        <item name=\"android:actionBarStyle\">...</item>\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        classpath(),
                        manifest().minSdk(10),
                        projectProperties().compileSdk(19),
                        mStyles2
                ));
    }

    public void testStyleDeclarationInV9() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/values-v9/styles2.xml:5: Error: android:actionBarStyle requires API level 11 (current min is 10) [NewApi]\n"
                + "        <item name=\"android:actionBarStyle\">...</item>\n"
                + "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values-v9: Warning: This folder configuration (v9) is unnecessary; minSdkVersion is 10. Merge all the resources in this folder into values. [ObsoleteSdkInt]\n"
                + "1 errors, 1 warnings\n",

                lintProject(
                        classpath(),
                        manifest().minSdk(10),
                        projectProperties().compileSdk(19),
                        mStyles2_class
                ));
    }

    public void testStyleDeclarationInV11() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

                lintProject(
                        classpath(),
                        manifest().minSdk(10),
                        projectProperties().compileSdk(19),
                        mStyles2_class2
                ));
    }

    public void testStyleDeclarationInV14() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

                lintProject(
                        classpath(),
                        manifest().minSdk(10),
                        projectProperties().compileSdk(19),
                        mStyles2_class3
                ));
    }

    public void testMovedConstants() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/ApiSourceCheck2.java:10: Warning: Field requires API level 11 (current min is 1): android.widget.AbsListView#CHOICE_MODE_MULTIPLE_MODAL [InlinedApi]\n"
                + "        int mode2 = AbsListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiSourceCheck2.java:14: Warning: Field requires API level 11 (current min is 1): android.widget.AbsListView#CHOICE_MODE_MULTIPLE_MODAL [InlinedApi]\n"
                + "        int mode6 = ListView.CHOICE_MODE_MULTIPLE_MODAL;\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

                null,
                classpath(),
                manifest().minSdk(1),
                projectProperties().compileSdk(19),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiSourceCheck2.class", ""
                        + "H4sIAAAAAAAAAHWPzUrDQBSFzySTpK3VprXV2p0bURcGq0bBWpCCIBRdKF25"
                        + "SWOw6T9t6nPpSnDhA/hQ4sl0EBFMYL575545Z+bz6/0DQB01B4ZANYnmiTcd"
                        + "PHmX0/huspiFUasXhYO6Ayng9oPnwBsG4yfvttuPwkTAbsTjOGkKmLt7HQHZ"
                        + "mjxGOZjI5mHBFii043F0sxh1o9l90B1GAqX2JAyGnWAWp73elEkvngvU2v/l"
                        + "n6cazgSsESMOBcS1ruuaR5rHmieavuap5plAbml9FafZ5T9RB+krsQ2+Celn"
                        + "sOJbuDrsPFKQ1v4bMq9qnONqq00HK1zzSwG5Sgqs/Rx+IbPkhtWwL8ym9KXl"
                        + "W7Zvm74j/V9uO0qVVZ2kp02vLN3y9CrwL8FFBUWV9rB01GkGypy7zJFUFVkZ"
                        + "PO2qE6byWGclqc5R6fKWBd6swsqmRtLHVb3AJqsMWVWuW98BqxM2KQIAAA==")
        );
    }

    public void testInheritCompatLibrary() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/MyActivityImpl.java:8: Error: Call requires API level 11 (current min is 1): android.app.Activity#isChangingConfigurations [NewApi]\n"
                + "  boolean isChanging = super.isChangingConfigurations();\n"
                + "                             ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MyActivityImpl.java:12: Error: This method is not overriding anything with the current build target, but will in API level 11 (current target is 3): test.pkg.MyActivityImpl#isChangingConfigurations [Override]\n"
                + " public boolean isChangingConfigurations() {\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",
                null,

                classpath(),
                manifest().minSdk(1),
                projectProperties().compileSdk(3),
                java(""
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
                base64gzip("bin/classes/test/pkg/MyActivityImpl.class", ""
                        + "H4sIAAAAAAAAAHVPPU8CQRB9y8cBJ4iCgpZWfhRuYuwkJIaExARtNBR2C6zH"
                        + "Kuxd9vZI+E82ViYW/gB/lHH2QIkGt5g3M/vezJuPz7d3AGdoFpBhaFoZWx49"
                        + "Bfx6fjm0aqbs/GoaTQrIMRwKPTKhGvE4iaLQWD475yKKeNeIYCq1/RYweC2l"
                        + "lW0zZI+O+wy5TjiSPrIolZGHx1DtKS1vkulAmjsxmEiGWi8ciklfGOXqZTNn"
                        + "xypm2O/94+rCUejLjd4qYxs1hj0Vd8ZCB0oHnVA/qCAxwqpQx6mZewZ/RWBg"
                        + "rnEbJmYou8qtrP/ecPooZgIHIDHcy1JGF1AsUMUJGWH+5BXFF0oy8Cl6adPD"
                        + "BsXygkBYIWTY/BG3CTOOR+Jqa6X2026RNKV0QmPBWk7Ik6qOnXQx3bvGSPX5"
                        + "j5HKWiO7KavxBfnS5REAAgAA"),
                java(""
                        + "package android.support.v4.app;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "\n"
                        + "public class FragmentActivity extends Activity {\n"
                        + "}\n"),
                base64gzip("bin/classes/android/support/v4/app/FragmentActivity.class", ""
                        + "H4sIAAAAAAAAAI1PwUrDQBB906aJjdGK6Ad40vbgHvRmEUToKXhp6X2bLDql"
                        + "zYbtJuBneRI89AP8KHES20tPzsBj3pv3Bub752sL4A5nMbo4iXAaYUAIx1yw"
                        + "fyR0b4ZzQvBsc0MYpFyYl2q9MG6mFytRzlOb6dVcO274Tgz8G28Iw1QXubOc"
                        + "q01VltZ5Vd8rXZZq4vTr2hT+KfNcs39/IMRTW7nMTLjJXx4abpe61gkC9AjX"
                        + "/zxKuNg7m/VexhU68mhTJC0nBUNhquVAb/QJ+pChg0gw/BNxJJjs5j7iNn7c"
                        + "upJfBov9NUMBAAA=")
        );
    }

    public void testImplements() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
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
                + "4 errors, 0 warnings\n",

                ""
                + "src/test/pkg/ApiCallTest13.java:8: Error: Class requires API level 14 (current min is 4): android.widget.GridLayout [NewApi]\n"
                + "public class ApiCallTest13 extends GridLayout implements\n"
                + "                                   ~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest13.java:9: Error: Class requires API level 11 (current min is 4): android.view.View.OnLayoutChangeListener [NewApi]\n"
                + "  View.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {\n"
                + "                                           ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest13.java:9: Error: Class requires API level 11 (current min is 4): android.view.View.OnSystemUiVisibilityChangeListener [NewApi]\n"
                + "  View.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {\n"
                + "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ApiCallTest13.java:12: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]\n"
                + "  super(context);\n"
                + "  ~~~~~\n"
                + "4 errors, 0 warnings\n",

                classpath(),
                manifest().minSdk(4),
                projectProperties().compileSdk(19),
                java("src/test/pkg/ApiCallTest13.java", ""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest13.class", ""
                        + "H4sIAAAAAAAAAIWS227TQBCG/8nBjkPa0hRCW6DQUmjaSlicroIqQSRQJAsk"
                        + "GnLvxNtkwfFG9iYlD8ADcYXEBQ/AQyEm6zQhIhG+WH+zM/PPzuz++v3jJ4Cn"
                        + "OLWRIVS0SLQ7+Nx1Xw1k3Q/DJttPntnIEXb8KIiVDNxLGXSFdt/GMvD8sRpq"
                        + "Gxbh+ZV7JMWl2+Ll8H10Pk606H+ULZnItgylHtd7ftQVnuT9SMQ2CoTqssxU"
                        + "eTGaYL2UkdRnhDtV7yqroyL2arc++X/RteMWIVdXgSgii7USHBQJG56MxLth"
                        + "vy3ipt8OBaHsqY4ftvxYTuzpZk73ZELY9pbPoUawO2kZnsfKExB21crWuUi1"
                        + "MTljcTTzEKhBWFcLXRP25k3ORlNrTL+JBI0IW0tiuEYoLviMWa0GhHwsuz22"
                        + "rLbSWvW5CRUGngmwmJqTmALDhzTMYXw9jSyeq2HcEW+kGdnCMB5/8kc+odSI"
                        + "+GrqoZ8kIrFRJWz+cyB+WKtu9OD/jwT74E6QQR42v9UsW3ynvF5j64z3M/y3"
                        + "Tk6/o/SNKYN1Xotmt8Q5a9hgqqRRuI5NwFAZW6xBuIGbU60XJhuguYzFLnDK"
                        + "XIJmEtwVbhmJbexMJb6a0kskKkbiInX+JbGL28bPT5rF0gJ3mbKG9phyhu4x"
                        + "5Q3dZ7IM7TPZhg6YCoYeMDmsc2jqP8Qj/peZbBzh2HJY4wgnlvMHh/nzUPgD"
                        + "AAA=")
        );
    }

    public void testFieldSuppress() throws Exception {
        // See https://code.google.com/p/android/issues/detail?id=52726
        //noinspection all // Sample code
        checkApiCheck(""
                + "No warnings.",
                null,

                manifest().minSdk(4),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/ApiCallTest14.class", ""
                        + "H4sIAAAAAAAAAI1SbWsTQRB+NklzyfVMY1o1TRs1NdUkas+mfmsRSkAoxApN"
                        + "CRQ/7V2WdtvL3nEv8XeJlIIFf4A/Spy9SKTQAxd23ueZmZ399fvHTwB9vDOQ"
                        + "Y3gciyi2g6tz+zCQA+55p6TvvjdQYKhe8hm3Pa7O7c/OpXBjhpzrMPSGXE1C"
                        + "X07sqZhIbn/S9MRPYhG2R3IaeEIDOdy92mfYOElULKfiSM1kJB1PHCrlxzyW"
                        + "vooYWgsovjDboyQIQhFFQ6liQliacS8RDMVj8ZWaZMi7Tj+lewylA9eTSsYf"
                        + "yNDpjhkKA38iDDxkqN8/WnvPRBWrFtZQIdCDNLsMegoLFooMK1RXHCdTR4Sn"
                        + "nBpmqA19l3tjHkqtp0YTeawa2Mgus2uigaYu85ReoTO8P2y/O9a1n1tYQtHA"
                        + "VjZe30QLTR28bWFZN1qILyS9YT0LmsEc+Unoio8yneKOd0cvl8E6UkqEA49H"
                        + "kYgMvGHo/O9yDewwrGdGM1TuxlP7tCT6e3niNC1Rg7QK8RLd8hewCCZyergM"
                        + "X0NvKDOvhAepdYW8Nun6LN+ienaD2nc8+kZqDk+Imqlzk3gJ9dQKSltbJI9Q"
                        + "IAlo9m6w3rtF44yEzWs8I7ml5RfXaP+Dq1E4CKqA9GcR8BzWmoPgJV6lsZ2U"
                        + "dtEjvkXRr/GW+izTVPPT+surc/YHBHFCy6oDAAA="),
                base64gzip("bin/classes/test/pkg/ApiCallTest14$1.class", ""
                        + "H4sIAAAAAAAAAI1SXUsbQRQ9141ZE7cmta3V2I9YV4gWmqb0oaBUyrYFIZai"
                        + "4vskO01GNzNhdyP4s/pQBB/6A/qjSu+MwSK44sDMvXPuOXfm3pk/fy9/A3iH"
                        + "DR8zhOVcZnl7fDpofxqrSCTJEe8778OOjxKhJXScGhW3RzJWor1v1wMzyWUa"
                        + "HqrROJFW0RP9U0I5H6osfMsZu7en3GbOjtIq/0hYbRWRNo8JpcjEsgLCfIBZ"
                        + "lKvw8CCAjwWC17KEWldp+W0y6sn0SPQSSVjsmr5IjkWq7H4KluyVCI2Cs8IO"
                        + "X2nNaFfQ91RmUuciV0Z/Vtk4EefRUOiBjAlRq1vYh+3iUOjMnv5hXFXl1KFc"
                        + "/B3ZCLOORti4V2JC9dBM0r78qlwbbhT45kScCe7WF91PTKb0YF/mQxP7eE5Y"
                        + "ur0nhGBPa5lGicgymfloEtbvcREfrwgrhURC5ZpKWLj5d9Dkp/b4T1K9bt+b"
                        + "vRmePuYYr7D3gfcWqW69/sXLBYKfjlOzmItw29irsxewb9GHWHT4IzyeZtnl"
                        + "6U75Ly47cs0Jm1fBqdB6T7Dk4oSnWGaFhxWnbGCVbQnPeH3Bs8GRl1jDOh/j"
                        + "sQ1RcToe/wBjiJDwbQMAAA=="),
                base64gzip("bin/classes/test/pkg/ApiCallTest14$2.class", ""
                        + "H4sIAAAAAAAAAI1SUU8TQRD+pi09W08ooAWKaJEjqZpYbXwgqdGYExKSQowQ"
                        + "3re9tV247jZ3VxN/Fg+EhAd/gD/KOLs0GBLOsMnszH7zzczO7P7+c/ULQAfb"
                        + "HgqE1UymWXtyNmx/nqhQxPExn9+9DzoeSoSW0FFiVNQey0iJ9oHdv5lpJpPg"
                        + "SI0nsbQRfTE4I5SzkUqDt5yxd3fKLnM+KK2yj4T1Vh7p5QmhFJpIVkB46GMO"
                        + "5SqKeOTDwzyh2LKEhZ7S8nA67svkWPRjSVjqmYGIT0Si7HkGluyVCI2cWkGH"
                        + "r7RptGvoayJTqTORKaO/qHQSi5/hSOihjAhhq5c7h26+K3BqX383rqty4lBu"
                        + "/j/ZCHOORti+V2JC9chMk4HcU24Mtxp8cyp+CJ7Wrh7EJlV6eCCzkYk8bBDq"
                        + "d8+E4O9rLZMwFmkqUw9NwtY9LuLhBWEtl0io3FAJ87f/Dpr81EX+k1Sr2fdm"
                        + "q8Di4QHjFbZ2+GyR6qvXF7xdwj93nAWLOQ+PDcuoseWzbdFFLDl8GY9nWT6x"
                        + "uCr/gsuOvOoCm9fOWaC1nqDu/IQV5hTYv+YiG1hnXcJT3p+xNNjzHJvY4jJF"
                        + "1gEqLo7XX6vonc5tAwAA"),
                base64gzip("bin/classes/test/pkg/ApiCallTest14$3.class", ""
                        + "H4sIAAAAAAAAAI1RyUoDQRB9ZcaMiXHf4xYxQvRgcLlFFBkVhCii4r2TaZPW"
                        + "SXeYmQh+lifBgx/gR4k1bVAEIzZUvdpeUV319v7yCmAbBRd9hNlYRnG5fd8o"
                        + "H7aVJ4Lgmv2t3eKOC4dQEtoPjfLLLekrUT5L9KXpxDIsXqlWO5AJoybq94T0"
                        + "ntIq3iekSus3BMczvswihUwO/UgTRqpKy/NOqybDa1ELJGG8auoiuBGhSvxu"
                        + "0ImbKiLkq73mqhBWjLZDXIQykjoWsTL6SEXtQDx6TaEb0id4pWrP2Su9U0UL"
                        + "p/rWVJJfpEMbJcz/0Y3Qb8sIa/9qTMhemU5YlyfKruHHBzfvxIPgbR3remAi"
                        + "pRtnMm4a38UsYfr3nRByp1rL0AtEFMnIxTxh9R+DuFgkzPUsJGS+SgnDP++N"
                        + "AvjSSF4fC1+YfZetHUZKIhvPGHiy6SzrrC1bgMPJQbZyn0WMQ4zcHiPdBgcs"
                        + "SWP6Jqdt8YolFj6TXWJijWLM5nmTmGBGCpOWOYVpRgczrOdY8pzJ8whLyLC1"
                        + "gGVGsj3wATssX04UAwAA")
        );
    }

    public void testTryWithResources() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/TryWithResources.java:13: Error: Try-with-resources requires API level 19 (current min is 1) [NewApi]\n"
                + "        try (BufferedReader br = new BufferedReader(new FileReader(path))) {\n"
                + "             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/TryWithResources.java:21: Error: Multi-catch with these reflection exceptions requires API level 19 (current min is 1) because they get compiled to the common but new super type ReflectiveOperationException. As a workaround either create individual catch statements, or catch Exception. [NewApi]\n"
                + "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(1),
                        mTryWithResources
                ));
    }

    public void testTryWithResourcesOk() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "No warnings.",
                lintProject(
                        manifest().minSdk(19),
                        mTryWithResources
                ));
    }

    public void testDefaultMethods() throws Exception {
        if (createClient().getHighestKnownApiLevel() < 24) {
            // This test only works if you have at least Android N installed
            return;
        }

        // Default methods require minSdkVersion >= N
        //noinspection ClassNameDiffersFromFileName
        assertEquals(""
                + "src/test/pkg/InterfaceMethodTest.java:6: Error: Default method requires API level 24 (current min is 15) [NewApi]\n"
                + "    default void method2() {\n"
                + "    ^\n"
                + "src/test/pkg/InterfaceMethodTest.java:9: Error: Static interface  method requires API level 24 (current min is 15) [NewApi]\n"
                + "    static void method3() {\n"
                + "    ^\n"
                + "2 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(15),
                        java("src/test/pkg/InterfaceMethodTest.java", ""
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
                                + "}")
                ));
    }

    public void testDefaultMethodsOk() throws Exception {
        // Default methods require minSdkVersion=N
        //noinspection ClassNameDiffersFromFileName
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(24),
                        java("src/test/pkg/InterfaceMethodTest.java", ""
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
                                + "}")
                ));
    }

    public void testRepeatableAnnotations() throws Exception {
        if (createClient().getHighestKnownApiLevel() < 24) {
            // This test only works if you have at least Android N installed
            return;
        }

        // Repeatable annotations require minSdkVersion >= N
        //noinspection ClassNameDiffersFromFileName
        checkApiCheck(""
                + "src/test/pkg/MyAnnotation.java:5: Error: Repeatable annotation requires API level 24 (current min is 15) [NewApi]\n"
                + "@Repeatable(android.annotation.SuppressLint.class)\n"
                + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                null,
                true,
                false,
                null,

                manifest().minSdk(15),
                java("src/test/pkg/MyAnnotation.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.lang.annotation.Repeatable;\n"
                        + "\n"
                        + "@Repeatable(android.annotation.SuppressLint.class)\n"
                        + "public @interface MyAnnotation {\n"
                        + "    int test() default 1;\n"
                        + "}")
       );
    }

    /* Disabled for now while we investigate test failure when switching to API 24 as stable */
    @SuppressWarnings("OnDemandImport")
    public void ignored_testTypeAnnotations() throws Exception {
        if (createClient().getHighestKnownApiLevel() < 24) {
            // This test only works if you have at least Android N installed
            return;
        }

        // Type annotations are not supported
        //noinspection all
        assertEquals(""
                + "src/test/pkg/MyAnnotation2.java:9: Error: Type annotations are not supported in Android: TYPE_PARAMETER [NewApi]\n"
                + "@Target(TYPE_PARAMETER)\n"
                + "        ~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(15),
                        java("src/test/pkg/MyAnnotation.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import java.lang.annotation.*;\n"
                                + "import static java.lang.annotation.ElementType.*;\n"
                                + "import static java.lang.annotation.RetentionPolicy.*;\n"
                                + "\n"
                                + "@Documented\n"
                                + "@Retention(SOURCE)\n"
                                + "@Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, TYPE_PARAMETER, TYPE_USE})\n"
                                + "public @interface MyAnnotation {\n"
                                + "}"),
                        java("src/test/pkg/MyAnnotation2.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import java.lang.annotation.*;\n"
                                + "import static java.lang.annotation.ElementType.*;\n"
                                + "import static java.lang.annotation.RetentionPolicy.*;\n"
                                + "\n"
                                + "@Documented\n"
                                + "@Retention(RUNTIME)\n"
                                + "@Target(TYPE_PARAMETER)\n"
                                + "public @interface MyAnnotation2 {\n"
                                + "}"),
                        java("src/test/pkg/MyAnnotation3.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.annotation.SuppressLint;\n"
                                + "import java.lang.annotation.*;\n"
                                + "import static java.lang.annotation.ElementType.*;\n"
                                + "import static java.lang.annotation.RetentionPolicy.*;\n"
                                + "\n"
                                + "@Documented\n"
                                + "@Retention(SOURCE)\n"
                                + "@SuppressLint(\"NewApi\")\n"
                                + "@Target(TYPE_PARAMETER)\n"
                                + "public @interface MyAnnotation3 {\n"
                                + "}"),
                        java("src/test/pkg/MyAnnotation4.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import java.lang.annotation.*;\n"
                                + "import static java.lang.annotation.ElementType.*;\n"
                                + "import static java.lang.annotation.RetentionPolicy.*;\n"
                                + "\n"
                                + "@Documented\n"
                                // No warnings if not using runtime retention (class is default)
                                + "@Target(TYPE_PARAMETER)\n"
                                + "public @interface MyAnnotation2 {\n"
                                + "}")
                ));
    }

    public void testAnonymousInherited() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=172621
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                null,
                manifest().minSdk(1),
                java(""
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
                        + "}")
        );
    }

    public void testUpdatedDescriptions() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=78495
        // Without this fix, the required API level for getString would be 21 instead of 12
        //noinspection all // Sample code
        checkApiCheck(
                ""
                        + "src/test/pkg/Test.java:5: Error: Class requires API level 11 (current min is 1): android.app.Fragment [NewApi]\n"
                        + "public class Test extends Fragment {\n"
                        + "                          ~~~~~~~~\n"
                        + "src/test/pkg/Test.java:11: Error: Call requires API level 12 (current min is 1): android.os.Bundle#getString [NewApi]\n"
                        + "            mCurrentPhotoPath = savedInstanceState.getString(\"mCurrentPhotoPath\", \"\");\n"
                        + "                                                   ~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n",
                "No warnings.",
                manifest().minSdk(1),
                java(""
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
                        + "}")
        );
    }

    public void testListView() throws Exception {
        // Regression test for 56236: AbsListView#getChoiceMode incorrectly requires API 11
        //noinspection all // Sample code
        checkApiCheck(
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
                        + "4 errors, 0 warnings\n",
                "No warnings.",
                manifest().minSdk(1),
                java(""
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
                        + "}\n")
        );
    }

    public void testThisCall() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=93158
        // Make sure we properly resolve super classes in Class.this.call()
        //noinspection all // Sample code
        checkApiCheck(
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
                        + "3 errors, 0 warnings\n",
                "No warnings.",
                manifest().minSdk(1),
                java(""
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
                        + "}")
        );
    }

    public void testReflectiveOperationException() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/Java7API.java:8: Error: Class requires API level 19 (current min is 1): java.lang.ReflectiveOperationException [NewApi]\n"
                + "        } catch (ReflectiveOperationException e) {\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                ""
                + "src/test/pkg/Java7API.java:9: Error: Call requires API level 19 (current min is 1): java.lang.ReflectiveOperationException#printStackTrace [NewApi]\n"
                + "            e.printStackTrace();\n"
                + "              ~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                manifest().minSdk(1),
                mJava7API,
                base64gzip("bin/classes/test/pkg/Java7API.class", ""
                    + "H4sIAAAAAAAAAIVRy07CQBQ9Q4FKKSKK+MTHQgNqZOHCBcbEEE0gPIwQ90Md"
                    + "SHm0TVvQz8KNGhd+gB9lvFMlGGPiLM6duXPPuefOvH+8vgE4wY4GFYsalpCW"
                    + "sKwioyGMFRWrKtYYomemZfrnDEouf8sQLtl3giFZNS1RHw3bwm3x9oAy877w"
                    + "/BvRGQjDN22LYT9X7fExLwy41S00fde0usX8j1Sj3aPSIkOi6XOjX+NOoET9"
                    + "GbSmPXINcWVK5USFOKcX1+VjSdYxh5iKdR0b2NSRxRa1mqlOHYxFwxEul1Yu"
                    + "HwzhyI2ObcQYUtJpwel3C1NhhoXfvmjEWao04J7HoHZst86HZGnvn9kCAo0W"
                    + "t8R92fJ8bhnESuf+nD/pEN8PHqHlckNgF1H6E7lCYHJcQo1OWYqMYuTgGeyR"
                    + "NtSAMBokFUIdie/SGkWFYurwBaHa0ROUicQImwTFMcg/DgX0DCKBSIR6atQt"
                    + "TrdJwvkv6YpKJ3qgwE3qEyefN6M3AgAA")
        );
    }

    public void testReflectiveOperationExceptionOk() throws Exception {
        //noinspection all // Sample code
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(19),
                        mJava7API
                ));
    }

    @Ignore("http://b.android.com/266795")
    public void ignore_testMissingApiDatabase() throws Exception {
        ApiLookup.dispose();
        //noinspection all // Sample code
        checkApiCheck(""
                        + "testMissingApiDatabase: Error: Can't find API database; API check not performed [LintError]\n"
                        + "1 errors, 0 warnings\n",
                null,
                false,
                true,
                new com.android.tools.lint.checks.infrastructure.TestLintClient() {
                    @Override
                    public File findResource(@NonNull String relativePath) {
                        return null;
                    }
                },
                manifest().minSdk(1),
                mLayout,
                mThemes,
                mThemes2,
                classpath(),
                mApiCallTest,
                mApiCallTest2
        );
    }

    public void testRipple() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable/ripple.xml:1: Error: <ripple> requires API level 21 (current min is 14) [NewApi]\n"
                + "<ripple\n"
                + "^\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(14),
                        mRipple
                ));
    }

    public void testRippleOk1() throws Exception {
        // minSdkVersion satisfied
        //noinspection all // Sample code
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(21),
                        mRipple
                ));
    }

    public void testRippleOk2() throws Exception {
        // -vNN location satisfied
        //noinspection all // Sample code
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(4),
                        mRipple2
                ));
    }

    public void testVector() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable/vector.xml:1: Error: <vector> requires API level 21 (current min is 4) or building with Android Gradle plugin 1.4 or higher [NewApi]\n"
                + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                + "^\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        manifest().minSdk(4),
                        mVector
                ));
    }

    public void testVector_withGradleSupport() throws Exception {
        //noinspection all // Sample code
        lint().files(
                manifest().minSdk(4),
                mVector,
                gradle(""
                        + "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath 'com.android.tools.build:gradle:1.4.0-alpha1'\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expectClean();
    }

    public void testAnimatedSelector() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable/animated_selector.xml:1: Error: <animated-selector> requires API level 21 (current min is 14) [NewApi]\n"
                + "<animated-selector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(14),
                        xml("res/drawable/animated_selector.xml", ""
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
                            + "</animated-selector>\n")
                ));
    }

    public void testAnimatedVector() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable/animated_vector.xml:1: Error: <animated-vector> requires API level 21 (current min is 14) [NewApi]\n"
                + "<animated-vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "1 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(14),
                        xml("res/drawable/animated_vector.xml", ""
                            + "<animated-vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:drawable=\"@drawable/vector_drawable_progress_bar_large\" >\n"
                            + "    <target\n"
                            + "        android:name=\"progressBar\"\n"
                            + "        android:animation=\"@anim/progress_indeterminate_material\" />\n"
                            + "    <target\n"
                            + "        android:name=\"root\"\n"
                            + "        android:animation=\"@anim/progress_indeterminate_rotation_material\" />\n"
                            + "</animated-vector>\n")
                ));
    }

    public void testPaddingStart() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/padding_start.xml:14: Error: Attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                + "            android:paddingStart=\"20dp\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/padding_start.xml:21: Error: Attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                + "            android:paddingStart=\"20dp\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/padding_start.xml:28: Error: Attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                + "            android:paddingStart=\"20dp\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n",
            lintProject(
                    manifest().minSdk(4),
                    mPadding_start
            ));
    }

    public void testPaddingStartNotApplicable() throws Exception {
        //noinspection all // Sample code
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(4),
                        mPadding_start2
                ));
    }

    public void testPaddingStartWithOldBuildTools() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/padding_start.xml:14: Error: Upgrade buildToolsVersion from 22.2.1 to at least 23.0.1; if not, attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                + "            android:paddingStart=\"20dp\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/padding_start.xml:21: Error: Upgrade buildToolsVersion from 22.2.1 to at least 23.0.1; if not, attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                + "            android:paddingStart=\"20dp\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/padding_start.xml:28: Error: Upgrade buildToolsVersion from 22.2.1 to at least 23.0.1; if not, attribute paddingStart referenced here can result in a crash on some specific devices older than API 17 (current min is 4) [NewApi]\n"
                + "            android:paddingStart=\"20dp\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(4),
                        mPadding_start
                ));
    }

    public void testPaddingStartWithNewBuildTools() throws Exception {
        //noinspection all // Sample code
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(4),
                        mPadding_start
                ));
    }

    public void testSwitch() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                null,
                classpath(),
                manifest().minSdk(4),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/TargetApiTest.class", ""
                        + "H4sIAAAAAAAAAI1TW08TQRT+ppRuW8qtchVEwBVKFSrglSJSKoQmRRNbSYhP"
                        + "03ZcBrazm+0W8E/4Q3xCHyCRRN/9UcazbQULxbDJzNlz/86Zc379/v4DwALW"
                        + "w+hBLIRezIQxingQD4J4GMSshjkNiTCCiAXxyKPz3rXgXYthaHis4YmGpxqe"
                        + "MYAhklFKOGmTVyqiwhBYlkq6KwxtsZltBn/aKgmG7qxU4k21XBBOnhdMkkSz"
                        + "VpGb29yRHt8Q+t1dSTGGsq6ouAl730jkuWMIN2XLPEmSDMPEpa2y7YhKZcNy"
                        + "ytzdkmWR/2QLDS8Yupp1DMuxLFclx5KlhOFwe1cWK4k16Za5rTebJmeye/yA"
                        + "J0yujETOdaQyKF3gYyPOjcMwdOZcXtzf4najqJF3VeUSyIw6kBVJopRSlstd"
                        + "aSmqdew8MD8XX1RN4doPuFkVbfRoXQzhnFV1imJD1lrY1Jw5D34EfeiPYAnJ"
                        + "CJbxkhxkmRsisWcLgyFUZ2xlnCsORcFmmLwoPWOawuBmyjGqZaHc9aOisD1M"
                        + "DIOX+7NWlWZJOFTheyWObFF0RWm8uR1L4xGs4JV3rUaQwpoHMM0w0PqBGXou"
                        + "krwt7FFImobWtvq8htcMUzd7F4Z5PXco3eIuPYze8NH/+ujX+Pg+ZBg0yylJ"
                        + "xc3aSBMf4LYtVIlhNnZ1ZK5OUaNLyUv29er+ax90rbqIoS/Wcjz7WyGgrRu8"
                        + "pimYQDetvff5aHVpVugeIG6RKG0z2uOnYF9r6kG6w0RBK+8npyH6i9SNMIzb"
                        + "REMYwRhZeAE+k42n2/wGX/wEbXNfallwH6jJvfkdpXOXzmTUfxxtP44Gjs+g"
                        + "7ZwhuHOKUDR8gg7yjJyg8xRdP2s+HoYByuch0Sn7NLoQJyyzuPUPnk2KOk50"
                        + "gk4Aft3n82GS/jtIe4+OniEYPkzVypomVyBKf73UC/QECdZz3Fkd/gP1ydEf"
                        + "GgUAAA=="),
                base64gzip("bin/classes/test/pkg/TargetApiTest$1.class", ""
                        + "H4sIAAAAAAAAAI1T70/TUBQ9byvrNgvMqTAQdYa6H4JUSNQPGqMsmwG3sWQL"
                        + "fODTW/uyPeja5bVD/yATP6uJMcbwB/hHGW8bHNG4hKS9957zes99Oe/156/v"
                        + "5wB2sJPFClYzSON2hqq1CN7RcTcC96JQ1HFfh8mwbXbfydAetvjY5J6jfOmY"
                        + "A8XHQ2kH5q4MR8TX/NFYiSBo+GrEQ4bE8R5D+oXtSk+GLxmSleohg1bzHcGw"
                        + "2JSeaE9GfaF6vO8Sk2/6NncPuZIRviAT4j3DWvOEn3HL5d7AavvdiT1sSOE6"
                        + "daV89Zxhvhty+5R2FvfQ9hmyXX+ibNGQsXCPq4EIX49lTwThVqRF8+ue7fqB"
                        + "9AYtEQ59R8cDHWUDVTw0kMU1AxvYNPAIWwyrs8cbsKLPHmOToRCSvDU+HVh/"
                        + "DTS3GcBg7HmeUDWXB4EIGHKXmgf9E2GTX0v/79dBAqUL060/plszTF/4l0id"
                        + "cXcSTdyoVI+bV5MhU7X9Tv0NQ+XqHbqvHOlxNz5oOvlkp00C2lF9t8OwPEMm"
                        + "V6TLl6HLyHKFyPaoIsbAPOUFQh+QpAp49g3s/Ef2CxKfoyf5FZp28BHa2xim"
                        + "CM5dQp1gKoafqD2Dm1jGHApYR4lymW7+E8pJLJJwKpbPIkexSFyB8nXkKZan"
                        + "1dNpdSOuUkc6abamgdpu0UICSzQI0GKRdXrzxKUjlEvT31RC5dXKb9VhRsd9"
                        + "AwAA")
        );
    }

    public void testGravity() throws Exception {
        //noinspection all // Sample code
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(4),
                        java(""
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
                            + "}\n")
                ));
    }

    public void testSuperCall() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/SuperCallTest.java:20: Error: Call requires API level 21 (current min is 19): android.service.wallpaper.WallpaperService.Engine#onApplyWindowInsets [NewApi]\n"
                + "            super.onApplyWindowInsets(insets); // Error\n"
                + "                  ~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SuperCallTest.java:27: Error: Call requires API level 21 (current min is 19): android.service.wallpaper.WallpaperService.Engine#onApplyWindowInsets [NewApi]\n"
                + "            onApplyWindowInsets(insets); // Error: not overridden\n"
                + "            ~~~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",
                null,

                classpath(),
                manifest().minSdk(19),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/SuperCallTest.class", ""
                        + "H4sIAAAAAAAAAJVRwU7CQBB9UwoVrKCoiOhBEw5AjA0aTxovRBOT6gWC5wU2"
                        + "uFpb0haMf6UnEw9+gB9lHNpqMCFR28zs7Jt5b2Z33z9e3wAcYjuHFEoGNgyU"
                        + "DWwSspePZ+5QufKAYF64rvRbjggCGRiozGSbhMyJclV4SkjV6l2C3vIGklCw"
                        + "OXs1vu9JvyN6DiNF2+sLpyt8Nd0noB7eqIBQtkMZhNbobmi1xyPuJRynw8gx"
                        + "Ie+5LV+KUMYNDWxxyzgmHNXqtnAHvqcGViD9iepL64G5I8Ei1vVX1I5T1ZjG"
                        + "orm2N/b78lxFg/1ouX8rJsJEFjlCaf5UhMbfmxJ25qtUZ274txK+5ua/z4ld"
                        + "aPyq008DTU/EfjF6buIfSDdeQM9R2mSf4xXIQEcZSxyZcRHyKPBKWMZqIrCX"
                        + "CGj09M3OREh2hqklTA1rkV/nGqDCI+m8S7MZbAtsK4wVQZ/3l+YnjgIAAA=="),
                base64gzip("bin/classes/test/pkg/SuperCallTest$MyEngine2.class", ""
                        + "H4sIAAAAAAAAAI1SXUvjQBQ9U2Ni27jWqrW73fWj9kFbMH7gkyKIuCDUfano"
                        + "87QZdDSdhCRt8Wf5oIIP/gB/lHgzbf0AXSUwc+bOOefeM+Tx6f4BwCaW0jAw"
                        + "m8EoihlCPy2ULPxhMONzGVXWGIr1WESxE1yeOY1OIMJ97nnHVNkmzo5UMt5l"
                        + "KC1/Rlo5YTD2fVcwTNSlEv867aYIj3nTo0q+7re4d8JDmZwHRSNpzJA+ujpQ"
                        + "Z6TYYLAPlSJPj0eRoKvyJ80qLxKabVz5cYO3xZGIz32XYW65zpUb+tJ1ulL0"
                        + "nFOpXL93qCIRR3pKU2pMYf5DZMg0/E7YEn+lDvCu/+oF73IbJiwbY5i3sYC8"
                        + "hUWGha/mtVBmWB+2jUTYlS3h9IgXcBI4p0PU6F9V+joaegic13hfy3XeKV/t"
                        + "BYF39TYfQ+HjURmq37en3Ab9TwDL5ZLXIJSibwxp0PvRaUufgWy1dgNWrd0h"
                        + "da1JWVpNIgFF2LTaGmcxjlxih0nkBxa7AwuzWrvFyKs6o6u/qH1JOxT6rIFD"
                        + "gqYwTR4pzGhNARO05wkZpPpB+yh+Yw7sGaEeHqUhAwAA"),
                base64gzip("bin/classes/test/pkg/SuperCallTest$MyEngine1.class", ""
                        + "H4sIAAAAAAAAAI1SwW7TQBScdVOb2G4bWiiFQCklh5JImApxAlVCFaBKKZdU"
                        + "7Xkbr9oFd23ZTqJ+FgdA4sAH8FGIsePEIFHKZf3e25l5Myv/+PntO4Dn6DSx"
                        + "iLsubNwrjrbLtu3ggYOHAnZ+rrPOM4GNfq6yPEg+ngWDUaLSfRlFR5y8JOaV"
                        + "NjrfE2jvXAV6cizQ2I9DJbDS10a9H12cqvRInkacrPbjoYyOZaqLvho2isUC"
                        + "zcPLN+aMjF0B/8AYakYyyxSvtq9Y1plT6G0tNq+TJLo80SaMJwcmUzmpmzt9"
                        + "acI01mEw1moS/H5berV1hWz/AyiwZOJ8IC/UocrP41DAHcSjdKje6jLWH66e"
                        + "fpBj6cPBDR9NPPKxijUH2wJb16Vw8Fhgd2YjU+lYD1UwIS6RJAQns2owvepM"
                        + "eQwxK4I67vX0Mv/6300JdP9fCFv8kWz+YqLVKnKzWoDF8C4EPHYv2Fn8et3e"
                        + "Z4hu7yusT2wt+DxtgsBqqTyL2sMybhZyxdNVEnuVhF2wF2q2W05XaKBVKqxP"
                        + "UZVCUd3CbWow6VzrXW2HWt3eFzRqwWV6B6GLJLqk1qLeXNSrRC3cKXkbXA6a"
                        + "tci6TzMW127yVcQv+7D1mHsDAAA=")
        );
    }

    public void testSuperClassInLibrary() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=97006
        // 97006: Gradle lint does not recognize Context.getDrawable() as API 21+
        //noinspection all // Sample code
        checkApiCheck(""
                        + "src/test/pkg/MyFragment.java:10: Error: Call requires API level 21 (current min is 14): android.content.Context#getDrawable [NewApi]\n"
                        + "        getActivity().getDrawable(R.color.my_color);\n"
                        + "                      ~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                ""
                        + "src/test/pkg/MyFragment.java:10: Error: Call requires API level 21 (current min is 14): android.app.Activity#getDrawable [NewApi]\n"
                        + "        getActivity().getDrawable(R.color.my_color);\n"
                        + "                      ~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                true,
                false,
                null,

                // Master project
                manifest().pkg("foo.master").minSdk(14),
                projectProperties().property("android.library.reference.1", "../LibraryProject"),
                java(""
                        + "package foo.main;\n"
                        + "\n"
                        + "public class MainCode {\n"
                        + "    static {\n"
                        + "        System.out.println(R.string.string2);\n"
                        + "    }\n"
                        + "}\n"),
                java(""
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
                java(""
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
                base64gzip("bin/classes/test/pkg/MyFragment$R$color.class", ""
                        + "H4sIAAAAAAAAAH1QwUrDQBSc17RNjWlTW6vWInjooXowiEfFS6FQiApVepVt"
                        + "XGJqupFkI/TkN3kSPPgBfpT4EgteipeZefNmZ5f9+v74BHCGngUDHRM7JnYJ"
                        + "tcXy3o+jOCHQmFAfxirVQumpiDJpvJo4JVQvQhXqS4IxOJoSysP4QRIcL1Ty"
                        + "OlvMZHInZhE7LS/2RTQVSZjPK7OsH8PURJf7JwR7rJRMhpFIU5kSKqurDzwt"
                        + "U+0+PwXu1XKUiGAhle5P+sX6nGDdxlniy1GYNzp/kZO5eBE2atgg9P6pIDTz"
                        + "pBsJFbg3s7n0tYl9QmftGUJ7jY9DlPjnAEIXZVSYqzyVYLIq5W9gtthxmYm5"
                        + "cvwOeisim4zVwmzAZrR/A6ijVezbBW6jydxi1cAeHC7L1RasH4Ynyfq8AQAA"),
                base64gzip("bin/classes/test/pkg/MyFragment$R.class", ""
                        + "H4sIAAAAAAAAAG1QsU7DMBS8l6YNhNCUltKBhaEDMJCBkYqlUiWkAFJB3Z1g"
                        + "BZfUQY6LxGcxIXXgA/goxEuoxBIPd+d792T5vn82XwAuMfLRwoGHgYdDAs0J"
                        + "wY3W0kxzUZay9DAktNMiLwyhM1Fa2WtC6/RsQXCnxZMkhLHS8m69SqR5FEnO"
                        + "Tj8uUpEvhFHVfWu69lmVhFFsZWmj15csun2fGZGtpLbj+RXBfyjWJpUzVaXD"
                        + "/+HFUryJADvY9XBEGDbuE3pVLMqFzqL7ZClTSzhujI63vxk0THECh/uoDrHi"
                        + "N5n9uiguh7l9/gn6YOFgj9FnBvbhoouAVfAXYqdbZ8Iae+wAfVYOo8tbHVYe"
                        + "/F/FjmudhgEAAA=="),
                base64gzip("bin/classes/test/pkg/MyFragment.class", ""
                        + "H4sIAAAAAAAAAI2R20rDQBCG/+0pmkSr1no+K7RWMIjeSEUQRRCqFyp6vaZL"
                        + "XW2TsNlWvPKZvFHwwgfwocRJ2lqRgu7CzO7MP9+wOx+fb+8AtrFqwkDeRAYT"
                        + "BiaTTwa2TExjxsCsgTkD8wzsnME+8TyhDus8DEXIkNmTntT7DMni+hVD6tCv"
                        + "CoZsRXrirNm4EeqS39QpMlbxXV6/4kpG904wpW8lMfIVLULtBPc15/TxWPFa"
                        + "Q3i6HKUpzGBe+E3limMZlWR7is073uI2LNg2FrDIMNsHs3a+5vp1XzGkY29g"
                        + "ycYyVhhyfdQMi9yrKl9WnbAZBL7STmvH4UHg9BT5vl0YrJrQB66WLakfGTaK"
                        + "65U/UF0xvbTwT2m7y5HiD+0PLBRPem1qige30g2daifvdIVlenGGphutBFj0"
                        + "ZWSH4rHTVMmnS69gz3F6mKxJHlSSoqIsney2CCMYJU/T/AbsxkDAKr0gMZZ6"
                        + "Qfr6N8akbf3AWB1MArnYjlMehExggFoO0i1JpymYX1vZ5PubAgAA"),

                // Library project
                manifest().pkg("foo.library").minSdk(14),
                source("../LibraryProject/project.properties", ""
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
                java(""
                        + "package foo.library;\n"
                        + "\n"
                        + "public class LibraryCode {\n"
                        + "    static {\n"
                        + "        System.out.println(R.string.string1);\n"
                        + "    }\n"
                        + "}\n"),
                java(""
                        + "package foo.library;\n"
                        + "public class R {\n"
                        + "    public static class string {\n"
                        + "        public static final int string1 = 0x7f070033;\n"
                        + "    }\n"
                        + "}\n"
                        + ""),
                xml("../LibraryProject/res/values/strings.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <string name=\"app_name\">LibraryProject</string>\n"
                        + "    <string name=\"string1\">String 1</string>\n"
                        + "    <string name=\"string2\">String 2</string>\n"
                        + "    <string name=\"string3\">String 3</string>\n"
                        + "\n"
                        + "</resources>\n"),
                base64gzip("../LibraryProject/libs/fragment_support.jar", ""
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
                        + "F9qD0wUAV7HJDXoGAAA=")
        );
    }

    public void testTargetInLambda() throws Exception {
        // Regression test for
        // // 226364: TargetAPI annotation on method doesn't apply to lambda
        //noinspection all // Sample code
        checkApiCheck(
                "No warnings.",
                "No warnings.",
                manifest().minSdk(4),
                java(""
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
                        + "}\n")
        );
    }

    public void testConditionalAroundException() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=220968
        // 220968: Build version check not work on exception
        // (and https://code.google.com/p/android/issues/detail?id=209129)

        //noinspection all // Sample code
        checkApiCheck("No warnings.",
                null,

                manifest().minSdk(4),
                java(""
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
                        + "}\n")
        );
    }

    public void testMethodReferences() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=219413
        checkApiCheck(
                ""
                        + "src/test/pkg/Class.java:7: Error: Method reference requires API level 17 (current min is 4): TextView::getCompoundPaddingEnd [NewApi]\n"
                        + "        System.out.println(TextView::getCompoundPaddingEnd);\n"
                        + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                null,
                true,
                false,
                null,
                manifest().minSdk(4),
                java(""
                        + "package test.pkg;\n"
                        + "import android.widget.TextView;\n"
                        + "\n"
                        + "@SuppressWarnings({\"unused\",\"WeakerAccess\"})\n"
                        + "public class Class {\n"
                        + "    protected void test(TextView textView) {\n"
                        + "        System.out.println(TextView::getCompoundPaddingEnd);\n"
                        + "    }\n"
                        + "}")
        );
    }

    public void testLambdas() throws Exception {
        checkApiCheck(""
                        + "src/test/pkg/LambdaTest.java:9: Error: Call requires API level 23 (current min is 1): android.view.View#performContextClick [NewApi]\n"
                        + "    private View.OnTouchListener myListener = (v, event) -> v.performContextClick();\n"
                        + "                                                              ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/LambdaTest.java:12: Error: Call requires API level 24 (current min is 1): java.util.Map#forEach [NewApi]\n"
                        + "        map.forEach((t, u) -> Log.i(\"tag\", t + u));\n"
                        + "            ~~~~~~~\n"
                        + "2 errors, 0 warnings\n",
                "No warnings.",
                java(""
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
                        + "}\n"));
    }

    public void testVirtualMethods() throws Exception {
        // Regression test for b/32430124
        //noinspection all // Sample code
        checkApiCheck(
                ""
                        + "src/test/pkg/SupportLibTest.java:19: Error: Call requires API level 21 (current min is 4): android.graphics.drawable.Drawable#inflate [NewApi]\n"
                        + "        drawable1.inflate(resources, parser, attrs, theme); // ERROR\n"
                        + "                  ~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                "No warnings.",

                manifest().minSdk(4),
                java(""
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
                        + "}\n")
        );
    }

    @Ignore("http://b.android.com/266795")
    public void ignore_testHigherCompileSdkVersionThanPlatformTools() throws Exception {
        // Warn if the platform tools are too old on the system
        lint().files(
                manifest().minSdk(14),
                projectProperties().compileSdk(400), // in the future
                mApiCallTest12,
                mApiCallTest12_class)
                .run()
                .expectMatches(""
                        + "Error: The SDK platform-tools version \\([^)]+\\) is too old to check APIs compiled with API 400; please update");
    }

    @Ignore("http://b.android.com/266795")
    public void ignore_testHigherCompileSdkVersionThanPlatformToolsInEditor() throws Exception {
        // When editing a file we place the error on the first line of the file instead
        lint().files(
                manifest().minSdk(14),
                projectProperties().compileSdk(400), // in the future
                mApiCallTest12,
                mApiCallTest12_class)
                .incremental("src/test/pkg/ApiCallTest12.java")
                .run()
                .expectMatches(""
                        + "src/test/pkg/ApiCallTest12.java:1: Error: The SDK platform-tools version \\([^)]+\\) is too old to check APIs compiled with API 400; please update");
    }

    @SuppressWarnings({"MethodMayBeStatic", "ConstantConditions", "ClassNameDiffersFromFileName"})
    public void testCastChecks() throws Exception {
        // When editing a file we place the error on the first line of the file instead
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/CastTest.java:15: Error: Cast from Cursor to Closeable requires API level 16 (current min is 14) [NewApi]\n"
                + "        Closeable closeable = (Closeable) cursor; // Requires 16\n"
                + "                              ~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CastTest.java:21: Error: Cast from KeyCharacterMap to Parcelable requires API level 16 (current min is 14) [NewApi]\n"
                + "        Parcelable parcelable2 = (Parcelable)map; // Requires API 16\n"
                + "                                 ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CastTest.java:27: Error: Cast from AnimatorListenerAdapter to AnimatorPauseListener requires API level 19 (current min is 14) [NewApi]\n"
                + "        AnimatorPauseListener listener = (AnimatorPauseListener)adapter;\n"
                + "                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n",

                lintProject(
                        java("src/test/pkg/CastTest.java", ""
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
                                + "        Closeable closeable = (Closeable) cursor; // Requires 16\n"
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
                        manifest().minSdk(14)
                ));
    }

    @SuppressWarnings({"MethodMayBeStatic", "ConstantConditions", "ClassNameDiffersFromFileName",
            "UnnecessaryLocalVariable"})
    public void testImplicitCastTest() throws Exception {
        // When editing a file we place the error on the first line of the file instead
        //noinspection all
        assertEquals(""
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
                + "4 errors, 0 warnings\n",

                lintProject(
                        java("src/test/pkg/ImplicitCastTest.java", ""
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
                        manifest().minSdk(14)
                ));
    }

    @SuppressWarnings("all") // sample code
    public void testResourceReference() throws Exception {
        checkApiCheck(""
                        + "src/test/pkg/TestResourceReference.java:5: Warning: Field requires API level 21 (current min is 10): android.R.interpolator#fast_out_linear_in [InlinedApi]\n"
                        + "        int id = android.R.interpolator.fast_out_linear_in;\n"
                        + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n", null,
                manifest().minSdk(10),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "public class TestResourceReference {\n"
                        + "    protected void test() {\n"
                        + "        int id = android.R.interpolator.fast_out_linear_in;\n"
                        + "    }\n"
                        + "}")
        );
    }

    public void testSupportLibraryCalls() throws Exception {
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/SupportLibraryApiTest.java:22: Error: Call requires API level 21 (current min is 14): android.view.View#setBackgroundTintList [NewApi]\n"
                + "        button.setBackgroundTintList(colors); // ERROR\n"
                + "               ~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                ""
                + "src/test/pkg/SupportLibraryApiTest.java:22: Error: Call requires API level 21 (current min is 14): android.widget.ImageButton#setBackgroundTintList [NewApi]\n"
                + "        button.setBackgroundTintList(colors); // ERROR\n"
                + "               ~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                manifest().minSdk(14),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.content.Context;\n"
                        + "import android.content.res.ColorStateList;\n"
                        + "import android.support.design.widget.FloatingActionButton;\n"
                        + "import android.util.AttributeSet;\n"
                        + "import android.widget.ImageButton;\n"
                        + "\n"
                        + "public class SupportLibraryApiTest extends FloatingActionButton {\n"
                        + "    public SupportLibraryApiTest(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {\n"
                        + "        super(context, attrs, defStyleAttr, defStyleRes);\n"
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
                base64gzip("bin/classes/test/pkg/SupportLibraryApiTest.class", ""
                        + "H4sIAAAAAAAAAJ1Ty27TQBQ90zgJOKFpC0mhL/qClqRgEXWDgpDSSkWRLBYk"
                        + "6oYNY3vqDnXtyB5T+llsisSCD+CjEHccl6BCw8OLO/c155w74/n67fMXAG1s"
                        + "myhhzUQR69ojs4H1MjbLeMBQei5DqV4wPNu2eejFkfQsNwqVCJW1r9cPqvOj"
                        + "kCoZWF2lYumkSvSF6vR6jw4ZjP3IEww1W4biVXrqiHjAnYAyc3bk8uCQx1LH"
                        + "edJQxzJhWLWVSJQ1PPGtfjocRrGypRPz+Lw7lAOqdBjK7kgBw71rxTEUOSki"
                        + "wMUJOhmqnjjqq/NA6DwD6zFULlOvBW0vajlPGbZ+PYhYJMQXRHFfcSVsSeL0"
                        + "2CVX52jr5t/syBnaDGdjhmQ0ueWJRPqhdSY9XyjrIIi4kqHfdZWMwr1UqSgc"
                        + "30Le1Dvlvrha+4PiwhF3GHb/h57GdTKPYWmSFAazH6WxKw6kvuyF397tk3f8"
                        + "Pa+ijBtVPESjjC2Glcm/A0P731Uz1BOh9rh74sdRGnoDGSp9GKTr+hGwRk+l"
                        + "BP0VwLRIsjcpekv5Iq1ms7WzXDc+gX2kaAqmztEKVKivShbYHXVSdCtDMjGN"
                        + "GuFobwazWbeJOdwmDu3dIc+geh2NnO0l9WSYzdYFpjIzppvOttWIZIbi2Yyy"
                        + "kUu4pKxgHncJjF4PFnLQN9Rj6Frr8QUKO2SMq6B1Am1QPJ+BNkftP4EuYimb"
                        + "o4JlrOSUmkif1f0Ma/U7Ct87rnsEAAA="),
                java(""
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
                        + "}\n"),
                base64gzip("bin/classes/android/support/design/widget/FloatingActionButton.class",
                        ""
                                + "H4sIAAAAAAAAAJVSTW/TQBB9U5wE3JCWlBRSKCUSEtAiLFW9oKBKaaVKlqIe"
                                + "6qoXLmzsxWxxdiN7ncLP4lSJAz+AH4WYdVof+BLY2p3ZmTczb2b32/cvXwHs"
                                + "YuDDw6bbHrWw1cJjQvO10sruE149Gwud5EYlQWy0ldoGh05+tMPaUVqVBSNr"
                                + "czUprYykHYbh8zOCd2gSSVgZKy2Py+lE5qdikrGlOzaxyM5Ertz5yujZ96og"
                                + "7NVpi3I2M7kNElmoVAcXKkmlDY4yI6zS6Si2yuiD0lqjh4RWvGBF6P+RMKEh"
                                + "mCUXefAX7oR2It9F9lMmnZ1AIWH52nQiXfhJqa2aylDPVaGY/UhrY4UjxN5B"
                                + "nVzU5iDiZnJZFDyLishcZCU33TyWF6OZIvQKaQ9E/CHNTamTU0aNVcHdPP11"
                                + "/pyGW8pMHnFu6WDDato8FQ548i94gh+ZMo/lkXKj7/9uqC/PxVy00UCzjXu4"
                                + "T9j9/4shbFwHXaHCqUjlwukNcIPfnPuWQK4Qyxaf3rLeYOlv77zY7HmXoM8V"
                                + "5qazsQRu4xY6vIC9BZL/5SqTjzZ7qdI6WKnQPlZxh2s5rcuahzXWO4y6y6v3"
                                + "BlRgnTXuknfHYJ/jXGRze+cSSz9X73LNtar6+gJVV2+ijw3OwU+kinn4AwEJ"
                                + "Ps9kAwAA")
        );
    }

    @SuppressWarnings("all") // sample code
    public void testEnumInitialization() throws Exception {
        checkApiCheck(""
                + "src/test/pkg/ApiDetectorTest2.java:8: Warning: Field requires API level 19 (current min is 15): android.location.LocationManager#MODE_CHANGED_ACTION [InlinedApi]\n"
                + "    LOCATION_MODE_CHANGED(LocationManager.MODE_CHANGED_ACTION) {\n"
                + "                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
                null,

                manifest().minSdk(15),
                java("src/test/pkg/ApiDetectorTest2.java", ""
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
                        + "}")
        );
    }

    @SuppressWarnings("all") // sample code
    public void testRequiresApiAsTargetApi() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(15),
                        java("src/test/pkg/ApiDetectorTest2.java", ""
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
                        mSupportJar

                ));
    }

    @SuppressWarnings("all") // sample code
    public void testRequiresApi() throws Exception {
        assertEquals(""
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
                + "4 errors, 0 warnings\n",
                lintProject(
                        manifest().minSdk(15),
                        java("src/test/pkg/TestRequiresApi.java", ""
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
                        mSupportJar

                ));
    }

    public void testRequiresApiInheritance() throws Exception {
        lint().files(
                java("package android.support.v7.app;\n"
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
                .run()
                .expect(""
                        + "src/android/support/v7/app/RequiresApiTest.java:8: Error: Call requires API level 16 (current min is 1): ParentClass [NewApi]\n"
                        + "        new ParentClass().foo1(); // ERROR\n"
                        + "        ~~~~~~~~~~~~~~~\n"
                        + "src/android/support/v7/app/RequiresApiTest.java:8: Error: Call requires API level 18 (current min is 1): foo1 [NewApi]\n"
                        + "        new ParentClass().foo1(); // ERROR\n"
                        + "                          ~~~~\n"
                        + "2 errors, 0 warnings\n");
    }

    public void testDrawableThemeReferences() throws Exception {
        // Regression test for
        // https://code.google.com/p/android/issues/detail?id=199597
        // Make sure that theme references in drawable XML files are checked
        assertEquals(""
                + "res/drawable/my_drawable.xml:3: Error: Using theme references in XML drawables requires API level 21 (current min is 9) [NewApi]\n"
                + "    <item android:drawable=\"?android:windowBackground\"/>\n"
                + "          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/drawable/my_drawable.xml:4: Error: Using theme references in XML drawables requires API level 21 (current min is 9) [NewApi]\n"
                + "    <item android:drawable=\"?android:selectableItemBackground\"/>\n"
                + "          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                lintProject(
                        manifest().minSdk(9),
                        xml("res/drawable/my_drawable.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <item android:drawable=\"?android:windowBackground\"/>\n"
                                + "    <item android:drawable=\"?android:selectableItemBackground\"/>\n"
                                + "</layer-list>"),
                        xml("res/drawable-v21/my_drawable.xml", "" // OK
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <item android:drawable=\"?android:windowBackground\"/>\n"
                                + "    <item android:drawable=\"?android:selectableItemBackground\"/>\n"
                                + "</layer-list>")
        ));
    }

    public void testNonAndroidProjects() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=228481
        // Don't flag API violations in plain java modules if there are no dependent
        // Android modules pointing to it
        lint().projects(project(
                java(""
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
                .run()
                .expectClean();
    }

    @Override
    protected TestLintClient createClient() {
        if (getName().equals("testMissingApiDatabase")) {
            // Simulate an environment where there is no API database
            return new ToolsBaseTestLintClient() {
                @Override
                public File findResource(@NonNull String relativePath) {
                    return null;
                }
            };
        }
        if (getName().equals("testPaddingStart")) {
            return new ToolsBaseTestLintClient() {
                @NonNull
                @Override
                protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                    Project fromSuper = super.createProject(dir, referenceDir);
                    Project spy = spy(fromSuper);
                    when(spy.getBuildTools()).thenReturn(null);
                    return spy;
                }
            };
        }
        if (getName().equals("testPaddingStartWithOldBuildTools")) {
            return new ToolsBaseTestLintClient() {
                @NonNull
                @Override
                protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                    Revision revision = new Revision(22, 2, 1);
                    BuildToolInfo info = BuildToolInfo.fromStandardDirectoryLayout(revision, dir);

                    Project fromSuper = super.createProject(dir, referenceDir);
                    Project spy = spy(fromSuper);
                    when(spy.getBuildTools()).thenReturn(info);
                    return spy;
                }
            };
        }
        if (getName().equals("testPaddingStartWithNewBuildTools")) {
            return new ToolsBaseTestLintClient() {
                @NonNull
                @Override
                protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                    Revision revision = new Revision(23, 0, 2);
                    BuildToolInfo info = BuildToolInfo.fromStandardDirectoryLayout(revision, dir);

                    Project fromSuper = super.createProject(dir, referenceDir);
                    Project spy = spy(fromSuper);
                    when(spy.getBuildTools()).thenReturn(info);
                    return spy;
                }
            };
        }
        return super.createClient();
    }

    // bug 198295: Add a test for a case that crashes ApiDetector due to an
    // invalid parameterIndex causing by a varargs method invocation.
    public void testMethodWithPrimitiveAndVarargs() throws Exception {
        // In case of a crash, there is an assertion failure in tearDown()
        //noinspection ClassNameDiffersFromFileName
        assertEquals("No warnings.",
                lintProject(
                        manifest().minSdk(14),
                        java("src/test/pkg/LogHelper.java", "" +
                                "package test.pkg;\n"
                                + "\n"
                                + "public class LogHelper {\n"
                                + "\n"
                                + "    public static void log(String tag, Object... args) {\n"
                                + "    }\n"
                                + "}"),
                        java("src/test/pkg/Browser.java", "" +
                                "package test.pkg;\n"
                                + "\n"
                                + "public class Browser {\n"
                                + "    \n"
                                + "    public void onCreate() {\n"
                                + "        LogHelper.log(\"TAG\", \"arg1\", \"arg2\", 1, \"arg4\", this /*non primitive*/);\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testMethodInvocationWithGenericTypeArgs() throws Exception {
        // Test case for https://code.google.com/p/android/issues/detail?id=198439
        //noinspection ClassNameDiffersFromFileName
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/Loader.java", ""
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
                                + "}\n")
                ));
    }

    @SuppressWarnings("all") // sample code
    public void testInlinedConstantConditional() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=205925
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/MainActivity.java", ""
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
                                + "}")
                ));
    }

    @SuppressWarnings("all") // sample code
    public void testSdkSuppress() throws Exception {
        // Regression test for b/31799926
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/MainActivity.java", ""
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
                                + "}")
                ));
    }

    public void testMultiCatch() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=198854
        // Check disjointed exception types

        //noinspection all
        checkApiCheck(""
                + "src/test/pkg/MultiCatch.java:12: Error: Class requires API level 18 (current min is 1): android.media.UnsupportedSchemeException [NewApi]\n"
                + "        } catch (MediaDrm.MediaDrmStateException | UnsupportedSchemeException e) {\n"
                + "                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:12: Error: Class requires API level 21 (current min is 1): android.media.MediaDrm.MediaDrmStateException [NewApi]\n"
                + "        } catch (MediaDrm.MediaDrmStateException | UnsupportedSchemeException e) {\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:18: Error: Class requires API level 21 (current min is 1): android.media.MediaDrm.MediaDrmStateException [NewApi]\n"
                + "        } catch (MediaDrm.MediaDrmStateException\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:19: Error: Class requires API level 18 (current min is 1): android.media.UnsupportedSchemeException [NewApi]\n"
                + "                  | UnsupportedSchemeException e) {\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:25: Error: Multi-catch with these reflection exceptions requires API level 19 (current min is 1) because they get compiled to the common but new super type ReflectiveOperationException. As a workaround either create individual catch statements, or catch Exception. [NewApi]\n"
                + "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "5 errors, 0 warnings\n",

                ""
                + "src/test/pkg/MultiCatch.java:12: Error: Class requires API level 18 (current min is 1): android.media.UnsupportedSchemeException [NewApi]\n"
                + "        } catch (MediaDrm.MediaDrmStateException | UnsupportedSchemeException e) {\n"
                + "                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:12: Error: Class requires API level 21 (current min is 1): android.media.MediaDrm.MediaDrmStateException [NewApi]\n"
                + "        } catch (MediaDrm.MediaDrmStateException | UnsupportedSchemeException e) {\n"
                + "                          ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:18: Error: Class requires API level 21 (current min is 1): android.media.MediaDrm.MediaDrmStateException [NewApi]\n"
                + "        } catch (MediaDrm.MediaDrmStateException\n"
                + "                          ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:19: Error: Class requires API level 18 (current min is 1): android.media.UnsupportedSchemeException [NewApi]\n"
                + "                  | UnsupportedSchemeException e) {\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/MultiCatch.java:26: Error: Multi-catch with these reflection exceptions requires API level 19 (current min is 1) because they get compiled to the common but new super type ReflectiveOperationException. As a workaround either create individual catch statements, or catch Exception. [NewApi]\n"
                + "            e.printStackTrace();\n"
                + "              ~~~~~~~~~~~~~~~\n"
                + "5 errors, 0 warnings\n",

                java("src/test/pkg/MultiCatch.java", ""
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
                        + "}\n"),
                base64("bin/classes/test/pkg/MultiCatch.class", ""
                        + "yv66vgAAADMARgoADAAmCgASACcHACkHACwKAC0ALgoAEgAvBwAwCAAxBwAy"
                        + "CgAJADMIADQHADUKADYANwcAOAcAOQcAOgoAOwAuBwA8AQAGPGluaXQ+AQAD"
                        + "KClWAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEAEkxvY2FsVmFyaWFibGVU"
                        + "YWJsZQEABHRoaXMBABVMdGVzdC9wa2cvTXVsdGlDYXRjaDsBAAR0ZXN0AQAB"
                        + "ZQEAFUxqYXZhL2xhbmcvRXhjZXB0aW9uOwEAKExqYXZhL2xhbmcvUmVmbGVj"
                        + "dGl2ZU9wZXJhdGlvbkV4Y2VwdGlvbjsBAA1TdGFja01hcFRhYmxlBwA9BwA+"
                        + "AQAHbWV0aG9kMQEACkV4Y2VwdGlvbnMBAAdtZXRob2QyAQAKU291cmNlRmls"
                        + "ZQEAD011bHRpQ2F0Y2guamF2YQwAEwAUDAAhABQHAD8BAC1hbmRyb2lkL21l"
                        + "ZGlhL01lZGlhRHJtJE1lZGlhRHJtU3RhdGVFeGNlcHRpb24BABZNZWRpYURy"
                        + "bVN0YXRlRXhjZXB0aW9uAQAMSW5uZXJDbGFzc2VzAQAoYW5kcm9pZC9tZWRp"
                        + "YS9VbnN1cHBvcnRlZFNjaGVtZUV4Y2VwdGlvbgcAPQwAQAAUDAAjABQBABBq"
                        + "YXZhL2xhbmcvU3RyaW5nAQAEdHJpbQEAD2phdmEvbGFuZy9DbGFzcwwAQQBC"
                        + "AQAAAQAQamF2YS9sYW5nL09iamVjdAcAQwwARABFAQAgamF2YS9sYW5nL0ls"
                        + "bGVnYWxBY2Nlc3NFeGNlcHRpb24BACtqYXZhL2xhbmcvcmVmbGVjdC9JbnZv"
                        + "Y2F0aW9uVGFyZ2V0RXhjZXB0aW9uAQAfamF2YS9sYW5nL05vU3VjaE1ldGhv"
                        + "ZEV4Y2VwdGlvbgcAPgEAE3Rlc3QvcGtnL011bHRpQ2F0Y2gBABNqYXZhL2xh"
                        + "bmcvRXhjZXB0aW9uAQAmamF2YS9sYW5nL1JlZmxlY3RpdmVPcGVyYXRpb25F"
                        + "eGNlcHRpb24BABZhbmRyb2lkL21lZGlhL01lZGlhRHJtAQAPcHJpbnRTdGFj"
                        + "a1RyYWNlAQAJZ2V0TWV0aG9kAQBAKExqYXZhL2xhbmcvU3RyaW5nO1tMamF2"
                        + "YS9sYW5nL0NsYXNzOylMamF2YS9sYW5nL3JlZmxlY3QvTWV0aG9kOwEAGGph"
                        + "dmEvbGFuZy9yZWZsZWN0L01ldGhvZAEABmludm9rZQEAOShMamF2YS9sYW5n"
                        + "L09iamVjdDtbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0"
                        + "OwAhABIADAAAAAAABAABABMAFAABABUAAAAvAAEAAQAAAAUqtwABsQAAAAIA"
                        + "FgAAAAYAAQAAAAgAFwAAAAwAAQAAAAUAGAAZAAAAAQAaABQAAQAVAAAA/QAD"
                        + "AAIAAAA2KrYAAqcACEwrtgAFKrYABqcACEwrtgAFEgcSCAO9AAm2AAoSCwO9"
                        + "AAy2AA1XpwAITCu2ABGxAAcAAAAEAAcAAwAAAAQABwAEAAwAEAATAAMADAAQ"
                        + "ABMABAAYAC0AMAAOABgALQAwAA8AGAAtADAAEAADABYAAAA2AA0AAAALAAQA"
                        + "DgAHAAwACAANAAwAEQAQABUAEwASABQAFAAYABgALQAbADAAGQAxABoANQAc"
                        + "ABcAAAAqAAQACAAEABsAHAABABQABAAbABwAAQAxAAQAGwAdAAEAAAA2ABgA"
                        + "GQAAAB4AAAARAAZHBwAfBEYHAB8EVwcAIAQAAQAhABQAAgAVAAAAKwAAAAEA"
                        + "AAABsQAAAAIAFgAAAAYAAQAAAB8AFwAAAAwAAQAAAAEAGAAZAAAAIgAAAAYA"
                        + "AgADAAQAAQAjABQAAgAVAAAAKwAAAAEAAAABsQAAAAIAFgAAAAYAAQAAACEA"
                        + "FwAAAAwAAQAAAAEAGAAZAAAAIgAAAAYAAgADAAQAAgAkAAAAAgAlACsAAAAK"
                        + "AAEAAwAoACoAGQ==")

        );
    }

    @SuppressWarnings("all") // Sample code
    public void testConcurrentHashMapUsage() throws Exception {
        ApiLookup lookup = ApiLookup.get(createClient());
        int version = lookup.getCallVersion("java/util/concurrent/ConcurrentHashMap", "keySet",
                "(Ljava/lang/Object;)");
        if (version == -1) {
            // This test machine doesn't have the right version of Nougat yet
            return;
        }

        checkApiCheck(""
                + "src/test/pkg/MapUsage.java:7: Error: The type of the for loop iterated value is java.util.concurrent.ConcurrentHashMap.KeySetView<java.lang.String,java.lang.Object>, which requires API level 24 (current min is 1); to work around this, add an explicit cast to (Map) before the keySet call. [NewApi]\n"
                + "        for (String key : map.keySet()) {\n"
                + "                          ~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                ""
                + "src/test/pkg/MapUsage.java:7: Error: Call requires API level 24 (current min is 1): java.util.concurrent.ConcurrentHashMap.KeySetView#iterator. The keySet() method in ConcurrentHashMap changed in a backwards incompatible way in Java 8; to work around this issue, add an explicit cast to (Map) before the keySet() call. [NewApi]\n"
                + "        for (String key : map.keySet()) {\n"
                + "        ^\n"
                + "1 errors, 0 warnings\n",

                java("src/test/pkg/MapUsage.java", ""
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
                        java("src/java/util/concurrent/ConcurrentHashMap.java", ""
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
                                + "}"),
                        base64("bin/classes/test/pkg/MapUsage.class", ""
                                + "yv66vgAAADMAQgoACgAgCgAhACIKACMAJAsAJQAmCwAlACcHACgJACkAKgoA"
                                + "KwAsBwAtBwAuAQAGPGluaXQ+AQADKClWAQAEQ29kZQEAD0xpbmVOdW1iZXJU"
                                + "YWJsZQEAEkxvY2FsVmFyaWFibGVUYWJsZQEABHRoaXMBABNMdGVzdC9wa2cv"
                                + "TWFwVXNhZ2U7AQAIZHVtcEtleXMBACsoTGphdmEvdXRpbC9jb25jdXJyZW50"
                                + "L0NvbmN1cnJlbnRIYXNoTWFwOylWAQADa2V5AQASTGphdmEvbGFuZy9TdHJp"
                                + "bmc7AQADbWFwAQAoTGphdmEvdXRpbC9jb25jdXJyZW50L0NvbmN1cnJlbnRI"
                                + "YXNoTWFwOwEAFkxvY2FsVmFyaWFibGVUeXBlVGFibGUBAE5MamF2YS91dGls"
                                + "L2NvbmN1cnJlbnQvQ29uY3VycmVudEhhc2hNYXA8TGphdmEvbGFuZy9TdHJp"
                                + "bmc7TGphdmEvbGFuZy9PYmplY3Q7PjsBAA1TdGFja01hcFRhYmxlBwAvAQAJ"
                                + "U2lnbmF0dXJlAQBRKExqYXZhL3V0aWwvY29uY3VycmVudC9Db25jdXJyZW50"
                                + "SGFzaE1hcDxMamF2YS9sYW5nL1N0cmluZztMamF2YS9sYW5nL09iamVjdDs+"
                                + "OylWAQAKU291cmNlRmlsZQEADU1hcFVzYWdlLmphdmEMAAsADAcAMAwAMQA0"
                                + "BwA1DAA2ADcHAC8MADgAOQwAOgA7AQAQamF2YS9sYW5nL1N0cmluZwcAPAwA"
                                + "PQA+BwA/DABAAEEBABF0ZXN0L3BrZy9NYXBVc2FnZQEAEGphdmEvbGFuZy9P"
                                + "YmplY3QBABJqYXZhL3V0aWwvSXRlcmF0b3IBACZqYXZhL3V0aWwvY29uY3Vy"
                                + "cmVudC9Db25jdXJyZW50SGFzaE1hcAEABmtleVNldAEACktleVNldFZpZXcB"
                                + "AAxJbm5lckNsYXNzZXMBADUoKUxqYXZhL3V0aWwvY29uY3VycmVudC9Db25j"
                                + "dXJyZW50SGFzaE1hcCRLZXlTZXRWaWV3OwEAMWphdmEvdXRpbC9jb25jdXJy"
                                + "ZW50L0NvbmN1cnJlbnRIYXNoTWFwJEtleVNldFZpZXcBAAhpdGVyYXRvcgEA"
                                + "FigpTGphdmEvdXRpbC9JdGVyYXRvcjsBAAdoYXNOZXh0AQADKClaAQAEbmV4"
                                + "dAEAFCgpTGphdmEvbGFuZy9PYmplY3Q7AQAQamF2YS9sYW5nL1N5c3RlbQEA"
                                + "A291dAEAFUxqYXZhL2lvL1ByaW50U3RyZWFtOwEAE2phdmEvaW8vUHJpbnRT"
                                + "dHJlYW0BAAdwcmludGxuAQAVKExqYXZhL2xhbmcvU3RyaW5nOylWACEACQAK"
                                + "AAAAAAACAAEACwAMAAEADQAAAC8AAQABAAAABSq3AAGxAAAAAgAOAAAABgAB"
                                + "AAAABQAPAAAADAABAAAABQAQABEAAAABABIAEwACAA0AAACTAAIABAAAACYr"
                                + "tgACtgADTSy5AAQBAJkAFyy5AAUBAMAABk6yAActtgAIp//msQAAAAQADgAA"
                                + "ABIABAAAAAcAGwAIACIACQAlAAoADwAAACAAAwAbAAcAFAAVAAMAAAAmABAA"
                                + "EQAAAAAAJgAWABcAAQAYAAAADAABAAAAJgAWABkAAQAaAAAACwAC/AAIBwAb"
                                + "+gAcABwAAAACAB0AAgAeAAAAAgAfADMAAAAKAAEAIwAhADIACQ==")
        );
    }

    @SuppressWarnings("all") // sample code
    public void testObsoleteVersionCheck() throws Exception {
        assertEquals(""
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
                + "src/test/pkg/TestVersionCheck.java:21: Warning: Unnecessary; SDK_INT is never < 23 [ObsoleteSdkInt]\n"
                + "        if (Build.VERSION.SDK_INT < 23) { }\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 9 warnings\n",

                lintProject(
                        manifest().minSdk(23),
                        java("src/test/pkg/TestVersionCheck.java", ""
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
                        mSupportJar
                ));
    }

    public void testMapGetOrDefault() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=235665
        //noinspection all // Sample code
        checkApiCheck(""
                        + "src/test/pkg/MapApiTest.java:8: Error: Call requires API level 24 (current min is 1): java.util.Map#getOrDefault [NewApi]\n"
                        + "        map.getOrDefault(\"foo\", \"bar\");\n"
                        + "            ~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                "No warnings.",
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.util.Map;\n"
                        + "\n"
                        + "@SuppressWarnings(\"Since15\")\n"
                        + "public class MapApiTest  {\n"
                        + "    public void test(Map<String,String> map) {\n"
                        + "        map.getOrDefault(\"foo\", \"bar\");\n"
                        + "    }\n"
                        + "}\n"));
    }

    public void testObsoleteFolder()  {
        // Regression test for https://code.google.com/p/android/issues/detail?id=236018
        @Language("XML")
        String stringsXml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string name=\"home_title\">Home Sample</string>\n"
                + "</resources>\n";
        String expected = ""
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
                .run()
                .expect(expected);
    }

    public void testVectorDrawableCompat() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=222654
        //noinspection all // Sample code
        checkApiCheck(""
                + "src/test/pkg/VectorTest.java:17: Error: Call requires API level 21 (current min is 15): android.graphics.drawable.Drawable#setTint [NewApi]\n"
                + "        vector3.setTint(0xFFFFFF); // ERROR\n"
                + "                ~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                null,
                true,
                false,
                null,

                classpath(),
                manifest().minSdk(15),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.content.res.Resources;\n"
                        + "import android.graphics.drawable.Drawable;\n"
                        + "import android.support.graphics.drawable.VectorDrawableCompat;\n"
                        + "\n"
                        + "public class VectorTest {\n"
                        + "    public void test(Resources resources) throws Exception {\n"
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
                        + "}\n"),
                base64gzip("bin/classes/test/pkg/VectorTest.class", ""
                        + "H4sIAAAAAAAAALVUW08TQRT+hm5ZqcutiIoI2ILachu5iMQaE4OSkFQk0hBf"
                        + "p9tJu7jd3cxOkTd/hL/EFzE++AP8UeKZbWlNNNRo3Icz5/qdb86c7LfvX74C"
                        + "2MB2BkOYzWAOd1I4Pz83Ws6IfAbzWDAiZ+OujXsMg0+8wNNPGVKF4hGDtRPW"
                        + "JMNo2QvkfqtZlaoiqj55suXQFf6RUJ6xO05LN7yYYbKsZax59LbOj6SrQ1Uh"
                        + "s2TCdDLkC2UR1FTo1bgbBloGmisZ89cyDlvKlXHJNB5SFybDXJ98Yn2S9GHY"
                        + "7qbGrSgKleZ1JaKG58a8psQ7Q7PD6XnH3AmbkTDs7DbGelfbYFjowv0KcwFA"
                        + "pZkXp66MtBcGsY37ZB8mzHY9M5TR3gxWj8WJcJDBVRsFB0WsOFgFd/AAazbW"
                        + "HXqrTYaJ30yPYczUcl8Edf6qekwBSuy5ugQYtv5uAjREV0mhpY0thnSlIZtE"
                        + "3tkLAql2fBHH5iXe93u6vcvjCwlsqfgPrxRLXfEComsV9syiZNusd1XYfNP0"
                        + "E7oMZ/14lkNV56dNP2r5Pj9Z41R6QOqBULFUpW5xS3s+f6a18qotLQ+lLv3v"
                        + "++X7LxzDSO/OB0I3aO0L5d4uHBLdoP4TiUt218Yjhvk/uBPDzKVZyOEK/WXM"
                        + "NwBmVpykQxank9GZXjwD+5iEh0kOJk4bIySddgJGMUYnwziylGWKP8AiP1Bc"
                        + "SrHPGHi5nE19grXEGFnp/ZXEInXwsTVlGcPuNZglcBAjm7gME9gktZqmBjmC"
                        + "L1AD03izDd5pbLQJXCMCNvKUf52QJsm+QVoqud9NTBGhW5RpUdY0xW8n/Waw"
                        + "TGeGfIt4iCWM/wAiBV6iewUAAA==")
        );
    }

    public void testInnerClassAccess() throws Exception {
        // "Calling new methods on older version" doesn't work with inner classes
        // Regression test for https://code.google.com/p/android/issues/detail?id=228035
        checkApiCheck(""
                        + "src/pkg/my/myapplication/Fragment.java:8: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getContext [NewApi]\n"
                        + "            Context c1 = getContext();\n"
                        + "                         ~~~~~~~~~~\n"
                        + "src/pkg/my/myapplication/Fragment.java:9: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getContext [NewApi]\n"
                        + "            Context c2 = Fragment.this.getContext();\n"
                        + "                                       ~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n",
                "No warnings.",

                manifest().minSdk(15),
                java(""
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
                        + "}\n"));
    }

    public void testMethodWithInterfaceAlternative() throws Exception {
        // Make sure we correctly handle the case where you ensure that a method exists
        // at runtime (e.g. is always overridden) by using an interface
        //noinspection all // Sample code
        checkApiCheck("No warnings.",
                "No warnings.",

                manifest().minSdk(1),
                java(""
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
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "public interface LifecycleAware {\n"
                        + "    boolean isDestroyed();\n"
                        + "}\n"));
    }

    public void testMethodWithInterfaceAlternative2() throws Exception {
        // Slight variation on testMethodWithInterfaceAlternative where
        // we extend a class which implements the interface
        //noinspection all // Sample code
        checkApiCheck("No warnings.",
                "No warnings.",

                manifest().minSdk(1),
                java(""
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
                java(""
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
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "public interface LifecycleAware {\n"
                        + "    boolean isDestroyed();\n"
                        + "}\n"));
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
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @NonNull Location location, @NonNull String message) {
        if (issue == UNSUPPORTED || issue == INLINED) {
            if (message.startsWith("The SDK platform-tools version (")) {
                return;
            }
            if (message.startsWith("Type annotations")) {
                return;
            }
            int requiredVersion = ApiDetector.getRequiredVersion(issue, message, TEXT);
            assertTrue("Could not extract message tokens from \"" + message + "\"",
                    requiredVersion >= 1 && requiredVersion <= SdkVersionInfo.HIGHEST_KNOWN_API);
        }
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mApiCallTest = java(""
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
    private TestFile mApiCallTest11 = java(""
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
    private TestFile mApiCallTest11_class = base64gzip("bin/classes/test/pkg/ApiCallTest11.class", ""
            + "H4sIAAAAAAAAAIVSTW/TQBB947ix65oUQukHBUpoQW1BWO21CCkEISoFkGjV"
            + "A5y28bZdcGzLu7aU/8QBJAQSB34APwoxttw0RFQcdmZ2V+/NvLf76/ePnwB2"
            + "8cCBRVg0Upsg/XgadFPVE1F0yPudHQc2YUHEYZaoMBBpGnQHRhXKjAitYVkn"
            + "8TORvUy0Iaz3/02y8Wp0jtojNJ+oWJmnhMbm1hHB7iWh9NDAnA8HLmG+r2L5"
            + "Oh8ey+xQHEeS0O4nAxEdiUyV+/rQNmdKE5Yv6cmN/FNpxhMSVja3+tNCqqu9"
            + "WRCu+5hB08MNLDlYJHT+K8XHVVwjzCn9nK+yZCTDStM7duaEJeqz7kmZS6tW"
            + "3+axUUO5HxdKK56/G8eJEeUArKFzMdf4ODjI0zSTWrMbhsXMFCLKWbb7ppBZ"
            + "pkIuvYMkzwbyhao8+mvMxx9EIdiB/TiWWS8SWkvu410M72CDsHapxvIJSs/c"
            + "8xId9qjB/4V4sVEcba75wTjO8i7gXN1tf4f3hQsLPsdmdejhCke/BrcwXxGx"
            + "fTV4twa721/R/oaFz1P41gTeHeOXsVLjH9V4y/40hWxPIK0x8iazWFitmAm3"
            + "eN1+D9K4U7E9rGXStIylCTKqySysVfEuO1S2s/gHEe5xXud8H9Yfe1w/GGgD"
            + "AAA=");

    @SuppressWarnings("all") // Sample code
    private TestFile mApiCallTest12 = java(""
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
    private TestFile mApiCallTest12_class = base64gzip("bin/classes/test/pkg/ApiCallTest12.class", ""
            + "H4sIAAAAAAAAAH1T604TQRT+prT0QoHSQrlL8UK7aFnBuxgTAiEh2YKx2MT4"
            + "a9qOdXC72+zONvI+voD+qdHEB/ChjGe2yCoBJtlzznw753xnz3z76/f3nwC2"
            + "8CyJGENRCV+ZvY8dc6cnd7ltH9N+cyuJOEPuhPe5aXOnYx41T0RLMYy+kI5U"
            + "LxlGKkaDIb7rtkUGI0hnkcAow6QlHXEYdJvCO+ZNWzDkLbfF7Qb3pN6fgXH1"
            + "QfoMc9bl7Nv6BEVJ5BgWwi6U+KTMuuz2bLHHldh3vS5XGUwinUKBIXNKq1qr"
            + "VdttDc7odooMMxUr+oa68qTT2TYaGcxhIYl5hsWodlS1ftpturafxSKWGMY6"
            + "Qh04vuJOixpfqRjWNTnbmvyGJl9h2LqE/Npko5HGKm4lcfPv7AMlbTMcoMji"
            + "Nu4wxN7U9Uwvvg2Jy5q4wlC+kvjfDKORwjoNukYrhXsM49EMS5aVwgZBrVYp"
            + "QlO4z1AYHijXTku6kJKuUyZdvA+/hGHJuvq66FozdTfwWmJfhtL479I3dCJd"
            + "yevAUbIrDpy+9CWpZcdxXBXykGJWLe60PVe2TX4Om/Wg1/OE75P2NEeiz+2A"
            + "yucu8tNwSbjQK0YRCZZsknYmeUY+sT5A6kv4OkN2NATHMUY2OzxAfpw8w8R5"
            + "8mcqGSP/6gcmB5gim88PMB36b5gdYDkMv6I0wJoOjSi8ex6+zVcpydKgOQSj"
            + "PtaQJpsjxinizWMeBRLDNLU9i+e028MSjrAc9lkc9nLW5x75TfrXSTd4ENZ7"
            + "iEfkJwh5TM+Td2A+nv4BQoARTxQEAAA=");

    @SuppressWarnings("all") // Sample code
    private TestFile mApiCallTest2 = base64gzip("bin/classes/foo/bar/ApiCallTest.class", ""
            + "H4sIAAAAAAAAAJ1U7VLbRhQ9a4yFbVEIIQkfoaWUUNukVkISkmJKYwxJ3Mo4"
            + "kxCn7Z+OkNf2FlnSrASp+1RtpkNn+qMP0HfpK3RyVxaDx4HQqT2zn/eee+7d"
            + "e/T3v3/+BWANdQ0JhumQB6HhH7aNsi8qluPs015Dkm4styk90TQs3zfKdiiO"
            + "RdhjSG0KV4RbDCO5fIMhWfGaPIMRpHWMIsUwYQqX7x11D7jctw4czjBlerbl"
            + "NCwp1D4+TIYdETBcN8+LX6IwXR52vCbDw5x5SuSNaLZ5aFQ60nM9uueyZHqy"
            + "bby5ZxtNr2vs1GsqVOjJUr6RAcMVHVO4yqCTm8rAc7ctyTCby5vDyUVXJQ3X"
            + "GOaHMHel9OQzcnC4zOAGZjXMMMxdzErHHOYZbtJx3R043xf2oSmCkLucWDwa"
            + "YPE+xvKFniXFYUHHx/iE4WrAw33+c1gNXnKH22Fc3NwP+UYan+IzDUtEtWk5"
            + "x+LQOOiF3KbnMuq+mqpuy9OxjFsM47Xyd9Xaq9qPjbL5apeBVVX5PteRQ54h"
            + "TdxecN+TIcPKcO183xFUcypgVKe+XSmNVXyh4TbD0uXWOoowGLIHVki59hQv"
            + "hjv/Iczy9pkHhbyLexrWGBZPPdvS8jvCDoznZMzlzlGrtVyjzHXcxwMGrd7Y"
            + "fWGWvyeS5mUu1JJZ++xB6HU/1JaE7fQ7kWHmoh5lGOUqF6WC81qODEZs5xdq"
            + "Y/Mn69gyHMttGxXHCgLl2hLcIXlkWsJxnluSu/Q42a4V2p3TXbJLvCPwQfn1"
            + "/FMJTg/Dbha2CHmcknkqRdO0et4RwSy836dn12SfeekdSZs/EQpzckDFRQVP"
            + "4qu61LVRAB5o+IYE9oFiE21Vbg01hvv/Rx4k7wvvSBFUU6hfklb0vaJRo51B"
            + "M6N5tPAHxn6jRQIZGlPR4RVkadT7BjSP08zwESZi539ia6NwgsnXbG9qeiO5"
            + "eoLrr1eTJ7j5Fovro4n1VGJdI/CV31HYGHuLOxtpFWYkcixGAaYJ/AYBztBu"
            + "HpOk72vEdw5LWCCRLmIFefoXUYisW/2QMR21WsfDiKKBR/iSKGkk3g2UKEaW"
            + "/DfxFeU8R5hbuEWRFujL+DWtUoQ8gce00gg9pZSIMRoZyniANLbj1PsYFcIA"
            + "drAbp347rluC/TpUtOJA0RIxy4T6nFzquXau55NofIpnNE/R6i6q+PbxLEyq"
            + "2V4q/Q6UwMkB1gYAAA==");

    @SuppressWarnings("all") // Sample code
    private TestFile mAttribute2 = xml("res/layout/attribute2.xml", ""
            + "<ExitText\n"
            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:text=\"Hello\"\n"
            + "    android:editTextColor=\"?android:switchTextAppearance\"\n"
            + "    android:layout_width=\"wrap_content\"\n"
            + "    android:layout_height=\"wrap_content\" />\n"
            + "\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mIntermediate = java(""
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
    private TestFile mJava7API = java(""
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
    private TestFile mLayout = xml("res/layout/layout.xml", ""
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
    private TestFile mLayout2 = xml("res/layout-v11/layout.xml", ""
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
    private TestFile mLayout3 = xml("res/layout-v14/layout.xml", ""
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
    private TestFile mPadding_start = xml("res/layout/padding_start.xml", ""
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
    private TestFile mPadding_start2 = xml("res/layout-v17/padding_start.xml", ""
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
    private TestFile mRipple = xml("res/drawable/ripple.xml", ""
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
    private TestFile mRipple2 = xml("res/drawable-v21/ripple.xml", ""
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
    private TestFile mStyles2 = xml("res/values/styles2.xml", ""
            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
            + "         unless this is in a -v11 (or better) resource folder -->\n"
            + "        <item name=\"android:actionBarStyle\">...</item>\n"
            + "        <item name=\"android:textColor\">#999999</item>\n"
            + "    </style>\n"
            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mStyles2_class = xml("res/values-v9/styles2.xml", ""
            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
            + "         unless this is in a -v11 (or better) resource folder -->\n"
            + "        <item name=\"android:actionBarStyle\">...</item>\n"
            + "        <item name=\"android:textColor\">#999999</item>\n"
            + "    </style>\n"
            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mStyles2_class2 = xml("res/values-v11/styles2.xml", ""
            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
            + "         unless this is in a -v11 (or better) resource folder -->\n"
            + "        <item name=\"android:actionBarStyle\">...</item>\n"
            + "        <item name=\"android:textColor\">#999999</item>\n"
            + "    </style>\n"
            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mStyles2_class3 = xml("res/values-v14/styles2.xml", ""
            + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <style android:name=\"MyStyle\" parent=\"android:Theme.Light\">\n"
            + "    <!-- if the minSdk level is less then 11, then this should be a lint error, since android:actionBarStyle is since API 11,\n"
            + "         unless this is in a -v11 (or better) resource folder -->\n"
            + "        <item name=\"android:actionBarStyle\">...</item>\n"
            + "        <item name=\"android:textColor\">#999999</item>\n"
            + "    </style>\n"
            + "</resources>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mThemes = xml("res/values/themes.xml", ""
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
    private TestFile mThemes2 = xml("res/color/colors.xml", ""
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
    private TestFile mThemes3 = xml("res/values-v11/themes.xml", ""
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
    private TestFile mThemes4 = xml("res/color-v11/colors.xml", ""
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
    private TestFile mThemes5 = xml("res/values-v14/themes.xml", ""
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
    private TestFile mThemes6 = xml("res/color-v14/colors.xml", ""
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
    private TestFile mTryWithResources = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import java.io.BufferedReader;\n"
            + "import java.io.FileReader;\n"
            + "import java.io.IOException;\n"
            + "import java.lang.reflect.InvocationTargetException;\n"
            + "import java.util.List;\n"
            + "import java.util.Map;\n"
            + "import java.util.TreeMap;\n"
            + "\n"
            + "public class TryWithResources {\n"
            + "    public String testTryWithResources(String path) throws IOException {\n"
            + "        try (BufferedReader br = new BufferedReader(new FileReader(path))) {\n"
            + "            return br.readLine();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
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
    private TestFile mVector = xml("res/drawable/vector.xml", ""
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

    public static final String SUPPORT_JAR_PATH = "libs/support-annotations.jar";
    private TestFile mSupportJar = base64gzip(SUPPORT_JAR_PATH,
            SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP);
    private TestFile mSupportClasspath = classpath(SUPPORT_JAR_PATH);
}
