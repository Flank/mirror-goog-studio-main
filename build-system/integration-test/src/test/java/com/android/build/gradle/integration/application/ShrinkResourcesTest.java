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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import static com.android.build.gradle.tasks.ResourceUsageAnalyzer.REPLACE_DELETED_WITH_EMPTY;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static java.io.File.separator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.tasks.ResourceUsageAnalyzer;
import com.android.builder.model.AndroidProject;
import com.android.testutils.apk.Apk;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for shrink. */
@RunWith(Parameterized.class)
public class ShrinkResourcesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("shrink").create();

    @Parameterized.Parameters(name = "useProguard {0}")
    public static List<Boolean> data() {
        return ImmutableList.of(true, false);
    }

    @Parameterized.Parameter public boolean useProguard;

    @Test
    public void checkShrinkResources() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.buildTypes.release.useProguard = " + useProguard);

        project.execute("clean", "assembleRelease", "assembleDebug", "assembleMinifyDontShrink");

        File intermediates = project.file("build/" + AndroidProject.FD_INTERMEDIATES);

        // The release target has shrinking enabled.
        // The minifyDontShrink target has proguard but no shrinking enabled.
        // The debug target has neither proguard nor shrinking enabled.

        Apk apkRelease = project.getApk(ApkType.RELEASE);
        Apk apkDebug = project.getApk("debug");
        Apk apkProguardOnly = project.getApk(ApkType.of("minifyDontShrink", false));

        assertTrue(apkDebug.toString() + " is not a file", Files.isRegularFile(apkDebug.getFile()));
        assertTrue(
                apkRelease.toString() + " is not a file",
                Files.isRegularFile(apkRelease.getFile()));
        assertTrue(
                apkProguardOnly.toString() + " is not a file",
                Files.isRegularFile(apkProguardOnly.getFile()));

        File compressed =
                new File(
                        intermediates,
                        "res_stripped/release" + separator + "resources-release-stripped.ap_");
        File uncompressed =
                new File(intermediates, "res/release" + separator + "resources-release.ap_");
        assertTrue(compressed.toString() + " is not a file", compressed.isFile());
        assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());

        // Check that there is no shrinking in the other two targets:
        assertTrue(
                new File(intermediates, "res/debug" + separator + "resources-debug.ap_").exists());
        assertFalse(
                new File(
                                intermediates,
                                "res_stripped/debug" + separator + "resources-debug-stripped.ap_")
                        .exists());
        assertTrue(
                new File(
                                intermediates,
                                "res/minifyDontShrink"
                                        + separator
                                        + "resources-minifyDontShrink.ap_")
                        .exists());
        assertFalse(
                new File(
                                intermediates,
                                "res_stripped/minifyDontShrink"
                                        + separator
                                        + "resources-minifyDontShrink-stripped.ap_")
                        .exists());

        String expectedUnstrippedApk =
                ""
                        + "AndroidManifest.xml\n"
                        + "classes.dex\n"
                        + "res/drawable/force_remove.xml\n"
                        + "res/raw/keep.xml\n"
                        + "res/layout/l_used_a.xml\n"
                        + "res/layout/l_used_b2.xml\n"
                        + "res/layout/l_used_c.xml\n"
                        + "res/layout/lib_unused.xml\n"
                        + "res/layout/prefix_3_suffix.xml\n"
                        + "res/layout/prefix_used_1.xml\n"
                        + "res/layout/prefix_used_2.xml\n"
                        + "resources.arsc\n"
                        + "res/layout/unused1.xml\n"
                        + "res/layout/unused2.xml\n"
                        + "res/drawable/unused9.xml\n"
                        + "res/drawable/unused10.xml\n"
                        + "res/drawable/unused11.xml\n"
                        + "res/menu/unused12.xml\n"
                        + "res/layout/unused13.xml\n"
                        + "res/layout/unused14.xml\n"
                        + "res/layout/used1.xml\n"
                        + "res/layout/used2.xml\n"
                        + "res/layout/used3.xml\n"
                        + "res/layout/used4.xml\n"
                        + "res/layout/used5.xml\n"
                        + "res/layout/used6.xml\n"
                        + "res/layout/used7.xml\n"
                        + "res/layout/used8.xml\n"
                        + "res/drawable/used9.xml\n"
                        + "res/drawable/used10.xml\n"
                        + "res/drawable/used11.xml\n"
                        + "res/drawable/used12.xml\n"
                        + "res/menu/used13.xml\n"
                        + "res/layout/used14.xml\n"
                        + "res/drawable/used15.xml\n"
                        + "res/layout/used16.xml\n"
                        + "res/layout/used17.xml\n"
                        + "res/layout/used18.xml\n"
                        + "res/layout/used19.xml\n"
                        + "res/layout/used20.xml\n"
                        + "res/layout/used21.xml";

        String expectedStrippedApkContents =
                ""
                        + "AndroidManifest.xml\n"
                        + "classes.dex\n"
                        + "res/layout/l_used_a.xml\n"
                        + "res/layout/l_used_b2.xml\n"
                        + "res/layout/l_used_c.xml\n"
                        + "res/layout/prefix_3_suffix.xml\n"
                        + "res/layout/prefix_used_1.xml\n"
                        + "res/layout/prefix_used_2.xml\n"
                        + "resources.arsc\n"
                        + "res/layout/used1.xml\n"
                        + "res/layout/used2.xml\n"
                        + "res/layout/used3.xml\n"
                        + "res/layout/used4.xml\n"
                        + "res/layout/used5.xml\n"
                        + "res/layout/used6.xml\n"
                        + "res/layout/used7.xml\n"
                        + "res/layout/used8.xml\n"
                        + "res/drawable/used9.xml\n"
                        + "res/drawable/used10.xml\n"
                        + "res/drawable/used11.xml\n"
                        + "res/drawable/used12.xml\n"
                        + "res/menu/used13.xml\n"
                        + "res/layout/used14.xml\n"
                        + "res/drawable/used15.xml\n"
                        + "res/layout/used16.xml\n"
                        + "res/layout/used17.xml\n"
                        + "res/layout/used18.xml\n"
                        + "res/layout/used19.xml\n"
                        + "res/layout/used20.xml\n"
                        + "res/layout/used21.xml";
        if (REPLACE_DELETED_WITH_EMPTY) {
            // If replacing deleted files with empty files, the file list will include
            // the "unused" files too, though they will be much smaller. This is checked
            // later on in the test.
            expectedStrippedApkContents =
                    ""
                            + "AndroidManifest.xml\n"
                            + "classes.dex\n"
                            + "res/drawable/force_remove.xml\n"
                            + "res/layout/l_used_a.xml\n"
                            + "res/layout/l_used_b2.xml\n"
                            + "res/layout/l_used_c.xml\n"
                            + "res/layout/lib_unused.xml\n"
                            + "res/layout/prefix_3_suffix.xml\n"
                            + "res/layout/prefix_used_1.xml\n"
                            + "res/layout/prefix_used_2.xml\n"
                            + "resources.arsc\n"
                            + "res/layout/unused1.xml\n"
                            + "res/layout/unused2.xml\n"
                            + "res/drawable/unused9.xml\n"
                            + "res/drawable/unused10.xml\n"
                            + "res/drawable/unused11.xml\n"
                            + "res/menu/unused12.xml\n"
                            + "res/layout/unused13.xml\n"
                            + "res/layout/unused14.xml\n"
                            + "res/layout/used1.xml\n"
                            + "res/layout/used2.xml\n"
                            + "res/layout/used3.xml\n"
                            + "res/layout/used4.xml\n"
                            + "res/layout/used5.xml\n"
                            + "res/layout/used6.xml\n"
                            + "res/layout/used7.xml\n"
                            + "res/layout/used8.xml\n"
                            + "res/drawable/used9.xml\n"
                            + "res/drawable/used10.xml\n"
                            + "res/drawable/used11.xml\n"
                            + "res/drawable/used12.xml\n"
                            + "res/menu/used13.xml\n"
                            + "res/layout/used14.xml\n"
                            + "res/drawable/used15.xml\n"
                            + "res/layout/used16.xml\n"
                            + "res/layout/used17.xml\n"
                            + "res/layout/used18.xml\n"
                            + "res/layout/used19.xml\n"
                            + "res/layout/used20.xml\n"
                            + "res/layout/used21.xml";
        }

        // Should not have any unused resources in the compressed list
        if (!REPLACE_DELETED_WITH_EMPTY) {
            assertFalse(
                    expectedStrippedApkContents, expectedStrippedApkContents.contains("unused"));
        }
        // Should have *all* the used resources, currently 1-21
        for (int i = 1; i <= 21; i++) {
            assertTrue(
                    "Missing used" + i + " in " + expectedStrippedApkContents,
                    expectedStrippedApkContents.contains("/used" + i + "."));
        }

        // Check that the uncompressed resources (.ap_) for the release target have everything
        // we expect
        String expectedUncompressed = expectedUnstrippedApk.replace("classes.dex\n", "");
        assertEquals(
                "expectedUncompressed", expectedUncompressed, dumpZipContents(uncompressed).trim());

        // The debug target should have everything there in the APK
        assertEquals(
                "The debug target should have everything there in the APK",
                expectedUnstrippedApk,
                dumpZipContents(apkDebug.getFile()));
        assertEquals(
                "The debug target should have everything there in the APK",
                expectedUnstrippedApk,
                dumpZipContents(apkProguardOnly.getFile()));

        // Make sure force_remove was replaced with a small file if replacing rather than removing
        if (REPLACE_DELETED_WITH_EMPTY) {
            assertThatZip(compressed)
                    .containsFileWithContent(
                            "res/drawable/force_remove.xml", ResourceUsageAnalyzer.TINY_XML);
        }

        // Check the compressed .ap_:
        String actualCompressed = dumpZipContents(compressed);
        String expectedCompressed = expectedStrippedApkContents.replace("classes.dex\n", "");
        assertEquals("Check the compressed .ap_:", expectedCompressed, actualCompressed);
        if (!REPLACE_DELETED_WITH_EMPTY) {
            assertFalse(
                    "expectedCompressed does not contain unused resources",
                    expectedCompressed.contains("unused"));
        }
        assertEquals(
                "expectedStrippedApkContents",
                expectedStrippedApkContents,
                dumpZipContents(apkRelease.getFile()));

        // Check splits -- just sample one of them
        //noinspection SpellCheckingInspection
        compressed =
                project.file(
                        "abisplits/build/intermediates/res_stripped/release/resources-arm64-v8a-release-stripped.ap_");
        //noinspection SpellCheckingInspection
        uncompressed =
                project.file(
                        "abisplits/build/intermediates/res/release/resources-arm64-v8aRelease.ap_");
        assertTrue(compressed.toString() + " is not a file", compressed.isFile());
        assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());
        //noinspection SpellCheckingInspection
        assertEquals(
                ""
                        + "AndroidManifest.xml\n"
                        + "resources.arsc\n"
                        + (REPLACE_DELETED_WITH_EMPTY ? "res/layout/unused.xml\n" : "")
                        + "res/layout/used.xml",
                dumpZipContents(compressed));
        //noinspection SpellCheckingInspection
        assertEquals(
                ""
                        + "AndroidManifest.xml\n"
                        + "resources.arsc\n"
                        + "res/layout/unused.xml\n"
                        + "res/layout/used.xml",
                dumpZipContents(uncompressed));

        // Check WebView string handling (android_res strings etc)

        //noinspection SpellCheckingInspection
        uncompressed =
                project.file("webview/build/intermediates/res/release/resources-release.ap_");
        //noinspection SpellCheckingInspection
        compressed =
                project.file(
                        "webview/build/intermediates/res_stripped/release/resources-release-stripped.ap_");
        assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());
        assertTrue(compressed.toString() + " is not a file", compressed.isFile());

        //noinspection SpellCheckingInspection
        assertEquals(
                ""
                        + "AndroidManifest.xml\n"
                        + "res/xml/my_xml.xml\n"
                        + "resources.arsc\n"
                        + "res/raw/unknown\n"
                        + "res/raw/unused_icon.png\n"
                        + "res/raw/unused_index.html\n"
                        + "res/drawable/used1.xml\n"
                        + "res/raw/used_icon.png\n"
                        + "res/raw/used_icon2.png\n"
                        + "res/raw/used_index.html\n"
                        + "res/raw/used_index2.html\n"
                        + "res/raw/used_index3.html\n"
                        + "res/layout/used_layout1.xml\n"
                        + "res/layout/used_layout2.xml\n"
                        + "res/layout/used_layout3.xml\n"
                        + "res/raw/used_script.js\n"
                        + "res/raw/used_styles.css\n"
                        + "res/layout/webview.xml",
                dumpZipContents(uncompressed));

        //noinspection SpellCheckingInspection
        assertEquals(
                ""
                        + "AndroidManifest.xml\n"
                        + (REPLACE_DELETED_WITH_EMPTY ? "res/xml/my_xml.xml\n" : "")
                        + "resources.arsc\n"
                        + "res/raw/unknown\n"
                        + (REPLACE_DELETED_WITH_EMPTY ? "res/raw/unused_icon.png\n" : "")
                        + (REPLACE_DELETED_WITH_EMPTY ? "res/raw/unused_index.html\n" : "")
                        + "res/drawable/used1.xml\n"
                        + "res/raw/used_icon.png\n"
                        + "res/raw/used_icon2.png\n"
                        + "res/raw/used_index.html\n"
                        + "res/raw/used_index2.html\n"
                        + "res/raw/used_index3.html\n"
                        + "res/layout/used_layout1.xml\n"
                        + "res/layout/used_layout2.xml\n"
                        + "res/layout/used_layout3.xml\n"
                        + "res/raw/used_script.js\n"
                        + "res/raw/used_styles.css\n"
                        + "res/layout/webview.xml",
                dumpZipContents(compressed));

        // Check stored vs deflated state:
        // This is the state of the original source _ap file:
        assertEquals(
                ""
                        + "  stored  resources.arsc\n"
                        + "deflated  AndroidManifest.xml\n"
                        + "deflated  res/xml/my_xml.xml\n"
                        + "deflated  res/raw/unknown\n"
                        + "  stored  res/raw/unused_icon.png\n"
                        + "deflated  res/raw/unused_index.html\n"
                        + "deflated  res/drawable/used1.xml\n"
                        + "  stored  res/raw/used_icon.png\n"
                        + "  stored  res/raw/used_icon2.png\n"
                        + "deflated  res/raw/used_index.html\n"
                        + "deflated  res/raw/used_index2.html\n"
                        + "deflated  res/raw/used_index3.html\n"
                        + "deflated  res/layout/used_layout1.xml\n"
                        + "deflated  res/layout/used_layout2.xml\n"
                        + "deflated  res/layout/used_layout3.xml\n"
                        + "deflated  res/raw/used_script.js\n"
                        + "deflated  res/raw/used_styles.css\n"
                        + "deflated  res/layout/webview.xml",
                dumpZipContents(uncompressed, true));

        // This is the state of the rewritten ap_ file: the zip states should match
        assertEquals(
                ""
                        + "  stored  resources.arsc\n"
                        + "deflated  AndroidManifest.xml\n"
                        + (REPLACE_DELETED_WITH_EMPTY ? "deflated  res/xml/my_xml.xml\n" : "")
                        + "deflated  res/raw/unknown\n"
                        + (REPLACE_DELETED_WITH_EMPTY ? "  stored  res/raw/unused_icon.png\n" : "")
                        + (REPLACE_DELETED_WITH_EMPTY
                                ? "deflated  res/raw/unused_index.html\n"
                                : "")
                        + "deflated  res/drawable/used1.xml\n"
                        + "  stored  res/raw/used_icon.png\n"
                        + "  stored  res/raw/used_icon2.png\n"
                        + "deflated  res/raw/used_index.html\n"
                        + "deflated  res/raw/used_index2.html\n"
                        + "deflated  res/raw/used_index3.html\n"
                        + "deflated  res/layout/used_layout1.xml\n"
                        + "deflated  res/layout/used_layout2.xml\n"
                        + "deflated  res/layout/used_layout3.xml\n"
                        + "deflated  res/raw/used_script.js\n"
                        + "deflated  res/raw/used_styles.css\n"
                        + "deflated  res/layout/webview.xml",
                dumpZipContents(compressed, true));

        // Make sure the (remaining) binary contents of the files in the compressed APK are
        // identical to the ones in uncompressed:
        FileInputStream fis1 = new FileInputStream(compressed);
        JarInputStream zis1 = new JarInputStream(fis1);
        FileInputStream fis2 = new FileInputStream(uncompressed);
        JarInputStream zis2 = new JarInputStream(fis2);

        ZipEntry entry1 = zis1.getNextEntry();
        ZipEntry entry2 = zis2.getNextEntry();
        while (entry1 != null) {
            String name1 = entry1.getName();
            String name2 = entry2.getName();
            while (!name1.equals(name2)) {
                // uncompressed should contain a superset of all the names in compressed
                entry2 = zis2.getNextJarEntry();
                name2 = entry2.getName();
            }
            assertEquals(name1, name2);
            if (!entry1.isDirectory()) {
                assertEquals(name1, entry1.getMethod(), entry2.getMethod());

                byte[] bytes1 = ByteStreams.toByteArray(zis1);
                byte[] bytes2 = ByteStreams.toByteArray(zis2);

                if (REPLACE_DELETED_WITH_EMPTY) {
                    switch (name1) {
                        case "res/xml/my_xml.xml":
                            assertTrue(
                                    name1, Arrays.equals(bytes1, ResourceUsageAnalyzer.TINY_XML));
                            break;
                        case "res/raw/unused_icon.png":
                            assertTrue(
                                    name1, Arrays.equals(bytes1, ResourceUsageAnalyzer.TINY_PNG));
                            break;
                        case "res/raw/unused_index.html":
                            assertTrue(name1, Arrays.equals(bytes1, new byte[0]));
                            break;
                        default:
                            assertTrue(name1, Arrays.equals(bytes1, bytes2));
                            break;
                    }
                } else {
                    assertTrue(name1, Arrays.equals(bytes1, bytes2));
                }
            } else {
                assertTrue(entry2.isDirectory());
            }
            entry1 = zis1.getNextEntry();
            entry2 = zis2.getNextEntry();
        }

        zis1.close();
        zis2.close();

        //noinspection SpellCheckingInspection
        uncompressed = project.file("keep/build/intermediates/res/release/resources-release.ap_");
        //noinspection SpellCheckingInspection
        compressed =
                project.file(
                        "keep/build/intermediates/res_stripped/release/resources-release-stripped.ap_");
        assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());
        assertTrue(compressed.toString() + " is not a file", compressed.isFile());

        //noinspection SpellCheckingInspection
        assertEquals(
                ""
                        + "AndroidManifest.xml\n"
                        + "res/raw/keep.xml\n"
                        + "resources.arsc\n"
                        + "res/layout/unused1.xml\n"
                        + "res/layout/unused2.xml\n"
                        + "res/layout/used1.xml",
                dumpZipContents(uncompressed));

        //noinspection SpellCheckingInspection
        assertEquals(
                ""
                        + "AndroidManifest.xml\n"
                        + "resources.arsc\n"
                        + (REPLACE_DELETED_WITH_EMPTY ? "res/layout/unused1.xml\n" : "")
                        + (REPLACE_DELETED_WITH_EMPTY ? "res/layout/unused2.xml\n" : "")
                        + "res/layout/used1.xml",
                dumpZipContents(compressed));
    }

    private static List<String> getZipPaths(File zipFile, boolean includeMethod)
            throws IOException {
        List<String> lines = Lists.newArrayList();

        Closer closer = Closer.create();

        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName();
                if (includeMethod) {
                    String method;
                    switch (entry.getMethod()) {
                        case ZipEntry.STORED:
                            method = "  stored";
                            break;
                        case ZipEntry.DEFLATED:
                            method = "deflated";
                            break;
                        default:
                            method = " unknown";
                            break;
                    }
                    path = method + "  " + path;
                }
                lines.add(path);
            }
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        return lines;
    }

    private static String dumpZipContents(File zipFile) throws IOException {
        return dumpZipContents(zipFile, false);
    }

    private static String dumpZipContents(Path zipFile) throws IOException {
        return dumpZipContents(zipFile.toFile(), false);
    }

    private static String dumpZipContents(File zipFile, final boolean includeMethod)
            throws IOException {
        List<String> lines = getZipPaths(zipFile, includeMethod);

        // Remove META-INF statements
        lines.removeIf(s -> s.startsWith("META-INF/"));

        // Sort by base name (and numeric sort such that unused10 comes after unused9)
        final Pattern pattern = Pattern.compile("(.*[^\\d])(\\d+)(\\..+)?");
        lines.sort(
                (line1, line2) -> {
                    String name1 = line1.substring(line1.lastIndexOf('/') + 1);
                    String name2 = line2.substring(line2.lastIndexOf('/') + 1);
                    int delta = name1.compareTo(name2);
                    if (delta != 0) {
                        // Try to do numeric sort
                        Matcher match1 = pattern.matcher(name1);
                        if (match1.matches()) {
                            Matcher match2 = pattern.matcher(name2);
                            //noinspection ConstantConditions
                            if (match2.matches() && match1.group(1).equals(match2.group(1))) {
                                //noinspection ConstantConditions
                                int num1 = Integer.parseInt(match1.group(2));
                                //noinspection ConstantConditions
                                int num2 = Integer.parseInt(match2.group(2));
                                if (num1 != num2) {
                                    return num1 - num2;
                                }
                            }
                        }
                        return delta;
                    }

                    if (includeMethod) {
                        line1 = line1.substring(10);
                        line2 = line2.substring(10);
                    }
                    return line1.compareTo(line2);
                });

        return Joiner.on('\n').join(lines);
    }
}
