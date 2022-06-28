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
import com.android.tools.lint.checks.infrastructure.TestFile
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
            manifestTarget33LocationPermission,
            bytecodeUsage,
            *notificationStubs
        ).run().expect(
            """
            libs/usage.jar: Error: When targeting Android 13 or higher, posting a permission requires holding the POST_NOTIFICATIONS permission (usage from test.pkg.NotificationTestAndroidx) [NotificationPermission]
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Data for libs/usage.jar line 0:   missing : android.permission.POST_NOTIFICATIONS
            """
        )
    }

    fun testClassFileUsageFromAndroidX() {
        // Just depending on AndroidX doesn't mean you're using notifications
        lint().files(
            manifestTarget33LocationPermission,
            bytecodeUsageInAndroidX,
            *notificationStubs
        ).run().expectClean()
    }

    fun testSuppressedViaRequiresPermission() {
        lint().files(
            manifestTarget33LocationPermission,
            notificationUsageWithRequiresPermissionAnnotation,
            SUPPORT_ANNOTATIONS_JAR
        ).skipTestModes(PLATFORM_ANNOTATIONS_TEST_MODE).run().expectClean()
    }

    fun testSuppressedViaRequiresPermissionInBytecode() {
        lint().files(
            manifestTarget33LocationPermission,
            bytecode(
                "libs/usage.jar",
                notificationUsageWithRequiresPermissionAnnotation,
                0x8e4261c0,
                """
                com/example/myapplication/TestNotification.class:
                H4sIAAAAAAAAAIVR22rbQBA949hW4jrNre4lISmhlDgXvI99cGkxxgaBK5lI
                5CUPZm1vwgZp5UhySD4rLy30oR/QjwqZdY1bSkMXdmb2zJmZnZmfD99/APiA
                7QqK2KxgCy8c1By8JJQ/aqPzT4Sl+uEZodhOxoqw1tNGedN4qNJQDiNGirnK
                csLnek+acZrosZCTifCSXF/okcx1YppPer5IIy9V2nRthZ3Tqcl1rFxzozPN
                uVvGJPmMlxEW6W+FXODiVF1PdaqyvkpjnWW2GKEko8i/ILyfRzQmC2+j7wfh
                wPNDt+u2W6HrewHh4B+8VrvdCYJB1/U6g57/i0qoBMk0Hamuto3XQm78z24a
                V/JGVlFC2cGrKl7jDeFolMRC3cp4EikR3/EAojlb/B1OWLcJRCTNpfCHV2rE
                Y337n9HxnoxF7wh7dffpFRyeYR9LvGR7CiD7S5YOv3ZZE+vS0TfQPRuEZZbl
                GbjCcgUVDrHUd/MUzsnu8VcUfpMtAahy0CqesbXBeJXv6jkKGZ5nWOPH+qz0
                xiOQVjv9dQIAAA==
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).skipTestModes(PLATFORM_ANNOTATIONS_TEST_MODE).run().expectClean()
    }

    fun testClassAndSourceFileUsage() {
        // When we also have source file usages, only flag the source file usage, not the bytecode usage
        lint().files(
            manifestTarget33LocationPermission,
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

    private val javaNotificationUsage: TestFile = java(
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

    private val manifestTarget33LocationPermission: TestFile = manifest(
"""
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg.permissiontest">
            <uses-sdk android:minSdkVersion="17" android:targetSdkVersion="33" />
            <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
        </manifest>
        """
    ).indented()

    private val notificationUsageWithRequiresPermissionAnnotation: TestFile = java(
        """
        package com.example.myapplication;

        import android.Manifest;
        import android.app.Notification;
        import android.app.NotificationManager;

        import androidx.annotation.RequiresPermission;

        public class TestNotification {
            @RequiresPermission(allOf = {"android.permission.POST_NOTIFICATIONS", Manifest.permission.ACCESS_FINE_LOCATION})
            public void test(Notification notification, NotificationManager manager, int id) {
                manager.notify(id, notification);
            }
        }
        """
    ).indented()

    private val bytecodeUsage: TestFile = bytecode(
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

    // Like bytecodeUsage, but with the package replaced with an androidx usage
    private val bytecodeUsageInAndroidX: TestFile = bytecode(
        "libs/usage.jar",
        java(javaNotificationUsage.contents.replace("package test.pkg;", "package androidx.core;")),
        0x54470941,
        """
            androidx/core/NotificationTestAndroidx.class:
            H4sIAAAAAAAAAJVUW28SQRT+BihL6bZQvKKlXSut0CJYrVWhVVtMExKsRkij
            vg0w0mmWXbIMvTz4o3yhiSbGZ3+U8Qy9YbWJ7CbnnJk953zfucDPX1+/A3iC
            V2FcwZSBRBg+WAZm/SyETX1IhjCn9XwI97ROGUhrvaDFohYZA/fDyCIXRgQP
            DCwZeMgQXJWOVM8Z/Kn0NkOg6DYEQ6QsHbHVbdWEV+U1m25MJTpq3Wl4rmy8
            Z1hKlfnxIVd3HSUclStqfaAK5V2+x3M2d5q5ivKk0yyUSiWdO1xxu15dbEqd
            L7HlKvlJ1rmSrlM9z32Q1eEmruIaQ+YE44BAPJHj7XZuMKzottpcJTe60m4I
            j8E4s8yS4wivaPNOR3R0skcGlhlip5zfJRse3z+uLHRqmniMFYapQQir6vFW
            27WpHZZmadIQnjJkq7xtqR3ZsZxBZ+VaNu869R2LW47YtzxRF3JPeCae6ajk
            /5RjIo8VEwWsmlgDDWbmlPRF/9fc4U3hGXhh4iXWTWygyDD/J8hlbWaIns/p
            TW1X1BWVNcxU9UhHzxpKPe8IVWlx2y5RrN6QUro8zPwKtHaUongMXJVKD2cj
            9Q/gYdNODKSlWhjG6OKtJ11PqkOGcTqtd5Vb5E5d2Jr5x6EhRmrapOVJnYX+
            FURuNy5pL02jSd077CjRqghvT9ap9rnB2vu7TKVfHBolDfZ3kAqZTpUuB09v
            4w5i9PehHx+Y/oWRvE6nBGlGemThCOwLGUSUZLB/OUryJuInrp8RoBtg7Rt8
            HxYzR/DHRnoIxoweQrHRHsL+HsxAD+P5YDzYw0TeWIxFeoj+QCQfiocScXKc
            PIfIYozkGL3jiGKCGEUIKkqMJjFLbNPEN0NMl+nLKhHw4Vaf/W3MkI6R5aPY
            aaIUoOruIv4bB1IRVioFAAA=
            """
    )
}
