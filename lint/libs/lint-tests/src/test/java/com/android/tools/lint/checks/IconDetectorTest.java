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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.mockito.stubbing.OngoingStubbing;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("javadoc")
public class IconDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new IconDetector();
    }

    private Set<Issue> mEnabled = new HashSet<>();
    private boolean mAbbreviate;

    private static final Set<Issue> ALL = new HashSet<>();
    static {
        ALL.add(IconDetector.DUPLICATES_CONFIGURATIONS);
        ALL.add(IconDetector.DUPLICATES_NAMES);
        ALL.add(IconDetector.GIF_USAGE);
        ALL.add(IconDetector.ICON_DENSITIES);
        ALL.add(IconDetector.ICON_DIP_SIZE);
        ALL.add(IconDetector.ICON_EXTENSION);
        ALL.add(IconDetector.ICON_LOCATION);
        ALL.add(IconDetector.ICON_MISSING_FOLDER);
        ALL.add(IconDetector.ICON_NODPI);
        ALL.add(IconDetector.ICON_COLORS);
        ALL.add(IconDetector.ICON_XML_AND_PNG);
        ALL.add(IconDetector.ICON_LAUNCHER_SHAPE);
        ALL.add(IconDetector.ICON_MIX_9PNG);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAbbreviate = true;
    }

    @Override
    protected void configureDriver(LintDriver driver) {
        driver.setAbbreviating(mAbbreviate);
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    @Override
    protected TestConfiguration getConfiguration(LintClient client, Project project) {
        return new TestConfiguration(client, project, null) {
            @Override
            public boolean isEnabled(@NonNull Issue issue) {
                return super.isEnabled(issue) && mEnabled.contains(issue);
            }
        };
    }

    public void test() throws Exception {
        mEnabled = ALL;
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-mdpi/sample_icon.gif: Warning: Using the .gif format for bitmaps is discouraged [GifUsage]\n"
                + "res/drawable/ic_launcher.png: Warning: The ic_launcher.png icon has identical contents in the following configuration folders: drawable-mdpi, drawable [IconDuplicatesConfig]\n"
                + "    res/drawable-mdpi/ic_launcher.png: <No location-specific message\n"
                + "res/drawable/ic_launcher.png: Warning: Found bitmap drawable res/drawable/ic_launcher.png in densityless folder [IconLocation]\n"
                + "res/drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: sample_icon.gif (found in drawable-mdpi) [IconDensities]\n"
                + "res: Warning: Missing density variation folders in res: drawable-xhdpi, drawable-xxhdpi, drawable-xxxhdpi [IconMissingDensityFolder]\n"
                + "0 errors, 5 warnings\n",

            lintProject(
                    // Use minSDK4 to ensure that we get warnings about missing drawables
                    manifest().minSdk(4),
                    image("res/drawable/ic_launcher.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/ic_launcher.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/sample_icon.gif", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    // Make a dummy file named .svn to make sure it doesn't get seen as
                    // an icon name
                    source("res/drawable-hdpi/.svn", ""),
                    image("res/drawable-hdpi/ic_launcher.png", 72, 72).fill(10, 10, 30, 30, 0xFFFF00FF)));
    }

    public void testMixed() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_XML_AND_PNG);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable/background.xml: Warning: The following images appear both as density independent .xml files and as bitmap files: res/drawable-mdpi/background.png, res/drawable/background.xml [IconXmlAndPng]\n"
                + "    res/drawable-mdpi/background.png: <No location-specific message\n"
                + "0 errors, 1 warnings\n",

            lintProject(
                    manifest().minSdk(4),
                    xml("res/drawable/background.xml", ""
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
                    image("res/drawable-mdpi/background.png", 48, 48).fill(0xFF00FF30)));
    }

    public void testApi1() throws Exception {
        mEnabled = ALL;
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            lintProject(
                    // manifest file which specifies uses sdk = 2
                    manifest().minSdk(2),
                    image("res/drawable/ic_launcher.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF)));
    }

    public void test2() throws Exception {
        mEnabled = ALL;
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-hdpi/other.9.png: Warning: The following unrelated icon files have identical contents: appwidget_bg.9.png, other.9.png [IconDuplicates]\n"
                + "    res/drawable-hdpi/appwidget_bg.9.png: <No location-specific message\n"
                + "res/drawable-hdpi/unrelated.png: Warning: The following unrelated icon files have identical contents: ic_launcher.png, unrelated.png [IconDuplicates]\n"
                + "    res/drawable-hdpi/ic_launcher.png: <No location-specific message\n"
                + "res: Warning: Missing density variation folders in res: drawable-mdpi, drawable-xhdpi, drawable-xxhdpi, drawable-xxxhdpi [IconMissingDensityFolder]\n"
                + "0 errors, 3 warnings\n",

            lintProject(
                    image("res/drawable-hdpi/appwidget_bg.9.png", 48, 48).fill(0xFF00FF29),
                    image("res/drawable-hdpi/appwidget_bg_focus.9.png", 49, 49).fill(0xFF00FF29),
                    image("res/drawable-hdpi/other.9.png", 48, 48).fill(0xFF00FF29),
                    image("res/drawable-hdpi/ic_launcher.png", 72, 72).fill(10, 10, 20, 20, 0x00000000),
                    image("res/drawable-hdpi/unrelated.png", 72, 72).fill(10, 10, 20, 20, 0x00000000)
            ));
    }

    public void testNoDpi() throws Exception {
        mEnabled = ALL;
        assertEquals(""
                + "res/drawable-mdpi/frame.png: Warning: The following images appear in both -nodpi and in a density folder: frame.png [IconNoDpi]\n"
                + "res/drawable-xlarge-nodpi-v11/frame.png: Warning: The frame.png icon has identical contents in the following configuration folders: drawable-mdpi, drawable-nodpi, drawable-xlarge-nodpi-v11 [IconDuplicatesConfig]\n"
                + "    res/drawable-nodpi/frame.png: <No location-specific message\n"
                + "    res/drawable-mdpi/frame.png: <No location-specific message\n"
                + "res: Warning: Missing density variation folders in res: drawable-hdpi, drawable-xhdpi, drawable-xxhdpi, drawable-xxxhdpi [IconMissingDensityFolder]\n"
                + "0 errors, 3 warnings\n",

            lintProject(
                    image("res/drawable-mdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-nodpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-xlarge-nodpi-v11/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000)));
    }

    public void testNoDpi2() throws Exception {
        mEnabled = ALL;
        // Having additional icon names in the no-dpi folder should not cause any complaints
        assertEquals(""
                + "res/drawable-xxxhdpi/frame.png: Warning: The image frame.png varies significantly in its density-independent (dip) size across the various density versions: drawable-ldpi/frame.png: 629x387 dp (472x290 px), drawable-mdpi/frame.png: 472x290 dp (472x290 px), drawable-hdpi/frame.png: 315x193 dp (472x290 px), drawable-xhdpi/frame.png: 236x145 dp (472x290 px), drawable-xxhdpi/frame.png: 157x97 dp (472x290 px), drawable-xxxhdpi/frame.png: 118x73 dp (472x290 px) [IconDipSize]\n"
                + "    res/drawable-xxhdpi/frame.png: <No location-specific message\n"
                + "    res/drawable-xhdpi/frame.png: <No location-specific message\n"
                + "    res/drawable-hdpi/frame.png: <No location-specific message\n"
                + "    res/drawable-mdpi/frame.png: <No location-specific message\n"
                + "    res/drawable-ldpi/frame.png: <No location-specific message\n"
                + "res/drawable-xxxhdpi/frame.png: Warning: The following unrelated icon files have identical contents: frame.png, frame.png, frame.png, file1.png, file2.png, frame.png, frame.png, frame.png [IconDuplicates]\n"
                + "    res/drawable-xxhdpi/frame.png: <No location-specific message\n"
                + "    res/drawable-xhdpi/frame.png: <No location-specific message\n"
                + "    res/drawable-nodpi/file2.png: <No location-specific message\n"
                + "    res/drawable-nodpi/file1.png: <No location-specific message\n"
                + "    res/drawable-mdpi/frame.png: <No location-specific message\n"
                + "    res/drawable-ldpi/frame.png: <No location-specific message\n"
                + "    res/drawable-hdpi/frame.png: <No location-specific message\n"
                + "0 errors, 2 warnings\n",

            lintProject(
                    image("res/drawable-mdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-hdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-ldpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-xhdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-xxhdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-xxxhdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-nodpi/file1.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-nodpi/file2.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000)));
    }

    public void testNoDpiMix() throws Exception {
        mEnabled = ALL;
        assertEquals(""
                + "res/drawable-mdpi/frame.xml: Warning: The following images appear in both -nodpi and in a density folder: frame.png, frame.xml [IconNoDpi]\n"
                + "    res/drawable-mdpi/frame.png: <No location-specific message\n"
                + "res/drawable-nodpi/frame.xml: Warning: The following images appear both as density independent .xml files and as bitmap files: res/drawable-mdpi/frame.png, res/drawable-nodpi/frame.xml [IconXmlAndPng]\n"
                + "    res/drawable-mdpi/frame.png: <No location-specific message\n"
                + "res: Warning: Missing density variation folders in res: drawable-hdpi, drawable-xhdpi, drawable-xxhdpi, drawable-xxxhdpi [IconMissingDensityFolder]\n"
                + "0 errors, 3 warnings\n",

            lintProject(
                    image("res/drawable-mdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    xml("res/drawable-nodpi/frame.xml", ""
                            + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <item  android:color=\"#ff000000\"/> <!-- WRONG, SHOULD BE LAST -->\n"
                            + "    <item android:state_pressed=\"true\"\n"
                            + "          android:color=\"#ffff0000\"/> <!-- pressed -->\n"
                            + "    <item android:state_focused=\"true\"\n"
                            + "          android:color=\"#ff0000ff\"/> <!-- focused -->\n"
                            + "</selector>\n")));
    }

    public void testMixedFormat() throws Exception {
        mEnabled = ALL;
        // Test having a mixture of .xml and .png resources for the same name
        // Make sure we don't get:
        // drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: f.png (found in drawable-mdpi)
        // drawable-xhdpi: Warning: Missing the following drawables in drawable-xhdpi: f.png (found in drawable-mdpi)
        assertEquals(""
                + "res/drawable-xxxhdpi/f.xml: Warning: The following images appear both as density independent .xml files and as bitmap files: res/drawable-hdpi/f.xml, res/drawable-mdpi/f.png [IconXmlAndPng]\n"
                + "    res/drawable-xxhdpi/f.xml: <No location-specific message\n"
                + "    res/drawable-xhdpi/f.xml: <No location-specific message\n"
                + "    res/drawable-mdpi/f.png: <No location-specific message\n"
                + "    res/drawable-hdpi/f.xml: <No location-specific message\n"
                + "0 errors, 1 warnings\n",

            lintProject(
                    image("res/drawable-mdpi/f.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    xml("res/drawable-hdpi/f.xml", ""
                            + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <item  android:color=\"#ff000000\"/> <!-- WRONG, SHOULD BE LAST -->\n"
                            + "    <item android:state_pressed=\"true\"\n"
                            + "          android:color=\"#ffff0000\"/> <!-- pressed -->\n"
                            + "    <item android:state_focused=\"true\"\n"
                            + "          android:color=\"#ff0000ff\"/> <!-- focused -->\n"
                            + "</selector>\n"),
                    xml("res/drawable-xhdpi/f.xml", ""
                            + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <item  android:color=\"#ff000000\"/> <!-- WRONG, SHOULD BE LAST -->\n"
                            + "    <item android:state_pressed=\"true\"\n"
                            + "          android:color=\"#ffff0000\"/> <!-- pressed -->\n"
                            + "    <item android:state_focused=\"true\"\n"
                            + "          android:color=\"#ff0000ff\"/> <!-- focused -->\n"
                            + "</selector>\n"),
                    xml("res/drawable-xxhdpi/f.xml", ""
                            + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <item  android:color=\"#ff000000\"/> <!-- WRONG, SHOULD BE LAST -->\n"
                            + "    <item android:state_pressed=\"true\"\n"
                            + "          android:color=\"#ffff0000\"/> <!-- pressed -->\n"
                            + "    <item android:state_focused=\"true\"\n"
                            + "          android:color=\"#ff0000ff\"/> <!-- focused -->\n"
                            + "</selector>\n"),
                    xml("res/drawable-xxxhdpi/f.xml", ""
                            + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <item  android:color=\"#ff000000\"/> <!-- WRONG, SHOULD BE LAST -->\n"
                            + "    <item android:state_pressed=\"true\"\n"
                            + "          android:color=\"#ffff0000\"/> <!-- pressed -->\n"
                            + "    <item android:state_focused=\"true\"\n"
                            + "          android:color=\"#ff0000ff\"/> <!-- focused -->\n"
                            + "</selector>\n")));
    }

    public void testMisleadingFileName() throws Exception {
        if (!imageFormatSupported("JPG")) {
            return;
        }

        mEnabled = Collections.singleton(IconDetector.ICON_EXTENSION);
        assertEquals(""
                + "res/drawable-mdpi/frame.gif: Warning: Misleading file extension; named .gif but the file format is png [IconExtension]\n"
                + "res/drawable-mdpi/frame.jpg: Warning: Misleading file extension; named .jpg but the file format is png [IconExtension]\n"
                + "res/drawable-mdpi/myjpg.png: Warning: Misleading file extension; named .png but the file format is JPEG [IconExtension]\n"
                + "res/drawable-mdpi/sample_icon.jpeg: Warning: Misleading file extension; named .jpeg but the file format is gif [IconExtension]\n"
                + "res/drawable-mdpi/sample_icon.jpg: Warning: Misleading file extension; named .jpg but the file format is gif [IconExtension]\n"
                + "res/drawable-mdpi/sample_icon.png: Warning: Misleading file extension; named .png but the file format is gif [IconExtension]\n"
                + "0 errors, 6 warnings\n",

            lintProject(
                    image("res/drawable-mdpi/myjpg.jpg", 48, 48).format("JPG").fill(10, 10, 20, 20, 0xFF00FFFF), // VALID
                    image("res/drawable-mdpi/myjpg.jpeg", 48, 48).format("JPG").fill(10, 10, 20, 20, 0xFF00FFFF), // VALID
                    image("res/drawable-mdpi/frame.gif", 472, 290).format("PNG").fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-mdpi/frame.jpg", 472, 290).format("PNG").fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                    image("res/drawable-mdpi/myjpg.png", 48, 48).format("JPG").fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/sample_icon.jpg", 48, 48).format("GIF").fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/sample_icon.jpeg", 48, 48).format("GIF").fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/sample_icon.png", 48, 48).format("GIF").fill(10, 10, 20, 20, 0xFF00FFFF)));
    }

    public void testColors() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_COLORS);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-mdpi/ic_menu_my_action.png: Warning: Action Bar icons should use a single gray color (#333333 for light themes (with 60%/30% opacity for enabled/disabled), and #FFFFFF with opacity 80%/30% for dark themes [IconColors]\n"
                + "res/drawable-mdpi-v11/ic_stat_my_notification.png: Warning: Notification icons must be entirely white [IconColors]\n"
                + "res/drawable-mdpi-v9/ic_stat_my_notification2.png: Warning: Notification icons must be entirely white [IconColors]\n"
                + "0 errors, 3 warnings\n",

            lintProject(
                manifest().minSdk(14),
                    image("res/drawable-mdpi/ic_menu_my_action.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi-v11/ic_stat_my_notification.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi-v9/ic_stat_my_notification2.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi/ic_menu_add_clip_normal.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF))); // OK
    }

    public void testNotActionBarIcons() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_COLORS);
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            // No Java code designates the menu as an action bar menu
            lintProject(
                manifest().minSdk(14),
                mMenu,
                    image("res/drawable-mdpi/icon1.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi/icon2.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi/icon3.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF), // Not action bar
                    image("res/drawable-mdpi/ic_menu_add_clip_normal.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF))); // OK
    }

    public void testActionBarIcons() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_COLORS);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-mdpi/icon1.png: Warning: Action Bar icons should use a single gray color (#333333 for light themes (with 60%/30% opacity for enabled/disabled), and #FFFFFF with opacity 80%/30% for dark themes [IconColors]\n"
                + "res/drawable-mdpi/icon2.png: Warning: Action Bar icons should use a single gray color (#333333 for light themes (with 60%/30% opacity for enabled/disabled), and #FFFFFF with opacity 80%/30% for dark themes [IconColors]\n"
                + "0 errors, 2 warnings\n",

            lintProject(
                manifest().minSdk(14),
                mMenu,
                mActionBarTest,
                    image("res/drawable-mdpi/icon1.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi/icon2.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi/icon3.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF), // Not action bar
                    image("res/drawable-mdpi/ic_menu_add_clip_normal.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF))); // OK
    }

    public void testOkActionBarIcons() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_COLORS);
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            lintProject(
                manifest().minSdk(14),
                mMenu,
                    image("res/drawable-mdpi/icon1.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF),
                    image("res/drawable-mdpi/icon2.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF)));
    }

    public void testNotificationIcons() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_COLORS);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-mdpi/icon1.png: Warning: Notification icons must be entirely white [IconColors]\n"
                + "res/drawable-mdpi/icon2.png: Warning: Notification icons must be entirely white [IconColors]\n"
                + "res/drawable-mdpi/icon3.png: Warning: Notification icons must be entirely white [IconColors]\n"
                + "res/drawable-mdpi/icon4.png: Warning: Notification icons must be entirely white [IconColors]\n"
                + "res/drawable-mdpi/icon5.png: Warning: Notification icons must be entirely white [IconColors]\n"
                + "0 errors, 5 warnings\n",

            lintProject(
                manifest().minSdk(14),
                mNotificationTest,
                    image("res/drawable-mdpi/icon1.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi/icon2.png", 48, 48).fill(0xFF00FF30),
                    image("res/drawable-mdpi/icon3.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/icon4.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/icon5.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/icon6.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF), // not a notification
                    image("res/drawable-mdpi/icon7.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF), // ditto
                    image("res/drawable-mdpi/ic_menu_add_clip_normal.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF))); // OK
    }

    public void testOkNotificationIcons() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_COLORS);
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            lintProject(
                manifest().minSdk(14),
                mNotificationTest,
                    image("res/drawable-mdpi/icon1.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF),
                    image("res/drawable-mdpi/icon2.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF),
                    image("res/drawable-mdpi/icon3.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF),
                    image("res/drawable-mdpi/icon4.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF),
                    image("res/drawable-mdpi/icon5.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF)));
    }

    public void testExpectedSize() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_EXPECTED_SIZE);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-mdpi/ic_launcher.png: Warning: Incorrect icon size for drawable-mdpi/ic_launcher.png: expected 48x48, but was 24x24 [IconExpectedSize]\n"
                + "res/drawable-mdpi/icon1.png: Warning: Incorrect icon size for drawable-mdpi/icon1.png: expected 32x32, but was 48x48 [IconExpectedSize]\n"
                + "res/drawable-mdpi/icon3.png: Warning: Incorrect icon size for drawable-mdpi/icon3.png: expected 24x24, but was 48x48 [IconExpectedSize]\n"
                + "0 errors, 3 warnings\n",

            lintProject(
                manifest().minSdk(14),
                mNotificationTest,
                mMenu,
                mActionBarTest,

                // 3 wrong-sized icons:
                image("res/drawable-mdpi/icon1.png", 48, 48).fill(0xFF00FF30),
                image("res/drawable-mdpi/icon3.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                image("res/drawable-mdpi/ic_launcher.png", 24, 24),

                // OK sizes
                image("res/drawable-mdpi/icon2.png", 32, 32).fill(10, 10, 20, 20, 0xFFFFFFFF),
                image("res/drawable-mdpi/icon4.png", 24, 24),
                image("res/drawable-mdpi/ic_launcher2.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF)
            ));
    }

    public void testAbbreviate() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_DENSITIES);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: ic_launcher10.png, ic_launcher11.png, ic_launcher12.png, ic_launcher2.png, ic_launcher3.png... (6 more) [IconDensities]\n"
                + "res/drawable-xhdpi: Warning: Missing the following drawables in drawable-xhdpi: ic_launcher10.png, ic_launcher11.png, ic_launcher12.png, ic_launcher2.png, ic_launcher3.png... (6 more) [IconDensities]\n"
                + "0 errors, 2 warnings\n",

            lintProject(
                    // Use minSDK4 to ensure that we get warnings about missing drawables
                    manifest().minSdk(4),
                    image("res/drawable-hdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF16),
                    image("res/drawable-xhdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF17),
                    image("res/drawable-mdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF18),
                    image("res/drawable-mdpi/ic_launcher2.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/ic_launcher3.png", 48, 48).fill(0xFF00FF19),
                    image("res/drawable-mdpi/ic_launcher4.png", 48, 48).fill(0xFF00FF20),
                    image("res/drawable-mdpi/ic_launcher5.png", 48, 48).fill(0xFF00FF21),
                    image("res/drawable-mdpi/ic_launcher6.png", 48, 48).fill(0xFF00FF22),
                    image("res/drawable-mdpi/ic_launcher7.png", 48, 48).fill(0xFF00FF23),
                    image("res/drawable-mdpi/ic_launcher8.png", 48, 48).fill(0xFF00FF24),
                    image("res/drawable-mdpi/ic_launcher9.webp", 48, 48).fill(0xFF00FF25).format("PNG"),
                    image("res/drawable-mdpi/ic_launcher10.png", 48, 48).fill(0xFF00FF26),
                    image("res/drawable-mdpi/ic_launcher11.png", 48, 48).fill(0xFF00FF26),
                    image("res/drawable-mdpi/ic_launcher12.png", 48, 48).fill(0xFF00FF28)
            ));
    }

    public void testShowAll() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_DENSITIES);
        mAbbreviate = false;
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: ic_launcher10.png, ic_launcher11.png, ic_launcher12.png, ic_launcher2.png, ic_launcher3.png, ic_launcher4.png, ic_launcher5.png, ic_launcher6.png, ic_launcher7.png, ic_launcher8.png, ic_launcher9.png [IconDensities]\n"
                + "res/drawable-xhdpi: Warning: Missing the following drawables in drawable-xhdpi: ic_launcher10.png, ic_launcher11.png, ic_launcher12.png, ic_launcher2.png, ic_launcher3.png, ic_launcher4.png, ic_launcher5.png, ic_launcher6.png, ic_launcher7.png, ic_launcher8.png, ic_launcher9.png [IconDensities]\n"
                + "0 errors, 2 warnings\n",

            lintProject(
                    // Use minSDK4 to ensure that we get warnings about missing drawables
                    manifest().minSdk(4),
                    image("res/drawable-hdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF16),
                    image("res/drawable-xhdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF17),
                    image("res/drawable-mdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF18),
                    image("res/drawable-mdpi/ic_launcher2.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                    image("res/drawable-mdpi/ic_launcher3.png", 48, 48).fill(0xFF00FF19),
                    image("res/drawable-mdpi/ic_launcher4.png", 48, 48).fill(0xFF00FF20),
                    image("res/drawable-mdpi/ic_launcher5.png", 48, 48).fill(0xFF00FF21),
                    image("res/drawable-mdpi/ic_launcher6.png", 48, 48).fill(0xFF00FF22),
                    image("res/drawable-mdpi/ic_launcher7.png", 48, 48).fill(0xFF00FF23),
                    image("res/drawable-mdpi/ic_launcher8.png", 48, 48).fill(0xFF00FF24),
                    image("res/drawable-mdpi/ic_launcher9.png", 48, 48).fill(0xFF00FF29),
                    image("res/drawable-mdpi/ic_launcher10.png", 48, 48).fill(0xFF00FF26),
                    image("res/drawable-mdpi/ic_launcher11.png", 48, 48).fill(0xFF00FF26),
                    image("res/drawable-mdpi/ic_launcher12.png", 48, 48).fill(0xFF00FF28)
            ));
    }

    public void testIgnoreMissingFolders() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_DENSITIES);
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",

            lintProject(
                    // Use minSDK4 to ensure that we get warnings about missing drawables
                    manifest().minSdk(4),
                    // source() instead of xml() because IDE validation shows error on <ignore...>
                    source("lint.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<lint>\n"
                            + "    <issue id=\"IconDensities\" severity=\"warning\">\n"
                            + "        <ignore path=\"res/drawable-hdpi\" />\n"
                            + "  </issue>\n"
                            + "</lint>\n"),
                    image("res/drawable-hdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF16),
                    image("res/drawable-mdpi/ic_launcher1.png", 48, 48).fill(0xFF00FF18),
                    image("res/drawable-mdpi/ic_launcher2.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF)
            ));
    }

    public void testSquareLauncher() throws Exception {
        mEnabled = Collections.singleton(IconDetector.ICON_LAUNCHER_SHAPE);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-hdpi/ic_launcher_filled.png: Warning: Launcher icons should not fill every pixel of their square region; see the design guide for details [IconLauncherShape]\n"
                + "0 errors, 1 warnings\n",

            lintProject(
                    manifest().minSdk(4),
                    image("res/drawable-hdpi/ic_launcher_filled.png", 50, 40).fill(0xFFFFFFFF),
                    image("res/drawable-mdpi/ic_launcher_2.png", 50, 40).text(5, 5, "x", 0xFFFFFFFF)
            ));
    }

    public void testMixNinePatch() throws Exception {
        // https://code.google.com/p/android/issues/detail?id=43075
        mEnabled = Collections.singleton(IconDetector.ICON_MIX_9PNG);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-mdpi/ic_launcher_filled.png: Warning: The files ic_launcher_filled.png and ic_launcher_filled.9.png clash; both will map to @drawable/ic_launcher_filled [IconMixedNinePatch]\n"
                + "    res/drawable-hdpi/ic_launcher_filled.png: <No location-specific message\n"
                + "    res/drawable-hdpi/ic_launcher_filled.9.png: <No location-specific message\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        manifest().minSdk(4),
                        image("res/drawable-mdpi/ic_launcher_filled.png", 50, 40).fill(0xFFFFFFFF),
                        image("res/drawable-hdpi/ic_launcher_filled.png", 50, 40).fill(0xFFFFFFFF),
                        image("res/drawable-hdpi/ic_launcher_filled.9.png", 50, 40).fill(0xFFFFFFFF),
                        image("res/drawable-mdpi/ic_launcher_2.png", 50, 40).text(5, 5, "x", 0xFFFFFFFF)
                ));
    }

    public void test67486() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=67486
        mEnabled = Collections.singleton(IconDetector.ICON_COLORS);
        //noinspection all // Sample code
        assertEquals("No warnings.",

                lintProject(
                        manifest().minSdk(14),
                        base64gzip("res/drawable-xhdpi/ic_stat_notify.png", ""
                            + "H4sIAAAAAAAAAAGwAU/+iVBORw0KGgoAAAANSUhEUgAAABgAAAAlCAYAAABY"
                            + "kymLAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAA"
                            + "B3RJTUUH3gMYEiAHIq8UQAAAAT1JREFUSMftljtOxEAMhj9nIyEKkJCQ6Oi2"
                            + "oqbhEhSIk9ByCa5AQ881aDkANfW+YJP8NA4MqySMdmMKhCUr0mSSz//4oYF/"
                            + "+/NmQy8lFRn/kJkpPFJJlq1A0gVwA+wD6tnXrhvwYGb3uZGcSHqW1CjfXiVd"
                            + "t0pS7wKcSVpIqt2bTH+RdL4JKNMz9GSp41iGiqFN8ClwB9wCReeHLmkKPAEH"
                            + "PwA0kJNPKzc2HQOXwOEWJd6pskii3wOu3MdvNElT4BE4ciWTgRLNtrRTK2Dl"
                            + "z9E6MwWs3G3XqPsA6wTQRACqBBByRPVvAJaRgAqYJ+U5OqCJViAHFGN28tfU"
                            + "M6uBma+FlCnAIkyB23LMLu4DEAmY5Vxnds1BqIK5V5BF5qCOBKx9ZIQB3h1S"
                            + "RAGqaAVv7pOxAGWHgjTJ20zVb+o/ACXX5l8tolS6AAAAAElFTkSuQmCCifyr"
                            + "tLABAAA=")
                ));
    }

    public void testDuplicatesWithDpNames() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=74584
        mEnabled = Collections.singleton(IconDetector.DUPLICATES_NAMES);
        assertEquals("No warnings.",

                lintProject(
                        image("res/drawable-mdpi/foo_72dp.png", 72, 72).fill(10, 10, 20, 20, 0x00000000),
                        image("res/drawable-xhdpi/foo_36dp.png", 72, 72).fill(10, 10, 20, 20, 0x00000000)
                ));
    }

    public void testClaimedSize() throws Exception {
        // Check that icons which declare a dp size actually correspond to that dp size
        mEnabled = Collections.singleton(IconDetector.ICON_DIP_SIZE);
        assertEquals(""
                + "res/drawable-xhdpi/foo_30dp.png: Warning: Suspicious file name foo_30dp.png: The implied 30 dp size does not match the actual dp size (pixel size 72×72 in a drawable-xhdpi folder computes to 36×36 dp) [IconDipSize]\n"
                + "res/drawable-mdpi/foo_80dp.png: Warning: Suspicious file name foo_80dp.png: The implied 80 dp size does not match the actual dp size (pixel size 72×72 in a drawable-mdpi folder computes to 72×72 dp) [IconDipSize]\n"
                + "0 errors, 2 warnings\n",

                lintProject(
                        image("res/drawable-mdpi/foo_72dp.png", 72, 72).fill(10, 10, 20, 20, 0x00000000),  // ok
                        image("res/drawable-mdpi/foo_80dp.png", 72, 72).fill(10, 10, 20, 20, 0x00000000),  // wrong
                        image("res/drawable-xhdpi/foo_36dp.png", 72, 72).fill(10, 10, 20, 20, 0x00000000), // ok
                        image("res/drawable-xhdpi/foo_35dp.png", 72, 72).fill(10, 10, 20, 20, 0x00000000), // ~ok
                        image("res/drawable-xhdpi/foo_30dp.png", 72, 72).fill(10, 10, 20, 20, 0x00000000)  // wrong
                ));
    }

    public void testResConfigs1() throws Exception {
        // resConfigs in the Gradle model sets up the specific set of resource configs
        // that are included in the packaging: we use this to limit the set of required
        // densities
        mEnabled = Sets.newHashSet(IconDetector.ICON_DENSITIES, IconDetector.ICON_MISSING_FOLDER);
        assertEquals(""
                + "res: Warning: Missing density variation folders in res: drawable-hdpi [IconMissingDensityFolder]\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        image("res/drawable-mdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                        image("res/drawable-nodpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                        image("res/drawable-xlarge-nodpi-v11/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000)));
    }

    public void testResConfigs2() throws Exception {
        mEnabled = Sets.newHashSet(IconDetector.ICON_DENSITIES, IconDetector.ICON_MISSING_FOLDER);
        //noinspection all // Sample code
        assertEquals(""
                + "res/drawable-hdpi: Warning: Missing the following drawables in drawable-hdpi: sample_icon.gif (found in drawable-mdpi) [IconDensities]\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        // Use minSDK4 to ensure that we get warnings about missing drawables
                        manifest().minSdk(4),
                        image("res/drawable/ic_launcher.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                        image("res/drawable-mdpi/ic_launcher.png", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                        image("res/drawable-xhdpi/ic_launcher.png", 48, 48).fill(0xFF00FF30),
                        image("res/drawable-mdpi/sample_icon.gif", 48, 48).fill(10, 10, 20, 20, 0xFF00FFFF),
                        image("res/drawable-hdpi/ic_launcher.png", 72, 72).fill(10, 10, 30, 30, 0xFFFF00FF)));
    }

    public void testSplits1() throws Exception {
        // splits in the Gradle model sets up the specific set of resource configs
        // that are included in the packaging: we use this to limit the set of required
        // densities
        mEnabled = Sets.newHashSet(IconDetector.ICON_DENSITIES, IconDetector.ICON_MISSING_FOLDER);
        assertEquals(""
                + "res: Warning: Missing density variation folders in res: drawable-hdpi [IconMissingDensityFolder]\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        image("res/drawable-mdpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                        image("res/drawable-nodpi/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000),
                        image("res/drawable-xlarge-nodpi-v11/frame.png", 472, 290).fill(0xFFFFFFFF).fill(10, 10, 362, 280, 0x00000000)));
    }

    @Override
    protected TestLintClient createClient() {
        String testName = getName();
        if (testName.startsWith("testResConfigs")) {
            return createClientForTestResConfigs();
        } else if (testName.startsWith("testSplits")) {
            return createClientForTestSplits();
        } else {
            return super.createClient();
        }
    }

    private TestLintClient createClientForTestResConfigs() {

        // Set up a mock project model for the resource configuration test(s)
        // where we provide a subset of densities to be included

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
                        /*
                        Simulate variant freeBetaDebug in this setup:
                            defaultConfig {
                                ...
                                resConfigs "mdpi"
                            }
                            flavorDimensions  "pricing", "releaseType"
                            productFlavors {
                                beta {
                                    flavorDimension "releaseType"
                                    resConfig "en"
                                    resConfigs "nodpi", "hdpi"
                                }
                                normal { flavorDimension "releaseType" }
                                free { flavorDimension "pricing" }
                                paid { flavorDimension "pricing" }
                            }
                         */
                        ProductFlavor flavorFree = mock(ProductFlavor.class);
                        when(flavorFree.getName()).thenReturn("free");
                        when(flavorFree.getResourceConfigurations())
                                .thenReturn(Collections.emptyList());

                        ProductFlavor flavorNormal = mock(ProductFlavor.class);
                        when(flavorNormal.getName()).thenReturn("normal");
                        when(flavorNormal.getResourceConfigurations())
                                .thenReturn(Collections.emptyList());

                        ProductFlavor flavorPaid = mock(ProductFlavor.class);
                        when(flavorPaid.getName()).thenReturn("paid");
                        when(flavorPaid.getResourceConfigurations())
                                .thenReturn(Collections.emptyList());

                        ProductFlavor flavorBeta = mock(ProductFlavor.class);
                        when(flavorBeta.getName()).thenReturn("beta");
                        List<String> resConfigs = Arrays.asList("hdpi", "en", "nodpi");
                        when(flavorBeta.getResourceConfigurations()).thenReturn(resConfigs);

                        ProductFlavor defaultFlavor = mock(ProductFlavor.class);
                        when(defaultFlavor.getName()).thenReturn("main");
                        when(defaultFlavor.getResourceConfigurations()).thenReturn(
                                Collections.singleton("mdpi"));

                        ProductFlavorContainer containerBeta =
                                mock(ProductFlavorContainer.class);
                        when(containerBeta.getProductFlavor()).thenReturn(flavorBeta);

                        ProductFlavorContainer containerFree =
                                mock(ProductFlavorContainer.class);
                        when(containerFree.getProductFlavor()).thenReturn(flavorFree);

                        ProductFlavorContainer containerPaid =
                                mock(ProductFlavorContainer.class);
                        when(containerPaid.getProductFlavor()).thenReturn(flavorPaid);

                        ProductFlavorContainer containerNormal =
                                mock(ProductFlavorContainer.class);
                        when(containerNormal.getProductFlavor()).thenReturn(flavorNormal);

                        ProductFlavorContainer defaultContainer =
                                mock(ProductFlavorContainer.class);
                        when(defaultContainer.getProductFlavor()).thenReturn(defaultFlavor);

                        List<ProductFlavorContainer> containers = Arrays.asList(
                                containerPaid, containerFree, containerNormal, containerBeta
                        );

                        AndroidProject project = mock(AndroidProject.class);
                        when(project.getProductFlavors()).thenReturn(containers);
                        when(project.getDefaultConfig()).thenReturn(defaultContainer);
                        return project;
                    }

                    @Nullable
                    @Override
                    public Variant getCurrentVariant() {
                        List<String> productFlavorNames = Arrays.asList("free", "beta");
                        Variant mock = mock(Variant.class);
                        when(mock.getProductFlavors()).thenReturn(productFlavorNames);
                        return mock;
                    }
                };
            }
        };
    }

    private TestLintClient createClientForTestSplits() {

        // Set up a mock project model for the resource configuration test(s)
        // where we provide a subset of densities to be included

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
                        /*
                            Simulate variant debug in this setup:
                            splits {
                                density {
                                    enable true
                                    reset()
                                    include "mdpi", "hdpi"
                                }
                            }
                         */

                        ProductFlavor defaultFlavor = mock(ProductFlavor.class);
                        when(defaultFlavor.getName()).thenReturn("main");
                        when(defaultFlavor.getResourceConfigurations()).thenReturn(
                                Collections.emptyList());

                        ProductFlavorContainer defaultContainer =
                                mock(ProductFlavorContainer.class);
                        when(defaultContainer.getProductFlavor()).thenReturn(defaultFlavor);

                        AndroidProject project = mock(AndroidProject.class);
                        when(project.getProductFlavors()).thenReturn(
                                Collections.emptyList());
                        when(project.getDefaultConfig()).thenReturn(defaultContainer);
                        return project;
                    }

                    @Nullable
                    @Override
                    public Variant getCurrentVariant() {
                        Collection<AndroidArtifactOutput> outputs = Lists.newArrayList();

                        outputs.add(createAndroidArtifactOutput("", ""));
                        outputs.add(createAndroidArtifactOutput("DENSITY", "mdpi"));
                        outputs.add(createAndroidArtifactOutput("DENSITY", "hdpi"));

                        AndroidArtifact mainArtifact = mock(AndroidArtifact.class);
                        when(mainArtifact.getOutputs()).thenReturn(outputs);

                        List<String> productFlavorNames = Collections.emptyList();
                        Variant mock = mock(Variant.class);
                        when(mock.getProductFlavors()).thenReturn(productFlavorNames);
                        when(mock.getMainArtifact()).thenReturn(mainArtifact);
                        return mock;
                    }

                    private AndroidArtifactOutput createAndroidArtifactOutput(
                            @NonNull String filterType,
                            @NonNull String identifier) {
                        AndroidArtifactOutput artifactOutput = mock(
                                AndroidArtifactOutput.class);

                        OutputFile outputFile = mock(OutputFile.class);
                        if (filterType.isEmpty()) {
                            when(outputFile.getFilterTypes())
                                    .thenReturn(Collections.emptyList());
                            when(outputFile.getFilters())
                                    .thenReturn(Collections.emptyList());
                        } else {
                            when(outputFile.getFilterTypes())
                                    .thenReturn(Collections.singletonList(filterType));
                            List<FilterData> filters = Lists.newArrayList();
                            FilterData filter = mock(FilterData.class);
                            when(filter.getFilterType()).thenReturn(filterType);
                            when(filter.getIdentifier()).thenReturn(identifier);
                            filters.add(filter);
                            when(outputFile.getFilters()).thenReturn(filters);
                        }

                        // Work around wildcard capture
                        //when(artifactOutput.getOutputs()).thenReturn(outputFiles);
                        List<OutputFile> outputFiles = Collections.singletonList(outputFile);
                        OngoingStubbing<? extends Collection<? extends OutputFile>> when = when(
                                artifactOutput.getOutputs());
                        //noinspection unchecked,RedundantCast
                        ((OngoingStubbing<Collection<? extends OutputFile>>) (OngoingStubbing<?>) when)
                                .thenReturn(outputFiles);

                        return artifactOutput;
                    }
                };
            }
        };
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mActionBarTest = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import android.app.Activity;\n"
            + "import android.view.Menu;\n"
            + "import android.view.MenuInflater;\n"
            + "\n"
            + "public class ActionBarTest extends Activity {\n"
            + "    @Override\n"
            + "    public boolean onCreateOptionsMenu(Menu menu) {\n"
            + "        MenuInflater inflater = getMenuInflater();\n"
            + "        inflater.inflate(R.menu.menu, menu);\n"
            + "        return true;\n"
            + "    }\n"
            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mMenu = xml("res/menu/menu.xml", ""
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
            + "</menu>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mNotificationTest = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import android.app.Notification;\n"
            + "import android.app.Notification.Builder;\n"
            + "import android.content.Context;\n"
            + "import android.graphics.Bitmap;\n"
            + "\n"
            + "@SuppressWarnings({ \"deprecation\", \"unused\", \"javadoc\" })\n"
            + "class NotificationTest {\n"
            + "    public void test1() {\n"
            + "        Notification notification = new Notification(R.drawable.icon1, \"Test1\", 0);\n"
            + "    }\n"
            + "\n"
            + "    public void test2() {\n"
            + "        int resource = R.drawable.icon2;\n"
            + "        Notification notification = new Notification(resource, \"Test1\", 0);\n"
            + "    }\n"
            + "\n"
            + "    public void test3() {\n"
            + "        int icon = R.drawable.icon3;\n"
            + "        CharSequence tickerText = \"Hello\";\n"
            + "        long when = System.currentTimeMillis();\n"
            + "        Notification notification = new Notification(icon, tickerText, when);\n"
            + "    }\n"
            + "\n"
            + "    public void test4(Context context, String sender, String subject, Bitmap bitmap) {\n"
            + "        Notification notification = new Notification.Builder(context)\n"
            + "                .setContentTitle(\"New mail from \" + sender.toString())\n"
            + "                .setContentText(subject).setSmallIcon(R.drawable.icon4)\n"
            + "                .setLargeIcon(bitmap).build();\n"
            + "    }\n"
            + "\n"
            + "    public void test5(Context context, String sender, String subject, Bitmap bitmap) {\n"
            + "        Notification notification = new Builder(context)\n"
            + "                .setContentTitle(\"New mail from \" + sender.toString())\n"
            + "                .setContentText(subject).setSmallIcon(R.drawable.icon5)\n"
            + "                .setLargeIcon(bitmap).build();\n"
            + "    }\n"
            + "}\n");

}
