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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class ViewTypeDetectorTest : AbstractCheckTest() {

    private val casts = xml(
        "res/layout/casts.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    android:orientation=\"vertical\" >\n" +
            "\n" +
            "    <view class=\"Button\"\n" +
            "        android:id=\"@+id/button\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"Button\" />\n" +
            "\n" +
            "    <EditText\n" +
            "        android:id=\"@+id/edittext\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"EditText\" />\n" +
            "\n" +
            "    <view\n" + // missing class=
            "        android:id=\"@+id/view\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\" />\n" +
            "\n" +
            "</LinearLayout>\n"
    )

    private val casts2 = xml(
        "res/layout/casts2.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<!-- unit test from issue 27441 -->\n" +
            "<ScrollView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"wrap_content\" >\n" +
            "\n" +
            "    <RadioGroup\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:orientation=\"vertical\" >\n" +
            "\n" +
            "        <RadioButton\n" +
            "            android:id=\"@+id/additional\"\n" +
            "            android:layout_width=\"match_parent\"\n" +
            "            android:layout_height=\"wrap_content\" />\n" +
            "\n" +
            "        <Spinner\n" +
            "            android:id=\"@+id/reminder_lead\"\n" +
            "            android:layout_width=\"match_parent\"\n" +
            "            android:layout_height=\"wrap_content\" />\n" +
            "    </RadioGroup>\n" +
            "\n" +
            "</ScrollView>\n"
    )

    private val wrongCastActivity = java(
        "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import android.os.Bundle;\n" +
            "import android.widget.*;\n" +
            "\n" +
            "public class WrongCastActivity extends Activity {\n" +
            "    @Override\n" +
            "    public void onCreate(Bundle savedInstanceState) {\n" +
            "        super.onCreate(savedInstanceState);\n" +
            "        setContentView(R.layout.casts);\n" +
            "        Button button = (Button) findViewById(R.id.button);\n" +
            "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n" +
            "        TextView textView = (TextView) findViewById(R.id.edittext);\n" +
            "    }\n" +
            "}\n"
    )

    private val rClass = java(
        "" +
            "package test.pkg;\n" +
            "public final class R {\n" +
            "    public static final class layout {\n" +
            "        public static final int casts = 0x7f0a0002;\n" +
            "    }\n" +
            "    public static final class id {\n" +
            "        public static final int button = 0x7f0a0000;\n" +
            "        public static final int edittext = 0x7f0a0001;\n" +
            "    }\n" +
            "}\n"
    )

    override fun getDetector(): Detector {
        return ViewTypeDetector()
    }

    fun testBasic1() {
        val expected = (
            "" +
                "src/test/pkg/WrongCastActivity.java:13: Error: Unexpected cast to ToggleButton: layout tag was Button [WrongViewCast]\n" +
                "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n" +
                "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings\n"
            )
        lint().files(casts, wrongCastActivity, rClass).run().expect(expected)
    }

    fun testBasic2() {
        val expected = (
            "" +
                "src/test/pkg/WrongCastActivity.java:13: Error: Unexpected cast to ToggleButton: layout tag was Button|RadioButton [WrongViewCast]\n" +
                "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n" +
                "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings\n"
            )

        lint().files(
            casts,
            xml(
                "res/layout/casts3.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <RadioButton\n" +
                    "        android:id=\"@+id/button\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "    <EditText\n" +
                    "        android:id=\"@+id/edittext\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"EditText\" />\n" +
                    "\n" +
                    "</LinearLayout>\n"
            ),
            wrongCastActivity,
            rClass
        ).run().expect(expected)
    }

    fun testBasic3() {

        lint().files(
            casts,
            xml(
                "res/layout/casts4.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <ToggleButton\n" +
                    "        android:id=\"@+id/button\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "    <EditText\n" +
                    "        android:id=\"@+id/edittext\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"EditText\" />\n" +
                    "\n" +
                    "</LinearLayout>\n"
            ),
            wrongCastActivity,
            rClass
        ).run().expectClean()
    }

    fun test27441() {

        lint().files(
            casts2,
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.app.*;\n" +
                    "import android.view.*;\n" +
                    "import android.widget.*;\n" +
                    "\n" +
                    "public class WrongCastActivity2 extends Activity {\n" +
                    "    private TextView additionalButton;\n" +
                    "\n" +
                    "    private void configureAdditionalButton(View bodyView) {\n" +
                    "        this.additionalButton = (TextView) bodyView\n" +
                    "                .findViewById(R.id.additional);\n" +
                    "        Object x = (AdapterView<?>) bodyView.findViewById(R.id.reminder_lead);\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int additional = 0x7f0a0000;\n" +
                    "        public static final int reminder_lead = 0x7f0a0001;\n" +
                    "    }\n" +
                    "}\n"
            )
        ).run().expectClean()
    }

    fun testCheckable() {

        lint().files(
            casts2,
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.app.*;\n" +
                    "import android.view.*;\n" +
                    "import android.widget.*;\n" +
                    "\n" +
                    "public class WrongCastActivity3 extends Activity {\n" +
                    "    private void test() {\n" +
                    "        final Checkable check = (Checkable) findViewById(R.id.additional);\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int additional = 0x7f0a0000;\n" +
                    "    }\n" +
                    "}\n"
            )
        ).run().expectClean()
    }

    fun testIncremental() {
        val expected = (
            "" +
                "src/test/pkg/WrongCastActivity.java:13: Error: Unexpected cast to ToggleButton: layout tag was Button [WrongViewCast]\n" +
                "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n" +
                "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "    src/test/pkg/WrongCastActivity.java:13: Id bound to a Button in casts.xml\n" +
                "1 errors, 0 warnings\n"
            )

        lint().files(casts, wrongCastActivity, rClass)
            .incremental("src/test/pkg/WrongCastActivity.java")
            .run()
            .expect(expected)
            .expectFixDiffs(
                """
                Fix for src/test/pkg/WrongCastActivity.java line 13: Cast to Button:
                @@ -13 +13
                -         ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);
                +         ToggleButton toggleButton = (Button) findViewById(R.id.button);
                """
            )
    }

    fun testQuickFix() {
        lint().files(
            casts,
            rClass,
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.app.Activity;\n" +
                    "import android.os.Bundle;\n" +
                    "import android.widget.ToggleButton;\n" +
                    "\n" +
                    "public class WrongCastActivity extends Activity {\n" +
                    "    @Override\n" +
                    "    public void onCreate(Bundle savedInstanceState) {\n" +
                    "        super.onCreate(savedInstanceState);\n" +
                    "        setContentView(R.layout.casts);\n" +
                    "        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);\n" +
                    "    }\n" +
                    "}\n"
            )
        )
            .incremental("src/test/pkg/WrongCastActivity.java")
            .run()
            .expect(
                """
                src/test/pkg/WrongCastActivity.java:12: Error: Unexpected cast to ToggleButton: layout tag was Button [WrongViewCast]
                        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);
                                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    src/test/pkg/WrongCastActivity.java:12: Id bound to a Button in casts.xml
                1 errors, 0 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for src/test/pkg/WrongCastActivity.java line 12: Cast to Button:
                @@ -12 +12
                -         ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);
                +         ToggleButton toggleButton = (android.widget.Button) findViewById(R.id.button);
                """
            )
    }

    fun test34968488() {
        // Regression test for 34968488:
        // Casting to ProgressBar is valid for a SeekBar: it's an ancestor class

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.app.Activity;\n" +
                    "import android.widget.ProgressBar;\n" +
                    "\n" +
                    "public class CastTest extends Activity {\n" +
                    "    private ProgressBar progressBar;\n" +
                    "\n" +
                    "    private void test() {\n" +
                    "        progressBar = (ProgressBar) findViewById(R.id.seekBar);\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int seekBar = 0x7f0a0000;\n" +
                    "    }\n" +
                    "}\n"
            ),
            xml(
                "res/layout/my_layout.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:orientation=\"vertical\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\">\n" +
                    "\n" +
                    "    <SeekBar\n" +
                    "        android:id=\"@+id/seekBar\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\" />\n" +
                    "\n" +
                    "</LinearLayout>"
            )
        ).run().expectClean()
    }

    fun testImplicitCast() {

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.widget.CheckBox;\n" +
                    "import android.widget.TextView;\n" +
                    "import android.widget.ImageView;\n" +
                    "\n" +
                    "public class ImplicitCastTest extends MyActivityStub {\n" +
                    "    public void test() {\n" +
                    "        TextView textView1 = findViewById(R.id.textView);    // OK\n" +
                    "        TextView textView2 = findViewById(R.id.checkBox);    // OK\n" +
                    "        TextView textView3 = findViewById(R.id.imageView);   // ERROR\n" +
                    "        CheckBox checkBox1 = findViewById(R.id.textView);    // ERROR\n" +
                    "        CheckBox checkBox2 = findViewById(R.id.checkBox);    // OK\n" +
                    "        ImageView imageView1 = findViewById(R.id.imageView); // OK\n" +
                    "        ImageView imageView2 = findViewById(R.id.textView);  // ERROR\n" +
                    "\n" +
                    "        textView1 = findViewById(R.id.textView);  // OK\n" +
                    "        findViewById(R.id.checkBox);              // OK\n" +
                    "        textView3 = findViewById(R.id.imageView); // ERROR\n" +
                    "    }\n" +
                    "    public void testRequireById() {\n" +
                    "        TextView textView1 = requireViewById(R.id.textView);    // OK\n" +
                    "        TextView textView2 = requireViewById(R.id.checkBox);    // OK\n" +
                    "        TextView textView3 = requireViewById(R.id.imageView);   // ERROR\n" +
                    "        CheckBox checkBox1 = requireViewById(R.id.textView);    // ERROR\n" +
                    "        CheckBox checkBox2 = requireViewById(R.id.checkBox);    // OK\n" +
                    "        ImageView imageView1 = requireViewById(R.id.imageView); // OK\n" +
                    "        ImageView imageView2 = requireViewById(R.id.textView);  // ERROR\n" +
                    "    }\n" +
                    "}\n"
            ),
            xml(
                "res/layout/my_layout.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:orientation=\"vertical\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\">\n" +
                    "\n" +
                    "    <TextView\n" +
                    "        android:id=\"@+id/textView\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"TextView\" />\n" +
                    "\n" +
                    "    <CheckBox\n" +
                    "        android:id=\"@+id/checkBox\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"CheckBox\" />\n" +
                    "\n" +
                    "    <ImageView\n" +
                    "        android:id=\"@+id/imageView\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\" />\n" +
                    "\n" +
                    "</LinearLayout>"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int textView = 0x7f0a0000;\n" +
                    "        public static final int checkBox = 0x7f0a0001;\n" +
                    "        public static final int imageView = 0x7f0a0002;\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.view.View;\n" +
                    "\n" +
                    "public class MyActivityStub {\n" +
                    "    public <T extends View> T findViewById(int id) {\n" +
                    "        throw new RuntimeException(\"Stub!\");\n" +
                    "    }\n" +
                    "    public final <T extends View> T requireViewById(int id) {\n" +
                    "        throw new RuntimeException(\"Stub!\");\n" +
                    "    }\n" +
                    "}\n"
            )
        )
            .incremental("src/test/pkg/ImplicitCastTest.java")
            .run()
            .expect(
                "" +
                    "src/test/pkg/ImplicitCastTest.java:11: Error: Unexpected implicit cast to TextView: layout tag was ImageView [WrongViewCast]\n" +
                    "        TextView textView3 = findViewById(R.id.imageView);   // ERROR\n" +
                    "        ~~~~~~~~\n" +
                    "    src/test/pkg/ImplicitCastTest.java:11: Id bound to an ImageView in my_layout.xml\n" +
                    "src/test/pkg/ImplicitCastTest.java:12: Error: Unexpected implicit cast to CheckBox: layout tag was TextView [WrongViewCast]\n" +
                    "        CheckBox checkBox1 = findViewById(R.id.textView);    // ERROR\n" +
                    "        ~~~~~~~~\n" +
                    "    src/test/pkg/ImplicitCastTest.java:12: Id bound to a TextView in my_layout.xml\n" +
                    "src/test/pkg/ImplicitCastTest.java:15: Error: Unexpected implicit cast to ImageView: layout tag was TextView [WrongViewCast]\n" +
                    "        ImageView imageView2 = findViewById(R.id.textView);  // ERROR\n" +
                    "        ~~~~~~~~~\n" +
                    "    src/test/pkg/ImplicitCastTest.java:15: Id bound to a TextView in my_layout.xml\n" +
                    "src/test/pkg/ImplicitCastTest.java:19: Error: Unexpected implicit cast to TextView: layout tag was ImageView [WrongViewCast]\n" +
                    "        textView3 = findViewById(R.id.imageView); // ERROR\n" +
                    "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "src/test/pkg/ImplicitCastTest.java:24: Error: Unexpected implicit cast to TextView: layout tag was ImageView [WrongViewCast]\n" +
                    "        TextView textView3 = requireViewById(R.id.imageView);   // ERROR\n" +
                    "        ~~~~~~~~\n" +
                    "    src/test/pkg/ImplicitCastTest.java:24: Id bound to an ImageView in my_layout.xml\n" +
                    "src/test/pkg/ImplicitCastTest.java:25: Error: Unexpected implicit cast to CheckBox: layout tag was TextView [WrongViewCast]\n" +
                    "        CheckBox checkBox1 = requireViewById(R.id.textView);    // ERROR\n" +
                    "        ~~~~~~~~\n" +
                    "    src/test/pkg/ImplicitCastTest.java:25: Id bound to a TextView in my_layout.xml\n" +
                    "src/test/pkg/ImplicitCastTest.java:28: Error: Unexpected implicit cast to ImageView: layout tag was TextView [WrongViewCast]\n" +
                    "        ImageView imageView2 = requireViewById(R.id.textView);  // ERROR\n" +
                    "        ~~~~~~~~~\n" +
                    "    src/test/pkg/ImplicitCastTest.java:28: Id bound to a TextView in my_layout.xml\n" +
                    "7 errors, 0 warnings"
            )
    }

    fun testCastNeeded() {

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.view.View;\n" +
                    "\n" +
                    "import com.example.tnorbye.myapplication.R;\n" +
                    "\n" +
                    "public class ImplicitCastTest2 extends MyActivityStub {\n" +
                    "    public void test() {\n" +
                    "        checkNotNull1(findViewById(R.id.textView)).setAlpha(0.5f); // WARN\n" +
                    "        checkNotNull2(findViewById(R.id.textView)).setAlpha(0.5f); // OK\n" +
                    "        checkNotNull3(findViewById(R.id.textView)).setAlpha(0.5f); // OK\n" +
                    "        checkNotNull1(findViewById(R.id.textView)); // OK\n" +
                    "        checkNotNull2(findViewById(R.id.textView)); // OK\n" +
                    "        checkNotNull3(findViewById(R.id.textView)); // OK\n" +
                    "        checkNotNull1((View)findViewById(R.id.textView)); // OK\n" +
                    "        checkNotNull2((View)findViewById(R.id.textView)); // OK\n" +
                    "        checkNotNull3((View)findViewById(R.id.textView)); // OK\n" +
                    "        View view1 = checkNotNull1(findViewById(R.id.textView)); // OK\n" +
                    "        View view2 = checkNotNull2(findViewById(R.id.textView)); // OK\n" +
                    "        View view3 = checkNotNull3(findViewById(R.id.textView)); // OK\n" +
                    "        findViewById(R.id.textView); // OK\n" +
                    "    }\n" +
                    "\n" +
                    "    public static <T> T checkNotNull1(T reference) {\n" +
                    "        if(reference == null) {\n" +
                    "            throw new NullPointerException();\n" +
                    "        } else {\n" +
                    "            return reference;\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    public static <T extends View> T checkNotNull2(T reference) {\n" +
                    "        if(reference == null) {\n" +
                    "            throw new NullPointerException();\n" +
                    "        } else {\n" +
                    "            return reference;\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    public static View checkNotNull3(View reference) {\n" +
                    "        if(reference == null) {\n" +
                    "            throw new NullPointerException();\n" +
                    "        } else {\n" +
                    "            return reference;\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int textView = 0x7f0a0000;\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.view.View;\n" +
                    "\n" +
                    "public class MyActivityStub {\n" +
                    "    public <T extends View> T findViewById(int id) {\n" +
                    "        throw new RuntimeException(\"Stub!\");\n" +
                    "    }\n" +
                    "}\n"
            ),
            gradle(
                "" +
                    "android {\n" +
                    "    compileOptions {\n" +
                    "        sourceCompatibility JavaVersion.VERSION_1_8\n" +
                    "        targetCompatibility JavaVersion.VERSION_1_8\n" +
                    "    }\n" +
                    "}"
            )
        ).run().expect(
            "" +
                "src/main/java/test/pkg/ImplicitCastTest2.java:9: Warning: Add explicit cast here; won't compile with Java language level 1.8 without it [FindViewByIdCast]\n" +
                "        checkNotNull1(findViewById(R.id.textView)).setAlpha(0.5f); // WARN\n" +
                "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings\n"
        )
            .expectFixDiffs(
                "" +
                    "Fix for src/main/java/test/pkg/ImplicitCastTest2.java line 9: Add cast:\n" +
                    "@@ -9 +9\n" +
                    "-         checkNotNull1(findViewById(R.id.textView)).setAlpha(0.5f); // WARN\n" +
                    "+         checkNotNull1((View)findViewById(R.id.textView)).setAlpha(0.5f); // WARN\n"
            )
    }

    fun testCastNeeded2() {
        // Regression test for https://issuetracker.google.com/112024656

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.content.Context;\n" +
                    "import android.widget.FrameLayout;\n" +
                    "import android.widget.LinearLayout;\n" +
                    "\n" +
                    "import java.util.ArrayList;\n" +
                    "import java.util.List;\n" +
                    "\n" +
                    "public class FindViewByIdTest extends LinearLayout {\n" +
                    "    public FindViewByIdTest(Context context) {\n" +
                    "        super(context);\n" +
                    "    }\n" +
                    "\n" +
                    "    List<FrameLayout> views = new ArrayList<>();\n" +
                    "\n" +
                    "    void bar(int id) {\n" +
                    "        views.add(findViewById(id));\n" +
                    "    }\n" +
                    "}\n"
            ),
            gradle(
                "" +
                    "android {\n" +
                    "    compileOptions {\n" +
                    "        sourceCompatibility JavaVersion.VERSION_1_8\n" +
                    "        targetCompatibility JavaVersion.VERSION_1_8\n" +
                    "    }\n" +
                    "}"
            )
        ).run().expectClean()
    }

    fun testFindViewByIdNotCastDirectly() {
        // Regression test for issue 68280786

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.content.Context;\n" +
                    "import android.widget.ImageButton;\n" +
                    "import android.widget.RelativeLayout;\n" +
                    "\n" +
                    "public class A extends RelativeLayout {\n" +
                    "\n" +
                    "    public A(Context context) {\n" +
                    "        super(context);\n" +
                    "        LayoutParams lp1 = (LayoutParams) findViewById(R.id.some_view).getLayoutParams();\n" +
                    "        LayoutParams lp2 = (LayoutParams) this.findViewById(R.id.some_view).getLayoutParams();\n" +
                    "    }\n" +
                    "}"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int some_view = 0x7f0a0000;\n" +
                    "    }\n" +
                    "}\n"
            ),
            xml(
                "res/layout/test.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\">\n" +
                    "\n" +
                    "    <TextView\n" +
                    "        android:id=\"@+id/some_view\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Hello World!\" />\n" +
                    "\n" +
                    "</LinearLayout>"
            )
        ).run().expectClean()
    }

    fun test62445968() {
        // Regression test for https://issuetracker.google.com/62445968

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.app.Activity;\n" +
                    "import android.os.Bundle;\n" +
                    "import android.view.View;\n" +
                    "\n" +
                    "public class FindViewByIdTest extends Activity {\n" +
                    "    private static void test() {\n" +
                    // "        View localVar = findViewById(R.id.some_view);\n" +
                    // "        Integer integer = doSomethingWithView(localVar);\n" +
                    "\n" +
                    "        doSomethingWithView(findViewById(R.id.some_view));\n" +
                    "    }\n" +
                    "\n" +
                    "    private static Integer doSomethingWithView(View view) {\n" +
                    "        return null;\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int some_view = 0x7f0a0000;\n" +
                    "    }\n" +
                    "}\n"
            ),
            xml(
                "res/layout/test.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\">\n" +
                    "\n" +
                    "    <TextView\n" +
                    "        android:id=\"@+id/some_view\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Hello World!\" />\n" +
                    "\n" +
                    "</LinearLayout>"
            )
        ).run().expectClean()
    }

    fun testFindFragmentByIdBatch() {

        lint().files(
            xml(
                "res/layout/my_layout.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                    "    <fragment\n" +
                    "        android:layout_width=\"match_parent\"\n" +
                    "        android:layout_height=\"match_parent\"\n" +
                    "        android:id=\"@+id/my_fragment\"\n" +
                    "        class=\"test.pkg.FindFragmentTest\$Fragment1\" />\n" +
                    "    <fragment\n" +
                    "        android:layout_width=\"match_parent\"\n" +
                    "        android:layout_height=\"match_parent\"\n" +
                    "        android:tag=\"my_tag\"\n" +
                    "        class=\"test.pkg.FindFragmentTest\$Fragment2\" />\n" +
                    "    <fragment\n" +
                    "        android:layout_width=\"match_parent\"\n" +
                    "        android:layout_height=\"match_parent\"\n" +
                    "        android:tag=\"other_tag\"\n" +
                    "        class=\"test.pkg.FindFragmentTest\$Fragment3\" />\n" +
                    "    <View\n" +
                    "        android:id=\"@+id/my_other_id\"\n" +
                    "        android:layout_width=\"match_parent\"\n" +
                    "        android:layout_height=\"match_parent\" />\n" +
                    "</merge>"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.app.Fragment;\n" +
                    "import android.app.FragmentManager;\n" +
                    "\n" +
                    "public class FindFragmentTest {\n" +
                    "    public static class Fragment1 extends Fragment {}\n" +
                    "    public static class Fragment2 extends Fragment {}\n" +
                    "    public static class Fragment3 extends Fragment {}\n" +
                    "\n" +
                    "    public void test(FragmentManager manager) {\n" +
                    "        // We don't currently flag these; there are a lot of false positives:\n" +
                    "        Fragment1 fragment1a = (Fragment1) manager.findFragmentById(R.id.my_fragment); // OK\n" +
                    "        Fragment2 fragment2a = (Fragment2) manager.findFragmentById(R.id.my_other_id); // ERROR\n" +
                    "        Fragment1 fragment1b = (Fragment1) manager.findFragmentById(R.id.my_fragment); // OK\n" +
                    "        Fragment2 fragment2b = (Fragment2) manager.findFragmentById(R.id.my_other_id); // ERROR\n" +
                    "\n" +
                    "        Fragment2 fragment2c = (Fragment2) manager.findFragmentByTag(\"my_tag\"); // OK\n" +
                    "        Fragment1 fragment1c = (Fragment1) manager.findFragmentByTag(\"other_tag\"); // ERROR\n" +
                    "\n" +
                    "        Fragment2 fragment2d = (Fragment2) findFragmentByTag(\"my_tag\"); // OK\n" +
                    "        Fragment1 fragment1d = (Fragment1) findFragmentByTag(\"other_tag\"); // ERROR\n" +
                    "    }\n" +
                    "    public Fragment findFragmentById(int id) { return null; }\n" +
                    "    public Fragment findFragmentByTag(String tag) { return null; }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "public final class R {\n" +
                    "    public static final class id {\n" +
                    "        public static final int my_fragment = 0x7f0a0000;\n" +
                    "        public static final int my_other_id = 0x7f0a0001;\n" +
                    "    }\n" +
                    "}\n"
            )
        ).run().expect(
            "" +
                "src/test/pkg/FindFragmentTest.java:19: Error: Unexpected cast to Fragment1: fragment tag referenced test.pkg.FindFragmentTest.Fragment3 [WrongViewCast]\n" +
                "        Fragment1 fragment1c = (Fragment1) manager.findFragmentByTag(\"other_tag\"); // ERROR\n" +
                "                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/FindFragmentTest.java:22: Error: Unexpected cast to Fragment1: fragment tag referenced test.pkg.FindFragmentTest.Fragment3 [WrongViewCast]\n" +
                "        Fragment1 fragment1d = (Fragment1) findFragmentByTag(\"other_tag\"); // ERROR\n" +
                "                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "2 errors, 0 warnings"
        )
    }
}
