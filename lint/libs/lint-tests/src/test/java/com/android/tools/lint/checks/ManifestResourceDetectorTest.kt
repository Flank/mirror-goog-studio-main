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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class ManifestResourceDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ManifestResourceDetector()
    }

    fun test() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:enabled="@bool/has_honeycomb" android:name="com.google.android.apps.iosched.appwidget.ScheduleWidgetProvider">
                            <intent-filter>
                                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
                            </intent-filter>
                            <!-- This specifies the widget provider info -->
                            <meta-data android:name="android.appwidget.provider" android:resource="@xml/widgetinfo"/>
                        </receiver>
                    </application>

                </manifest>
                """
            ).indented(),
            xml(
                "res/values/values.xml",
                """
                <resources>
                    <string name="app_name">App Name (Default)</string>
                    <bool name="has_honeycomb">false</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-v11/values.xml",
                """
                <resources>
                    <bool name="has_honeycomb">true</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-en-rUS/values.xml",
                """
                <resources>
                    <string name="app_name">App Name (English)</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-xlarge/values.xml",
                """
                <resources>
                    <dimen name="activity_horizontal_margin">16dp</dimen>
                </resources>
                """
            ).indented()
        ).incremental("AndroidManifest.xml").run().expectClean()
    }

    fun testInvalidManifestReference() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-sdk android:minSdkVersion="14" />

                    <application android:fullBackupContent="@xml/backup">
                        <service
                            android:process="@string/location_process"
                            android:enabled="@bool/enable_wearable_location_service">
                        </service>    </application>

                </manifest>
                """
            ).indented(),
            xml(
                "res/values/values.xml",
                """
                <resources>
                    <string name="location_process">Location Process</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values/bools.xml",
                """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                    <bool name="enable_wearable_location_service">true</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-en-rUS/values.xml",
                """
                <resources>
                    <string name="location_process">Location Process (English)</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-watch/bools.xml",
                """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                    <bool name="enable_wearable_location_service">false</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """
            ).indented(),
            xml(
                "res/xml-mcc/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="mcc"/>
                </full-backup-content>
                """
            ).indented()
        ).incremental("AndroidManifest.xml").run().expect(
            """
            AndroidManifest.xml:6: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21). Found variation in mcc [ManifestResource]
                <application android:fullBackupContent="@xml/backup">
                                                        ~~~~~~~~~~~
            AndroidManifest.xml:8: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21). Found variation in en-rUS [ManifestResource]
                        android:process="@string/location_process"
                                         ~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:9: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21). Found variation in watch-v20 [ManifestResource]
                        android:enabled="@bool/enable_wearable_location_service">
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testBatchAnalysis() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:fullBackupContent="@xml/backup"
                        android:label="@string/app_name" >
                        <receiver android:enabled="@bool/has_honeycomb" android:name="com.google.android.apps.iosched.appwidget.ScheduleWidgetProvider">
                            <intent-filter>
                                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
                            </intent-filter>
                            <!-- This specifies the widget provider info -->
                            <meta-data android:name="android.appwidget.provider" android:resource="@xml/widgetinfo"/>
                        </receiver>
                        <service
                            android:process="@string/location_process"
                            android:enabled="@bool/enable_wearable_location_service">
                        </service>    </application>

                </manifest>
                """
            ).indented(),
            xml(
                "res/values/values.xml",
                """
                <resources>
                    <string name="location_process">Location Process</string>
                    <string name="app_name">App Name (Default)</string>
                    <bool name="has_honeycomb">false</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/values/bools.xml",
                """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                    <bool name="enable_wearable_location_service">true</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-en-rUS/values.xml",
                """
                <resources>
                    <string name="location_process">Location Process (English)</string>
                    <string name="app_name">App Name (English)</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-watch/bools.xml",
                """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                    <bool name="enable_wearable_location_service">false</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-v11/values.xml",
                """
                <resources>
                    <bool name="has_honeycomb">true</bool>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-xlarge/values.xml",
                """
                <resources>
                    <dimen name="activity_horizontal_margin">16dp</dimen>
                </resources>
                """
            ).indented(),
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """
            ).indented(),
            xml(
                "res/xml-mcc/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="mcc"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:11: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21). Found variation in mcc [ManifestResource]
                    android:fullBackupContent="@xml/backup"
                                               ~~~~~~~~~~~
                res/xml-mcc/backup.xml:1: This value will not be used
            AndroidManifest.xml:21: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21). Found variation in en-rUS [ManifestResource]
                        android:process="@string/location_process"
                                         ~~~~~~~~~~~~~~~~~~~~~~~~
                res/values-en-rUS/values.xml:2: This value will not be used
            AndroidManifest.xml:22: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21). Found variation in watch [ManifestResource]
                        android:enabled="@bool/enable_wearable_location_service">
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/values-watch/bools.xml:2: This value will not be used
            3 errors, 0 warnings
            """
        )
    }

    fun testAllowPermissionNameLocalizations() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <permission-group android:name="android.permission-group.CONTACTS"
                        android:icon="@drawable/perm_group_contacts"
                        android:label="@string/permgrouplab_contacts"
                        android:description="@string/permgroupdesc_contacts"
                        android:priority="100" />

                    <permission android:name="android.permission.READ_CONTACTS"
                        android:permissionGroup="android.permission-group.CONTACTS"
                        android:label="@string/permlab_readContacts"
                        android:description="@string/permdesc_readContacts"
                        android:protectionLevel="dangerous" />
                </manifest>
                """
            ).indented(),
            xml(
                "res/values/values.xml",
                """
                <resources>
                    <string name="permgrouplab_contacts">Contacts</string>
                    <string name="permgroupdesc_contacts">access your contacts</string>
                    <string name="permlab_readContacts">read your contacts</string>
                    <string name="permdesc_readContacts">Allows the app to...</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-nb/values.xml",
                """
                <resources>
                    <string name="permgrouplab_contacts">"Kontakter"</string>
                    <string name="permgroupdesc_contacts">"se kontaktene dine"</string>
                    <string name="permlab_readContacts">"lese kontaktene dine"</string>
                    <string name="permdesc_readContacts">"Lar appen lese...</string>
                </resources>
                """
            ).indented()
        ).incremental("AndroidManifest.xml").run().expectClean()
    }

    fun testAllowMetadataResources() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=216279
        // <meta-data> elements are free to reference resources as they see fit.
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          xmlns:tools="http://schemas.android.com/tools"
                          package="foo.bar2">
                    <application>
                        <receiver android:name=".MyReceiver"
                                  android:label="@string/app_name">
                            <meta-data
                                    android:name="com.example.sdk.ApplicationId"
                                    android:value="@string/app_id"/>
                            <meta-data
                                    android:name="com.android.systemui.action_assist_icon"
                                    android:resource="@mipmap/ic_launcher"/>
                        </receiver>
                    </application>
                </manifest>
                """
            ).indented(),
            xml(
                "res/values/values.xml",
                """
                <resources>
                    <string name="app_id">Id</string>
                    <mipmap name="ic_launcher">@mipmap/other</mipmap>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-nb/values.xml",
                """
                <resources>
                    <string name="app_id">"Id"</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-hdpi/values.xml",
                """
                <resources>
                    <mipmap name="ic_launcher">@mipmap/other</mipmap>
                </resources>
                """
            ).indented()
        ).incremental("AndroidManifest.xml").run().expectClean()
    }

    fun testRoundIcon() {
        // Allow round icons in density folders
        // Regression test for https://code.google.com/p/android/issues/detail?id=225711
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="webp.test.tools.android.com.myapplication" >
                    <application
                        android:roundIcon="@mipmap/round_icon" />
                </manifest>
                """
            ).indented(),
            image("res/mipmap-mdpi/round_icon.png", 472, 290).fill(-0x1),
            image("res/mipmap-hdpi/round_icon.png", 472, 290).fill(-0x1),
            image("res/mipmap-xhdpi/round_icon.png", 472, 290).fill(-0x1),
            image("res/mipmap-xxhdpi/round_icon.png", 472, 290).fill(-0x1),
            image("res/mipmap-xxxhdpi/round_icon.png", 472, 290).fill(-0x1)
        ).incremental("AndroidManifest.xml").run().expectClean()
    }

    fun testAnyDensity() {
        // Allow round icons in density folders
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="webp.test.tools.android.com.myapplication" >
                    <application
                        android:randomAttribute="@mipmap/round_icon" />
                </manifest>
                """
            ).indented(),
            image("res/mipmap-mdpi/round_icon.png", 472, 290).fill(-0x1),
            image("res/mipmap-hdpi-v4/round_icon.png", 472, 290).fill(-0x1),
            image("res/mipmap-v21/round_icon.png", 472, 290).fill(-0x1)
        ).incremental("AndroidManifest.xml").run().expectClean()
    }
}
