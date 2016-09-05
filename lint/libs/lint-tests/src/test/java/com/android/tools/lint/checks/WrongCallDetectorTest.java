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

import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;

@SuppressWarnings("javadoc")
public class WrongCallDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new WrongCallDetector();
    }

    public void test() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/LayoutTest.java:23: Error: Suspicious method call; should probably call \"layout\" rather than \"onLayout\" [WrongCall]\n"
                + "  child.onLayout(changed, left, top, right, bottom); // Not OK\n"
                + "        ~~~~~~~~\n"
                + "src/test/pkg/LayoutTest.java:25: Error: Suspicious method call; should probably call \"measure\" rather than \"onMeasure\" [WrongCall]\n"
                + "  super.onMeasure(0, 0); // Not OK\n"
                + "        ~~~~~~~~~\n"
                + "src/test/pkg/LayoutTest.java:26: Error: Suspicious method call; should probably call \"draw\" rather than \"onDraw\" [WrongCall]\n"
                + "  super.onDraw(null); // Not OK\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/LayoutTest.java:33: Error: Suspicious method call; should probably call \"layout\" rather than \"onLayout\" [WrongCall]\n"
                + "  super.onLayout(false, 0, 0, 0, 0); // Not OK\n"
                + "        ~~~~~~~~\n"
                + "src/test/pkg/LayoutTest.java:34: Error: Suspicious method call; should probably call \"measure\" rather than \"onMeasure\" [WrongCall]\n"
                + "  child.onMeasure(widthMeasureSpec, heightMeasureSpec); // Not OK\n"
                + "        ~~~~~~~~~\n"
                + "src/test/pkg/LayoutTest.java:41: Error: Suspicious method call; should probably call \"draw\" rather than \"onDraw\" [WrongCall]\n"
                + "  child.onDraw(canvas); // Not OK\n"
                + "        ~~~~~~\n"
                + "6 errors, 0 warnings\n",

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
                            + "import android.content.Context;\n"
                            + "import android.graphics.Canvas;\n"
                            + "import android.util.AttributeSet;\n"
                            + "import android.view.ViewGroup;\n"
                            + "import android.widget.Button;\n"
                            + "import android.widget.FrameLayout;\n"
                            + "import android.widget.LinearLayout;\n"
                            + "\n"
                            + "@SuppressWarnings(\"unused\")\n"
                            + "public class LayoutTest extends LinearLayout {\n"
                            + "\tprivate MyChild child;\n"
                            + "\n"
                            + "\tpublic LayoutTest(Context context, AttributeSet attrs, int defStyle) {\n"
                            + "\t\tsuper(context, attrs, defStyle);\n"
                            + "\t}\n"
                            + "\n"
                            + "\t@Override\n"
                            + "\tprotected void onLayout(boolean changed, int left, int top, int right,\n"
                            + "\t\t\tint bottom) {\n"
                            + "\t\tsuper.onLayout(changed, left, top, right, bottom); // OK\n"
                            + "\t\tchild.onLayout(changed, left, top, right, bottom); // Not OK\n"
                            + "\n"
                            + "\t\tsuper.onMeasure(0, 0); // Not OK\n"
                            + "\t\tsuper.onDraw(null); // Not OK\n"
                            + "\t\tchild.layout(left, top, right, bottom); // OK\n"
                            + "\t}\n"
                            + "\n"
                            + "\t@Override\n"
                            + "\tprotected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {\n"
                            + "\t\tsuper.onMeasure(widthMeasureSpec, heightMeasureSpec); // OK\n"
                            + "\t\tsuper.onLayout(false, 0, 0, 0, 0); // Not OK\n"
                            + "\t\tchild.onMeasure(widthMeasureSpec, heightMeasureSpec); // Not OK\n"
                            + "\t\tchild.measure(widthMeasureSpec, heightMeasureSpec); // OK\n"
                            + "\t}\n"
                            + "\n"
                            + "\t@Override\n"
                            + "\tprotected void onDraw(Canvas canvas) {\n"
                            + "\t\tsuper.onDraw(canvas); // OK\n"
                            + "\t\tchild.onDraw(canvas); // Not OK\n"
                            + "\t\tchild.draw(canvas); // OK\n"
                            + "\t}\n"
                            + "\n"
                            + "\tprivate class MyChild extends FrameLayout {\n"
                            + "\t\tpublic MyChild(Context context, AttributeSet attrs, int defStyle) {\n"
                            + "\t\t\tsuper(context, attrs, defStyle);\n"
                            + "\t\t}\n"
                            + "\n"
                            + "\t\t@Override\n"
                            + "\t\tprotected void onLayout(boolean changed, int left, int top, int right,\n"
                            + "\t\t\t\tint bottom) {\n"
                            + "\t\t\tsuper.onLayout(changed, left, top, right, bottom);\n"
                            + "\t\t}\n"
                            + "\n"
                            + "\t\t@Override\n"
                            + "\t\tprotected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {\n"
                            + "\t\t\tsuper.onMeasure(widthMeasureSpec, heightMeasureSpec);\n"
                            + "\t\t}\n"
                            + "\n"
                            + "\t\t@Override\n"
                            + "\t\tprotected void onDraw(Canvas canvas) {\n"
                            + "\t\t\tsuper.onDraw(canvas);\n"
                            + "\t\t}\n"
                            + "\t}\n"
                            + "}\n")
            ));
    }

    public void testGetOldValue() {
        assertEquals("onLayout", WrongCallDetector.getOldValue(
                "Suspicious method call; should probably call \"layout\" rather than \"onLayout\"",
                TEXT));
    }

    public void testGetNewValue() {
        assertEquals("layout", WrongCallDetector.getNewValue(
                "Suspicious method call; should probably call \"layout\" rather than \"onLayout\"",
                TEXT));
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @NonNull Location location, @NonNull String message) {
        assertNotNull(message, WrongCallDetector.getOldValue(message, TEXT));
        assertNotNull(message, WrongCallDetector.getNewValue(message, TEXT));
    }
}
