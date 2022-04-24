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

import com.android.tools.lint.detector.api.Detector;

public class AppLinksAutoVerifyDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new AppLinksAutoVerifyDetector();
    }

    public void testOk() {
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        // language=JSON
                        "[{\n"
                                + "  \"relation\": [\"delegate_permission/common.handle_all_urls\"],\n"
                                + "  \"target\": {\n"
                                + "    \"namespace\": \"android_app\",\n"
                                + "    \"package_name\": \"com.example.helloworld\",\n"
                                + "    \"sha256_cert_fingerprints\":\n"
                                + "    [\"14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5\"]\n"
                                + "  }\n"
                                + "}]")
                .run()
                .expectClean();
    }

    public void testInvalidPackage() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        // language=JSON
                        "[{\n"
                                + "  \"relation\": [\"delegate_permission/common.handle_all_urls\"],\n"
                                + "  \"target\": {\n"
                                + "    \"namespace\": \"android_app\",\n"
                                + "    \"package_name\": \"com.example\",\n"
                                + "    \"sha256_cert_fingerprints\":\n"
                                + "    [\"14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5\"]\n"
                                + "  }\n"
                                + "}]")
                .run()
                .expect(expected);
    }

    public void testNotAppTarget() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        // language=JSON
                        "[{\n"
                                + "  \"relation\": [\"delegate_permission/common.handle_all_urls\"],\n"
                                + "  \"target\": {\n"
                                + "    \"namespace\": \"web\",\n"
                                + "    \"package_name\": \"com.example.helloworld\",\n"
                                + "    \"sha256_cert_fingerprints\":\n"
                                + "    [\"14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5\"]\n"
                                + "  }\n"
                                + "}]")
                .run()
                .expect(expected);
    }

    public void testHttpResponseError() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Warning: HTTP request for Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails. HTTP response code: 404 [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData("http://example.com/.well-known/assetlinks.json", 404)
                .run()
                .expect(expected);
    }

    public void testFailedHttpConnection() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL)
                .run()
                .expect(expected);
    }

    public void testMalformedUrl() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Error: Malformed URL of Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json. An unknown protocol is specified [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_MALFORMED_URL)
                .run()
                .expect(expected);
    }

    public void testUnknownHost() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Warning: Unknown host: http://example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST)
                .run()
                .expect(expected);
    }

    public void testNotFound() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Error: Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json is not found on the host [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_NOT_FOUND)
                .run()
                .expect(expected);
    }

    public void testWrongJsonSyntax() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Error: http://example.com/.well-known/assetlinks.json has incorrect JSON syntax [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_WRONG_JSON_SYNTAX)
                .run()
                .expect(expected);
    }

    public void testFailedJsonParsing() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Error: Parsing JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_JSON_PARSE_FAIL)
                .run()
                .expect(expected);
    }

    public void testNoAutoVerify() {
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .run()
                .expectClean();
    }

    public void testNotAppLinkInIntents() {
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "            </intent-filter>\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .run()
                .expectClean();
    }

    public void testMultipleLinks() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Error: Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json is not found on the host [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:15: Error: https://www.example.com/.well-known/assetlinks.json has incorrect JSON syntax [AppLinksAutoVerify]\n"
                        + "                <data android:host=\"www.example.com\" />\n"
                        + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:15: Warning: Unknown host: http://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]\n"
                        + "                <data android:host=\"www.example.com\" />\n"
                        + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "2 errors, 2 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <data android:scheme=\"https\" />\n"
                                        + "                <data android:host=\"www.example.com\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL)
                .networkData(
                        "https://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_NOT_FOUND)
                .networkData(
                        "http://www.example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST)
                .networkData(
                        "https://www.example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_WRONG_JSON_SYNTAX)
                .run()
                .expect(expected);
    }

    public void testMultipleIntents() {
        String expected =
                ""
                        + "AndroidManifest.xml:12: Warning: Unknown host: http://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]\n"
                        + "                    android:host=\"www.example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:20: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerify]\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"www.example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data android:scheme=\"http\"\n"
                                        + "                    android:host=\"example.com\"\n"
                                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL)
                .networkData(
                        "http://www.example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST)
                .run()
                .expect(expected);
    }

    public void testUnknownHostWithManifestPlaceholders() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=205990
        // Skip hosts that use manifest placeholders
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data\n"
                                        + "                    android:host=\"${intentFilterHost}\"\n"
                                        + "                    android:pathPrefix=\"/path/\"\n"
                                        + "                    android:scheme=\"https\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST)
                .run()
                .expectClean();
    }

    public void testUnknownHostWithResolvedManifestPlaceholders() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=205990
        // Skip hosts that use manifest placeholders
        String expected =
                ""
                        + "src/main/AndroidManifest.xml:12: Warning: Unknown host: http://example.com. Check if the host exists, and check your network connection [AppLinksAutoVerify]\n"
                        + "                    android:host=\"${intentFilterHost}\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.helloworld\" >\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:allowBackup=\"true\"\n"
                                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                                        + "        <activity android:name=\".MainActivity\" >\n"
                                        + "            <intent-filter android:autoVerify=\"true\">\n"
                                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                        + "                <data\n"
                                        + "                    android:host=\"${intentFilterHost}\"\n"
                                        + "                    android:pathPrefix=\"/gizmos/\"\n"
                                        + "                    android:scheme=\"http\" />\n"
                                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"),
                        gradle(
                                ""
                                        + "buildscript {\n"
                                        + "    dependencies {\n"
                                        + "        classpath 'com.android.tools.build:gradle:2.0.0'\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "android {\n"
                                        + "    defaultConfig {\n"
                                        + "        manifestPlaceholders = [ intentFilterHost:\"example.com\"]\n"
                                        + "    }\n"
                                        + "}\n"))
                .networkData(
                        "http://example.com/.well-known/assetlinks.json",
                        AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST)
                .run()
                .expect(expected);
    }
}
