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

class PrivateApiDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector {
        return PrivateApiDetector()
    }

    fun testFields() {
        lint().files(
            manifest().minSdk(20).targetSdk(28),
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.telephony.TelephonyManager;

                import java.lang.reflect.Field;

                public class TestReflection {
                    public void test(Context context, int subId) {
                        try {
                            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                            Field deniedField = TelephonyManager.class.getDeclaredField("NETWORK_SELECTION_MODE_MANUAL"); // ERROR 1
                            Object o1 = deniedField.get(tm);
                            Field allowedField = TelephonyManager.class.getDeclaredField("PHONE_TYPE_CDMA"); // OK
                            Object o2 = allowedField.get(tm);
                            Field maybeField = TelephonyManager.class.getDeclaredField("OTASP_NEEDED"); // ERROR 2
                            Object o3 = maybeField.get(tm);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
               """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/TestReflection.java:12: Error: Reflective access to NETWORK_SELECTION_MODE_MANUAL is forbidden when targeting API 28 and above [BlockedPrivateApi]
                        Field deniedField = TelephonyManager.class.getDeclaredField("NETWORK_SELECTION_MODE_MANUAL"); // ERROR 1
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestReflection.java:16: Error: Reflective access to OTASP_NEEDED will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        Field maybeField = TelephonyManager.class.getDeclaredField("OTASP_NEEDED"); // ERROR 2
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testForNameOnInternalClass() {
        val expected =
            """
            src/test/pkg/myapplication/ReflectionTest1.java:8: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Class<?> c = Class.forName("com.android.internal.widget.LockPatternUtils"); // ERROR
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/myapplication/ReflectionTest1.java:9: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    int titleContainerId = (Integer) Class.forName("com.android.internal.R{$}id").getField("title_container").get(null);
                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/myapplication/ReflectionTest1.java:11: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Class SystemProperties = cl.loadClass("android.os.SystemProperties");
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """

        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import android.app.Activity;

                public class ReflectionTest1 extends Activity {
                    public void testForName(ClassLoader cl) throws Exception {
                        Class.forName("java.lang.String"); // OK
                        Class<?> c = Class.forName("com.android.internal.widget.LockPatternUtils"); // ERROR
                        int titleContainerId = (Integer) Class.forName("com.android.internal.R{$}id").getField("title_container").get(null);
                        @SuppressWarnings("rawtypes")
                        Class SystemProperties = cl.loadClass("android.os.SystemProperties");
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testForNameOnSdkClass() {
        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import android.app.Activity;

                public class ReflectionTest1 extends Activity {
                    public void testForName() throws ClassNotFoundException {
                        Class.forName("android.view.View"); // OK
                    }
                }
            """
            ).indented()
        ).run().expectClean()
    }

    fun testLoadClass() {
        val expected =
            """
                src/test/pkg/myapplication/ReflectionTest2.java:9: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                        classLoader.loadClass("com.android.internal.widget.LockPatternUtils"); // ERROR
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """

        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import android.app.Activity;

                public class ReflectionTest2 extends Activity {
                    public void testLoadClass() throws ClassNotFoundException {
                        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
                        classLoader.loadClass("java.lang.String"); // OK
                        classLoader.loadClass("com.android.internal.widget.LockPatternUtils"); // ERROR
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testGetDeclaredMethod1() {
        val expected =
            """
            src/test/pkg/myapplication/ReflectionTest3.java:7: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Class<?> c = Class.forName("com.android.internal.widget.LockPatternUtils"); // ERROR
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/myapplication/ReflectionTest3.java:8: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    c.getDeclaredMethod("getKeyguardStoredPasswordQuality");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """

        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import android.app.Activity;

                public class ReflectionTest3 extends Activity {
                    public void testGetDeclaredMethod1() throws ClassNotFoundException, NoSuchMethodException {
                        Class<?> c = Class.forName("com.android.internal.widget.LockPatternUtils"); // ERROR
                        c.getDeclaredMethod("getKeyguardStoredPasswordQuality");
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testReflectionWithoutClassLoad() {
        val expected =
            """
            src/test/pkg/myapplication/ReflectionTest4.java:12: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Method m1 = tm.getClass().getDeclaredMethod("getITelephony"); // ERROR
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/myapplication/ReflectionTest4.java:13: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Method m2 = TelephonyManager.class.getDeclaredMethod("getITelephony"); // ERROR
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """

        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import android.app.Activity;
                import android.telephony.TelephonyManager;

                import java.lang.reflect.Method;

                public class ReflectionTest4 extends Activity {
                    public void testReflectionWithoutClassLoad() throws NoSuchMethodException {
                        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        // Reflection of unsupported method:
                        Method m1 = tm.getClass().getDeclaredMethod("getITelephony"); // ERROR
                        Method m2 = TelephonyManager.class.getDeclaredMethod("getITelephony"); // ERROR
                        // Reflection of supported method: OK (probably conditional for version checks
                        // compiling with old SDK; this one requires API 23)
                        Method m3 = tm.getClass().getDeclaredMethod("canChangeDtmfToneLength"); // OK
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testLoadingClassesViaDexFile() {
        val expected =
            """
            src/test/pkg/myapplication/ReflectionTest.java:15: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Class LocalePicker = df.loadClass(name, cl);
                                         ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/myapplication/ReflectionTest.java:16: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Class ActivityManagerNative = Class.forName("android.app.ActivityManagerNative");
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/myapplication/ReflectionTest.java:17: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Class IActivityManager = Class.forName("android.app.IActivityManager");
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """

        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import android.app.Activity;
                import android.content.res.Configuration;

                import java.io.File;
                import java.lang.reflect.Method;

                import dalvik.system.DexFile;

                public class ReflectionTest extends Activity {
                    public void testReflection(DexFile df) throws Exception {
                        String name = "com.android.settings.LocalePicker";
                        ClassLoader cl = getClassLoader();
                        Class LocalePicker = df.loadClass(name, cl);
                        Class ActivityManagerNative = Class.forName("android.app.ActivityManagerNative");
                        Class IActivityManager = Class.forName("android.app.IActivityManager");
                        Method getDefault = ActivityManagerNative.getMethod("getDefault", null);
                        Object am = IActivityManager.cast(getDefault.invoke(ActivityManagerNative, null));
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testCaseFromIssue78420() {
        // Testcase from https://code.google.com/p/android/issues/detail?id=78420
        val expected =
            """
            src/test/pkg/myapplication/ReflectionTest.java:9: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                    Class<?> loadedStringsClass = Class.forName("com.android.internal.R{$}styleable");
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """

        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import android.app.Activity;
                import android.util.Log;

                public class ReflectionTest extends Activity {
                    public void test(String TAG) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
                        Log.e (TAG, "TestClass 1, String load start");
                        Class<?> loadedStringsClass = Class.forName("com.android.internal.R{$}styleable");
                        Log.e (TAG, "    TestClass 1-1, Class.forName end, com.android.internal.R{$}styleable end, " + loadedStringsClass);
                        java.lang.reflect.Field color_field1 = loadedStringsClass.getField("TextAppearance_textColorHint");
                        Log.e (TAG, "    TestClass 1-2, getField end, TextAppearance_textColorHint = " + color_field1.getInt(null));
                        Log.e (TAG, "TestClass 1, String load end");
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testJavaReflection() {
        val expected =
            """
            src/test/pkg/application/ReflectionTestJava.java:20: Error: Reflective access to dispatchActivityPostCreated is forbidden when targeting API 28 and above [BlockedPrivateApi]
                                    Method m7 = activityClass.getDeclaredMethod("dispatchActivityPostCreated", bundleClass);
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestKotlin.kt:15: Error: Reflective access to dispatchActivityPostCreated is forbidden when targeting API 28 and above [BlockedPrivateApi]
                    val m5 = activityClass.getDeclaredMethod("dispatchActivityPostCreated", bundleClass)
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestJava.java:11: Warning: Reflective access to addAssetPath, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                                    Method m1 = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestJava.java:12: Warning: Reflective access to addAssetPath, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                                    Method m2 = assetManager.getClass().getDeclaredMethod("addAssetPath", String.class);
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestJava.java:13: Warning: Reflective access to addAssetPath, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                                    Method m3 = AssetManager.class.getDeclaredMethod("addAssetPath", path.getClass());
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestKotlin.kt:8: Warning: Reflective access to addAssetPath, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                    val m1 = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestKotlin.kt:11: Warning: Reflective access to addAssetPath, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                    val m4 = AssetManager::class.java.getDeclaredMethod("addAssetPath", path.javaClass)
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestJava.java:14: Error: Reflective access to invalidateCachesLocked will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                                    Method m4 = AssetManager.class.getDeclaredMethod("invalidateCachesLocked", int.class);
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestJava.java:15: Error: Reflective access to invalidateCachesLocked will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                                    Method m5 = AssetManager.class.getDeclaredMethod("invalidateCachesLocked", Integer.TYPE);
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTestKotlin.kt:9: Error: Reflective access to invalidateCachesLocked will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                    val m2 = assetManager.javaClass.getDeclaredMethod("invalidateCachesLocked", Int::class.javaPrimitiveType)
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            5 errors, 5 warnings
            """

        lint().files(
            manifest().targetSdk(28),
            java(
                """
                package test.pkg.application;

                import android.content.res.AssetManager;

                import java.lang.reflect.Method;

                public class ReflectionTestJava {
                    private static void addAssetPath(AssetManager assetManager) throws Exception {
                        String path = "foo/bar";
                        Method m1 = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                        Method m2 = assetManager.getClass().getDeclaredMethod("addAssetPath", String.class);
                        Method m3 = AssetManager.class.getDeclaredMethod("addAssetPath", path.getClass());
                        Method m4 = AssetManager.class.getDeclaredMethod("invalidateCachesLocked", int.class);
                        Method m5 = AssetManager.class.getDeclaredMethod("invalidateCachesLocked", Integer.TYPE);
                        Method m6 = AssetManager.class.getDeclaredMethod("invalidateCachesLocked", Integer.class); // OK, doesn't exist

                        Class<?> activityClass = Class.forName("android.app.Activity");
                        Class<?> bundleClass = Class.forName("android.os.Bundle");
                        Method m7 = activityClass.getDeclaredMethod("dispatchActivityPostCreated", bundleClass);
                    }
                }
            """
            ),
            kotlin(
                """
                package test.pkg.application;

                import android.content.res.AssetManager

                class ReflectionTestKotlin {
                    private fun addAssetPath(assetManager: AssetManager) {
                        val path = "foo/bar"
                        val m1 = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                        val m2 = assetManager.javaClass.getDeclaredMethod("invalidateCachesLocked", Int::class.javaPrimitiveType)
                        val m3 = assetManager.javaClass.getDeclaredMethod("invalidateCachesLocked", Int::class.java) // OK, doesn't exist
                        val m4 = AssetManager::class.java.getDeclaredMethod("addAssetPath", path.javaClass)

                        val activityClass = Class.forName("android.app.Activity")
                        val bundleClass = Class.forName("android.os.Bundle")
                        val m5 = activityClass.getDeclaredMethod("dispatchActivityPostCreated", bundleClass)

                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testMaybeListJavaCall() {
        val expected =
            """
            src/test/pkg/application/ReflectionTest.java:11: Error: Reflective access to toggleFreeformWindowingMode is forbidden when targeting API 28 and above [BlockedPrivateApi]
                        Method m2 = Activity.class.getDeclaredMethod("toggleFreeformWindowingMode");
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:10: Warning: Reflective access to setParent, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        Method m1 = Activity.class.getDeclaredMethod("setParent", Activity.class);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:12: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        Method m3 = Activity.class.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle.class); // MAYBE_MAX_O
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:14: Warning: Reflective access to setParent, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                    Method m4 = Activity.class.getDeclaredMethod("setParent", Activity.class); // MAYBE_MAX_P
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:15: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                    Method m5 = Activity.class.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle.class); // MAYBE_MAX_O
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 3 warnings
            """.trimIndent()
        lint().files(
            manifest().targetSdk(28),
            java(
                """
                package test.pkg.application;

                import android.app.Activity;

                import java.lang.reflect.Method;

                public class ReflectionTest {
                    private static void test() throws Exception {
                        if (android.os.Build.VERSION.SDK_INT <= 26) {
                            Method m1 = Activity.class.getDeclaredMethod("setParent", Activity.class);
                            Method m2 = Activity.class.getDeclaredMethod("toggleFreeformWindowingMode");
                            Method m3 = Activity.class.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle.class); // MAYBE_MAX_O
                        }
                        Method m4 = Activity.class.getDeclaredMethod("setParent", Activity.class); // MAYBE_MAX_P
                        Method m5 = Activity.class.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle.class); // MAYBE_MAX_O
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun testCornerCaseHandling() {
        lint().files(
            manifest().targetSdk(28),
            java(
                """
                package test.pkg.application;
                import android.app.Activity;
                import java.lang.reflect.Method;
                import android.os.Bundle;

                // Test to make sure we get the corner cases exactly right around API
                // levels. Here, the severity of the incident should be warning because
                // it's always run in a context where we know the code will only run on <= 28
                public class ReflectionTest {
                    private static void test() throws Exception {
                        Class<Activity> clz = Activity.class;
                        String name = "restoreManagedDialogs";
                        // Less than or equal
                        if (android.os.Build.VERSION.SDK_INT <= 26) {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 1
                        }
                        if (android.os.Build.VERSION.SDK_INT <= 27) {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 2
                        }
                        if (android.os.Build.VERSION.SDK_INT <= 28) {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 3
                        }
                        if (android.os.Build.VERSION.SDK_INT <= 29) {
                            clz.getDeclaredMethod(name, Bundle.class); // error 1
                        }

                        // Less than
                        if (android.os.Build.VERSION.SDK_INT < 26) {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 4
                        }
                        if (android.os.Build.VERSION.SDK_INT < 27) {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 5
                        }
                        if (android.os.Build.VERSION.SDK_INT < 28) {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 6
                        }
                        if (android.os.Build.VERSION.SDK_INT < 29) {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 7
                        }
                        if (android.os.Build.VERSION.SDK_INT < 30) {
                            clz.getDeclaredMethod(name, Bundle.class); // error 2
                        }

                        // Greater than or equals
                        if (android.os.Build.VERSION.SDK_INT >= 26) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 8
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 27) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 9
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 28) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 10
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 29) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // error 3
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 30) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // error 3b
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 31) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // error 3c
                        }

                        // Greater than
                        if (android.os.Build.VERSION.SDK_INT > 26) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 11
                        }
                        if (android.os.Build.VERSION.SDK_INT > 27) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // warn 12
                        }
                        if (android.os.Build.VERSION.SDK_INT > 28) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // error 4
                        }
                        if (android.os.Build.VERSION.SDK_INT > 29) { } else {
                            clz.getDeclaredMethod(name, Bundle.class); // error 5
                        }
                    }
                }
            """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/application/ReflectionTest.java:15: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 1
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:18: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 2
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:21: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 3
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:29: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 4
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:32: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 5
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:35: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 6
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:38: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 7
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:46: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 8
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:49: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 9
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:52: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 10
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:66: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 11
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:69: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // warn 12
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:24: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // error 1
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:41: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // error 2
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:55: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // error 3
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:58: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // error 3b
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:61: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // error 3c
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:72: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // error 4
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.java:75: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        clz.getDeclaredMethod(name, Bundle.class); // error 5
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            7 errors, 12 warnings
            """
        )
    }

    fun testMaybeListKotlinCall() {
        val expected =
            """
            src/test/pkg/application/ReflectionTest.kt:11: Error: Reflective access to toggleFreeformWindowingMode is forbidden when targeting API 28 and above [BlockedPrivateApi]
                        25 -> clazz.getDeclaredMethod("toggleFreeformWindowingMode") // DENY
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.kt:13: Warning: Reflective access to setParent, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                        27 -> clazz.getDeclaredMethod("setParent", Activity::class.java) // MAYBE_MAX_P
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.kt:18: Warning: Reflective access to restoreManagedDialogs, which is not part of the public SDK and therefore likely to change in future Android releases [DiscouragedPrivateApi]
                            clazz.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle::class.java)
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.kt:12: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        26 -> clazz.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle::class.java)
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/application/ReflectionTest.kt:14: Error: Reflective access to restoreManagedDialogs will throw an exception when targeting API 28 and above [SoonBlockedPrivateApi]
                        else -> clazz.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle::class.java) // MAYBE_MAX_O
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 2 warnings
            """.trimIndent()
        lint().files(
            manifest().targetSdk(28),
            kotlin(
                """
                package test.pkg.application;

                import android.app.Activity

                class ReflectionTest {
                    private fun test() {
                        val clazz = Activity::class.java
                        clazz.getDeclaredMethod("dismissDialog", Int::class.javaPrimitiveType) // OK
                        // TODO: Fix VersionChecks to recognize this
                        when (android.os.Build.VERSION.SDK_INT) {
                            25 -> clazz.getDeclaredMethod("toggleFreeformWindowingMode") // DENY
                            26 -> clazz.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle::class.java)
                            27 -> clazz.getDeclaredMethod("setParent", Activity::class.java) // MAYBE_MAX_P
                            else -> clazz.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle::class.java) // MAYBE_MAX_O
                        }
                        when {
                            android.os.Build.VERSION.SDK_INT == 26 ->
                                clazz.getDeclaredMethod("restoreManagedDialogs", android.os.Bundle::class.java)
                        }
                    }
                }
            """
            ).indented()
        ).run().expect(expected)
    }

    fun test140895401() {
        lint().files(
            manifest().minSdk(20).targetSdk(28),
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.telephony.TelephonyManager;

                import java.lang.reflect.Method;

                public class TestReflection {
                    public void test(Context context, int subId) {
                        try {
                            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                            Method setNetworkSelectionModeAutomatic =
                                    TelephonyManager.class.getDeclaredMethod("setNetworkSelectionModeAutomatic", int.class); // Error 1
                            setNetworkSelectionModeAutomatic.invoke(tm, subId);

                            Method getDataEnabled = TelephonyManager.class.getDeclaredMethod("getDataEnabled"); // OK: it's allow-listed
                            getDataEnabled.invoke(tm);
                            Method getNetworkSelectionMode = TelephonyManager.class.getDeclaredMethod("getNetworkSelectionMode"); // Error 2
                            getNetworkSelectionMode.invoke(tm);

                            Class<?> systemProperties = Class.forName("android.os.SystemProperties"); // Error 3
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
               """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/TestReflection.java:18: Error: Reflective access to getNetworkSelectionMode is forbidden when targeting API 28 and above [BlockedPrivateApi]
                        Method getNetworkSelectionMode = TelephonyManager.class.getDeclaredMethod("getNetworkSelectionMode"); // Error 2
                                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestReflection.java:21: Warning: Accessing internal APIs via reflection is not supported and may not work on all devices or in the future [PrivateApi]
                        Class<?> systemProperties = Class.forName("android.os.SystemProperties"); // Error 3
                                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 1 warnings
            """
        )
    }
}
