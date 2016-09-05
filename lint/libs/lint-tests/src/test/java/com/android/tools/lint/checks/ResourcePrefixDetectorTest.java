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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;

import java.io.File;
import java.util.Arrays;

public class ResourcePrefixDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new ResourcePrefixDetector();
    }

    public void testResourceFiles() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-mdpi/frame.png: Error: Resource named 'frame' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_frame' ? [ResourceName]\n"
                + "res/layout/layout1.xml:2: Error: Resource named 'layout1' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_layout1' ? [ResourceName]\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "res/menu/menu.xml:2: Error: Resource named 'menu' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_menu' ? [ResourceName]\n"
                + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                + "^\n"
                + "3 errors, 0 warnings\n",
            lintProject(
                    xml("res/layout/layout1.xml", ""
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
                            + "</LinearLayout>\n"),
                xml("res/menu/menu.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                            + "\n"
                            + "    <item\n"
                            + "        android:id=\"@+id/item1\"\n"
                            + "        android:icon=\"@drawable/icon1\"\n"
                            + "        android:title=\"My title 1\">\n"
                            + "    </item>\n"
                            + "    <item\n"
                            + "        android:id=\"@+id/item2\"\n"
                            + "        android:icon=\"@drawable/icon2\"\n"
                            + "        android:showAsAction=\"ifRoom\"\n"
                            + "        android:title=\"My title 2\">\n"
                            + "    </item>\n"
                            + "\n"
                            + "</menu>\n"),
                    xml("res/layout/unit_test_prefix_ok.xml", ""
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
                            + "</LinearLayout>\n"),
                    image("res/drawable-mdpi/frame.png", 472, 290),
                    image("res/drawable/unit_test_prefix_ok1.png", 472, 290),
                    image("res/drawable/unit_test_prefix_ok2.9.png", 472, 290)
            ));
    }

    public void testValues() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/values/customattr.xml:2: Error: Resource named 'ContentFrame' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_ContentFrame' ? [ResourceName]\n"
                + "    <declare-styleable name=\"ContentFrame\">\n"
                + "                       ~~~~~~~~~~~~~~~~~~~\n"
                + "res/values/customattr.xml:3: Error: Resource named 'content' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_content' ? [ResourceName]\n"
                + "        <attr name=\"content\" format=\"reference\" />\n"
                + "              ~~~~~~~~~~~~~~\n"
                + "res/values/customattr.xml:4: Error: Resource named 'contentId' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_contentId' ? [ResourceName]\n"
                + "        <attr name=\"contentId\" format=\"reference\" />\n"
                + "              ~~~~~~~~~~~~~~~~\n"
                + "res/layout/customattrlayout.xml:2: Error: Resource named 'customattrlayout' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_customattrlayout' ? [ResourceName]\n"
                + "<foo.bar.ContentFrame\n"
                + "^\n"
                + "4 errors, 0 warnings\n",

            lintProject(
                    xml("res/values/customattr.xml", ""
                            + "<resources>\n"
                            + "    <declare-styleable name=\"ContentFrame\">\n"
                            + "        <attr name=\"content\" format=\"reference\" />\n"
                            + "        <attr name=\"contentId\" format=\"reference\" />\n"
                            + "    </declare-styleable>\n"
                            + "</resources>\n"),
                    xml("res/layout/customattrlayout.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<foo.bar.ContentFrame\n"
                            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    xmlns:foobar=\"http://schemas.android.com/apk/res/foo.bar\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    foobar:contentId=\"@+id/test\" />\n"),
                    java(""
                            + "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n"
                            + " *\n"
                            + " * This class was automatically generated by the\n"
                            + " * aapt tool from the resource data it found.  It\n"
                            + " * should not be modified by hand.\n"
                            + " */\n"
                            + "\n"
                            + "package my.pkg;\n"
                            + "\n"
                            + "public final class R {\n"
                            + "    public static final class attr {\n"
                            + "        public static final int contentId=0x7f020000;\n"
                            + "    }\n"
                            + "}\n"),
                    manifest().minSdk(14)));
    }

    public void testMultiProject() throws Exception {
        File master = getProjectDir("MasterProject",
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
                            + "}\n")
        );
        File library = getProjectDir("LibraryProject",
                // Library project
                manifest().pkg("foo.library").minSdk(14),
                projectProperties().library(true).compileSdk(14),
                java(""
                            + "package foo.library;\n"
                            + "\n"
                            + "public class LibraryCode {\n"
                            + "    static {\n"
                            + "        System.out.println(R.string.string1);\n"
                            + "    }\n"
                            + "}\n"),
                xml("res/values/strings.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "\n"
                            + "    <string name=\"app_name\">LibraryProject</string>\n"
                            + "    <string name=\"string1\">String 1</string>\n"
                            + "    <string name=\"string2\">String 2</string>\n"
                            + "    <string name=\"string3\">String 3</string>\n"
                            + "\n"
                            + "</resources>\n")
        );
        assertEquals(""
                + "LibraryProject/res/values/strings.xml:4: Error: Resource named 'app_name' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_app_name' ? [ResourceName]\n"
                + "    <string name=\"app_name\">LibraryProject</string>\n"
                + "            ~~~~~~~~~~~~~~~\n"
                + "LibraryProject/res/values/strings.xml:5: Error: Resource named 'string1' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_string1' ? [ResourceName]\n"
                + "    <string name=\"string1\">String 1</string>\n"
                + "            ~~~~~~~~~~~~~~\n"
                + "LibraryProject/res/values/strings.xml:6: Error: Resource named 'string2' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_string2' ? [ResourceName]\n"
                + "    <string name=\"string2\">String 2</string>\n"
                + "            ~~~~~~~~~~~~~~\n"
                + "LibraryProject/res/values/strings.xml:7: Error: Resource named 'string3' does not start with the project's resource prefix 'unit_test_prefix_'; rename to 'unit_test_prefix_string3' ? [ResourceName]\n"
                + "    <string name=\"string3\">String 3</string>\n"
                + "            ~~~~~~~~~~~~~~\n"
                + "4 errors, 0 warnings\n",

            checkLint(Arrays.asList(master, library)).replace("/TESTROOT/",""));
    }

    public void testSuppressGeneratedRs() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        source("res/raw/blend.bc", "dummy file")
                ));
    }

    public void testAndroidPrefix() throws Exception {
        // Regression test for
        // 208973: Lint check for resource prefix doesn't ignore android: attributes
        assertEquals("No warnings.",
                lintProject(xml("res/values/values.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "  <declare-styleable name=\"Unit_test_prefix_MyView\">\n"
                        + "    <attr name=\"android:textColor\"/>\n"
                        + "    <attr name=\"unit_test_prefix_myAttribute\" format=\"reference\"/>\n"
                        + "  </declare-styleable>\n"
                        + "</resources>\n")
                ));
    }

    public void testStyleableName() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("res/values/values.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "  <declare-styleable name=\"Unit_test_prefixMyView\"/>\n"
                        + "</resources>\n")
                ));
    }
    // TODO: Test suppressing root level tag

    @Override
    protected TestLintClient createClient() {
        return new TestLintClient() {
            @NonNull
            @Override
            protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                return new Project(this, dir, referenceDir) {
                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Nullable
                    @Override
                    public AndroidProject getGradleProjectModel() {
                        AndroidProject project = mock(AndroidProject.class);
                        when(project.getResourcePrefix()).thenReturn("unit_test_prefix_");
                        return project;
                    }
                };
            }
        };
    }

}
