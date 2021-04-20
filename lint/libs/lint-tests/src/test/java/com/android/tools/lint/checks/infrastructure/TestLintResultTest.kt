/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.checks.infrastructure.TestLintResult.Companion.getDiff
import junit.framework.TestCase.assertEquals
import org.junit.Test

class TestLintResultTest {
    @Test
    fun testDiff() {
        assertEquals("", getDiff("", ""))
        assertEquals("", getDiff("aaa", "aaa"))
        assertEquals(
            """
            @@ -1 +1
            - aaa
            @@ -2 +1
            + bbb
            """.trimIndent(),
            getDiff("aaa", "bbb")
        )
        assertEquals(
            """
            @@ -1 +1
            - this
            @@ -4 +3
            + new
            """.trimIndent(),
            getDiff(
                """
                this
                is
                a
                test
                """.trimIndent(),
                """
                is
                a
                new
                test
                """.trimIndent()
            )
        )
        assertEquals(
            """
            @@ -4 +4
            - line4
            - line5
            @@ -8 +6
            - line8
            + line7.5
            """.trimIndent(),
            getDiff(
                """
                line1
                line2
                line3
                line4
                line5
                line6
                line7
                line8
                line9
                """.trimIndent(),
                """
                line1
                line2
                line3
                line6
                line7
                line7.5
                line9
                """.trimIndent()
            )
        )

        assertEquals(
            """
            @@ -4 +4
              line1
              line2
              line3
            - line4
            - line5
              line6
              line7
              line8
            @@ -8 +6
              line5
              line6
              line7
            - line8
            + line7.5
              line9
            """.trimIndent(),
            getDiff(
                """
                line1
                line2
                line3
                line4
                line5
                line6
                line7
                line8
                line9
                """.trimIndent(),
                """
                line1
                line2
                line3
                line6
                line7
                line7.5
                line9
                """.trimIndent(),
                3
            )
        )
        assertEquals(
            """
            @@ -8 +8
            -         android:id="@+id/textView1"
            +         android:id="@+id/output"
            @@ -19 +19
            -         android:layout_alignLeft="@+id/textView1"
            -         android:layout_below="@+id/textView1"
            +         android:layout_alignLeft="@+id/output"
            +         android:layout_below="@+id/output"
            """.trimIndent(),
            getDiff(
                """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    tools:context=".MainActivity" >

                    <TextView
                        android:id="@+id/textView1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_centerVertical="true"
                        android:layout_toRightOf="@+id/button2"
                        android:text="@string/hello_world" />

                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignLeft="@+id/textView1"
                        android:layout_below="@+id/textView1"
                        android:layout_marginLeft="22dp"
                        android:layout_marginTop="24dp"
                        android:text="Button" />

                    <Button
                        android:id="@+id/button2"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_alignParentTop="true"
                        android:text="Button" />

                </RelativeLayout>
                """.trimIndent(),
                """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    tools:context=".MainActivity" >

                    <TextView
                        android:id="@+id/output"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_centerVertical="true"
                        android:layout_toRightOf="@+id/button2"
                        android:text="@string/hello_world" />

                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignLeft="@+id/output"
                        android:layout_below="@+id/output"
                        android:layout_marginLeft="22dp"
                        android:layout_marginTop="24dp"
                        android:text="Button" />

                    <Button
                        android:id="@+id/button2"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_alignParentTop="true"
                        android:text="Button" />

                </RelativeLayout>
                """.trimIndent()
            )
        )
    }

    @Test
    fun testMyDiff() {
        val a =
            """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                tools:context=".MainActivity" >

                <TextView
                    android:id="@+id/textView1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerVertical="true"
                    android:layout_toRightOf="@+id/button2"
                    android:text="@string/hello_world" />

                <Button
                    android:id="@+id/button1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignLeft="@+id/textView1"
                    android:layout_below="@+id/textView1"
                    android:layout_marginLeft="22dp"
                    android:layout_marginTop="24dp"
                    android:text="Button" />

                <Button
                    android:id="@+id/button2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentLeft="true"
                    android:layout_alignParentTop="true"
                    android:text="Button" />

            </RelativeLayout>
            """.trimIndent()
        val b =
            """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                tools:context=".MainActivity" >

                <TextView
                    android:id="@+id/textView1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_height2="wrap_content"
                    android:layout_height3="wrap_content"
                    android:layout_centerVertical="true"
                    android:layout_toRightOf="@+id/button2"
                    android:text="@string/hello_world" />

                <Button
                    android:id="@+id/button1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignLeft="@+id/textView1"
                    android:layout_below="@+id/textView1"
                    android:layout_marginLeft="22dp"
                    android:layout_marginTop="24dp"
                    android:text="Button" />

                <Button
                    android:id="@+id/button2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentLeft="true"
                    android:layout_alignParentTop="true"
                    android:text="Button" />

            </RelativeLayout>
            """.trimIndent()
        assertEquals(
            """
            @@ -11 +11
                      android:id="@+id/textView1"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
            +         android:layout_height2="wrap_content"
            +         android:layout_height3="wrap_content"
                      android:layout_centerVertical="true"
                      android:layout_toRightOf="@+id/button2"
                      android:text="@string/hello_world" />
            """.trimIndent(),
            getDiff(a, b, 3)
        )
    }
}
