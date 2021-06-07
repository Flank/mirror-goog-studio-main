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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
import org.w3c.dom.Element
import java.net.URL

class AppLinksValidDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return AppLinksValidDetector()
    }

    fun testIntentFilterDataDeclaration() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <activity android:name="com.example.Activity">
                        <intent-filter>
                            <data android:scheme="https" android:host="example.com"/>
                            <data android:scheme="http" android:host="example.org"/>
                        </intent-filter>
                    </activity>
                    <receiver android:name="com.example.Receiver">
                        <intent-filter>
                            <data android:scheme="https"/>
                            <data
                                android:host="example.com"
                                android:path="/path"
                                android:scheme="https"
                                />
                        </intent-filter>
                    </receiver>
                    <service android:name="com.example.Service">
                        <intent-filter>
                            <data android:scheme="https"/>
                            <!-- Don't warn on only host and port -->
                            <data android:host="example.com" android:port="40"/>
                            <data android:host="example.com" android:port="41" android:path="/sub"/>
                        </intent-filter>
                    </service>
                    <provider android:name="com.example.Provider">
                        <intent-filter>
                            <data android:scheme="https"/>
                            <data android:host="example.com" android:mimeType="image/jpeg"/>
                        </intent-filter>
                    </provider>
                    <activity android:name="com.example.Activity2">
                        <intent-filter>
                            <data android:scheme="https" android:host="example.com"/>
                            <!-- Don't warn on multiple attributes of only path variants-->
                            <data
                                android:pathPrefix="/prefix"
                                android:path="/path"
                                android:pathPattern="/pattern/*"
                                />
                        </intent-filter>
                    </activity>
                    <activity android:name="com.example.Activity3">
                        <intent-filter>
                            <!-- Don't warn on only 1 data tag -->
                            <data android:scheme="https" android:host="example.com"/>
                        </intent-filter>
                    </activity>
                </manifest>
                """
            ).indented()
        )
            .issues(AppLinksValidDetector.INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES)
            .run()
            .expect(
                """
                AndroidManifest.xml:5: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                            <data android:scheme="https" android:host="example.com"/>
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:6: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                            <data android:scheme="http" android:host="example.org"/>
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:12: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                            <data
                            ^
                AndroidManifest.xml:24: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                            <data android:host="example.com" android:port="41" android:path="/sub"/>
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:30: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                            <data android:host="example.com" android:mimeType="image/jpeg"/>
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 5 warnings
                """
            )
            .verifyFixes()
            .robot(true)
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 5: Replace with <data android:scheme="https"/>...:
                @@ -5 +5
                -             <data android:scheme="https" android:host="example.com"/>
                +             <data android:scheme="https"/>
                +             <data android:host="example.com"/>
                Fix for AndroidManifest.xml line 6: Replace with <data android:scheme="http"/>...:
                @@ -6 +6
                -             <data android:scheme="http" android:host="example.org"/>
                +             <data android:scheme="http"/>
                +             <data android:host="example.org"/>
                Fix for AndroidManifest.xml line 12: Replace with <data android:scheme="https"/>...:
                @@ -12 +12
                -             <data
                -                 android:host="example.com"
                -                 android:path="/path"
                -                 android:scheme="https"
                -                 />
                +             <data android:scheme="https"/>
                +             <data android:host="example.com"/>
                +             <data android:path="/path"/>
                Fix for AndroidManifest.xml line 24: Replace with <data android:host="example.com" android:port="41"/>...:
                @@ -24 +24
                -             <data android:host="example.com" android:port="41" android:path="/sub"/>
                +             <data android:host="example.com" android:port="41"/>
                +             <data android:path="/sub"/>
                Fix for AndroidManifest.xml line 30: Replace with <data android:host="example.com"/>...:
                @@ -30 +30
                -             <data android:host="example.com" android:mimeType="image/jpeg"/>
                +             <data android:host="example.com"/>
                +             <data android:mimeType="image/jpeg"/>
                """
            )
    }

    fun testWrongNamespace() {
        val expected =
            """
            AndroidManifest.xml:12: Error: Validation nodes should be in the tools: namespace to ensure they are removed from the manifest at build time [TestAppLink]
                        <validation />
                         ~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                            </intent-filter>
                            <validation />
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testMissingTestUrl() {
        val expected = """
            AndroidManifest.xml:12: Error: Expected testUrl attribute [AppLinkUrlError]
                        <tools:validation />
                        ~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                            </intent-filter>
                            <tools:validation />
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testBadTestUrl() {
        val expected = """
            AndroidManifest.xml:11: Error: Invalid test URL: no protocol: no-protocol [TestAppLink]
                        <tools:validation testUrl="no-protocol"/>
                                                   ~~~~~~~~~~~
            AndroidManifest.xml:12: Error: Invalid test URL: unknown protocol: unknown-protocol [TestAppLink]
                        <tools:validation testUrl="unknown-protocol://example.com/gizmos/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:13: Error: Invalid test URL: Invalid host: [FEDC:BA98:7654:3210:GEDC:BA98:7654:3210] [TestAppLink]
                        <tools:validation testUrl="http://[FEDC:BA98:7654:3210:GEDC:BA98:7654:3210]:80/index.html"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                            </intent-filter>
                            <tools:validation testUrl="no-protocol"/>
                            <tools:validation testUrl="unknown-protocol://example.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="http://[FEDC:BA98:7654:3210:GEDC:BA98:7654:3210]:80/index.html"/>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testValidation1() {
        val expected =
            """
            AndroidManifest.xml:14: Error: Test URL did not match path prefix /gizmos, path literal /literal/path [TestAppLink]
                        <tools:validation testUrl="http://example.com/notmatch/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:15: Error: Test URL did not match host example.com [TestAppLink]
                        <tools:validation testUrl="http://notmatch.com/gizmos/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:16: Error: Test URL did not match scheme http [TestAppLink]
                        <tools:validation testUrl="https://example.com/gizmos/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                                <data android:path="/literal/path" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="http://example.com/notmatch/foo/bar"/>
                            <tools:validation testUrl="http://notmatch.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="https://example.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="http://example.com/literal/path"/>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testValidation2() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher" >
                        <activity android:name=".MainActivity" >
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http" />
                                <data android:scheme="https" />
                                <data android:host="www.twitter.com" />
                                <data android:host="twitter.com" />
                                <data android:host="*.twitter.com" />
                                <data android:host="*twitter.com" />
                                <data android:pathPattern="/vioside/.*" />
                            </intent-filter>
                            <tools:validation testUrl="https://twitter.com/vioside/status/761453456683069440" />
                            <tools:validation testUrl="https://www.twitter.com/vioside/status/761453456683069440" />
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testHostWildcardMatching() {
        val expected = """
            AndroidManifest.xml:12: Error: Test URL did not match host *.example.com [TestAppLink]
                        <tools:validation testUrl="http://example.com/path/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                    android:host="*.example.com"
                                    android:pathPrefix="/path" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://.example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://www.example.com/path/foo/bar"/>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testPortMatching() {
        val expected =
            """
            AndroidManifest.xml:25: Error: Test URL did not match port none or did not match port 85 or did not match host android.com [TestAppLink]
                        <tools:validation testUrl="http://example.com:80/path/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:29: Error: Test URL did not match host example.com or did not match port 86 [TestAppLink]
                        <tools:validation testUrl="http://android.com/path/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="/path" />
                            </intent-filter>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:port="85"
                                      android:pathPrefix="/path" />
                            </intent-filter>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="http"
                                      android:host="android.com"
                                      android:port="86"
                                      android:pathPrefix="/path" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://example.com:80/path/foo/bar"/>
                            <tools:validation testUrl="http://example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://example.com:85/path/foo/bar"/>
                            <tools:validation testUrl="http://android.com:86/path/foo/bar"/>
                            <tools:validation testUrl="http://android.com/path/foo/bar"/>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testHostAndPortCombination() {
        // Host and port must be specified on the same element
        val expected =
            """
            AndroidManifest.xml:11: Error: The port must be specified in the same <data> element as the host [AppLinkUrlError]
                            <data android:port="80" />
                                                ~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"/>
                                <data android:host="example.com"
                                      android:port="81" />
                                <data android:host="example.com" />
                                <data android:port="80" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testValidPortNumber() {
        // Port numbers must be in the valid range
        val expected = """
            AndroidManifest.xml:8: Error: not a valid port number [AppLinkUrlError]
                                  android:port="-1" />
                                                ~~
            AndroidManifest.xml:10: Error: not a valid port number [AppLinkUrlError]
                                  android:port="128000" />
                                                ~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <data android:scheme="http"/>
                                <data android:host="example.com"
                                      android:port="-1" />
                                <data android:host="example.com"
                                      android:port="128000" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testNonEmpty() {
        // Attributes are not allowed to be empty
        val expected = """
            AndroidManifest.xml:6: Error: android:scheme cannot be empty [AppLinkUrlError]
                            <data android:scheme=""
                                  ~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:7: Error: android:host cannot be empty [AppLinkUrlError]
                                  android:host=""
                                  ~~~~~~~~~~~~~~~
            AndroidManifest.xml:8: Error: android:port cannot be empty [AppLinkUrlError]
                                  android:port=""
                                  ~~~~~~~~~~~~~~~
            AndroidManifest.xml:9: Error: android:pathPrefix cannot be empty [AppLinkUrlError]
                                  android:pathPrefix=""
                                  ~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:10: Error: android:path cannot be empty [AppLinkUrlError]
                                  android:path=""
                                  ~~~~~~~~~~~~~~~
            AndroidManifest.xml:11: Error: android:pathPattern cannot be empty [AppLinkUrlError]
                                  android:pathPattern=""
                                  ~~~~~~~~~~~~~~~~~~~~~~
            6 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <data android:scheme=""
                                      android:host=""
                                      android:port=""
                                      android:pathPrefix=""
                                      android:path=""
                                      android:pathPattern=""
                                      android:mimeType=""/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testNoTrailingSchemeColon() {
        // There should be no trailing colons for schemes
        val expected =
            """
            AndroidManifest.xml:6: Error: Don't include trailing colon in the scheme declaration [AppLinkUrlError]
                            <data android:scheme="http:"/>
                                                  ~~~~~
            AndroidManifest.xml:7: Error: Don't include trailing colon in the scheme declaration [AppLinkUrlError]
                            <data android:scheme="https:"/>
                                                  ~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <data android:scheme="http:"/>
                                <data android:scheme="https:"/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testWrongHostnameWildcard() {
        // Wildcard can only be at the beginning
        val expected =
            """
            AndroidManifest.xml:7: Error: The host wildcard (*) can only be the first character [AppLinkUrlError]
                            <data android:host="example.*.com"
                                                ~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <data android:scheme="http"/>
                                <data android:host="example.*.com"
                 />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testLowerCase() {
        // Scheme, host and mime type are all case sensitive and should only use lower case
        val expected =
            """
            AndroidManifest.xml:7: Error: Scheme matching is case sensitive and should only use lower-case characters [AppLinkUrlError]
                            <data android:scheme="HTTP"
                                                  ~~~~
            AndroidManifest.xml:8: Error: Host matching is case sensitive and should only use lower-case characters [AppLinkUrlError]
                                  android:host="Example.Com"
                                                ~~~~~~~~~~~
            AndroidManifest.xml:12: Error: Mime-type matching is case sensitive and should only use lower-case characters [AppLinkUrlError]
                                  android:mimeType="MimeType"/>
                                                    ~~~~~~~~
            3 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="HTTP"
                                      android:host="Example.Com"
                                      android:pathPrefix="/Foo"
                                      android:path="/Foo"
                                      android:pathPattern="/Foo"
                                      android:mimeType="MimeType"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testPathsBeginWithSlash() {
        // Paths should begin with /
        val expected =
            """
            AndroidManifest.xml:9: Error: android:pathPrefix attribute should start with /, but it is samplePrefix [AppLinkUrlError]
                                  android:pathPrefix="samplePrefix"
                                                      ~~~~~~~~~~~~
            AndroidManifest.xml:10: Error: android:path attribute should start with /, but it is samplePath [AppLinkUrlError]
                                  android:path="samplePath"
                                                ~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="samplePrefix"
                                      android:path="samplePath"
                                      android:pathPattern="samplePattern"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 9: Replace with /samplePrefix:
            @@ -9 +9
            -                       android:pathPrefix="samplePrefix"
            +                       android:pathPrefix="/samplePrefix"
            Fix for AndroidManifest.xml line 10: Replace with /samplePath:
            @@ -10 +10
            -                       android:path="samplePath"
            +                       android:path="/samplePath"
            """
        )
    }

    fun testSuppressWithOldId() {
        // Make sure that the ignore-issue mechanism works for both the current and the
        // previous issue id
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >
                    <application>
                        <activity>
                            <intent-filter>
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPattern="foo"
                                      tools:ignore="AppLinkUrlError"/>
                            </intent-filter>
                            <intent-filter>
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPattern="foo"
                                      tools:ignore="GoogleAppIndexingUrlError"/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testWrongPathPrefix() {
        val expected =
            """
            AndroidManifest.xml:18: Error: android:pathPrefix attribute should start with /, but it is gizmos [AppLinkUrlError]
                                android:pathPrefix="gizmos" />
                                                    ~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testWrongPort() {
        val expected = """
            AndroidManifest.xml:18: Error: not a valid port number [AppLinkUrlError]
                                android:port="ABCD"
                                              ~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:port="ABCD"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testSchemeAndHostMissing() {
        val expected = """
            AndroidManifest.xml:14: Error: Missing URL [AppLinkUrlError]
                        <intent-filter android:label="@string/title_activity_fullscreen">
                        ^
            AndroidManifest.xml:16: Error: At least one host must be specified [AppLinkUrlError]
                            <data android:pathPrefix="/gizmos" />
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:16: Error: At least one scheme must be specified [AppLinkUrlError]
                            <data android:pathPrefix="/gizmos" />
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:16: Error: Missing URL for the intent filter [AppLinkUrlError]
                            <data android:pathPrefix="/gizmos" />
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run()
            .expect(expected)
            .verifyFixes()
            .window(1)
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 14: Set scheme="http":
                @@ -15 +15
                              android:theme="@style/FullscreenTheme" >
                -             <intent-filter android:label="@string/title_activity_fullscreen" >
                +             <intent-filter
                +                 android:label="@string/title_activity_fullscreen"
                +                 android:scheme="http" >
                                  <action android:name="android.intent.action.VIEW" />
                Fix for AndroidManifest.xml line 16: Set host:
                @@ -18 +18

                -                 <data android:pathPrefix="/gizmos" />
                +                 <data
                +                     android:host="[TODO]|"
                +                     android:pathPrefix="/gizmos" />
                Fix for AndroidManifest.xml line 16: Set scheme="http":
                @@ -18 +18

                -                 <data android:pathPrefix="/gizmos" />
                +                 <data
                +                     android:pathPrefix="/gizmos"
                +                     android:scheme="http" />
                """
            )
    }

    fun testMultiData() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMultiIntent() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMultiIntentWithError() {
        val expected = """
            AndroidManifest.xml:20: Error: At least one host must be specified [AppLinkUrlError]
                            <data android:scheme="http"
                            ^
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
            .verifyFixes()
            .window(1)
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 20: Set host:
                @@ -24 +24
                                  <data
                +                     android:host="[TODO]|"
                                      android:pathPrefix="/gizmos"
                """
            )
    }

    fun testNotExported() {
        val expected =
            """
            AndroidManifest.xml:6: Error: Activity supporting ACTION_VIEW is not exported [AppLinkUrlError]
                    <activity android:exported="false"
                    ^
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:theme="@style/AppTheme" >
                        <activity android:exported="false"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example1.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                            <intent-filter android:label="@string/title_activity_fullscreen"/>
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example2.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <activity android:exported="true">
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example1.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testOkWithResource() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          package="com.example.helloworld">

                    <application
                            android:allowBackup="true"
                            android:icon="@mipmap/ic_launcher"
                            android:label="@string/app_name"
                            android:theme="@style/AppTheme" >
                        <activity
                                android:name=".FullscreenActivity"
                                android:configChanges="orientation|keyboardHidden|screenSize"
                                android:label="@string/title_activity_fullscreen"
                                android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="@string/path_prefix"
                                      android:port="@string/port"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>

                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented(),
            xml(
                "res/values/appindexing_strings.xml",
                """
                <resources>
                    <string name="path_prefix">/pathprefix</string>
                    <string name="port">8080</string>
                </resources>
                """
            ).indented()
        ).incremental("AndroidManifest.xml").run().expectClean()
    }

    fun testWrongWithResource() {
        val expected =
            """
            AndroidManifest.xml:18: Error: android:pathPrefix attribute should start with /, but it is pathprefix [AppLinkUrlError]
                                  android:pathPrefix="@string/path_prefix"
                                                      ~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:19: Error: not a valid port number [AppLinkUrlError]
                                  android:port="@string/port"/>
                                                ~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          package="com.example.helloworld">

                    <application
                            android:allowBackup="true"
                            android:icon="@mipmap/ic_launcher"
                            android:label="@string/app_name"
                            android:theme="@style/AppTheme" >
                        <activity
                                android:name=".FullscreenActivity"
                                android:configChanges="orientation|keyboardHidden|screenSize"
                                android:label="@string/title_activity_fullscreen"
                                android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="@string/path_prefix"
                                      android:port="@string/port"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>

                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented(),
            xml(
                "res/values/appindexing_wrong_strings.xml",
                """
                <resources>
                    <string name="path_prefix">pathprefix</string>
                    <string name="port">gizmos</string>
                </resources>
                """
            ).indented()
        ).incremental("AndroidManifest.xml").run().expect(expected)
    }

    fun testNoUrl() {
        val expected = """
            AndroidManifest.xml:14: Error: Missing URL [AppLinkUrlError]
                        <intent-filter android:label="@string/title_activity_fullscreen">
                        ^
            AndroidManifest.xml:16: Error: Missing URL for the intent filter [AppLinkUrlError]
                            <data />
                            ~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testMimeType() {
        val expected = """
            AndroidManifest.xml:14: Error: Missing URL [AppLinkUrlError]
                        <intent-filter android:label="@string/title_activity_fullscreen">
                        ^
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:mimeType="mimetype" />                 <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testDataMissing() {
        val expected = """
            AndroidManifest.xml:14: Error: Missing data element [AppLinkUrlError]
                        <intent-filter android:label="@string/title_activity_fullscreen">
                        ^
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testNotBrowsable() {
        val expected =
            """
            AndroidManifest.xml:24: Error: Activity supporting ACTION_VIEW is not set as BROWSABLE [AppLinkUrlError]
                        <intent-filter android:label="@string/title_activity_fullscreen">
                        ^
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".MainActivity"
                            android:label="@string/app_name" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>

                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testDataBinding() {
        // When using data binding don't give incorrect validation messages such as
        // uppercase usage, missing slash prefix etc.
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data android:scheme="@={Schemes.default}"
                                    android:host="@{Hosts.lookup}"
                                    android:pathPrefix="@{Prefixes.lookup}" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/gizmos/foo/bar"/>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun test37343746() {
        // Regression test for https://issuetracker.google.com/issues/37343746
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter>
                                 <action android:name="android.intent.action.PROVIDER_CHANGED"/>
                                 <data android:scheme="content"/>
                                 <data android:host="＄{applicationId}.provider"/>
                                 <data android:path="/beep/boop"/>
                                 <data android:mimeType="*/*"/>
                             </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun test79995047() {
        // Regression test for https://issuetracker.google.com/issues/79995047
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter>
                                <!-- Following https://developer.android.com/guide/topics/providers/content-provider-basics#MIMETypeReference -->
                                <data android:mimeType="vnd.android.cursor.item/vnd.＄{applicationId}.item" /> <!-- OK -->
                            </intent-filter>
                            <intent-filter>
                                <data android:mimeType="vnd.android.cursor.item/vnd.＄{placeholder}.item" /> <!-- WARN -->
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented(),
            gradle(
                """
                android {
                    defaultConfig {
                        manifestPlaceholders = [ placeholder:"ABC"]
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
                src/main/AndroidManifest.xml:12: Error: Mime-type matching is case sensitive and should only use lower-case characters (without placeholders, value is vnd.android.cursor.item/vnd.ABCitem) [AppLinkUrlError]
                                <data android:mimeType="vnd.android.cursor.item/vnd.＄{placeholder}.item" /> <!-- WARN -->
                                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
        )
    }

    fun test62810553() {
        // Regression test for https://issuetracker.google.com/62810553
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter>
                                <data android:scheme="myscheme" android:pathPrefix="/path/to/there"/> <!-- OK -->
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun test68322249() {
        // Regression test for https://issuetracker.google.com/issues/68322249
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity>
                            <intent-filter android:autoVerify="true">
                                <data
                                    android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="＄{DEEP_LINK_PREFIX}" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testStaticValidation() {
        // Usage outside of lint
        val document = XmlUtils.parseDocument(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.pkg" >

                <application>
                    <activity>
                        <intent-filter android:autoVerify="true">
                            <data android:scheme="http"
                                android:host="example.com"
                                android:pathPrefix="/gizmos" />
                            <data android:path="/literal/path" />
                        </intent-filter>
                        <tools:validation testUrl="http://example.com/gizmos/foo/bar"/>
                        <tools:validation testUrl="http://example.com/notmatch/foo/bar"/>
                        <tools:validation testUrl="http://notmatch.com/gizmos/foo/bar"/>
                        <tools:validation testUrl="https://example.com/gizmos/foo/bar"/>
                        <tools:validation testUrl="http://example.com/literal/path"/>
                    </activity>
                </application>

            </manifest>
            """,
            true
        )
        val root = document.documentElement
        val application = XmlUtils.getFirstSubTag(root)
        val activity = XmlUtils.getFirstSubTag(application)
        assertThat(activity).isNotNull()

        val detector = AppLinksValidDetector()
        fun createUriInfos(activity: Element, context: XmlContext?): List<AppLinksValidDetector.UriInfo> =
            detector.createUriInfos(activity, context)
        fun testElement(testUrl: URL, infos: List<AppLinksValidDetector.UriInfo>): String? =
            detector.testElement(testUrl, infos)

        val infos = createUriInfos(activity!!, null)
        assertThat(testElement(URL("http://example.com/literal/path"), infos)).isNull() // success
        assertThat(testElement(URL("http://example.com/gizmos/foo/bar"), infos))
            .isNull() // success
        assertThat(testElement(URL("https://example.com/gizmos/foo/bar"), infos))
            .isEqualTo("Test URL did not match scheme http")
        assertThat(testElement(URL("http://example.com/notmatch/foo/bar"), infos))
            .isEqualTo("Test URL did not match path prefix /gizmos, path literal /literal/path")
        assertThat(testElement(URL("http://notmatch.com/gizmos/foo/bar"), infos))
            .isEqualTo("Test URL did not match host example.com")
    }
}
