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
public class ViewTypeDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ViewTypeDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/WrongCastActivity.java:13: Error: Unexpected cast to ToggleButton: layout tag was Button [WrongViewCast]\n"
                + "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n"
                + "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

            lintProject(
                    mCasts,
                    mWrongCastActivity
                ));
    }

    public void test2() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/WrongCastActivity.java:13: Error: Unexpected cast to ToggleButton: layout tag was Button|RadioButton [WrongViewCast]\n"
                + "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n"
                + "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

            lintProject(
                    mCasts,
                    xml("res/layout/casts3.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <RadioButton\n"
                            + "        android:id=\"@+id/button\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <EditText\n"
                            + "        android:id=\"@+id/edittext\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"EditText\" />\n"
                            + "\n"
                            + "</LinearLayout>\n"),
                    mWrongCastActivity
                ));
    }

    public void test3() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            lintProject(
                    mCasts,
                    xml("res/layout/casts4.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <ToggleButton\n"
                            + "        android:id=\"@+id/button\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <EditText\n"
                            + "        android:id=\"@+id/edittext\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"EditText\" />\n"
                            + "\n"
                            + "</LinearLayout>\n"),
                    mWrongCastActivity
                ));
    }

    public void test27441() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            lintProject(
                mCasts2,
                java(""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.*;\n"
                            + "import android.view.*;\n"
                            + "import android.widget.*;\n"
                            + "\n"
                            + "public class WrongCastActivity2 extends Activity {\n"
                            + "    private TextView additionalButton;\n"
                            + "\n"
                            + "    private void configureAdditionalButton(View bodyView) {\n"
                            + "        this.additionalButton = (TextView) bodyView\n"
                            + "                .findViewById(R.id.additional);\n"
                            + "        Object x = (AdapterView<?>) bodyView.findViewById(R.id.reminder_lead);\n"
                            + "    }\n"
                            + "\n"
                            + "    public static final class R {\n"
                            + "        public static final class id {\n"
                            + "            public static final int additional = 0x7f0a0000;\n"
                            + "            public static final int reminder_lead = 0x7f0a0001;\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n")
            ));
    }

    public void testCheckable() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

            lintProject(
                mCasts2,
                java(""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.*;\n"
                            + "import android.view.*;\n"
                            + "import android.widget.*;\n"
                            + "\n"
                            + "public class WrongCastActivity3 extends Activity {\n"
                            + "    private void test() {\n"
                            + "        final Checkable check = (Checkable) findViewById(R.id.additional);\n"
                            + "    }\n"
                            + "\n"
                            + "    public static final class R {\n"
                            + "        public static final class id {\n"
                            + "            public static final int additional = 0x7f0a0000;\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n")
            ));
    }

    public void testIncremental() throws Exception {
        assertEquals(""
                + "src/test/pkg/WrongCastActivity.java:13: Error: Unexpected cast to ToggleButton: layout tag was Button [WrongViewCast]\n"
                + "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n"
                + "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProjectIncrementally(
                        "src/test/pkg/WrongCastActivity.java",
                        mCasts,
                        mWrongCastActivity
                ));
    }
    @SuppressWarnings("all") // Sample code
    private TestFile mCasts = xml("res/layout/casts.xml", ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:orientation=\"vertical\" >\n"
            + "\n"
            + "    <Button\n"
            + "        android:id=\"@+id/button\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"Button\" />\n"
            + "\n"
            + "    <EditText\n"
            + "        android:id=\"@+id/edittext\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"EditText\" />\n"
            + "\n"
            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mCasts2 = xml("res/layout/casts2.xml", ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- unit test from issue 27441 -->\n"
            + "<ScrollView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"wrap_content\" >\n"
            + "\n"
            + "    <RadioGroup\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:orientation=\"vertical\" >\n"
            + "\n"
            + "        <RadioButton\n"
            + "            android:id=\"@+id/additional\"\n"
            + "            android:layout_width=\"match_parent\"\n"
            + "            android:layout_height=\"wrap_content\" />\n"
            + "\n"
            + "        <Spinner\n"
            + "            android:id=\"@+id/reminder_lead\"\n"
            + "            android:layout_width=\"match_parent\"\n"
            + "            android:layout_height=\"wrap_content\" />\n"
            + "    </RadioGroup>\n"
            + "\n"
            + "</ScrollView>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mWrongCastActivity = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import android.app.Activity;\n"
            + "import android.os.Bundle;\n"
            + "import android.widget.*;\n"
            + "\n"
            + "public class WrongCastActivity extends Activity {\n"
            + "    @Override\n"
            + "    public void onCreate(Bundle savedInstanceState) {\n"
            + "        super.onCreate(savedInstanceState);\n"
            + "        setContentView(R.layout.casts);\n"
            + "        Button button = (Button) findViewById(R.id.button);\n"
            + "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n"
            + "        TextView textView = (TextView) findViewById(R.id.edittext);\n"
            + "    }\n"
            + "\n"
            + "    public static final class R {\n"
            + "        public static final class layout {\n"
            + "            public static final int casts = 0x7f0a0002;\n"
            + "        }\n"
            + "        public static final class id {\n"
            + "            public static final int button = 0x7f0a0000;\n"
            + "            public static final int edittext = 0x7f0a0001;\n"
            + "        }\n"
            + "    }\n"
            + "}\n");
}
