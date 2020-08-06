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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class LeakDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return LeakDetector()
    }

    fun testStaticFields() {
        val expected =
            """
            src/test/pkg/LeakTest.java:18: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                private static Activity sField7; // LEAK!
                        ~~~~~~
            src/test/pkg/LeakTest.java:19: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                private static Fragment sField8; // LEAK!
                        ~~~~~~
            src/test/pkg/LeakTest.java:20: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                private static Button sField9; // LEAK!
                        ~~~~~~
            src/test/pkg/LeakTest.java:21: Warning: Do not place Android context classes in static fields (static reference to MyObject which has field mActivity pointing to Activity); this is a memory leak [StaticFieldLeak]
                private static MyObject sField10;
                        ~~~~~~
            src/test/pkg/LeakTest.java:30: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                private static Activity sAppContext1; // LEAK
                        ~~~~~~
            0 errors, 5 warnings
            """

        lint().files(
            java(
                "src/test/pkg/LeakTest.java",
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.app.Activity;
                import android.content.Context;
                import android.app.Fragment;
                import android.widget.Button;
                import java.util.List;

                @SuppressWarnings("unused")
                public class LeakTest {
                    private static int sField1;
                    private static Object sField2;
                    private static String sField3;
                    private static List sField4;
                    private int mField5;
                    private Activity mField6;
                    private static Activity sField7; // LEAK!
                    private static Fragment sField8; // LEAK!
                    private static Button sField9; // LEAK!
                    private static MyObject sField10;
                    private MyObject mField11;
                    @SuppressLint("StaticFieldLeak")
                    private static Activity sField12;

                    private static class MyObject {
                        private int mKey;
                        private Activity mActivity;
                    }
                    private static Activity sAppContext1; // LEAK
                    private static Context sAppContext2; // Probably app context leak
                    private static Context applicationCtx; // Probably app context leak
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testLoader() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.content.Loader;

                public class LoaderTest {
                    public static class MyLoader1 extends Loader { // OK
                        public MyLoader1(Context context) { super(context); }
                    }

                    public class MyLoader2 extends Loader { // Leak
                        public MyLoader2(Context context) { super(context); }
                    }

                    public static class MyLoader3 extends Loader {
                        private Activity activity; // Leak
                        public MyLoader3(Context context) { super(context); }
                    }

                    public Loader createLoader(Context context) {
                        return new Loader(context) { // Leak
                        };
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/LoaderTest.java:12: Warning: This Loader class should be static or leaks might occur (test.pkg.LoaderTest.MyLoader2) [StaticFieldLeak]
                public class MyLoader2 extends Loader { // Leak
                             ~~~~~~~~~
            src/test/pkg/LoaderTest.java:17: Warning: This field leaks a context object [StaticFieldLeak]
                    private Activity activity; // Leak
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LoaderTest.java:22: Warning: This Loader class should be static or leaks might occur (anonymous android.content.Loader) [StaticFieldLeak]
                    return new Loader(context) { // Leak
                           ^
            0 errors, 3 warnings
            """
        )
    }

    fun testSupportLoader() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.support.v4.content.Loader;

                public class SupportLoaderTest {
                    public static class MyLoader1 extends Loader { // OK
                        public MyLoader1(Context context) { super(context); }
                    }

                    public class MyLoader2 extends Loader { // Leak
                        public MyLoader2(Context context) { super(context); }
                    }

                    public static class MyLoader3 extends Loader {
                        private Activity activity; // Leak
                        public MyLoader3(Context context) { super(context); }
                    }

                    public Loader createLoader(Context context) {
                        return new Loader(context) { // Leak
                        };
                    }
                }
                """
            ).indented(),
            // Stub since support library isn't in SDK
            java(
                """
                package android.support.v4.content;
                public class Loader {
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/SupportLoaderTest.java:12: Warning: This Loader class should be static or leaks might occur (test.pkg.SupportLoaderTest.MyLoader2) [StaticFieldLeak]
                public class MyLoader2 extends Loader { // Leak
                             ~~~~~~~~~
            src/test/pkg/SupportLoaderTest.java:17: Warning: This field leaks a context object [StaticFieldLeak]
                    private Activity activity; // Leak
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SupportLoaderTest.java:22: Warning: This Loader class should be static or leaks might occur (anonymous android.support.v4.content.Loader) [StaticFieldLeak]
                    return new Loader(context) { // Leak
                           ^
            0 errors, 3 warnings
            """
        )
    }

    fun testTopLevelLoader() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.content.Loader;

                public abstract class SupportLoaderTest extends Loader {
                    private Activity activity; // Leak
                    public SupportLoaderTest(Context context) { super(context); }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/SupportLoaderTest.java:8: Warning: This field leaks a context object [StaticFieldLeak]
                private Activity activity; // Leak
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testAsyncTask() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.os.AsyncTask;

                public class AsyncTaskTest {
                    public static class MyAsyncTask1 extends AsyncTask { // OK
                        @Override protected Object doInBackground(Object[] objects) { return null; }
                    }

                    public class MyAsyncTask2 extends AsyncTask { // Leak
                        @Override protected Object doInBackground(Object[] objects) { return null; }
                    }

                    public AsyncTask createTask() {
                        return new AsyncTask() { // Leak
                            @Override protected Object doInBackground(Object[] objects) { return null; }
                            android.view.View view; // Leak
                        };
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AsyncTaskTest.java:10: Warning: This AsyncTask class should be static or leaks might occur (test.pkg.AsyncTaskTest.MyAsyncTask2) [StaticFieldLeak]
                public class MyAsyncTask2 extends AsyncTask { // Leak
                             ~~~~~~~~~~~~
            src/test/pkg/AsyncTaskTest.java:15: Warning: This AsyncTask class should be static or leaks might occur (anonymous android.os.AsyncTask) [StaticFieldLeak]
                    return new AsyncTask() { // Leak
                           ^
            src/test/pkg/AsyncTaskTest.java:17: Warning: This field leaks a context object [StaticFieldLeak]
                        android.view.View view; // Leak
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        )
    }

    fun testAssignAppContext() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;

                public class StaticFieldTest {
                    public static Context context;

                    public StaticFieldTest(Context c) {
                        context = c.getApplicationContext();
                    }

                    public StaticFieldTest() {
                        context = null;
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testNoAssignAppContext() {
        // Regression test for 62318813; prior to this fix this code would trigger
        // an NPE in lint
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;

                public class StaticFieldTest {
                    public static Context context;

                    public StaticFieldTest(Context c) {
                    }

                    public StaticFieldTest() {
                        context = null;
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/StaticFieldTest.java:6: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                public static Context context;
                       ~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testLifeCycle() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.arch.lifecycle.ViewModel;
                import android.content.Context;
                import android.view.View;
                import android.widget.LinearLayout;

                public class MyModel extends ViewModel {
                    private String myString; // OK
                    private LinearLayout myLayout; // ERROR
                    private InnerClass2 myObject; // ERROR
                    private Context myContext; // ERROR

                    public static class InnerClass1 {
                        public View view; // OK
                    }

                    public static class InnerClass2 {
                        public View view; // OK
                    }
                }
                """
            ).indented(),
            java(
                """
                package android.arch.lifecycle;
                public class ViewModel { }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/MyModel.java:10: Warning: This field leaks a context object [StaticFieldLeak]
                private LinearLayout myLayout; // ERROR
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyModel.java:12: Warning: This field leaks a context object [StaticFieldLeak]
                private Context myContext; // ERROR
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testApplicationOk() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Application;
                import android.content.Context;

                public class ApplicationTest {
                    public static Application application;
                    public static MyApp app;

                    public static class MyApp {
                        private Application application;
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testClassesInStaticMethods() {
        // Regression test for https://issuetracker.google.com/70496601
        lint().files(
            java(
                """
                package test.pkg;

                import android.os.AsyncTask;

                class C {
                    public static void f(String param) {
                        class CustomInternalTask extends AsyncTask<Void, Void, Void> {
                            @Override
                            protected Void doInBackground(Void... params) {
                                return null;
                            }
                        }
                        new CustomInternalTask().execute();

                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... params) {
                                return null;
                            }
                        }.execute();
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testKotlinPropertySuppress() {
        // Regression test for https://issuetracker.google.com/112191486

        lint().files(
            kotlin(
                """
                package test.pkg

                import android.annotation.SuppressLint
                import android.content.Context

                @Suppress("PropertyName")
                val Test
                    get() = _globalContext
                @SuppressLint("StaticFieldLeak")
                @Suppress("ObjectPropertyName")
                lateinit var _globalContext: Context
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAppContextReference() {
        // Regression test for 119440194
        lint().files(
            java(
                "src/test/pkg/LeakTest.java",
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.app.Activity;
                import android.content.Context;
                import android.app.Fragment;
                import android.widget.Button;
                import java.util.List;

                @SuppressWarnings("unused")
                public class LeakTest {
                    private static MyObject sField10;

                    private static class MyObject {
                        private int mKey;
                        private Context applicationCtx;
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAppContextInitialization() {
        // Regression test for 70510835
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;

                public class LeakTest {
                    private static StaticFieldLeak sInstance;
                    public static synchronized StaticFieldLeak getInstance(final Context context) {
                        if (sInstance == null) {
                            sInstance = new StaticFieldLeak(context.getApplicationContext());
                        }
                        return sInstance;
                    }

                    private static class StaticFieldLeak {
                        private final Context mContext;

                        private StaticFieldLeak(Context context) {
                            mContext = context.getApplicationContext();
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.annotation.SuppressLint
                import android.app.Activity
                import android.content.Context
                import android.app.Fragment
                import android.widget.Button

                class LeakTest {
                    private val mField5: Int = 0
                    private val mField6: Activity? = null
                    private val mField11: MyObject? = null

                    private class MyObject {
                        private val mKey: Int = 0
                        private val mActivity: Activity? = null
                    }

                    companion object {
                        private val sField1: Int = 0
                        private val sField2: Any? = null
                        private val sField3: String? = null
                        private val sField4: List<*>? = null
                        private val sField7: Activity? = null // LEAK!
                        private val sField8: Fragment? = null // LEAK!
                        private val sField9: Button? = null // LEAK!
                        private val sField10: MyObject? = null
                        @SuppressLint("StaticFieldLeak")
                        private val sField12: Activity? = null
                        private val sAppContext1: Activity? = null // LEAK
                        private val sAppContext2: Context? = null // Probably app context leak
                        private val applicationCtx: Context? = null // Probably app context leak
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/LeakTest.kt:24: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                    private val sField7: Activity? = null // LEAK!
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LeakTest.kt:25: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                    private val sField8: Fragment? = null // LEAK!
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LeakTest.kt:26: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                    private val sField9: Button? = null // LEAK!
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LeakTest.kt:27: Warning: Do not place Android context classes in static fields (static reference to MyObject which has field mActivity pointing to Activity); this is a memory leak [StaticFieldLeak]
                    private val sField10: MyObject? = null
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LeakTest.kt:30: Warning: Do not place Android context classes in static fields; this is a memory leak [StaticFieldLeak]
                    private val sAppContext1: Activity? = null // LEAK
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 5 warnings
            """
        )
    }
}
