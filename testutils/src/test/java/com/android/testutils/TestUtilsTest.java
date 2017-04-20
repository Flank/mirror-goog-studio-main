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

package com.android.testutils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class TestUtilsTest {
    @Test
    public void testDiff() throws Exception {
        assertEquals(
                "",
                TestUtils.getDiff(
                    "",
                    ""));
        assertEquals(
                "",
                TestUtils.getDiff(
                    "aaa",
                    "aaa"));
        assertEquals(
                "@@ -1 +1\n" +
                "- aaa\n" +
                "@@ -2 +1\n" +
                "+ bbb\n",
                TestUtils.getDiff(
                    "aaa",
                    "bbb"));
        assertEquals(
                "@@ -1 +1\n" +
                "- this\n" +
                "@@ -4 +3\n" +
                "+ new\n",
                TestUtils.getDiff(
                    "this\n" +
                    "is\n" +
                    "a\n" +
                    "test\n",

                    "is\n" +
                    "a\n" +
                    "new\n" +
                    "test\n"));
        assertEquals(
                "@@ -4 +4\n" +
                "- line4\n" +
                "- line5\n" +
                "@@ -8 +6\n" +
                "- line8\n" +
                "+ line7.5\n",
                TestUtils.getDiff(
                    "line1\n" +
                    "line2\n" +
                    "line3\n" +
                    "line4\n" +
                    "line5\n" +
                    "line6\n" +
                    "line7\n" +
                    "line8\n" +
                    "line9\n",

                    "line1\n" +
                    "line2\n" +
                    "line3\n" +
                    "line6\n" +
                    "line7\n" +
                    "line7.5\n" +
                    "line9\n"));

        assertEquals(""
                        + "@@ -4 +4\n"
                        + "  line1\n"
                        + "  line2\n"
                        + "  line3\n"
                        + "- line4\n"
                        + "- line5\n"
                        + "  line6\n"
                        + "  line7\n"
                        + "  line8\n"
                        + "@@ -8 +6\n"
                        + "  line5\n"
                        + "  line6\n"
                        + "  line7\n"
                        + "- line8\n"
                        + "+ line7.5\n"
                        + "  line9\n",
                TestUtils.getDiff(""
                                + "line1\n"
                                + "line2\n"
                                + "line3\n"
                                + "line4\n"
                                + "line5\n"
                                + "line6\n"
                                + "line7\n"
                                + "line8\n"
                                + "line9\n",
                        ""
                                + "line1\n"
                                + "line2\n"
                                + "line3\n"
                                + "line6\n"
                                + "line7\n"
                                + "line7.5\n"
                                + "line9\n",
                        3));

        assertEquals(
                "@@ -8 +8\n" +
                "-         android:id=\"@+id/textView1\"\n" +
                "+         android:id=\"@+id/output\"\n" +
                "@@ -19 +19\n" +
                "-         android:layout_alignLeft=\"@+id/textView1\"\n" +
                "-         android:layout_below=\"@+id/textView1\"\n" +
                "+         android:layout_alignLeft=\"@+id/output\"\n" +
                "+         android:layout_below=\"@+id/output\"\n",

                TestUtils.getDiff(
                "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    tools:context=\".MainActivity\" >\n" +
                "\n" +
                "    <TextView\n" +
                "        android:id=\"@+id/textView1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_centerVertical=\"true\"\n" +
                "        android:layout_toRightOf=\"@+id/button2\"\n" +
                "        android:text=\"@string/hello_world\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignLeft=\"@+id/textView1\"\n" +
                "        android:layout_below=\"@+id/textView1\"\n" +
                "        android:layout_marginLeft=\"22dp\"\n" +
                "        android:layout_marginTop=\"24dp\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button2\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignParentLeft=\"true\"\n" +
                "        android:layout_alignParentTop=\"true\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "</RelativeLayout>",

                "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    tools:context=\".MainActivity\" >\n" +
                "\n" +
                "    <TextView\n" +
                "        android:id=\"@+id/output\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_centerVertical=\"true\"\n" +
                "        android:layout_toRightOf=\"@+id/button2\"\n" +
                "        android:text=\"@string/hello_world\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignLeft=\"@+id/output\"\n" +
                "        android:layout_below=\"@+id/output\"\n" +
                "        android:layout_marginLeft=\"22dp\"\n" +
                "        android:layout_marginTop=\"24dp\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button2\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignParentLeft=\"true\"\n" +
                "        android:layout_alignParentTop=\"true\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "</RelativeLayout>"));
    }

    @Test
    public void testMyDiff() {
        String a =
                "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    tools:context=\".MainActivity\" >\n" +
                "\n" +
                "    <TextView\n" +
                "        android:id=\"@+id/textView1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_centerVertical=\"true\"\n" +
                "        android:layout_toRightOf=\"@+id/button2\"\n" +
                "        android:text=\"@string/hello_world\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignLeft=\"@+id/textView1\"\n" +
                "        android:layout_below=\"@+id/textView1\"\n" +
                "        android:layout_marginLeft=\"22dp\"\n" +
                "        android:layout_marginTop=\"24dp\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button2\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignParentLeft=\"true\"\n" +
                "        android:layout_alignParentTop=\"true\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "</RelativeLayout>";
        String b =
                "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    tools:context=\".MainActivity\" >\n" +
                "\n" +
                "    <TextView\n" +
                "        android:id=\"@+id/textView1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_height2=\"wrap_content\"\n" +
                "        android:layout_height3=\"wrap_content\"\n" +
                "        android:layout_centerVertical=\"true\"\n" +
                "        android:layout_toRightOf=\"@+id/button2\"\n" +
                "        android:text=\"@string/hello_world\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignLeft=\"@+id/textView1\"\n" +
                "        android:layout_below=\"@+id/textView1\"\n" +
                "        android:layout_marginLeft=\"22dp\"\n" +
                "        android:layout_marginTop=\"24dp\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button2\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:layout_alignParentLeft=\"true\"\n" +
                "        android:layout_alignParentTop=\"true\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "</RelativeLayout>";

        assertThat(TestUtils.getDiff(a, b, 3)).isEqualTo(""
                + "@@ -11 +11\n"
                + "          android:id=\"@+id/textView1\"\n"
                + "          android:layout_width=\"wrap_content\"\n"
                + "          android:layout_height=\"wrap_content\"\n"
                + "+         android:layout_height2=\"wrap_content\"\n"
                + "+         android:layout_height3=\"wrap_content\"\n"
                + "          android:layout_centerVertical=\"true\"\n"
                + "          android:layout_toRightOf=\"@+id/button2\"\n"
                + "          android:text=\"@string/hello_world\" />\n");
    }

    @Test
    public void testGetPlatformFile_exception() throws Exception {
        try {
            TestUtils.getPlatformFile("foo.jar");
            fail("Should have thrown.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("foo.jar");
            assertThat(e.getMessage()).contains(TestUtils.getLatestAndroidPlatform());
        }
    }
}
