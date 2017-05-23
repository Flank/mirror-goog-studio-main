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

package com.android.tools.apk.analyzer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.process.ProcessException;
import com.android.testutils.TestResources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

/** Tests for {@link ApkAnalyzerCli} */
public class ApkAnalyzerImplTest {
    private ApkAnalyzerImpl impl;
    private ByteArrayOutputStream baos;
    private AaptInvoker aaptInvoker;
    private Path apk;

    @Before
    public void setUp() throws Exception {
        baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        aaptInvoker = mock(AaptInvoker.class);
        apk = TestResources.getFile("/test.apk").toPath();
        impl = new ApkAnalyzerImpl(ps, aaptInvoker);
    }

    @Test
    public void resPackagesTest() throws IOException {
        impl.resPackages(apk);
        assertEquals("com.example.helloworld\n", baos.toString());
    }

    @Test
    public void resXmlTest() throws IOException {
        impl.resXml(apk, "/AndroidManifest.xml");
        assertEquals(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:versionCode=\"1\"\n"
                        + "    android:versionName=\"1.0\"\n"
                        + "    package=\"com.example.helloworld\"\n"
                        + "    platformBuildVersionCode=\"23\"\n"
                        + "    platformBuildVersionName=\"6.0-2438415\">\n"
                        + "\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"3\" />\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:label=\"@ref/0x7f030000\"\n"
                        + "        android:debuggable=\"true\">\n"
                        + "\n"
                        + "        <activity\n"
                        + "            android:label=\"@ref/0x7f030000\"\n"
                        + "            android:name=\"com.example.helloworld.HelloWorld\">\n"
                        + "\n"
                        + "            <intent-filter>\n"
                        + "\n"
                        + "                <action\n"
                        + "                    android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category\n"
                        + "                    android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "</manifest>\n",
                baos.toString());
    }

    @Test
    public void resNamesTest() throws IOException {
        impl.resNames(apk, "string", "default", null);
        assertEquals("app_name\n", baos.toString());
        baos.reset();
        impl.resNames(apk, "string", "default", "com.example.helloworld");
        assertEquals("app_name\n", baos.toString());
    }

    @Test
    public void resValueTest() throws IOException {
        impl.resValue(apk, "string", "default", "app_name", null);
        assertEquals("HelloWorld\n", baos.toString());
        baos.reset();
        impl.resValue(apk, "string", "default", "app_name", "com.example.helloworld");
        assertEquals("HelloWorld\n", baos.toString());
    }

    @Test
    public void resConfigsTest() throws IOException {
        impl.resConfigs(apk, "string", null);
        assertEquals("default\n", baos.toString());
    }

    @Test
    public void dexCodeTest() throws IOException {
        impl.dexCode(apk, "com.example.helloworld.HelloWorld", null);
        assertEquals(
                ".class public Lcom/example/helloworld/HelloWorld;\n"
                        + ".super Landroid/app/Activity;\n"
                        + ".source \"HelloWorld.java\"\n"
                        + "\n"
                        + "\n"
                        + "# static fields\n"
                        + ".field private static hw:Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;\n"
                        + "    .annotation system Ldalvik/annotation/Signature;\n"
                        + "        value = {\n"
                        + "            \"Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater\",\n"
                        + "            \"<\",\n"
                        + "            \"Lcom/example/helloworld/HelloWorld;\",\n"
                        + "            \">;\"\n"
                        + "        }\n"
                        + "    .end annotation\n"
                        + ".end field\n"
                        + "\n"
                        + "\n"
                        + "# instance fields\n"
                        + ".field volatile volAtomicVarKept:I\n"
                        + "\n"
                        + "\n"
                        + "# direct methods\n"
                        + ".method static constructor <clinit>()V\n"
                        + "    .registers 2\n"
                        + "\n"
                        + "    .prologue\n"
                        + "    .line 8\n"
                        + "    const-class v0, Lcom/example/helloworld/HelloWorld;\n"
                        + "\n"
                        + "    const-string v1, \"volAtomicVarKept\"\n"
                        + "\n"
                        + "    invoke-static {v0, v1}, Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;->newUpdater(Ljava/lang/Class;Ljava/lang/String;)Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;\n"
                        + "\n"
                        + "    move-result-object v0\n"
                        + "\n"
                        + "    sput-object v0, Lcom/example/helloworld/HelloWorld;->hw:Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;\n"
                        + "\n"
                        + "    return-void\n"
                        + ".end method\n"
                        + "\n"
                        + ".method public constructor <init>()V\n"
                        + "    .registers 1\n"
                        + "\n"
                        + "    .prologue\n"
                        + "    .line 6\n"
                        + "    invoke-direct {p0}, Landroid/app/Activity;-><init>()V\n"
                        + "\n"
                        + "    return-void\n"
                        + ".end method\n"
                        + "\n"
                        + "\n"
                        + "# virtual methods\n"
                        + ".method public onCreate(Landroid/os/Bundle;)V\n"
                        + "    .registers 3\n"
                        + "    .param p1, \"savedInstanceState\"    # Landroid/os/Bundle;\n"
                        + "\n"
                        + "    .prologue\n"
                        + "    .line 13\n"
                        + "    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n"
                        + "\n"
                        + "    .line 14\n"
                        + "    const/high16 v0, 0x7f020000\n"
                        + "\n"
                        + "    invoke-virtual {p0, v0}, Lcom/example/helloworld/HelloWorld;->setContentView(I)V\n"
                        + "\n"
                        + "    .line 16\n"
                        + "    return-void\n"
                        + ".end method\n"
                        + "\n",
                baos.toString());
        baos.reset();
        impl.dexCode(apk, "com.example.helloworld.HelloWorld", "onCreate(Landroid/os/Bundle;)V");
        assertEquals(
                ".method public onCreate(Landroid/os/Bundle;)V\n"
                        + "    .registers 3\n"
                        + "    .param p1, \"savedInstanceState\"    # Landroid/os/Bundle;\n"
                        + "\n"
                        + "    .prologue\n"
                        + "    .line 13\n"
                        + "    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n"
                        + "\n"
                        + "    .line 14\n"
                        + "    const/high16 v0, 0x7f020000\n"
                        + "\n"
                        + "    invoke-virtual {p0, v0}, Lcom/example/helloworld/HelloWorld;->setContentView(I)V\n"
                        + "\n"
                        + "    .line 16\n"
                        + "    return-void\n"
                        + ".end method\n"
                        + "\n",
                baos.toString());
    }

    @Test
    public void dexPackagesTest() throws IOException {
        impl.dexPackages(apk, null, null, null, null, false, false, null);
        assertEquals(
                "P d 3\t7\t380\t<TOTAL>\n"
                        + "P d 3\t4\t306\tcom\n"
                        + "P d 3\t4\t306\tcom.example\n"
                        + "P d 3\t4\t306\tcom.example.helloworld\n"
                        + "C d 3\t4\t306\tcom.example.helloworld.HelloWorld\n"
                        + "M d 1\t1\t69\tcom.example.helloworld.HelloWorld <clinit>()\n"
                        + "M d 1\t1\t55\tcom.example.helloworld.HelloWorld <init>()\n"
                        + "M d 1\t1\t72\tcom.example.helloworld.HelloWorld void onCreate(android.os.Bundle)\n"
                        + "M r 0\t1\t26\tcom.example.helloworld.HelloWorld void setContentView(int)\n"
                        + "F d 0\t0\t18\tcom.example.helloworld.HelloWorld java.util.concurrent.atomic.AtomicIntegerFieldUpdater hw\n"
                        + "F d 0\t0\t10\tcom.example.helloworld.HelloWorld int volAtomicVarKept\n"
                        + "P r 0\t2\t46\tandroid\n"
                        + "P r 0\t2\t46\tandroid.app\n"
                        + "C r 0\t2\t46\tandroid.app.Activity\n"
                        + "M r 0\t1\t20\tandroid.app.Activity <init>()\n"
                        + "M r 0\t1\t26\tandroid.app.Activity void onCreate(android.os.Bundle)\n"
                        + "P r 0\t1\t28\tjava\n"
                        + "P r 0\t1\t28\tjava.util\n"
                        + "P r 0\t1\t28\tjava.util.concurrent\n"
                        + "P r 0\t1\t28\tjava.util.concurrent.atomic\n"
                        + "C r 0\t1\t28\tjava.util.concurrent.atomic.AtomicIntegerFieldUpdater\n"
                        + "M r 0\t1\t28\tjava.util.concurrent.atomic.AtomicIntegerFieldUpdater java.util.concurrent.atomic.AtomicIntegerFieldUpdater newUpdater(java.lang.Class,java.lang.String)\n",
                baos.toString());
    }

    @Test
    public void dexReferencesTest() throws IOException {
        impl.dexReferences(apk, null);
        assertEquals("classes.dex\t7\n", baos.toString());
    }

    @Test
    public void dexListTest() throws IOException {
        impl.dexList(apk);
        assertEquals("classes.dex\n", baos.toString());
    }

    @Test
    public void manifestDebuggableTest()
            throws IOException, ParserConfigurationException, SAXException {
        impl.manifestDebuggable(apk);
        assertEquals("true\n", baos.toString());
    }

    @Test
    public void manifestPermissionsTest() throws ProcessException {
        when(aaptInvoker.dumpBadging(apk.toFile()))
                .thenReturn(
                        Arrays.asList(
                                "package: name='com.example.packageName' versionCode='12345' versionName='someVersionName' platformBuildVersionName='8.0.0'",
                                "uses-permission: name='android.permission.ACCESS_COARSE_LOCATION'",
                                "uses-permission: name='android.permission.ACCESS_FINE_LOCATION'",
                                "uses-permission: name='android.permission.ACCESS_NETWORK_STATE'",
                                "uses-permission: name='android.permission.ACCESS_NOTIFICATION_POLICY'"));
        impl.manifestPermissions(apk);
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION\n"
                        + "android.permission.ACCESS_NOTIFICATION_POLICY\n"
                        + "android.permission.ACCESS_COARSE_LOCATION\n"
                        + "android.permission.ACCESS_NETWORK_STATE\n",
                baos.toString());
    }

    @Test
    public void manifestTargetSdkTest()
            throws IOException, ParserConfigurationException, SAXException {
        impl.manifestTargetSdk(apk);
        assertEquals("3\n", baos.toString());
    }

    @Test
    public void manifestMinSdkTest()
            throws IOException, ParserConfigurationException, SAXException {
        impl.manifestMinSdk(apk);
        assertEquals("3\n", baos.toString());
    }

    @Test
    public void manifestVersionCodeTest()
            throws IOException, ParserConfigurationException, SAXException {
        impl.manifestVersionCode(apk);
        assertEquals("1\n", baos.toString());
    }

    @Test
    public void manifestVersionNameTest()
            throws IOException, ParserConfigurationException, SAXException, ProcessException {
        when(aaptInvoker.dumpBadging(apk.toFile()))
                .thenReturn(
                        Collections.singletonList(
                                "package: name='com.example.packageName' versionCode='12345' versionName='someVersionName' platformBuildVersionName='8.0.0'"));
        impl.manifestVersionName(apk);
        assertEquals("someVersionName\n", baos.toString());
    }

    @Test
    public void manifestAppIdTest() throws IOException, ParserConfigurationException, SAXException {
        impl.manifestAppId(apk);
        assertEquals("com.example.helloworld\n", baos.toString());
    }

    @Test
    public void manifestPrint() throws IOException {
        impl.manifestPrint(apk);
        assertEquals(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:versionCode=\"1\"\n"
                        + "    android:versionName=\"1.0\"\n"
                        + "    package=\"com.example.helloworld\"\n"
                        + "    platformBuildVersionCode=\"23\"\n"
                        + "    platformBuildVersionName=\"6.0-2438415\">\n"
                        + "\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"3\" />\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:label=\"@ref/0x7f030000\"\n"
                        + "        android:debuggable=\"true\">\n"
                        + "\n"
                        + "        <activity\n"
                        + "            android:label=\"@ref/0x7f030000\"\n"
                        + "            android:name=\"com.example.helloworld.HelloWorld\">\n"
                        + "\n"
                        + "            <intent-filter>\n"
                        + "\n"
                        + "                <action\n"
                        + "                    android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category\n"
                        + "                    android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "</manifest>\n",
                baos.toString());
    }

    @Test
    public void apkSummaryTest() throws ProcessException {
        when(aaptInvoker.dumpBadging(apk.toFile()))
                .thenReturn(
                        Collections.singletonList(
                                "package: name='com.example.packageName' versionCode='12345' versionName='someVersionName' platformBuildVersionName='8.0.0'"));
        impl.apkSummary(apk);
        assertEquals("com.example.packageName\t12345\tsomeVersionName\n", baos.toString());
    }

    @Test
    public void apkRawSizeTest() {
        impl.apkRawSize(apk);
        assertEquals("5047\n", baos.toString());
    }

    @Test
    public void apkDownloadSizeTest() {
        impl.apkDownloadSize(apk);
        assertEquals("3952\n", baos.toString());
    }

    @Test
    public void apkFeaturesTest() throws ProcessException {
        when(aaptInvoker.dumpBadging(apk.toFile()))
                .thenReturn(
                        Arrays.asList(
                                ("uses-implied-permission: name='android.permission.READ_EXTERNAL_STORAGE' reason='requested WRITE_EXTERNAL_STORAGE'\n"
                                                + "feature-group: label=''\n"
                                                + "  uses-feature: name='android.hardware.camera.level.full'\n"
                                                + "  uses-feature-not-required: name='android.hardware.location'\n"
                                                + "  uses-feature-not-required: name='android.hardware.location.gps'\n"
                                                + "  uses-feature-not-required: name='android.hardware.location.network'\n"
                                                + "  uses-feature-not-required: name='android.hardware.wifi'\n"
                                                + "  uses-feature: name='com.google.android.feature.GOOGLE_EXPERIENCE'\n"
                                                + "  uses-feature: name='android.hardware.camera'\n"
                                                + "  uses-implied-feature: name='android.hardware.camera' reason='requested android.permission.CAMERA permission'\n"
                                                + "  uses-feature: name='android.hardware.faketouch'\n"
                                                + "  uses-implied-feature: name='android.hardware.faketouch' reason='default feature for all apps'\n"
                                                + "  uses-feature: name='android.hardware.microphone'\n"
                                                + "  uses-implied-feature: name='android.hardware.microphone' reason='requested android.permission.RECORD_AUDIO permission'\n")
                                        .split("\n")));
        impl.apkFeatures(apk, false);
        assertEquals(
                "android.hardware.camera implied: requested android.permission.CAMERA permission\n"
                        + "android.hardware.camera.level.full\n"
                        + "android.hardware.microphone implied: requested android.permission.RECORD_AUDIO permission\n"
                        + "com.google.android.feature.GOOGLE_EXPERIENCE\n"
                        + "android.hardware.faketouch implied: default feature for all apps\n",
                baos.toString());

        baos.reset();
        impl.apkFeatures(apk, true);
        assertEquals(
                "android.hardware.camera implied: requested android.permission.CAMERA permission\n"
                        + "android.hardware.camera.level.full\n"
                        + "android.hardware.microphone implied: requested android.permission.RECORD_AUDIO permission\n"
                        + "com.google.android.feature.GOOGLE_EXPERIENCE\n"
                        + "android.hardware.faketouch implied: default feature for all apps\n"
                        + "android.hardware.location.gps not-required\n"
                        + "android.hardware.location.network not-required\n"
                        + "android.hardware.location not-required\n"
                        + "android.hardware.wifi not-required\n",
                baos.toString());
    }

    @Test
    public void apkCompareTest() throws IOException {
        Path apk = TestResources.getFile("/2.apk").toPath();
        Path apk2 = TestResources.getFile("/1.apk").toPath();
        impl.apkCompare(apk, apk2, false, false, false);
        assertEquals(
                "960\t649\t-311\t/\n"
                        + "6\t6\t0\t/res/\n"
                        + "6\t6\t0\t/res/anim/\n"
                        + "6\t6\t0\t/res/anim/fade.xml\n"
                        + "13\t13\t0\t/AndroidManifest.xml\n"
                        + "352\t0\t-352\t/instant-run.zip\n"
                        + "2\t0\t-2\t/instant-run.zip/instant-run/\n"
                        + "2\t0\t-2\t/instant-run.zip/instant-run/classes1.dex\n",
                baos.toString());
    }

    @Test
    public void apkComparePatchTest() throws IOException {
        Path apk = TestResources.getFile("/1.apk").toPath();
        Path apk2 = TestResources.getFile("/2.apk").toPath();
        impl.apkCompare(apk, apk2, true, false, false);
        assertEquals(
                "649\t960\t158\t/\n"
                        + "0\t352\t158\t/instant-run.zip\n"
                        + "0\t2\t0\t/instant-run.zip/instant-run/\n"
                        + "0\t2\t0\t/instant-run.zip/instant-run/classes1.dex\n"
                        + "6\t6\t0\t/res/\n"
                        + "6\t6\t0\t/res/anim/\n"
                        + "6\t6\t0\t/res/anim/fade.xml\n"
                        + "13\t13\t0\t/AndroidManifest.xml\n",
                baos.toString());
    }

    @Test
    public void filesListTest() throws IOException {
        impl.filesList(apk, true, true, false);
        assertEquals(
                "4157\t3476\t/\n"
                        + "1568\t1568\t/META-INF/\n"
                        + "1054\t1054\t/META-INF/CERT.RSA\n"
                        + "264\t264\t/META-INF/CERT.SF\n"
                        + "250\t250\t/META-INF/MANIFEST.MF\n"
                        + "703\t703\t/classes.dex\n"
                        + "936\t255\t/resources.arsc\n"
                        + "312\t312\t/res/\n"
                        + "312\t312\t/res/layout/\n"
                        + "312\t312\t/res/layout/main.xml\n"
                        + "638\t638\t/AndroidManifest.xml\n",
                baos.toString());
    }

    @Test
    public void filesCatTest() throws IOException {
        impl.filesCat(apk, "META-INF/MANIFEST.MF");
        assertEquals(
                "Manifest-Version: 1.0\r\n"
                        + "Built-By: 2.1.0-SNAPSHOT\r\n"
                        + "Created-By: Android Gradle 2.1.0-SNAPSHOT\r\n"
                        + "\r\n"
                        + "Name: res/layout/main.xml\r\n"
                        + "SHA1-Digest: ZVwQNWtuauUPj8JLLPMv2Oln5rY=\r\n"
                        + "\r\n"
                        + "Name: AndroidManifest.xml\r\n"
                        + "SHA1-Digest: OW6a992Y3F953UPetQmBrO8kVd4=\r\n"
                        + "\r\n"
                        + "Name: resources.arsc\r\n"
                        + "SHA1-Digest: WMu0zV4Sw2J1nXfXRCyA0kPUFUU=\r\n"
                        + "\r\n"
                        + "Name: classes.dex\r\n"
                        + "SHA1-Digest: bf5rvESHL/Jx8mMfL1zoEgxGcwY=\r\n"
                        + "\r\n",
                baos.toString());
    }
}
