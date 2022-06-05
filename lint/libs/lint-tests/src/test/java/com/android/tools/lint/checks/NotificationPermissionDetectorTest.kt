/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.NotificationTrampolineDetectorTest.Companion.notificationStubs
import com.android.tools.lint.detector.api.Detector

class NotificationPermissionDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return NotificationPermissionDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
                    <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="33" />
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                </manifest>
                """
            ).indented(),
            javaNotificationUsage,
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTestAndroidx.java:21: Error: When targeting Android 13 or higher, posting a permission requires holding the POST_NOTIFICATIONS permission [NotificationPermission]
                    notificationManager.notify(id, notification);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testNoWarningWhenPermissionIsDeclared() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
                    <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="33" />
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
                </manifest>
                """
            ).indented(),
            javaNotificationUsage,
            *notificationStubs
        ).run().expectClean()
    }

    fun testNoWarningPreAndroid13() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
                    <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="32" />
                </manifest>
                """
            ).indented(),
            javaNotificationUsage,
            *notificationStubs
        ).run().expectClean()
    }

    fun testClassFileUsage() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
                    <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="33" />
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                </manifest>
                """
            ).indented(),
            bytecodeUsage,
            *notificationStubs
        ).run().expect(
            """
            libs/usage.jar: Error: When targeting Android 13 or higher, posting a permission requires holding the POST_NOTIFICATIONS permission [NotificationPermission]
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Data for libs/usage.jar line 0:   missing : android.permission.POST_NOTIFICATIONS
            """
        )
    }

    fun testClassAndSourceFileUsage() {
        // When we also have source file usages, only flag the source file usage, not the bytecode usage
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
                    <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="33" />
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                </manifest>
                """
            ).indented(),
            bytecodeUsage,
            javaNotificationUsage,
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTestAndroidx.java:21: Error: When targeting Android 13 or higher, posting a permission requires holding the POST_NOTIFICATIONS permission [NotificationPermission]
                    notificationManager.notify(id, notification);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Data for src/test/pkg/NotificationTestAndroidx.java line 21:   missing : android.permission.POST_NOTIFICATIONS
            """
        )
    }

    fun testClassFileUsageHasPermission() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
                    <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="33" />
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
                </manifest>
                """
            ).indented(),
            bytecodeUsage,
            *notificationStubs
        ).run().expectClean()
    }

    fun testClassFileUsageLowTargetSdkVersion() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
                    <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="28" />
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                </manifest>
                """
            ).indented(),
            bytecodeUsage,
            *notificationStubs
        ).run().expectClean()
    }

    private val javaNotificationUsage = java(
        """
        package test.pkg;

        import android.app.Notification;
        import android.app.NotificationManager;
        import android.content.Context;

        import androidx.core.app.NotificationCompat;

        public class NotificationTestAndroidx {
            public void testAndroidX(Context context, String channelId, int id, int requestCode, int flags) {
                NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(context, channelId)
                                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                                .setContentTitle("Notification Trampoline Test")
                                .setContentText("Tap this notification to launch a new receiver")
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setAutoCancel(true);
                Notification notification = builder.build();
                NotificationManager notificationManager =
                        context.getSystemService(NotificationManager.class);
                notificationManager.notify(id, notification);
            }
        }
        """
    ).indented()

    private val bytecodeUsage = bytecode(
        "libs/usage.jar",
        javaNotificationUsage,
        0xb08c0789,
"""
        test/pkg/NotificationTestAndroidx.class:
        H4sIAAAAAAAAAJVUW28SURD+DlCW0m2heEVLu61UoUXWaq0KrdpimpBgNUIa
        9e2wHOnWZZcsh14e/FG+0EQT47M/yjiH3ki1iewmM3POzuWb+QZ+/f72A8AT
        vIriCqY0pKIIwNAwF2QRbKpDOoJ5pe9GcE/pjIas0gtKLCqR03A/ijzMKGJ4
        oGFJw0OG8Krt2vI5QzCT3WYIlbyGYIhVbFdsdVt14dd43aEbXYqOXHcbvmc3
        3jMsZSr8+GBaniuFK82S0geyWNnle9x0uNs0q9K33WaxXC6r3NGq1/UtsWmr
        fKktT9qfbItL23Nr57kP8ipcx1VcY8id1DigIr4webttDoaVvFaby/RG13Ya
        wmfQziy97LrCLzm80xEdleyRhmWGxCnmd+mGz/ePO4ucmjoeY4VharCEUfN5
        q+05NA5DodSJhKcM+RpvG3LH7hjuoLP0DId3XWvH4IYr9g1fWMLeE76OZyoq
        /T/t6ChgRUcRqzrWQMTMnIK+6P+au7wpfA0vdLzEuo4NlBhmFVFm+3PTvGzC
        DPFzit7Ud4UlqaNhCFVsjp7NksbdEbLa4o5Tpli1HOVsZRjqirRxlKJ0XLhm
        S8XLRuYfhYdNOzGQlnphGKOLt77t+bY8ZBin03pXeiXuWsJRyD8OXWKkrkza
        m8xZ6F9B5HbjkvESG02a3mFHilZV+Hu2Rb3PD/beX2Nq/SJplDTcXz9qZDpT
        vrx4dhuzSNA/h3oCYOrHRfI6nVKkGemRhSOwr2QQUJLh/uUoyZtInrh+QYhu
        gLXvCHxYzB0hmBjpIZzQeogkRnuIBnvQQz2MF8LJcA8TBW0xEesh/hOxQiQZ
        SSXJcfK8RB5jJMfoHUccE4QoRqXihGgSc4Q2S3hzhHSZvqwSgABu9dHfxgzp
        BFkBip0mSCHq7g6SfwBQWBTlJQUAAA==
        """
    )
}
