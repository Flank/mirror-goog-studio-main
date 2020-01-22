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
package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.AnnotationDetectorTest.SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Detector;
import java.io.IOException;
import java.io.StringReader;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class LayoutInflationDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new LayoutInflationDetector();
    }

    @SuppressWarnings("all")
    private TestFile mLayoutInflationTest =
            java(
                    "src/test/pkg/LayoutInflationTest.java",
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.content.Context;\n"
                            + "import android.view.LayoutInflater;\n"
                            + "import android.view.View;\n"
                            + "import android.view.ViewGroup;\n"
                            + "import android.widget.BaseAdapter;\n"
                            + "import android.annotation.SuppressLint;\n"
                            + "import java.util.ArrayList;\n"
                            + "\n"
                            + "public abstract class LayoutInflationTest extends BaseAdapter {\n"
                            + "    public View getView(int position, View convertView, ViewGroup parent) {\n"
                            + "        convertView = mInflater.inflate(R.layout.your_layout, null);\n"
                            + "        convertView = mInflater.inflate(R.layout.your_layout, null, true);\n"
                            + "        //convertView = mInflater.inflate(R.layout.your_layout);\n"
                            + "        convertView = mInflater.inflate(R.layout.your_layout, parent);\n"
                            + "        convertView = WeirdInflater.inflate(convertView, null);\n"
                            + "\n"
                            + "        return convertView;\n"
                            + "    }\n"
                            + "\n"
                            // Suppressed checks
                            + "    @SuppressLint(\"InflateParams\")\n"
                            + "    public View getView2(int position, View convertView, ViewGroup parent) {\n"
                            + "        convertView = mInflater.inflate(R.layout.your_layout, null);\n"
                            + "        convertView = mInflater.inflate(R.layout.your_layout, null, true);\n"
                            + "        convertView = mInflater.inflate(R.layout.your_layout, parent);\n"
                            + "        convertView = WeirdInflater.inflate(convertView, null);\n"
                            + "\n"
                            + "        return convertView;\n"
                            + "    }\n"
                            // Test/Stub Setup
                            + "    private LayoutInflater mInflater;\n"
                            + "    private static class R {\n"
                            + "        private static class layout {\n"
                            + "            public static final int your_layout = 1;\n"
                            + "        }\n"
                            + "    }\n"
                            + "    private static class WeirdInflater {\n"
                            + "        public static View inflate(View view, Object params) { return null; }\n"
                            + "    }\n"
                            + "}\n");

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return false;
    }

    private static XmlPullParser createXmlPullParser(@NonNull String xml)
            throws XmlPullParserException {
        // Instantiate an XML pull parser based on the contents of the file.
        XmlPullParser parser;
        parser = new KXmlParser(); // Parser for regular text XML.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader(xml));
        return parser;
    }

    public void testFull() {
        String expected =
                ""
                        + "src/test/pkg/LayoutInflationTest.java:13: Warning: Avoid passing null as the view root (needed to resolve layout parameters on the inflated layout's root element) [InflateParams]\n"
                        + "        convertView = mInflater.inflate(R.layout.your_layout, null);\n"
                        + "                                                              ~~~~\n"
                        + "src/test/pkg/LayoutInflationTest.java:14: Warning: Avoid passing null as the view root (needed to resolve layout parameters on the inflated layout's root element) [InflateParams]\n"
                        + "        convertView = mInflater.inflate(R.layout.your_layout, null, true);\n"
                        + "                                                              ~~~~\n"
                        + "0 errors, 2 warnings\n";
        lint().files(
                        mLayoutInflationTest,
                        xml(
                                "res/layout/your_layout.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:id=\"@+id/LinearLayout1\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" />\n"),
                        xml(
                                "res/layout-port/your_layout.xml",
                                ""
                                        + "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:id=\"@id/text1\"\n"
                                        + "    style=\"?android:attr/listSeparatorTextViewStyle\" />\n"))
                .run()
                .expect(expected);
    }

    public void testIncremental() {
        String expected =
                ""
                        + "src/test/pkg/LayoutInflationTest.java:13: Warning: Avoid passing null as the view root (needed to resolve layout parameters on the inflated layout's root element) [InflateParams]\n"
                        + "        convertView = mInflater.inflate(R.layout.your_layout, null);\n"
                        + "                                                              ~~~~\n"
                        + "src/test/pkg/LayoutInflationTest.java:14: Warning: Avoid passing null as the view root (needed to resolve layout parameters on the inflated layout's root element) [InflateParams]\n"
                        + "        convertView = mInflater.inflate(R.layout.your_layout, null, true);\n"
                        + "                                                              ~~~~\n"
                        + "0 errors, 2 warnings\n";
        lint().files(
                        mLayoutInflationTest,
                        xml(
                                "res/layout/your_layout.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:id=\"@+id/LinearLayout1\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" />\n"),
                        xml(
                                "res/layout-port/your_layout.xml",
                                ""
                                        + "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:id=\"@id/text1\"\n"
                                        + "    style=\"?android:attr/listSeparatorTextViewStyle\" />\n"))
                .incremental(mLayoutInflationTest.targetRelativePath)
                .run()
                .expect(expected);
    }

    public void testNoLayoutParams() {
        lint().files(
                        mLayoutInflationTest,
                        xml(
                                "res/layout/your_layout.xml",
                                ""
                                        + "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:id=\"@id/text1\"\n"
                                        + "    style=\"?android:attr/listSeparatorTextViewStyle\" />\n"))
                .run()
                .expectClean();
    }

    public void testHasLayoutParams() throws IOException, XmlPullParserException {
        assertFalse(LayoutInflationDetector.hasLayoutParams(createXmlPullParser("")));
        assertFalse(
                LayoutInflationDetector.hasLayoutParams(createXmlPullParser("<LinearLayout/>")));
        assertFalse(
                LayoutInflationDetector.hasLayoutParams(
                        createXmlPullParser(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        layout=\"@layout/layoutcycle1\" />\n"
                                        + "\n"
                                        + "</LinearLayout>")));

        assertTrue(
                LayoutInflationDetector.hasLayoutParams(
                        createXmlPullParser(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        layout=\"@layout/layoutcycle1\" />\n"
                                        + "\n"
                                        + "</LinearLayout>")));
    }

    public void testAlertDialog() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.AlertDialog;\n"
                                        + "import android.app.Dialog;\n"
                                        + "import android.app.DialogFragment;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.view.LayoutInflater;\n"
                                        + "import android.view.View;\n"
                                        + "import android.support.annotation.NonNull;\n"
                                        + "\n"
                                        + "public class AlertDialogTestJava {\n"
                                        + "    public static class MyFragment extends DialogFragment {\n"
                                        + "        @NonNull\n"
                                        + "        @Override\n"
                                        + "        public Dialog onCreateDialog(Bundle savedInstanceState) {\n"
                                        + "            Activity activity = getActivity();\n"
                                        + "            if (activity != null) {\n"
                                        + "                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity);\n"
                                        + "                alertBuilder.setCustomTitle(activity.getLayoutInflater().inflate(R.layout.title, null));\n"
                                        + "                View view = LayoutInflater.from(activity).inflate(R.layout.the_layout, null);\n"
                                        + "                alertBuilder.setView(view);\n"
                                        + "                return alertBuilder.create();\n"
                                        + "            }\n"
                                        + "            return super.onCreateDialog(savedInstanceState);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    \n"
                                        + "    public AlertDialog test2(Activity activity) {\n"
                                        + "        AlertDialog.Builder builder = new AlertDialog.Builder(activity);\n"
                                        + "        LayoutInflater inflater = activity.getLayoutInflater();\n"
                                        + "        View rootView = inflater.inflate(R.layout.the_layout, null, false);\n"
                                        + "        builder.setView(rootView);\n"
                                        + "        builder.setTitle(\"Alert\");\n"
                                        + "        return builder.create();\n"
                                        + "    }\n"
                                        + "}"),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.app.Activity\n"
                                        + "import android.app.AlertDialog\n"
                                        + "import android.app.Dialog\n"
                                        + "import android.app.DialogFragment\n"
                                        + "import android.os.Bundle\n"
                                        + "import android.view.LayoutInflater\n"
                                        + "class AlertDialogTestKotlin {\n"
                                        + "    class MyFragment : DialogFragment() {\n"
                                        + "        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {\n"
                                        + "            val activity = activity\n"
                                        + "            if (activity != null) {\n"
                                        + "                val alertBuilder =\n"
                                        + "                    AlertDialog.Builder(activity)\n"
                                        + "                alertBuilder.setCustomTitle(activity.layoutInflater.inflate(R.layout.title, null))\n"
                                        + "                val view = LayoutInflater.from(activity).inflate(R.layout.the_layout, null)\n"
                                        + "                alertBuilder.setView(view)\n"
                                        + "                return alertBuilder.create()\n"
                                        + "            }\n"
                                        + "            return super.onCreateDialog(savedInstanceState)\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test2(activity: Activity): Dialog {\n"
                                        + "        val builder = AlertDialog.Builder(activity)\n"
                                        + "        val inflater = activity.layoutInflater\n"
                                        + "        val rootView = inflater.inflate(R.layout.the_laoyut, null, false)\n"
                                        + "        builder.setView(rootView)\n"
                                        + "        builder.setTitle(\"Alert\")\n"
                                        + "        return builder.create()\n"
                                        + "    }\n"
                                        + "}"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.Dialog;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.view.LayoutInflater;\n"
                                        + "import android.view.View;\n"
                                        + "import android.support.annotation.NonNull;\n"
                                        + "import androidx.appcompat.app.AlertDialog;\n"
                                        + "import androidx.fragment.app.DialogFragment;\n"
                                        + "\n"
                                        + "public class AlertDialogTestAndroidX {\n"
                                        + "    public static class MyFragment extends DialogFragment {\n"
                                        + "        @NonNull\n"
                                        + "        @Override\n"
                                        + "        public Dialog onCreateDialog(Bundle savedInstanceState) {\n"
                                        + "            Activity activity = getActivity();\n"
                                        + "            if (activity != null) {\n"
                                        + "                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity);\n"
                                        + "                alertBuilder.setCustomTitle(activity.getLayoutInflater().inflate(R.layout.title, null));\n"
                                        + "                View view = LayoutInflater.from(activity).inflate(R.layout.the_layout, null);\n"
                                        + "                alertBuilder.setView(view);\n"
                                        + "                return alertBuilder.create();\n"
                                        + "            }\n"
                                        + "            return super.onCreateDialog(savedInstanceState);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    \n"
                                        + "    public AlertDialog test2(Activity activity) {\n"
                                        + "        AlertDialog.Builder builder = new AlertDialog.Builder(activity);\n"
                                        + "        LayoutInflater inflater = activity.getLayoutInflater();\n"
                                        + "        View rootView = inflater.inflate(R.layout.the_layout, null, false);\n"
                                        + "        builder.setView(rootView);\n"
                                        + "        builder.setTitle(\"Alert\");\n"
                                        + "        return builder.create();\n"
                                        + "    }\n"
                                        + "}"),
                        java(
                                // AndroidX Stub
                                ""
                                        + "package androidx.appcompat.app;\n"
                                        + "\n"
                                        + "import android.app.Dialog;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.DialogInterface;\n"
                                        + "import android.view.View;\n"
                                        + "\n"
                                        + "public class AlertDialog extends Dialog implements DialogInterface {\n"
                                        + "    public AlertDialog(Context context) {\n"
                                        + "        super(context);\n"
                                        + "    }\n"
                                        + "    public static class Builder {\n"
                                        + "        public Builder setCustomTitle(View customTitleView) {\n"
                                        + "            return this;\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        public Builder setView(int layoutResId) {\n"
                                        + "            return this;\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        public Builder setView(View view) {\n"
                                        + "            return this;\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"),
                        // Regression test for http://b/144314412
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.app.AlertDialog\n"
                                        + "import android.app.Dialog\n"
                                        + "import android.content.Context\n"
                                        + "import android.view.LayoutInflater\n"
                                        + "import android.view.View\n"
                                        + "\n"
                                        + "class AlertTest2(\n"
                                        + "    private val alertDialogBuilderFactory: AlertDialogBuilderFactory\n"
                                        + ") {\n"
                                        + "    fun createDialog(context: Context): Dialog {\n"
                                        + "        val view =\n"
                                        + "            LayoutInflater.from(context)\n"
                                        + "                .inflate(R.layout.the_layout, /* root= */ null)\n"
                                        + "\n"
                                        + "        return alertDialogBuilderFactory.create()\n"
                                        + "            .setView(view)\n"
                                        + "            .create()\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "\n"
                                        + "class AlertDialogBuilderFactory {\n"
                                        + "    // Usually named something with AlertBuilder but\n"
                                        + "    // here we're not to test the return-type checks\n"
                                        + "    fun create(): MyBuilder {\n"
                                        + "        return MyBuilder()\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "\n"
                                        + "class MyBuilder {\n"
                                        + "    fun create(): AlertDialog {\n"
                                        + "        return AlertDialog(null)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun setView(view: View?): MyBuilder {\n"
                                        + "        return this\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                // AndroidX Stub
                                ""
                                        + "package androidx.fragment.app;\n"
                                        + "\n"
                                        + "public class DialogFragment extends android.app.Fragment {\n"
                                        + "}\n"),
                        xml(
                                "res/layout/the_layout.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:id=\"@+id/LinearLayout1\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" />\n"),
                        java(
                                ""
                                        + ""
                                        + "package test.pkg;\n"
                                        + "public class R {\n"
                                        + "    public static class layout {\n"
                                        + "        public static final int the_layout=0x7f050000;\n"
                                        + "        public static final int title=0x7f050001;\n"
                                        + "    }\n"
                                        + "}"),
                        base64gzip(SUPPORT_JAR_PATH, SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP))
                .run()
                .expectClean();
    }
}
