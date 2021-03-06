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

package com.android.tools.lint.client.api;

import static com.android.tools.lint.detector.api.LintUtilsTest.parse;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.AccessibilityDetector;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("javadoc")
public class LintDriverTest extends AbstractCheckTest {
    @SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions"})
    public void testClassEntryCompare() {
        File binDir = new File(".");
        byte[] bytes = new byte[0];
        ClassEntry c0 = new ClassEntry(new File("/a1/Foo.class"), null, binDir, bytes);
        ClassEntry c1 = new ClassEntry(new File("/a1/Foo.clazz"), null, binDir, bytes);
        ClassEntry c2 = new ClassEntry(new File("/a1/Foo$Inner1.class"), null, binDir, bytes);
        ClassEntry c3 = new ClassEntry(new File("/a1/Foo$Inner1$Inner.class"), null, binDir, bytes);
        ClassEntry c4 = new ClassEntry(new File("/a2/Foo$Inner2.clas"), null, binDir, bytes);
        ClassEntry c5 = new ClassEntry(new File("/a2/Foo$Inner2.class"), null, binDir, bytes);

        List<ClassEntry> expected = Arrays.asList(c0, c1, c2, c3, c4, c5);
        List<ClassEntry> list = new ArrayList<>(expected);
        Collections.sort(list);
        assertEquals(list, list);

        List<ClassEntry> list2 = Arrays.asList(c5, c4, c3, c2, c1, c0);
        Collections.sort(list2);
        assertEquals(expected, list2);

        List<ClassEntry> list3 = Arrays.asList(c3, c0, c1, c5, c2, c4);
        Collections.sort(list3);
        assertEquals(expected, list3);
    }

    @SuppressWarnings("ConstantConditions")
    public void testClassEntryCompareContract() {
        File binDir = new File(".");
        byte[] bytes = new byte[0];
        ClassEntry c0 = new ClassEntry(new File("abcde"), null, binDir, bytes);
        ClassEntry c1 = new ClassEntry(new File("abcde"), null, binDir, bytes);
        assertTrue(c0.compareTo(c1) <= 0);
        assertTrue(c1.compareTo(c0) <= 0);
    }

    public void testMissingResourceDirectory() {
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "res/layout/layout1.xml",
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
                                        + "        layout=\"@layout/layout2\" />\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button2\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expectClean();
    }

    public void testHasErrors() {
        //noinspection all // Sample code
        Pair<JavaContext, Disposable> unit =
                parse("package test.pkg;\nclass Foo {\n}\n", new File("src/test/pkg/Foo.java"));
        LintDriver driver = unit.getFirst().getDriver();
        driver.setHasParserErrors(true);
        assertTrue(driver.hasParserErrors());
        driver.setHasParserErrors(false);
        assertFalse(driver.hasParserErrors());
        Disposer.dispose(unit.getSecond());
    }

    @Override
    protected TestLintClient createClient() {
        return new TestLintClient() {
            private List<File> mResources;

            @NonNull
            @Override
            public List<File> getResourceFolders(@NonNull Project project) {
                if (mResources == null) {
                    mResources = Lists.newArrayList(super.getResourceFolders(project));
                    mResources.add(new File("bogus"));
                }
                return mResources;
            }
        };
    }

    @Override
    protected Detector getDetector() {
        return new AccessibilityDetector();
    }
}
