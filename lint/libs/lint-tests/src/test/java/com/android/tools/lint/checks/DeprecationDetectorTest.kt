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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class DeprecationDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector {
        return DeprecationDetector()
    }

    fun testApi4() {
        val expected =
            """
            res/layout/deprecation.xml:1: Warning: AbsoluteLayout is deprecated [Deprecated]
            <AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
             ~~~~~~~~~~~~~~
            res/layout/deprecation.xml:15: Warning: android:autoText is deprecated: Use inputType instead [Deprecated]
                    android:autoText="true"
                    ~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:16: Warning: android:capitalize is deprecated: Use inputType instead [Deprecated]
                    android:capitalize="true"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:17: Warning: android:editable is deprecated: Use an <EditText> to make it editable [Deprecated]
                    android:editable="true"
                    ~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:19: Warning: android:inputMethod is deprecated: Use inputType instead [Deprecated]
                    android:inputMethod="@+id/foo"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:20: Warning: android:numeric is deprecated: Use inputType instead [Deprecated]
                    android:numeric="true"
                    ~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:21: Warning: android:password is deprecated: Use inputType instead [Deprecated]
                    android:password="true"
                    ~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:22: Warning: android:phoneNumber is deprecated: Use inputType instead [Deprecated]
                    android:phoneNumber="true"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:25: Warning: android:editable is deprecated: <EditText> is already editable [Deprecated]
                <EditText android:editable="true" />
                          ~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/deprecation.xml:26: Warning: android:editable is deprecated: Use inputType instead [Deprecated]
                <EditText android:editable="false" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 10 warnings
            """
        lint().files(manifest().minSdk(4), mDeprecation).run().expect(expected)
    }

    fun testUsesSdkM() {
        val expected =
            """
            AndroidManifest.xml:7: Warning: uses-permission-sdk-m is deprecated: Use `uses-permission-sdk-23 instead [Deprecated]
                <uses-permission-sdk-m android:name="foo.bar.BAZ" />
                 ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <uses-sdk android:minSdkVersion="4" />
                    <uses-permission android:name="foo.bar.BAZ" />
                    <uses-permission-sdk-23 android:name="foo.bar.BAZ" />
                    <uses-permission-sdk-m android:name="foo.bar.BAZ" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name=".BytecodeTestsActivity"
                            android:label="@string/app_name" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    // Sample code
    private val mDeprecation = xml(
        "res/layout/deprecation.xml",
        """
        <AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent" >

            <Button
                android:id="@+id/button1"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_x="5dp"
                android:layout_y="100dp"
                android:text="Button" />

            <!--  Deprecated attributes -->
            <TextView
                android:autoText="true"
                android:capitalize="true"
                android:editable="true"
                android:enabled="true"
                android:inputMethod="@+id/foo"
                android:numeric="true"
                android:password="true"
                android:phoneNumber="true"
                android:singleLine="true" />

            <EditText android:editable="true" />
            <EditText android:editable="false" />

        </AbsoluteLayout>
        """
    ).indented()

    fun testAndroidX() {
        lint().files(
            xml(
                "res/xml/preferences.xml",
                """
                <androidx.preference.PreferenceScreen
                    xmlns:app="http://schemas.android.com/apk/res-auto">

                    <SwitchPreferenceCompat
                        app:key="notifications"
                        app:title="Enable message notifications"/>

                    <Preference
                        app:key="feedback"
                        app:title="Send feedback"
                        app:summary="Report technical issues or suggest new features"/>

                </androidx.preference.PreferenceScreen>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testFrameworkOldProject() {
        lint().files(
            xml(
                "res/xml/preferences.xml",
                """
                <android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <CheckBoxPreference>
                    </CheckBoxPreference>
                </android.preference.PreferenceScreen>
                """
            ).indented(),
            manifest().targetSdk(29)
        ).run().expect(
            """
                res/xml/preferences.xml:1: Warning: The android.preference library is deprecated, it is recommended that you migrate to the AndroidX Preference library instead. [Deprecated]
                <android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
        )
    }

    fun testFramworkNewProject() {
        lint().files(
            xml(
                "res/xml/preferences.xml",
                """
                <android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <CheckBoxPreference>
                    </CheckBoxPreference>
                </android.preference.PreferenceScreen>
                """
            ).indented(),
            manifest().targetSdk(30)
        ).run().expect(
            """
                res/xml/preferences.xml:1: Warning: The android.preference library is deprecated, it is recommended that you migrate to the AndroidX Preference library instead. [Deprecated]
                <android.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
        )
    }

    fun testCustomFrameworkPreference() {
        lint().files(
            java(
                """
                package android.preference;
                public class Preference {
                    public CustomOldPreference() {}}
                """
            ).indented(),
            java(
                """
                package com.example.myapplication;
                public class CustomOldPreference extends android.preference.Preference {
                    public CustomOldPreference() {
                        super();
                    }
                }
                """
            ).indented(),
            xml(
                "res/xml/preferences.xml",
                """
                <com.example.myapplication.CustomOldPreference xmlns:android="http://schemas.android.com/apk/res/android">
                    <CheckBoxPreference>
                    </CheckBoxPreference>
                </com.example.myapplication.CustomOldPreference>
                """
            ).indented(),
            manifest().targetSdk(30)
        ).run().expect(
            """
                res/xml/preferences.xml:1: Warning: com.example.myapplication.CustomOldPreference inherits from android.preference.Preference which is now deprecated, it is recommended that you migrate to the AndroidX Preference library. [Deprecated]
                <com.example.myapplication.CustomOldPreference xmlns:android="http://schemas.android.com/apk/res/android">
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings"""
        )
    }

    fun testGcmFjdDeprecation() {
        lint().files(
            java(
                """
                package test.pkg;
                import android.content.Context;
                import com.firebase.jobdispatcher.FirebaseJobDispatcher;
                import com.google.android.gms.gcm.GcmNetworkManager;

                @SuppressWarnings("unused")
                public class DeprecationTestJava {
                    public void test(Object driver) {
                        FirebaseJobDispatcher firebaseJobDispatcher =
                                new FirebaseJobDispatcher(driver);
                    }
                    public void testGcm(Context context, Object task) {
                        GcmNetworkManager.getInstance(context).schedule(task);
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                import android.content.Context
                import com.firebase.jobdispatcher.FirebaseJobDispatcher
                import com.google.android.gms.gcm.GcmNetworkManager

                @Suppress("unused", "UNUSED_VARIABLE")
                class DeprecationTestKotlin {
                    fun test(driver: Any) {
                        val firebaseJobDispatcher = FirebaseJobDispatcher(driver)
                    }
                    fun testGcm(context: Context?, task: Any) {
                        GcmNetworkManager.getInstance(context).schedule(task)
                    }
                }
                """
            ).indented(),

            // Stubs
            java(
                """
                package com.firebase.jobdispatcher;
                public class FirebaseJobDispatcher {
                    public FirebaseJobDispatcher(Object driver) { }
                }
                """
            ).indented(),
            java(
                """
                package com.google.android.gms.gcm;
                import android.content.Context;
                public class GcmNetworkManager {
                    public static GcmNetworkManager getInstance(Context context) {
                        return new GcmNetworkManager();
                    }
                    public void schedule(Object task) { }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/DeprecationTestJava.java:10: Warning: Job scheduling with FirebaseJobDispatcher is deprecated: Use AndroidX WorkManager instead [Deprecated]
                            new FirebaseJobDispatcher(driver);
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/DeprecationTestJava.java:13: Warning: Job scheduling with GcmNetworkManager is deprecated: Use AndroidX WorkManager instead [Deprecated]
                    GcmNetworkManager.getInstance(context).schedule(task);
                                      ~~~~~~~~~~~
            src/test/pkg/DeprecationTestKotlin.kt:9: Warning: Job scheduling with FirebaseJobDispatcher is deprecated: Use AndroidX WorkManager instead [Deprecated]
                    val firebaseJobDispatcher = FirebaseJobDispatcher(driver)
                                                ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/DeprecationTestKotlin.kt:12: Warning: Job scheduling with GcmNetworkManager is deprecated: Use AndroidX WorkManager instead [Deprecated]
                    GcmNetworkManager.getInstance(context).schedule(task)
                                      ~~~~~~~~~~~
            0 errors, 4 warnings
            """
        ).expectFixDiffs(
            """
            Show URL for src/test/pkg/DeprecationTestJava.java line 10: https://developer.android.com/topic/libraries/architecture/workmanager/migrating-fb
            Show URL for src/test/pkg/DeprecationTestJava.java line 13: https://developer.android.com/topic/libraries/architecture/workmanager/migrating-gcm
            Show URL for src/test/pkg/DeprecationTestKotlin.kt line 9: https://developer.android.com/topic/libraries/architecture/workmanager/migrating-fb
            Show URL for src/test/pkg/DeprecationTestKotlin.kt line 12: https://developer.android.com/topic/libraries/architecture/workmanager/migrating-gcm
            """
        )
    }

    fun testChooserTargetServiceDeprecation() {
        lint().files(
            java(
                """
                package test.pkg;
                import android.content.ComponentName;
                import android.content.IntentFilter;
                import android.service.chooser.ChooserTarget;
                import android.service.chooser.ChooserTargetService;

                @SuppressWarnings("unused")
                public class DeprecationTestJava {
                    public void test(Object driver) {
                        ChooserClass chooser = new ChooserClass();
                    }
                }

                class ChooserClass extends ChooserTargetService {
                    @Override
                    public List<ChooserTarget> onGetChooserTargets(
                        ComponentName c, IntentFilter i) {
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                import android.content.ComponentName;
                import android.content.IntentFilter;
                import android.service.chooser.ChooserTarget
                import android.service.chooser.ChooserTargetService

                @Suppress("unused", "UNUSED_VARIABLE")
                class DeprecationTestKotlin {
                    fun test(driver: Any) {
                        val chooser = ChooserClass()
                    }
                }

                class ChooserClass : ChooserTargetService() {
                    override fun onGetChooserTargets(
                        p0: ComponentName?,
                    p1: IntentFilter?
                    ): MutableList<ChooserTarget> {
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/DeprecationTestJava.java:14: Warning: ChooserClass extends the deprecated ChooserTargetService: Use the Share API instead [Deprecated]
            class ChooserClass extends ChooserTargetService {
                  ~~~~~~~~~~~~
            src/test/pkg/DeprecationTestKotlin.kt:14: Warning: ChooserClass extends the deprecated ChooserTargetService: Use the Share API instead [Deprecated]
            class ChooserClass : ChooserTargetService() {
                  ~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        ).expectFixDiffs(
            """
            Show URL for src/test/pkg/DeprecationTestJava.java line 14: https://developer.android.com/training/sharing/receive.html?source=studio#providing-direct-share-targets
            Show URL for src/test/pkg/DeprecationTestKotlin.kt line 14: https://developer.android.com/training/sharing/receive.html?source=studio#providing-direct-share-targets
            """
        )
    }

    fun testUsesChooserTargetServicePermission() {
        val expected =
            """
            AndroidManifest.xml:11: Warning: ChooserTargetService` is deprecated: Please see https://developer.android.com/training/sharing/receive.html?source=studio#providing-direct-share-targets [Deprecated]
                        android:permission="android.permission.BIND_CHOOSER_TARGET_SERVICE">
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-sdk android:minSdkVersion="1" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <service
                            android:name=".ChooserService"
                            android:label="@string/service_name"
                            android:permission="android.permission.BIND_CHOOSER_TARGET_SERVICE">
                            <intent-filter>
                                <action android:name="android.service.chooser.ChooserTargetService" />
                            </intent-filter>
                        </service>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(expected).expectFixDiffs(
            """
            Show URL for AndroidManifest.xml line 11: https://developer.android.com/training/sharing/receive.html?source=studio#providing-direct-share-targets
            """
        )
    }
}
