/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.instantapp.sdk;

import static com.android.instantapp.sdk.InstantAppSdkTests.getInstantAppSdk;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Collections;
import javax.xml.parsers.DocumentBuilderFactory;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Unit tests for {@link ManifestParser}. */
public class ManifestParserTest {
    private ManifestParser myManifestParser;
    private File myInstantAppSdk;

    @Before
    public void initializeParser() throws Exception {
        myInstantAppSdk = getInstantAppSdk();
        myManifestParser = new ManifestParser(myInstantAppSdk);
        int a = 3;
    }

    @Test
    public void testEntireParse() throws Exception {
        Metadata metadata = myManifestParser.getMetadata();

        assertTrue(metadata.isSupportedArch("arm64-v8a"));
        assertTrue(metadata.isSupportedArch("armeabi-v7a"));
        assertTrue(metadata.isSupportedArch("x86"));
        assertFalse(metadata.isSupportedArch("mips"));

        assertTrue(
                metadata.isSupportedDevice(
                        new Metadata.Device("sony", null, Collections.singleton(23), null, null)));
        assertTrue(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                null, "bullhead", Collections.singleton(24), null, null)));
        assertTrue(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                null, null, Collections.singleton(25), null, "ranchu")));
        assertFalse(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                null, null, Collections.singleton(20), null, "ranchu")));
        assertFalse(
                metadata.isSupportedDevice(
                        new Metadata.Device(
                                "noManufacturer", null, Collections.singleton(25), null, null)));

        assertEquals(
                6,
                metadata.getGServicesOverrides(
                                new Metadata.Device(
                                        null, null, Collections.singleton(24), null, "ranchu"))
                        .size());
        assertEquals(
                4,
                metadata.getGServicesOverrides(
                                new Metadata.Device(
                                        "sony", null, Collections.singleton(23), null, null))
                        .size());

        assertEquals(4, metadata.getApks(Metadata.Arch.ARM64_V8A, 23).size());
        assertEquals(1, metadata.getApks(Metadata.Arch.DEFAULT, 23).size());

        for (Metadata.ApkInfo apkInfo : metadata.getApks(Metadata.Arch.X86, 22)) {
            if (apkInfo.getPkgName().compareTo("com.google.android.gms") == 0) {
                assertTrue(apkInfo.getApk().getName().contains("lmp"));
            }
        }
        for (Metadata.ApkInfo apkInfo : metadata.getApks(Metadata.Arch.X86, 24)) {
            if (apkInfo.getPkgName().compareTo("com.google.android.gms") == 0) {
                assertTrue(apkInfo.getApk().getName().contains("mnc"));
            }
        }

        assertEquals(1, metadata.getAiaCompatApiMinVersion());
    }

    @Test
    public void testParseDevice() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element deviceElement =
                generateNode(
                        doc,
                        "device",
                        generateLeaf(doc, "manufacturer", "sony"),
                        generateLeaf(doc, "sdkInt", "23"),
                        generateLeaf(doc, "sdkInt", "24"),
                        generateLeaf(doc, "sdkInt", "25"));

        Metadata.Device device = ManifestParser.parseDevice(deviceElement);

        assertNull(device.getHardware());
        assertNull(device.getAndroidDevice());
        assertNull(device.getProduct());
        assertEquals("sony", device.getManufacturer());
        assertThat(device.getApiLevels(), CoreMatchers.hasItems(23, 24, 25));
    }

    @Test
    public void testParseGServicesOverrideWithoutDevice() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element gServicesElement =
                generateNode(
                        doc,
                        "gservicesOverride",
                        generateLeaf(doc, "key", "gms:wh:enable_westinghouse_support"),
                        generateLeaf(doc, "value", "true"));

        Metadata.GServicesOverride gServicesOverride =
                ManifestParser.parseGServicesOverride(gServicesElement);

        assertTrue(gServicesOverride.getDevices().isEmpty());
        assertEquals("gms:wh:enable_westinghouse_support", gServicesOverride.getKey());
        assertEquals("true", gServicesOverride.getValue());
    }

    @Test
    public void testParseGServicesOverride() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element gServicesElement =
                generateNode(
                        doc,
                        "gservicesOverride",
                        generateNode(
                                doc,
                                "device",
                                generateLeaf(doc, "hardware", "goldfish"),
                                generateLeaf(doc, "hardware", "ranchu")),
                        generateLeaf(doc, "key", "gms:wh:disableDomainFilterFallback"),
                        generateLeaf(doc, "value", "true"));

        Metadata.GServicesOverride gServicesOverride =
                ManifestParser.parseGServicesOverride(gServicesElement);

        for (Metadata.Device device : gServicesOverride.getDevices()) {
            assertThat(
                    device.getHardware(),
                    CoreMatchers.anyOf(CoreMatchers.is("goldfish"), CoreMatchers.is("ranchu")));
        }
        assertEquals("gms:wh:disableDomainFilterFallback", gServicesOverride.getKey());
        assertEquals("true", gServicesOverride.getValue());
    }

    @Test
    public void testParseApkVersionInfoWithoutSdkInt() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element apkVersionInfoElement =
                generateNode(
                        doc,
                        "apkVersionInfo",
                        generateLeaf(doc, "path", "release/GmsCore_prodmnc_xxhdpi_armeabi-v7a.apk"),
                        generateLeaf(doc, "arch", "armeabi-v7a"),
                        generateLeaf(doc, "packageName", "com.google.android.gms"),
                        generateLeaf(doc, "versionCode", "11034430"),
                        generateLeaf(doc, "versionName", "11.0.34 (430-153866981)"));

        Metadata.ApkInfo apkInfo = myManifestParser.parseApkVersionInfo(apkVersionInfoElement);

        assertEquals(
                FileUtils.join(
                                myInstantAppSdk,
                                "tools",
                                "apks",
                                "release",
                                "GmsCore_prodmnc_xxhdpi_armeabi-v7a.apk")
                        .getPath(),
                apkInfo.getApk().getPath());
        assertEquals(Metadata.Arch.ARMEABI_V7A, apkInfo.getArch());
        assertEquals("com.google.android.gms", apkInfo.getPkgName());
        assertEquals(11034430, apkInfo.getVersionCode());
    }

    @Test
    public void testParseApkVersionInfoWithoutArch() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element apkVersionInfoElement =
                generateNode(
                        doc,
                        "apkVersionInfo",
                        generateLeaf(doc, "path", "release/devman.apk"),
                        generateLeaf(doc, "packageName", "com.google.android.instantapps.devman"),
                        generateLeaf(doc, "versionCode", "37"),
                        generateLeaf(doc, "versionName", "1.0.154942813"));

        Metadata.ApkInfo apkInfo = myManifestParser.parseApkVersionInfo(apkVersionInfoElement);

        assertEquals(
                FileUtils.join(myInstantAppSdk, "tools", "apks", "release", "devman.apk").getPath(),
                apkInfo.getApk().getPath());
        assertEquals(Metadata.Arch.DEFAULT, apkInfo.getArch());
        assertEquals("com.google.android.instantapps.devman", apkInfo.getPkgName());
        assertEquals(37, apkInfo.getVersionCode());
    }

    @Test
    public void testParseApkVersionInfo() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element apkVersionInfoElement =
                generateNode(
                        doc,
                        "apkVersionInfo",
                        generateLeaf(doc, "path", "release/GmsCore_prodmnc_xxhdpi_armeabi-v7a.apk"),
                        generateLeaf(doc, "arch", "armeabi-v7a"),
                        generateLeaf(doc, "packageName", "com.google.android.gms"),
                        generateNode(
                                doc,
                                "sdkInt",
                                generateLeaf(doc, "sdkInt", "23"),
                                generateLeaf(doc, "sdkInt", "24"),
                                generateLeaf(doc, "sdkInt", "25")),
                        generateLeaf(doc, "versionCode", "11034430"),
                        generateLeaf(doc, "versionName", "11.0.34 (430-153866981)"));

        Metadata.ApkInfo apkInfo = myManifestParser.parseApkVersionInfo(apkVersionInfoElement);

        assertEquals(
                FileUtils.join(
                                myInstantAppSdk,
                                "tools",
                                "apks",
                                "release",
                                "GmsCore_prodmnc_xxhdpi_armeabi-v7a.apk")
                        .getPath(),
                apkInfo.getApk().getPath());
        assertEquals(Metadata.Arch.ARMEABI_V7A, apkInfo.getArch());
        assertEquals("com.google.android.gms", apkInfo.getPkgName());
        assertThat(apkInfo.getApiLevels(), CoreMatchers.hasItems(23, 24, 25));
        assertEquals(11034430, apkInfo.getVersionCode());
    }

    @Test
    public void testParseLibraryCompatibility() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element libraryCompatibilityNode =
                generateNode(
                        doc,
                        "libraryCompatibility",
                        generateLeaf(doc, "aiaCompatApiMinVersion", "1"));
    }

    private static Element generateNode(
            @NonNull Document doc, @NonNull String tag, @NonNull Element... children) {
        Element element = doc.createElement(tag);
        for (Element child : children) {
            element.appendChild(child);
        }
        return element;
    }

    private static Element generateLeaf(
            @NonNull Document doc, @NonNull String tag, @NonNull String value) {
        Element element = doc.createElement(tag);
        element.appendChild(doc.createTextNode(value));
        return element;
    }
}
