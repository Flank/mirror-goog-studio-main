/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

import com.android.tools.lint.detector.api.Detector;

public class WearStandaloneAppDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new WearStandaloneAppDetector();
    }

    public void testInvalidAttributeValueForUsesFeature() throws Exception {
        assertEquals("AndroidManifest.xml:4: Error: android:required=\"false\" is not supported for this feature [InvalidWearFeatureAttribute]\n"
                        + "    <uses-feature android:name=\"android.hardware.type.watch\" android:required=\"false\"/>\n"
                        + "                                                             ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                        + "    <uses-sdk android:targetSdkVersion=\"23\" />\n"
                        + "    <uses-feature android:name=\"android.hardware.type.watch\" android:required=\"false\"/>\n"
                        + "    <application>\n"
                        + "        <meta-data \n"
                        + "            android:name=\"com.google.android.wearable.standalone\" \n"
                        + "            android:value=\"true\"\n />"
                        + "    </application>\n"
                        + "</manifest>\n")));
    }

    public void testMissingMetadata() throws Exception {
        assertEquals("AndroidManifest.xml:5: Warning: Missing <meta-data android:name=\"com.google.android.wearable.standalone\" ../> element [WearStandaloneAppFlag]\n"
                        + "    <application>\n"
                        + "    ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                        + "    <uses-sdk android:targetSdkVersion=\"23\" />\n"
                        + "    <uses-feature android:name=\"android.hardware.type.watch\"/>\n"
                        + "    <application>\n"
                        + "        <!-- Missing meta-data element -->\n"
                        + "    </application>\n"
                        + "</manifest>\n")));
    }

    public void testInvalidAttributeValueForStandaloneMetadata() throws Exception {
        assertEquals("AndroidManifest.xml:7: Warning: Expecting a boolean value for attribute android:value [WearStandaloneAppFlag]\n"
                        + "            android:value=\"@string/foo\" />\n"
                        + "                           ~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                        + "    <uses-feature android:name=\"android.hardware.type.watch\"/>\n"
                        + "    <application>\n"
                        + "        <meta-data \n"
                        + "            android:name=\"com.google.android.wearable.standalone\" \n"
                        + "            android:value=\"@string/foo\" />\n"
                        + "    </application>\n"
                        + "</manifest>\n")));
    }

    public void testValidUsesFeatureAndMetadata() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                        + "    <uses-feature android:name=\"android.hardware.type.watch\"/>\n"
                        + "    <application>\n"
                        + "        <meta-data \n"
                        + "            android:name=\"com.google.android.wearable.standalone\" \n"
                        + "            android:value=\"true\" />\n"
                        + "    </application>\n"
                        + "</manifest>\n")));
    }
}
