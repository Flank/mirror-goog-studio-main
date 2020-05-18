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

package com.android.build.gradle.internal.res.shrinker;

import static java.io.File.separatorChar;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.res.shrinker.gatherer.ResourcesGathererFromRTxt;
import com.android.build.gradle.internal.res.shrinker.graph.RawResourcesGraphBuilder;
import com.android.build.gradle.internal.res.shrinker.obfuscation.ProguardMappingsRecorder;
import com.android.build.gradle.internal.res.shrinker.usages.DexUsageRecorder;
import com.android.build.gradle.internal.res.shrinker.usages.XmlAndroidManifestUsageRecorder;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** TODO: Test Resources#getIdentifier() handling */
@SuppressWarnings("SpellCheckingInspection")
public class ResourceShrinkerImplTest {

    enum CodeInput {
        NO_SHRINKER,
        PROGUARD,
        R8
    }

    @ClassRule public static TemporaryFolder sTemporaryFolder = new TemporaryFolder();

    @Test
    public void testObfuscatedInPlace() throws Exception {
        check(CodeInput.PROGUARD, true);
    }

    @Test
    public void testObfuscatedCopy() throws Exception {
        check(CodeInput.PROGUARD, false);
    }

    @Test
    public void testNoProGuardInPlace() throws Exception {
        check(CodeInput.NO_SHRINKER, true);
    }

    @Test
    public void testNoProGuardCopy() throws Exception {
        check(CodeInput.NO_SHRINKER, false);
    }

    @Test
    public void testR8InPlace() throws Exception {
        check(CodeInput.R8, true);
    }

    @Test
    public void testR8Copy() throws Exception {
        check(CodeInput.R8, false);
    }

    @Test
    public void testToolsKeepDiscard() throws Exception {
        File dir = sTemporaryFolder.newFolder();
        File resources = createResourceFolder(dir, true);

        File rDir = createResourceTextFile(dir);
        File classes = createR8Dex(dir);
        File mergedManifest = createMergedManifest(dir);
        File mapping = createMappingFile(dir);

        ResourceShrinkerImpl analyzer =
                new ResourceShrinkerImpl(
                        new ResourcesGathererFromRTxt(rDir, "com.example.shrinkunittest.app"),
                        mapping == null ? null : new ProguardMappingsRecorder(mapping.toPath()),
                        Arrays.asList(
                                new DexUsageRecorder(classes.toPath()),
                                new XmlAndroidManifestUsageRecorder(mergedManifest.toPath())),
                        new RawResourcesGraphBuilder(
                                Collections.singletonList(resources.toPath())),
                        NoDebugReporter.INSTANCE,
                        ApkFormat.BINARY);
        analyzer.analyze();
        checkState(analyzer);

        assertEquals(
                ""
                        + "@attr/myAttr1 : reachable=false\n"
                        + "@attr/myAttr2 : reachable=false\n"
                        + "@dimen/activity_horizontal_margin : reachable=true\n"
                        + "@dimen/activity_vertical_margin : reachable=true\n"
                        + "@drawable/avd_heart_fill : reachable=false\n"
                        + "    @drawable/avd_heart_fill_1\n"
                        + "    @drawable/avd_heart_fill_2\n"
                        + "@drawable/avd_heart_fill_1 : reachable=true\n"
                        + "@drawable/avd_heart_fill_2 : reachable=false\n"
                        + "@drawable/ic_launcher : reachable=true\n"
                        + "@drawable/unused : reachable=false\n"
                        + "@id/action_settings : reachable=true\n"
                        + "@id/action_settings2 : reachable=false\n"
                        + "@layout/activity_main : reachable=true\n"
                        + "    @dimen/activity_vertical_margin\n"
                        + "    @dimen/activity_horizontal_margin\n"
                        + "    @string/hello_world\n"
                        + "    @style/MyStyle_Child\n"
                        + "@menu/main : reachable=false\n"
                        + "    @string/action_settings\n"
                        + "@menu/menu2 : reachable=false\n"
                        + "    @string/action_settings2\n"
                        + "@raw/android_wear_micro_apk : reachable=true\n"
                        + "@raw/index1 : reachable=false\n"
                        + "    @raw/my_used_raw_drawable\n"
                        + "@raw/keep : reachable=false\n"
                        + "@raw/my_js : reachable=false\n"
                        + "@raw/my_used_raw_drawable : reachable=false\n"
                        + "@raw/styles2 : reachable=false\n"
                        + "@string/action_settings : reachable=false\n"
                        + "@string/action_settings2 : reachable=false\n"
                        + "@string/alias : reachable=false\n"
                        + "    @string/app_name\n"
                        + "@string/app_name : reachable=true\n"
                        + "@string/hello_world : reachable=true\n"
                        + "@style/AppTheme : reachable=false\n"
                        + "@style/MyStyle : reachable=true\n"
                        + "@style/MyStyle_Child : reachable=true\n"
                        + "    @style/MyStyle\n"
                        + "@xml/android_wear_micro_apk : reachable=true\n"
                        + "    @raw/android_wear_micro_apk\n",
                analyzer.model.getUsageModel().dumpResourceModel());
    }

    @Test
    public void testConfigOutput() throws Exception {
        File dir = sTemporaryFolder.newFolder();

        File mapping;
        File classes;

        classes = createR8Dex(dir);
        mapping = null;

        File rDir = createResourceTextFile(dir);
        File mergedManifest = createMergedManifest(dir);
        File resources = createResourceFolder(dir, false);

        ResourceShrinkerImpl analyzer =
                new ResourceShrinkerImpl(
                        new ResourcesGathererFromRTxt(rDir, "com.example.shrinkunittest.app"),
                        mapping == null ? null : new ProguardMappingsRecorder(mapping.toPath()),
                        Arrays.asList(
                                new DexUsageRecorder(classes.toPath()),
                                new XmlAndroidManifestUsageRecorder(mergedManifest.toPath())),
                        new RawResourcesGraphBuilder(
                                Collections.singletonList(resources.toPath())),
                        NoDebugReporter.INSTANCE,
                        ApkFormat.BINARY);
        analyzer.analyze();
        checkState(analyzer);
        assertEquals(
                ""
                        + "attr/myAttr1#remove\n"
                        + "attr/myAttr2#remove\n"
                        + "dimen/activity_horizontal_margin#\n"
                        + "dimen/activity_vertical_margin#\n"
                        + "drawable/avd_heart_fill#remove\n"
                        + "drawable/avd_heart_fill_1#remove\n"
                        + "drawable/avd_heart_fill_2#remove\n"
                        + "drawable/ic_launcher#\n"
                        + "drawable/unused#remove\n"
                        + "id/action_settings#\n"
                        + "id/action_settings2#remove\n"
                        + "layout/activity_main#\n"
                        + "menu/main#\n"
                        + "menu/menu2#remove\n"
                        + "raw/android_wear_micro_apk#\n"
                        + "raw/index1#remove\n"
                        + "raw/my_js#remove\n"
                        + "raw/my_used_raw_drawable#remove\n"
                        + "raw/styles2#remove\n"
                        + "string/action_settings#\n"
                        + "string/action_settings2#remove\n"
                        + "string/alias#remove\n"
                        + "string/app_name#\n"
                        + "string/hello_world#\n"
                        + "style/AppTheme#remove\n"
                        + "style/MyStyle#\n"
                        + "style/MyStyle_Child#\n"
                        + "xml/android_wear_micro_apk#\n",
                analyzer.model.getUsageModel().dumpConfig());
    }

    private void check(CodeInput codeInput, boolean inPlace) throws Exception {
        File dir = sTemporaryFolder.newFolder();

        File mapping;
        File classes;
        switch (codeInput) {
            case PROGUARD:
                classes = createProguardedDex(dir);
                mapping = createMappingFile(dir);
                break;
            case NO_SHRINKER:
                classes = createUnproguardedDex(dir);
                mapping = null;
                break;
            case R8:
                classes = createR8Dex(dir);
                mapping = createMappingFile(dir);
                break;
            default:
                throw new AssertionError();
        }
        File rSource = createResourceTextFile(dir);

        File mergedManifest = createMergedManifest(dir);
        File resources = createResourceFolder(dir, false);

        ResourceShrinkerImpl analyzer =
                new ResourceShrinkerImpl(
                        new ResourcesGathererFromRTxt(rSource, "com.example.shrinkunittest.app"),
                        mapping == null ? null : new ProguardMappingsRecorder(mapping.toPath()),
                        Arrays.asList(
                                new DexUsageRecorder(classes.toPath()),
                                new XmlAndroidManifestUsageRecorder(mergedManifest.toPath())),
                        new RawResourcesGraphBuilder(
                                Collections.singletonList(resources.toPath())),
                        NoDebugReporter.INSTANCE,
                        ApkFormat.BINARY);
        analyzer.analyze();
        checkState(analyzer);
        assertEquals(
                ""
                        + "@attr/myAttr1 : reachable=false\n"
                        + "@attr/myAttr2 : reachable=false\n"
                        + "@dimen/activity_horizontal_margin : reachable=true\n"
                        + "@dimen/activity_vertical_margin : reachable=true\n"
                        + "@drawable/avd_heart_fill : reachable=false\n"
                        + "    @drawable/avd_heart_fill_1\n"
                        + "    @drawable/avd_heart_fill_2\n"
                        + "@drawable/avd_heart_fill_1 : reachable=false\n"
                        + "@drawable/avd_heart_fill_2 : reachable=false\n"
                        + "@drawable/ic_launcher : reachable=true\n"
                        + "@drawable/unused : reachable=false\n"
                        + "@id/action_settings : reachable=true\n"
                        + "@id/action_settings2 : reachable=false\n"
                        + "@layout/activity_main : reachable=true\n"
                        + "    @dimen/activity_vertical_margin\n"
                        + "    @dimen/activity_horizontal_margin\n"
                        + "    @string/hello_world\n"
                        + "    @style/MyStyle_Child\n"
                        + "@menu/main : reachable=true\n"
                        + "    @string/action_settings\n"
                        + "@menu/menu2 : reachable=false\n"
                        + "    @string/action_settings2\n"
                        + "@raw/android_wear_micro_apk : reachable=true\n"
                        + "@raw/index1 : reachable=false\n"
                        + "    @raw/my_used_raw_drawable\n"
                        + "@raw/my_js : reachable=false\n"
                        + "@raw/my_used_raw_drawable : reachable=false\n"
                        + "@raw/styles2 : reachable=false\n"
                        + "@string/action_settings : reachable=true\n"
                        + "@string/action_settings2 : reachable=false\n"
                        + "@string/alias : reachable=false\n"
                        + "    @string/app_name\n"
                        + "@string/app_name : reachable=true\n"
                        + "@string/hello_world : reachable=true\n"
                        + "@style/AppTheme : reachable=false\n"
                        + "@style/MyStyle : reachable=true\n"
                        + "@style/MyStyle_Child : reachable=true\n"
                        + "    @style/MyStyle\n"
                        + "@xml/android_wear_micro_apk : reachable=true\n"
                        + "    @raw/android_wear_micro_apk\n",
                analyzer.model.getUsageModel().dumpResourceModel());

        File unusedBitmap = new File(resources, "drawable-xxhdpi" + separatorChar + "unused.png");
        assertTrue(unusedBitmap.exists());

        List<File> files = Lists.newArrayList();
        addFiles(resources, files);
        Collections.sort(files, (file, file2) -> file.getPath().compareTo(file2.getPath()));

        // Generate a .zip file from a directory
        File uncompressedFile = File.createTempFile("uncompressed", ".ap_");
        String prefix = resources.getPath() + File.separatorChar;
        try (FileOutputStream fos = new FileOutputStream(uncompressedFile);
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (File file : files) {
                if (file.equals(resources)) {
                    continue;
                }
                assertTrue(file.getPath().startsWith(prefix));
                String relative =
                        "res/"
                                + file.getPath()
                                        .substring(prefix.length())
                                        .replace(File.separatorChar, '/');
                boolean isValuesFile = relative.equals("res/values/values.xml");
                if (isValuesFile) {
                    relative = "resources.arsc";
                }
                ZipEntry ze = new ZipEntry(relative);
                zos.putNextEntry(ze);
                if (!file.isDirectory() && !isValuesFile) {
                    byte[] bytes = Files.toByteArray(file);
                    zos.write(bytes);
                }
                zos.closeEntry();
            }
        }

        assertEquals(
                ""
                        + "res/drawable\n"
                        + "res/drawable-hdpi\n"
                        + "res/drawable-hdpi/ic_launcher.png\n"
                        + "res/drawable-mdpi\n"
                        + "res/drawable-mdpi/ic_launcher.png\n"
                        + "res/drawable-xxhdpi\n"
                        + "res/drawable-xxhdpi/ic_launcher.png\n"
                        + "res/drawable-xxhdpi/unused.png\n"
                        + "res/drawable/avd_heart_fill.xml\n"
                        + "res/layout\n"
                        + "res/layout/activity_main.xml\n"
                        + "res/menu\n"
                        + "res/menu/main.xml\n"
                        + "res/menu/menu2.xml\n"
                        + "res/raw\n"
                        + "res/raw/android_wear_micro_apk.apk\n"
                        + "res/raw/index1.html\n"
                        + "res/raw/my_js.js\n"
                        + "res/raw/styles2.css\n"
                        + "res/values\n"
                        + "resources.arsc\n"
                        + "res/xml\n"
                        + "res/xml/android_wear_micro_apk.xml\n",
                dumpZipContents(uncompressedFile));

        File compressedFile = File.createTempFile("compressed", ".ap_");
        analyzer.rewriteResourceZip(uncompressedFile, compressedFile);

        assertEquals(dumpZipContents(uncompressedFile), dumpZipContents(compressedFile));
        assertArrayEquals(
                DummyContent.TINY_PNG,
                getZipContents(compressedFile, "res/drawable-xxhdpi/unused.png"));
        assertArrayEquals(
                DummyContent.TINY_BINARY_XML,
                getZipContents(compressedFile, "res/drawable/avd_heart_fill.xml"));

        uncompressedFile.delete();
        compressedFile.delete();
        deleteDir(dir);
    }

    private static String dumpZipContents(File zipFile) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(zipFile)) {
            try (ZipInputStream zis = new ZipInputStream(fis)) {
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    sb.append(entry.getName());
                    sb.append('\n');
                    entry = zis.getNextEntry();
                }
            }
        }

        return sb.toString();
    }

    @Nullable
    private static byte[] getZipContents(File zipFile, String name) throws IOException {
        try (FileInputStream fis = new FileInputStream(zipFile)) {
            try (ZipInputStream zis = new ZipInputStream(fis)) {
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    if (name.equals(entry.getName())) {
                        return ByteStreams.toByteArray(zis);
                    }
                    entry = zis.getNextEntry();
                }
            }
        }

        return null;
    }

    private static void addFiles(File file, List<File> files) {
        files.add(file);
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File f : list) {
                    addFiles(f, files);
                }
            }
        }
    }

    private static File createResourceFolder(File dir, boolean addKeepXml) throws IOException {
        File resources = new File(dir, "app/build/res/all/release".replace('/', separatorChar));
        //noinspection ResultOfMethodCallIgnored
        resources.mkdirs();

        createFile(resources, "drawable-hdpi/ic_launcher.png", new byte[0]);
        createFile(resources, "drawable-mdpi/ic_launcher.png", new byte[0]);
        createFile(resources, "drawable-xxhdpi/ic_launcher.png", new byte[0]);
        createFile(resources, "drawable-xxhdpi/unused.png", new byte[0]);

        createFile(
                resources,
                "layout/activity_main.xml",
                ""
                        + "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"match_parent\"\n"
                        + "    android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n"
                        + "    android:paddingRight=\"@dimen/activity_horizontal_margin\"\n"
                        + "    android:paddingTop=\"@dimen/activity_vertical_margin\"\n"
                        + "    android:paddingBottom=\"@dimen/activity_vertical_margin\"\n"
                        + "    tools:context=\".MainActivity\">\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        style=\"@style/MyStyle.Child\"\n"
                        + "        android:text=\"@string/hello_world\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\" />\n"
                        + "\n"
                        + "</RelativeLayout>");

        createFile(
                resources,
                "menu/main.xml",
                ""
                        + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    tools:context=\".MainActivity\" >\n"
                        + "    <item android:id=\"@+id/action_settings\"\n"
                        + "        android:title=\"@string/action_settings\"\n"
                        + "        android:orderInCategory=\"100\"\n"
                        + "        android:showAsAction=\"never\" />\n"
                        + "</menu>");

        createFile(
                resources,
                "menu/menu2.xml",
                ""
                        + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    tools:context=\".MainActivity\" >\n"
                        + "    <item android:id=\"@+id/action_settings2\"\n"
                        + "        android:title=\"@string/action_settings2\"\n"
                        + "        android:orderInCategory=\"100\"\n"
                        + "        android:showAsAction=\"never\" />\n"
                        + "</menu>");

        createFile(
                resources,
                "values/values.xml",
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <attr name=\"myAttr1\" format=\"integer\" />\n"
                        + "    <attr name=\"myAttr2\" format=\"boolean\" />\n"
                        + "\n"
                        + "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n"
                        + "    <dimen name=\"activity_vertical_margin\">16dp</dimen>\n"
                        + "\n"
                        + "    <string name=\"action_settings\">Settings</string>\n"
                        + "    <string name=\"action_settings2\">Settings2</string>\n"
                        + "    <string name=\"alias\"> @string/app_name </string>\n"
                        + "    <string name=\"app_name\">ShrinkUnitTest</string>\n"
                        + "    <string name=\"hello_world\">Hello world!</string>\n"
                        + "\n"
                        + "    <style name=\"AppTheme\" parent=\"android:Theme.Holo\"></style>\n"
                        + "\n"
                        + "    <style name=\"MyStyle\">\n"
                        + "        <item name=\"myAttr1\">50</item>\n"
                        + "    </style>\n"
                        + "\n"
                        + "    <style name=\"MyStyle.Child\">\n"
                        + "        <item name=\"myAttr2\">true</item>\n"
                        + "    </style>\n"
                        + "\n"
                        + "</resources>");

        createFile(resources, "raw/android_wear_micro_apk.apk", "<binary data>");

        createFile(
                resources,
                "xml/android_wear_micro_apk.xml",
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<wearableApp package=\"com.example.shrinkunittest.app\">\n"
                        + "    <versionCode>1</versionCode>\n"
                        + "    <versionName>1.0' platformBuildVersionName='5.0-1521886</versionName>\n"
                        + "    <rawPathResId>android_wear_micro_apk</rawPathResId>\n"
                        + "</wearableApp>");

        if (addKeepXml) {
            createFile(
                    resources,
                    "raw/keep.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "    tools:keep=\"@drawable/avd_heart_fill_1\" "
                            + "    tools:discard=\"@menu/main\" />");
        }

        // RAW content for HTML/web
        createFile(
                resources,
                "raw/index1.html",
                ""
                        // TODO: Test single quotes, attribute without quotes, spaces around = etc,
                        // prologue, xhtml
                        + "<!DOCTYPE html>\n"
                        + "<html>\n"
                        + "<!--\n"
                        + " Blah blah\n"
                        + "-->\n"
                        + "<head>\n"
                        + "  <meta charset=\"utf-8\">\n"
                        + "  <link href=\"http://fonts.googleapis.com/css?family=Alegreya:400italic,900italic|Alegreya+Sans:300\" rel=\"stylesheet\">\n"
                        + "  <link href=\"http://yui.yahooapis.com/2.8.0r4/build/reset/reset-min.css\" rel=\"stylesheet\">\n"
                        + "  <link href=\"static/landing.css\" rel=\"stylesheet\">\n"
                        + "  <script src=\"http://ajax.googleapis.com/ajax/libs/jquery/2.0.3/jquery.min.js\"></script>\n"
                        + "  <script src=\"static/modernizr.custom.14469.js\"></script>\n"
                        + "  <meta name=\"viewport\" content=\"width=690\">\n"
                        + "  <style type=\"text/css\">\n"
                        + "html, body {\n"
                        + "  margin: 0;\n"
                        + "  height: 100%;\n"
                        + "  background-image: url(file:///android_res/raw/my_used_raw_drawable);\n"
                        + "}\n"
                        + "</style>"
                        + "</head>\n"
                        + "<body>\n"
                        + "\n"
                        + "<div id=\"container\">\n"
                        + "\n"
                        + "  <div id=\"logo\"></div>\n"
                        + "\n"
                        + "  <div id=\"text\">\n"
                        + "    <p>\n"
                        + "      More ignored text here\n"
                        + "    </p>\n"
                        + "  </div>\n"
                        + "\n"
                        + "  <a id=\"playlink\" href=\"file/foo.png\">&nbsp;</a>\n"
                        + "</div>\n"
                        + "<script>\n"
                        + "\n"
                        + "if (Modernizr.cssanimations &&\n"
                        + "    Modernizr.svg &&\n"
                        + "    Modernizr.csstransforms3d &&\n"
                        + "    Modernizr.csstransitions) {\n"
                        + "\n"
                        + "  // progressive enhancement\n"
                        + "  $('#device-screen').css('display', 'block');\n"
                        + "  $('#device-frame').css('background-image', 'url( 'drawable-mdpi/tilted.png')' );\n"
                        + "  $('#opentarget').css('visibility', 'visible');\n"
                        + "  $('body').addClass('withvignette');\n"
                        + "</script>\n"
                        + "\n"
                        + "</body>\n"
                        + "</html>");

        createFile(
                resources,
                "raw/styles2.css",
                ""
                        + "/**\n"
                        + " * Copyright 2014 Google Inc.\n"
                        + " */\n"
                        + "\n"
                        + "html, body {\n"
                        + "  margin: 0;\n"
                        + "  height: 100%;\n"
                        + "  -webkit-font-smoothing: antialiased;\n"
                        + "}\n"
                        + "#logo {\n"
                        + "  position: absolute;\n"
                        + "  left: 0;\n"
                        + "  top: 60px;\n"
                        + "  width: 250px;\n"
                        + "  height: 102px;\n"
                        + "  background-image: url(img2.png);\n"
                        + "  background-repeat: no-repeat;\n"
                        + "  background-size: contain;\n"
                        + "  opacity: 0.7;\n"
                        + "  z-index: 100;\n"
                        + "}\n"
                        + "device-frame {\n"
                        + "  position: absolute;\n"
                        + "  right: -70px;\n"
                        + "  top: 0;\n"
                        + "  width: 420px;\n"
                        + "  height: 500px;\n"
                        + "  background-image: url(tilted_fallback.jpg);\n"
                        + "  background-size: cover;\n"
                        + "  -webkit-user-select: none;\n"
                        + "  -moz-user-select: none;\n"
                        + "}");

        createFile(
                resources,
                "raw/my_js.js",
                ""
                        + "function $(id) {\n"
                        + "  return document.getElementById(id);\n"
                        + "}\n"
                        + "\n"
                        + "/* Ignored block comment: \"ignore me\" */\n"
                        + "function show(id) {\n"
                        + "  $(id).style.display = \"block\";\n"
                        + "}\n"
                        + "\n"
                        + "function hide(id) {\n"
                        + "  $(id).style.display = \"none\";\n"
                        + "}\n"
                        + "// Line comment\n"
                        + "function onStatusBoxFocus(elt) {\n"
                        + "  elt.value = '';\n"
                        + "  elt.style.color = \"#000\";\n"
                        + "  show('status_submit');\n"
                        + "}\n");

        // Nested resources
        createFile(
                resources,
                "drawable/avd_heart_fill.xml",
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<animated-vector\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:aapt=\"http://schemas.android.com/aapt\">\n"
                        + "\n"
                        + "    <aapt:attr name=\"android:drawable\">\n"
                        + "        <vector\n"
                        + "            android:width=\"56dp\"\n"
                        + "            android:height=\"56dp\"\n"
                        + "            android:viewportWidth=\"56\"\n"
                        + "            android:viewportHeight=\"56\">\n"
                        + "        </vector>\n"
                        + "    </aapt:attr>\n"
                        + "\n"
                        + "    <target android:name=\"clip\">\n"
                        + "        <aapt:attr name=\"android:animation\">\n"
                        + "            <objectAnimator\n"
                        + "                android:propertyName=\"pathData\"\n"
                        + "                android:interpolator=\"@android:interpolator/fast_out_slow_in\" />\n"
                        + "        </aapt:attr>\n"
                        + "    </target>\n"
                        + "</animated-vector>");

        return resources;
    }

    private static File createMergedManifest(File dir) throws IOException {
        return createFile(
                dir,
                "app/build/manifests/release/AndroidManifest.xml",
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" android:versionCode=\"1\" android:versionName=\"1.0\" package=\"com.example.shrinkunittest.app\">\n"
                        + "    <uses-sdk android:minSdkVersion=\"20\" android:targetSdkVersion=\"19\"/>\n"
                        + "\n"
                        + "    <application android:allowBackup=\"true\" android:icon=\"@drawable/ic_launcher\" android:label=\"@string/app_name\">\n"
                        + "        <activity android:label=\"@string/app_name\" android:name=\"com.example.shrinkunittest.app.MainActivity\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data\n"
                        + "            android:name=\"com.google.android.wearable.beta.app\"\n"
                        + "            android:resource=\"@xml/android_wear_micro_apk\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>");
    }

    private File createResourceTextFile(File dir) throws IOException {
        File rDir = new File(dir, "app/build/source/r/release".replace('/', separatorChar));
        rDir.mkdirs();
        createFile(
                rDir,
                "com/example/shrinkunittest/app/R.txt",
                "int attr myAttr1 0x7f010000\n"
                        + "int attr myAttr2 0x7f010001\n"
                        + "int dimen activity_horizontal_margin 0x7f040000\n"
                        + "int dimen activity_vertical_margin 0x7f040001\n"
                        + "int drawable ic_launcher 0x7f020000\n"
                        + "int drawable unused 0x7f020001\n"
                        + "int id action_settings 0x7f080000\n"
                        + "int id action_settings2 0x7f080001\n"
                        + "int layout activity_main 0x7f030000\n"
                        + "int menu main 0x7f070000\n"
                        + "int raw android_wear_micro_apk 0x7f090000\n"
                        + "int raw index1 0x7f090001\n"
                        + "int raw styles2 0x7f090002\n"
                        + "int raw my_js 0x7f090003\n"
                        + "int raw my_used_raw_drawable 0x7f090004\n"
                        + "int string action_settings 0x7f050000\n"
                        + "int string action_settings2 0x7f050004\n"
                        + "int string alias 0x7f050001\n"
                        + "int string app_name 0x7f050002\n"
                        + "int string hello_world 0x7f050003\n"
                        + "int style AppTheme 0x7f060000\n"
                        + "int style MyStyle 0x7f060001\n"
                        + "int style MyStyle_Child 0x7f060002\n"
                        + "int xml android_wear_micro_apk 0x7f0a0000\n");

        return rDir;
    }

    private static File createR8Dex(File dir) throws IOException {
        /*
         Dex file contain the activity below, it has been produced with R8 with minSdkVersion 25.

         package com.example.shrinkunittest.app;
         import android.app.Activity;
         import android.os.Bundle;
         import android.view.Menu;
         import android.view.MenuItem;
         public class MainActivity extends Activity {
           public MainActivity() {
           }
           protected void onCreate(Bundle var1) {
             super.onCreate(var1);
             this.setContentView(2130903040);
           }
           public boolean onCreateOptionsMenu(Menu var1) {
             this.getMenuInflater().inflate(2131165184, var1);
             return true;
           }
           public boolean onOptionsItemSelected(MenuItem var1) {
             int var2 = var1.getItemId();
             return var2 == 2131230720 ? true : super.onOptionsItemSelected(var1);
           }
         }
        */
        byte[] dexContent =
                Resources.toByteArray(Resources.getResource("resourceShrinker/classes.dex"));
        return createFile(
                dir, "app/build/intermediates/transforms/r8/debug/0/classes.dex", dexContent);
    }

    private static File createMappingFile(File dir) throws IOException {
        return createFile(
                dir,
                "app/build/proguard/release/mapping.txt",
                ""
                        + "com.example.shrinkunittest.app.MainActivity -> com.example.shrinkunittest.app.MainActivity:\n"
                        + "    void onCreate(android.os.Bundle) -> onCreate\n"
                        + "    boolean onCreateOptionsMenu(android.view.Menu) -> onCreateOptionsMenu\n"
                        + "    boolean onOptionsItemSelected(android.view.MenuItem) -> onOptionsItemSelected\n"
                        + "com.foo.bar.R$layout -> com.foo.bar.t:\n"
                        + "    int checkable_option_view_layout -> a\n"
                        + "    int error_layout -> b\n"
                        + "    int glyph_button_icon_only -> c\n"
                        + "    int glyph_button_icon_with_text_below -> d\n"
                        + "    int glyph_button_icon_with_text_right -> e\n"
                        + "    int structure_status_view -> f\n"
                        + "android.support.annotation.FloatRange -> android.support.annotation.FloatRange:\n"
                        + "    double from() -> from\n"
                        + "    double to() -> to\n"
                        + "    boolean fromInclusive() -> fromInclusive\n"
                        + "    boolean toInclusive() -> toInclusive\n");
    }

    private static File createProguardedDex(File dir) throws IOException {
        byte[] dexContent =
                Resources.toByteArray(Resources.getResource("resourceShrinker/proguarded.dex"));
        return createFile(
                dir, "app/build/intermediates/transforms/r8/debug/0/classes.dex", dexContent);
    }

    private static File createUnproguardedDex(File dir) throws IOException {
        byte[] dexContent =
                Resources.toByteArray(Resources.getResource("resourceShrinker/notshrinked.dex"));
        return createFile(
                dir, "app/build/intermediates/transforms/r8/debug/0/classes.dex", dexContent);
    }

    /** Utility method to generate byte array literal dump (used by classesJarBytecode above) */
    @SuppressWarnings("UnusedDeclaration") // Utility for future .class/.jar additions
    public static void dumpBytes(File file) throws IOException {
        byte[] bytes = Files.toByteArray(file);
        int count = 0;
        for (byte b : bytes) {
            System.out.print("(byte)" + Byte.toString(b) + ", ");
            count++;
            if (count == 8) {
                count = 0;
                System.out.println();
            }
        }

        System.out.println();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected static void deleteDir(File root) {
        if (root.exists()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDir(file);
                    } else {
                        file.delete();
                    }
                }
            }
            root.delete();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NonNull
    private static File createFile(File dir, String relative) throws IOException {
        File file = new File(dir, relative.replace('/', separatorChar));
        file.getParentFile().mkdirs();
        return file;
    }

    @NonNull
    private static File createFile(File dir, String relative, String contents) throws IOException {
        File file = createFile(dir, relative);
        Files.asCharSink(file, Charsets.UTF_8).write(contents);
        return file;
    }

    @NonNull
    private static File createFile(File dir, String relative, byte[] contents) throws IOException {
        File file = createFile(dir, relative);
        Files.write(contents, file);
        return file;
    }

    private static void checkState(ResourceShrinkerImpl analyzer) {
        List<Resource> resources = analyzer.model.getUsageModel().getResources();
        Collections.sort(
                resources,
                new Comparator<Resource>() {
                    @Override
                    public int compare(Resource resource1, Resource resource2) {
                        int delta = resource1.type.compareTo(resource2.type);
                        if (delta != 0) {
                            return delta;
                        }
                        return resource1.name.compareTo(resource2.name);
                    }
                });

        // Ensure unique
        Resource prev = null;
        for (Resource resource : resources) {
            assertTrue(
                    resource + " and " + prev,
                    prev == null || resource.type != prev.type || !resource.name.equals(prev.name));
            prev = resource;
        }
    }
}
