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

class ProguardDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ProguardDetector()
    }

    fun testProguard() {
        lint().files(mProguard).run().expect(
            """
            proguard.cfg:21: Error: Obsolete ProGuard file; use -keepclasseswithmembers instead of -keepclasseswithmembernames [Proguard]
            -keepclasseswithmembernames class * {
            ^
            1 errors, 0 warnings
            """
        )
    }

    fun testProguardNewPath() {
        lint().files(mProguard2).run().expect(
            """
            proguard-project.txt:21: Error: Obsolete ProGuard file; use -keepclasseswithmembers instead of -keepclasseswithmembernames [Proguard]
            -keepclasseswithmembernames class * {
            ^
            1 errors, 0 warnings

            """
        )
    }

    fun testProguardRandomName() {
        lint().files(
            mProguard3,
            source(
                "project.properties",
                """
                        target=android-14
                        proguard.config=${"$"}{sdk.dir}/foo.cfg:${"$"}{user.home}/bar.pro;myfile.txt

                        """
            )
        ).run().expect(
            """
            myfile.txt:21: Error: Obsolete ProGuard file; use -keepclasseswithmembers instead of -keepclasseswithmembernames [Proguard]
            -keepclasseswithmembernames class * {
            ^
            myfile.txt:8: Warning: Local ProGuard configuration contains general Android configuration: Inherit these settings instead? Modify project.properties to define proguard.config=${"$"}{sdk.dir}/tools/proguard/proguard-android.txt:myfile.txt and then keep only project-specific configuration here [ProguardSplit]
            -keep public class * extends android.app.Activity
            ^
            1 errors, 1 warnings

            """
        )
    }

    fun testSilent() {
        lint().files(mProguard4, projectProperties().compileSdk(3)).run().expectClean()
    }

    fun testSilent2() {
        lint().files(mProguard4, projectProperties().compileSdk(3)).run().expectClean()
    }

    fun testSplit() {
        lint().files(
            mProguard4,
            projectProperties()
                .compileSdk(3)
                .property("proguard.config", "proguard.cfg")
        ).run().expect(
            """
            proguard.cfg:14: Warning: Local ProGuard configuration contains general Android configuration: Inherit these settings instead? Modify project.properties to define proguard.config=${"$"}{sdk.dir}/tools/proguard/proguard-android.txt:proguard.cfg and then keep only project-specific configuration here [ProguardSplit]
            -keep public class * extends android.app.Activity
            ^
            0 errors, 1 warnings
            """
        )
    }

    // Sample code
    private val mProguard = source(
        "proguard.cfg",
        """
        -optimizationpasses 5
        -dontusemixedcaseclassnames
        -dontskipnonpubliclibraryclasses
        -dontpreverify
        -verbose
        -optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

        -keep public class * extends android.app.Activity
        -keep public class * extends android.app.Application
        -keep public class * extends android.app.Service
        -keep public class * extends android.content.BroadcastReceiver
        -keep public class * extends android.content.ContentProvider
        -keep public class * extends android.app.backup.BackupAgentHelper
        -keep public class * extends android.preference.Preference
        -keep public class com.android.vending.licensing.ILicensingService

        -keepclasseswithmembernames class * {
            native <methods>;
        }

        -keepclasseswithmembernames class * {
            public <init>(android.content.Context, android.util.AttributeSet);
        }

        -keepclasseswithmembernames class * {
            public <init>(android.content.Context, android.util.AttributeSet, int);
        }

        -keepclassmembers enum * {
            public static **[] values();
            public static ** valueOf(java.lang.String);
        }

        -keep class * implements android.os.Parcelable {
          public static final android.os.Parcelable${"$"}Creator *;
        }
        """
    ).indented()

    // Sample code
    private val mProguard2 = source(
        "proguard-project.txt",
        """
        -optimizationpasses 5
        -dontusemixedcaseclassnames
        -dontskipnonpubliclibraryclasses
        -dontpreverify
        -verbose
        -optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

        -keep public class * extends android.app.Activity
        -keep public class * extends android.app.Application
        -keep public class * extends android.app.Service
        -keep public class * extends android.content.BroadcastReceiver
        -keep public class * extends android.content.ContentProvider
        -keep public class * extends android.app.backup.BackupAgentHelper
        -keep public class * extends android.preference.Preference
        -keep public class com.android.vending.licensing.ILicensingService

        -keepclasseswithmembernames class * {
            native <methods>;
        }

        -keepclasseswithmembernames class * {
            public <init>(android.content.Context, android.util.AttributeSet);
        }

        -keepclasseswithmembernames class * {
            public <init>(android.content.Context, android.util.AttributeSet, int);
        }

        -keepclassmembers enum * {
            public static **[] values();
            public static ** valueOf(java.lang.String);
        }

        -keep class * implements android.os.Parcelable {
          public static final android.os.Parcelable${"$"}Creator *;
        }
        """
    ).indented()

    // Sample code
    private val mProguard3 = source(
        "myfile.txt",
        """
        -optimizationpasses 5
        -dontusemixedcaseclassnames
        -dontskipnonpubliclibraryclasses
        -dontpreverify
        -verbose
        -optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

        -keep public class * extends android.app.Activity
        -keep public class * extends android.app.Application
        -keep public class * extends android.app.Service
        -keep public class * extends android.content.BroadcastReceiver
        -keep public class * extends android.content.ContentProvider
        -keep public class * extends android.app.backup.BackupAgentHelper
        -keep public class * extends android.preference.Preference
        -keep public class com.android.vending.licensing.ILicensingService

        -keepclasseswithmembernames class * {
            native <methods>;
        }

        -keepclasseswithmembernames class * {
            public <init>(android.content.Context, android.util.AttributeSet);
        }

        -keepclasseswithmembernames class * {
            public <init>(android.content.Context, android.util.AttributeSet, int);
        }

        -keepclassmembers enum * {
            public static **[] values();
            public static ** valueOf(java.lang.String);
        }

        -keep class * implements android.os.Parcelable {
          public static final android.os.Parcelable${"$"}Creator *;
        }
        """
    ).indented()

    // Sample code
    private val mProguard4 = source(
        "proguard.cfg",
        """
        -optimizationpasses 5
        -dontusemixedcaseclassnames
        -dontskipnonpubliclibraryclasses
        -verbose
        -optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
        -allowaccessmodification
        -keepattributes *Annotation*


        # dex does not like code run through proguard optimize and preverify steps.
        -dontoptimize
        -dontpreverify

        -keep public class * extends android.app.Activity
        -keep public class * extends android.app.Application
        -keep public class * extends android.app.Service
        -keep public class * extends android.content.BroadcastReceiver
        -keep public class * extends android.content.ContentProvider
        -keep public class * extends android.app.backup.BackupAgent
        -keep public class * extends android.preference.Preference
        -keep public class com.android.vending.licensing.ILicensingService

        # For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
        -keepclasseswithmembernames class * {
            native <methods>;
        }

        -keep public class * extends android.view.View {
            public <init>(android.content.Context);
            public <init>(android.content.Context, android.util.AttributeSet);
            public <init>(android.content.Context, android.util.AttributeSet, int);
            public void set*(...);
        }

        -keepclasseswithmembers class * {
            public <init>(android.content.Context, android.util.AttributeSet);
        }

        -keepclasseswithmembers class * {
            public <init>(android.content.Context, android.util.AttributeSet, int);
        }

        -keepclassmembers class * extends android.app.Activity {
           public void *(android.view.View);
        }

        # For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
        -keepclassmembers enum * {
            public static **[] values();
            public static ** valueOf(java.lang.String);
        }

        -keep class * implements android.os.Parcelable {
          public static final android.os.Parcelable${"$"}Creator *;
        }

        -keepclassmembers class **.R$* {
            public static <fields>;
        }

        # The support library contains references to newer platform versions.
        # Don't warn about those in case this app is linking against an older
        # platform version.  We know about them, and they are safe.
        -dontwarn android.support.**
        """
    ).indented()
}
