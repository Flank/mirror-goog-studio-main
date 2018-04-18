/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;

import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Detector;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@SuppressWarnings({
    "javadoc",
    "ClassNameDiffersFromFileName",
    "StatementWithEmptyBody",
    "MethodMayBeStatic"
})
public class UnsafeBroadcastReceiverDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new UnsafeBroadcastReceiverDetector();
    }

    public void testBroken() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "src/test/pkg/TestReceiver.java:10: Warning: This broadcast receiver declares an intent-filter for a protected broadcast action string, which can only be sent by the system, not third-party applications. However, the receiver's onReceive method does not appear to call getAction to ensure that the received Intent's action string matches the expected value, potentially making it possible for another actor to send a spoofed intent with no action string or a different action string and cause undesired behavior. [UnsafeProtectedBroadcastReceiver]\n"
                        + "    public void onReceive(Context context, Intent intent) {\n"
                        + "                ~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        manifest,
                        java(
                                "src/test/pkg/TestReceiver.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "\n"
                                        + "public class TestReceiver extends BroadcastReceiver {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void onReceive(Context context, Intent intent) {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Anonymous classes should NOT be counted as a must-register\n"
                                        + "    private static BroadcastReceiver dummy() {\n"
                                        + "        return new BroadcastReceiver() {\n"
                                        + "            @Override\n"
                                        + "            public void onReceive(Context context, Intent intent) {\n"
                                        + "            }\n"
                                        + "        };\n"
                                        + "    }\n"
                                        + "}\n")));
    }

    public void testBroken2() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Warning: BroadcastReceivers that declare an intent-filter for SMS_DELIVER or SMS_RECEIVED must ensure that the caller has the BROADCAST_SMS permission, otherwise it is possible for malicious actors to spoof intents. [UnprotectedSMSBroadcastReceiver]\n"
                        + "        <receiver\n"
                        + "         ~~~~~~~~\n"
                        + "0 errors, 1 warnings";
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.pkg\"\n"
                                        + "    android:versionCode=\"1\"\n"
                                        + "    android:versionName=\"1.0\" >\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <receiver\n"
                                        + "            android:label=\"@string/app_name\"\n"
                                        + "            android:name=\".TestReceiver\" >\n"
                                        + "                <intent-filter>\n"
                                        + "                    <action android:name=\"android.provider.Telephony.SMS_RECEIVED\"/>\n"
                                        + "                </intent-filter>\n"
                                        + "        </receiver>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"
                                        + "\n"))
                .run()
                .expect(expected)
                .verifyFixes()
                .window(1)
                .expectFixDiffs(
                        ""
                                + "Fix for AndroidManifest.xml line 11: Set permission=\"android.permission.BROADCAST_SMS\":\n"
                                + "@@ -14 +14\n"
                                + "              android:name=\".TestReceiver\"\n"
                                + "-             android:label=\"@string/app_name\" >\n"
                                + "+             android:label=\"@string/app_name\"\n"
                                + "+             android:permission=\"android.permission.BROADCAST_SMS\" >\n"
                                + "              <intent-filter>\n");
    }

    public void testReferencesIntentVariable() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "src/test/pkg/TestReceiver.java:10: Warning: This broadcast receiver declares an intent-filter for a protected broadcast action string, which can only be sent by the system, not third-party applications. However, the receiver's onReceive method does not appear to call getAction to ensure that the received Intent's action string matches the expected value, potentially making it possible for another actor to send a spoofed intent with no action string or a different action string and cause undesired behavior. In this case, it is possible that the onReceive method passed the received Intent to another method that checked the action string. If so, this finding can safely be ignored. [UnsafeProtectedBroadcastReceiver]\n"
                        + "    public void onReceive(Context context, Intent intent) {\n"
                        + "                ~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        manifest,
                        java(
                                "src/test/pkg/TestReceiver.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "\n"
                                        + "public class TestReceiver extends BroadcastReceiver {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void onReceive(Context context, Intent intent) {\n"
                                        + "        System.out.println(intent);\n"
                                        + "    }\n"
                                        + "}\n")));
    }

    public void testCorrect() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",
                lintProject(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.pkg\"\n"
                                        + "    android:versionCode=\"1\"\n"
                                        + "    android:versionName=\"1.0\" >\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <receiver\n"
                                        + "            android:label=\"@string/app_name\"\n"
                                        + "            android:name=\".TestReceiver2\" >\n"
                                        + "                <intent-filter>\n"
                                        + "                    <action android:name=\"android.intent.action.BOOT_COMPLETED\"/>\n"
                                        + "                </intent-filter>\n"
                                        + "        </receiver>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"
                                        + "\n"),
                        java(
                                "src/test/pkg/TestReceiver2.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "\n"
                                        + "public class TestReceiver2 extends BroadcastReceiver {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void onReceive(Context context, Intent intent) {\n"
                                        + "      if (intent.getAction() == Intent.ACTION_BOOT_COMPLETED) {\n"
                                        + "      }\n"
                                        + "    }\n"
                                        + "}\n")));
    }

    public void testCorrect2() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",
                lintProject(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.pkg\"\n"
                                        + "    android:versionCode=\"1\"\n"
                                        + "    android:versionName=\"1.0\" >\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <receiver\n"
                                        + "            android:label=\"@string/app_name\"\n"
                                        + "            android:name=\".TestReceiver\"\n"
                                        + "            android:permission=\"android.permission.BROADCAST_SMS\" >\n"
                                        + "                <intent-filter>\n"
                                        + "                    <action android:name=\"android.provider.Telephony.SMS_RECEIVED\"/>\n"
                                        + "                </intent-filter>\n"
                                        + "        </receiver>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"
                                        + "\n")));
    }

    public void testBrokenIncremental() {
        String expected =
                ""
                        + "src/test/pkg/TestReceiver.java:10: Warning: This broadcast receiver declares an intent-filter for a protected broadcast action string, which can only be sent by the system, not third-party applications. However, the receiver's onReceive method does not appear to call getAction to ensure that the received Intent's action string matches the expected value, potentially making it possible for another actor to send a spoofed intent with no action string or a different action string and cause undesired behavior. [UnsafeProtectedBroadcastReceiver]\n"
                        + "    public void onReceive(Context context, Intent intent) {\n"
                        + "                ~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        manifest,
                        java(
                                "src/test/pkg/TestReceiver.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "\n"
                                        + "public class TestReceiver extends BroadcastReceiver {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void onReceive(Context context, Intent intent) {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    // Anonymous classes should NOT be counted as a must-register\n"
                                        + "    private static BroadcastReceiver dummy() {\n"
                                        + "        return new BroadcastReceiver() {\n"
                                        + "            @Override\n"
                                        + "            public void onReceive(Context context, Intent intent) {\n"
                                        + "            }\n"
                                        + "        };\n"
                                        + "    }\n"
                                        + "}\n"))
                .incremental("src/test/pkg/TestReceiver.java")
                .run()
                .expect(expected);
    }

    public void testDbUpToDate() throws IOException {
        List<String> expected = getProtectedBroadcasts();
        if (expected == null) {
            return;
        }
        System.out.println("Current list of broadcasts:");
        for (String name : expected) {
            System.out.println("            case \"" + name + "\":");
        }
    }

    @Nullable
    private static List<String> getProtectedBroadcasts() throws IOException {
        String top = System.getenv("ANDROID_BUILD_TOP");

        // TODO: We should ship this file with the SDK!
        File file = new File(top, "frameworks/base/core/res/AndroidManifest.xml");
        if (!file.exists()) {
            System.out.println("Set $ANDROID_BUILD_TOP to point to the git repository");
            return null;
        }
        String xml = Files.toString(file, Charsets.UTF_8);
        Document document = XmlUtils.parseDocumentSilently(xml, true);
        Set<String> list = Sets.newHashSet();
        if (document != null && document.getDocumentElement() != null) {
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                short nodeType = child.getNodeType();
                if (nodeType == Node.ELEMENT_NODE
                        && child.getNodeName().equals("protected-broadcast")) {
                    Element element = (Element) child;
                    String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (!name.isEmpty()) {
                        list.add(name);
                    }
                }
            }
        }

        List<String> expected = Lists.newArrayList(list);
        Collections.sort(expected);
        return expected;
    }

    private TestFile manifest =
            xml(
                    "AndroidManifest.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"test.pkg\"\n"
                            + "    android:versionCode=\"1\"\n"
                            + "    android:versionName=\"1.0\" >\n"
                            + "\n"
                            + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:icon=\"@drawable/ic_launcher\"\n"
                            + "        android:label=\"@string/app_name\" >\n"
                            + "        <receiver\n"
                            + "            android:label=\"@string/app_name\"\n"
                            + "            android:name=\".TestReceiver\" >\n"
                            + "                <intent-filter>\n"
                            + "                    <action android:name=\"android.intent.action.BOOT_COMPLETED\"/>\n"
                            + "                </intent-filter>\n"
                            + "        </receiver>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n"
                            + "\n");
}
