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

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class OnClickDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new OnClickDetector();
    }

    public void test() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/OnClickActivity.java:27: Error: On click handler wrong5(View) must be public [OnClick]\n"
                + "    void wrong5(View view) {\n"
                + "         ~~~~~~\n"
                + "src/test/pkg/OnClickActivity.java:31: Error: On click handler wrong6(View) should not be static [OnClick]\n"
                + "    public static void wrong6(View view) {\n"
                + "                       ~~~~~~\n"
                + "src/test/pkg/OnClickActivity.java:45: Error: On click handler wrong7(View) must be public [OnClick]\n"
                + "    void wrong7(View view) {\n"
                + "         ~~~~~~\n"
                + "res/layout/onclick.xml:10: Error: Corresponding method handler 'public void nonexistent(android.view.View)' not found [OnClick]\n"
                + "        android:onClick=\"nonexistent\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/onclick.xml:16: Error: Corresponding method handler 'public void wrong1(android.view.View)' not found [OnClick]\n"
                + "        android:onClick=\"wrong1\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/onclick.xml:22: Error: Corresponding method handler 'public void wrong2(android.view.View)' not found [OnClick]\n"
                + "        android:onClick=\"wrong2\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/onclick.xml:28: Error: Corresponding method handler 'public void wrong3(android.view.View)' not found [OnClick]\n"
                + "        android:onClick=\"wrong3\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/onclick.xml:34: Error: Corresponding method handler 'public void wrong4(android.view.View)' not found [OnClick]\n"
                + "        android:onClick=\"wrong4\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/onclick.xml:58: Error: Corresponding method handler 'public void simple_typo(android.view.View)' not found (did you mean void test.pkg.OnClickActivity#simple_tyop(android.view.View) ?) [OnClick]\n"
                + "        android:onClick=\"simple_typo\"\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "9 errors, 0 warnings\n",

            lintProject(
                classpath(),
                manifest().minSdk(10),
                xml("res/layout/onclick.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"nonexistent\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong1\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong2\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong3\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong4\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong5\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong6\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"ok\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"simple_typo\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"my\\u1234method\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong7\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"@string/ok\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "</LinearLayout>\n"),
                java(""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.Activity;\n"
                            + "import android.util.Log;\n"
                            + "import android.view.View;\n"
                            + "\n"
                            + "/** Test data for the OnClickDetector */\n"
                            + "public class OnClickActivity extends Activity {\n"
                            + "    // Wrong argument type 1\n"
                            + "    public void wrong1() {\n"
                            + "    }\n"
                            + "\n"
                            + "    // Wrong argument type 2\n"
                            + "    public void wrong2(int i) {\n"
                            + "    }\n"
                            + "\n"
                            + "    // Wrong argument type 3\n"
                            + "    public void wrong3(View view, int i) {\n"
                            + "    }\n"
                            + "\n"
                            + "    // Wrong return type\n"
                            + "    public int wrong4(View view) {\n"
                            + "        return 0;\n"
                            + "    }\n"
                            + "\n"
                            + "    // Wrong modifier (not public)\n"
                            + "    void wrong5(View view) {\n"
                            + "    }\n"
                            + "\n"
                            + "    // Wrong modifier (is static)\n"
                            + "    public static void wrong6(View view) {\n"
                            + "    }\n"
                            + "\n"
                            + "    public void ok(View view) {\n"
                            + "    }\n"
                            + "\n"
                            + "    // Ok: Unicode escapes\n"
                            + "    public void my\\u1234method(View view) {\n"
                            + "    }\n"
                            + "\n"
                            + "    // Typo\n"
                            + "    public void simple_tyop(View view) {\n"
                            + "    }\n"
                            + "\n"
                            + "    void wrong7(View view) {\n"
                            + "        Log.i(\"x\", \"wrong7: called\");\n"
                            + "    }\n"
                            + "}\n"),
                base64gzip("bin/classes/test/pkg/OnClickActivity.class", ""
                            + "H4sIAAAAAAAAAIWS3W4SQRTH/2ehfCstYKm1KrUqUJJurLYmYjRNE5Mmm3pR"
                            + "g5dmCxs6suxuloXKG/gw3piYaLzwAXwLX8R4ZtldEaGQzJw5H//fOTPLz9/f"
                            + "fwDYRyMJhbDhGQNPdXpd9bV1bIp276jtiZHwxknECSXd6ri26Ki646hhhpB4"
                            + "LizhvSDEavUWIX5sd4wMYkjnsIIEIa8Jyzgd9s8N941+bhqEgma3dbOlu0L6"
                            + "QTDuXYgBYVNbNEOTW126ttV9FB72WVU7kU1J8DoJ448J5ZoWTjsSxqXa4q3p"
                            + "l8alTyjOyYf6J4T1efp61OFgUUUrrDgkKHaPkO2Pf3380je8C7vD3kD0HdN4"
                            + "541tJ6x8msI2T/8hhR3C9UnoWYUfyDQ6GTxANYmHhNWw2dATpqrZ3RzWUCNU"
                            + "a9p7faSrpm511TPPFVa3+X9ETp45s4du23gl5GuXZh53T0qwDf6KkL8sn/jr"
                            + "8Z5kT2VLbFd2vyL1mQ8KMrwn/GCKi4HcpIDtNbZ8kUjc8H1es8LslJAiYR6r"
                            + "gfDAr58jzPvC9UkyEMrTGgr+oEWUAsRLXrF5iKKPqEySU4gbjKUIprAtYyOA"
                            + "HbKVLZXYpxlaeWogJaIpEe0mNpfdaWvBnSaING5FiIXvWfnnPaVQ2q3lvXeu"
                            + "7E24vRxRXYK4sxzRuBIB3I0QR1wjq9KFSuHeN9x/+xeV8RN7/KdUp3DpCJeO"
                            + "Jqr7mt0/knyD1QIFAAA=")
                ));
    }

    public void testOk() throws Exception {
        // No onClick attributes
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

                lintProject(xml("res/layout/accessibility.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                            + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                            + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                            + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                            + "    <Button android:text=\"Button\" android:id=\"@+id/button2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                            + "    <Button android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                            + "    <ImageButton android:importantForAccessibility=\"no\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                            + "</LinearLayout>\n")));
    }
}
