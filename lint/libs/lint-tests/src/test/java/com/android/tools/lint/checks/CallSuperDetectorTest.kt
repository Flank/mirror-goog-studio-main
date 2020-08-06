/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.lint.checks.AnnotationDetectorTest.Companion.SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP
import com.android.tools.lint.detector.api.Detector

class CallSuperDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return CallSuperDetector()
    }

    fun testCallSuper() {
        val expected =
            """
            src/test/pkg/CallSuperTest.java:11: Error: Overriding method should call super.test1 [MissingSuperCall]
                    protected void test1() { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:14: Error: Overriding method should call super.test2 [MissingSuperCall]
                    protected void test2() { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:17: Error: Overriding method should call super.test3 [MissingSuperCall]
                    protected void test3() { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:20: Error: Overriding method should call super.test4 [MissingSuperCall]
                    protected void test4(int arg) { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:26: Error: Overriding method should call super.test5 [MissingSuperCall]
                    protected void test5(int arg1, boolean arg2, Map<List<String>,?> arg3,  // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:30: Error: Overriding method should call super.test5 [MissingSuperCall]
                    protected void test5() { // ERROR
                                   ~~~~~
            6 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.annotation.CallSuper;

                import java.util.List;
                import java.util.Map;

                @SuppressWarnings("UnusedDeclaration")
                public class CallSuperTest {
                    private static class Child extends Parent {
                        protected void test1() { // ERROR
                        }

                        protected void test2() { // ERROR
                        }

                        protected void test3() { // ERROR
                        }

                        protected void test4(int arg) { // ERROR
                        }

                        protected void test4(String arg) { // OK
                        }

                        protected void test5(int arg1, boolean arg2, Map<List<String>,?> arg3,  // ERROR
                                             int[][] arg4, int... arg5) {
                        }

                        protected void test5() { // ERROR
                            super.test6(); // (wrong super)
                        }

                        protected void test6() { // OK
                            int x = 5;
                            super.test6();
                            System.out.println(x);
                        }
                    }

                    private static class Parent extends ParentParent {
                        @CallSuper
                        protected void test1() {
                        }

                        protected void test3() {
                            super.test3();
                        }

                        @CallSuper
                        protected void test4(int arg) {
                        }

                        protected void test4(String arg) {
                        }

                        @CallSuper
                        protected void test5() {
                        }

                        @CallSuper
                        protected void test5(int arg1, boolean arg2, Map<List<String>,?> arg3,
                                             int[][] arg4, int... arg5) {
                        }
                    }

                    private static class ParentParent extends ParentParentParent {
                        @CallSuper
                        protected void test2() {
                        }

                        @CallSuper
                        protected void test3() {
                        }

                        @CallSuper
                        protected void test6() {
                        }

                        @CallSuper
                        protected void test7() {
                        }
                    }

                    private static class ParentParentParent {
                    }
                }
                """
            ).indented(),
            java(
                """
                package android.support.annotation;

                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;

                import static java.lang.annotation.ElementType.CONSTRUCTOR;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.RetentionPolicy.CLASS;

                @Retention(CLASS)
                @Target({METHOD,CONSTRUCTOR})
                public @interface CallSuper {
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testForeignSuperAnnotations() {
        val expected =
            """
            src/test/pkg/OverrideTest.java:9: Error: Overriding method should call super.test [MissingSuperCall]
                    protected void test() { // ERROR
                                   ~~~~
            src/test/pkg/OverrideTest.java:21: Error: Overriding method should call super.test [MissingSuperCall]
                    protected void test() { // ERROR
                                   ~~~~
            2 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import javax.annotation.OverridingMethodsMustInvokeSuper;
                import edu.umd.cs.findbugs.annotations.OverrideMustInvoke;

                @SuppressWarnings("UnusedDeclaration")
                public class OverrideTest {
                    private static class Child1 extends Parent1 {
                        protected void test() { // ERROR
                        }

                    }

                    private static class Parent1 {
                        @OverrideMustInvoke
                        protected void test() {
                        }
                    }

                    private static class Child2 extends Parent2 {
                        protected void test() { // ERROR
                        }

                    }

                    private static class Parent2 {
                        @OverridingMethodsMustInvokeSuper
                        protected void test() {
                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package edu.umd.cs.findbugs.annotations;

                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;

                import static java.lang.annotation.ElementType.CONSTRUCTOR;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.RetentionPolicy.CLASS;

                @Retention(CLASS)
                @Target({METHOD,CONSTRUCTOR})
                public @interface OverrideMustInvoke {
                }

                """
            ).indented(),
            java(
                """
                package javax.annotation;

                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;

                import static java.lang.annotation.ElementType.CONSTRUCTOR;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.RetentionPolicy.CLASS;

                @Retention(CLASS)
                @Target({METHOD,CONSTRUCTOR})
                public @interface OverridingMethodsMustInvokeSuper {
                }

                """
            ).indented()
        ).run().expect(expected)
    }

    fun testCallSuperIndirect() {
        // Ensure that when the @CallSuper is on an indirect super method,
        // we correctly check that you call the direct super method, not the ancestor.
        //
        // Regression test for
        //    https://code.google.com/p/android/issues/detail?id=174964
        lint().files(
            java(
                "src/test/pkg/CallSuperTest.java",
                """
                package test.pkg;

                import android.support.annotation.CallSuper;

                import java.util.List;
                import java.util.Map;

                @SuppressWarnings("UnusedDeclaration")
                public class CallSuperTest {
                    private static class Child extends Parent {
                        @Override
                        protected void test1() {
                            super.test1();
                        }
                    }

                    private static class Parent extends ParentParent {
                        @Override
                        protected void test1() {
                            super.test1();
                        }
                    }

                    private static class ParentParent extends ParentParentParent {
                        @CallSuper
                        protected void test1() {
                        }
                    }

                    private static class ParentParentParent {

                    }
                }
                """
            ).indented(),
            classpath(SUPPORT_JAR_PATH),
            base64gzip(
                SUPPORT_JAR_PATH,
                SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP
            )
        ).run().expectClean()
    }

    fun testDetachFromWindow() {
        val expected =
            """
            src/test/pkg/DetachedFromWindow.java:7: Error: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]
                    protected void onDetachedFromWindow() {
                                   ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/DetachedFromWindow.java:26: Error: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]
                    protected void onDetachedFromWindow() {
                                   ~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.view.View;

                public class DetachedFromWindow {
                    private static class Test1 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow() {
                            // Error
                        }
                    }

                    private static class Test2 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow(int foo) {
                            // OK: not overriding the right method
                        }
                    }

                    private static class Test3 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow() {
                            // OK: Calling super
                            super.onDetachedFromWindow();
                        }
                    }

                    private static class Test4 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow() {
                            // Error: missing detach call
                            int x = 1;
                            x++;
                            System.out.println(x);
                        }
                    }

                    private static class Test5 extends Object {
                        protected void onDetachedFromWindow() {
                            // OK - not in a view
                            // Regression test for http://b.android.com/73571
                        }
                    }

                    public class ViewWithDefaultConstructor extends View {
                        public ViewWithDefaultConstructor() {
                            super(null);
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testWatchFaceVisibility() {
        val expected =
            """
            src/test/pkg/WatchFaceTest.java:9: Error: Overriding method should call super.onVisibilityChanged [MissingSuperCall]
                    public void onVisibilityChanged(boolean visible) { // ERROR: Missing super call
                                ~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.wearable.watchface.CanvasWatchFaceService;

                @SuppressWarnings("UnusedDeclaration")
                public class WatchFaceTest extends CanvasWatchFaceService {
                    private static class MyEngine1 extends CanvasWatchFaceService.Engine {
                        @Override
                        public void onVisibilityChanged(boolean visible) { // ERROR: Missing super call
                        }
                    }

                    private static class MyEngine2 extends CanvasWatchFaceService.Engine {
                        @Override
                        public void onVisibilityChanged(boolean visible) { // OK: Super called
                            super.onVisibilityChanged(visible);
                        }
                    }

                    private static class MyEngine3 extends CanvasWatchFaceService.Engine {
                        @Override
                        public void onVisibilityChanged(boolean visible) { // OK: Super called sometimes
                            boolean something = System.currentTimeMillis() % 1 != 0;
                            if (visible && something) {
                                super.onVisibilityChanged(true);
                            }
                        }
                    }

                    private static class MyEngine4 extends CanvasWatchFaceService.Engine {
                        public void onVisibilityChanged() { // OK: Different signature
                        }
                        public void onVisibilityChanged(int flags) { // OK: Different signature
                        }
                        public void onVisibilityChanged(boolean visible, int flags) { // OK: Different signature
                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package android.support.wearable.watchface;

                // Unit testing stub
                public class WatchFaceService {
                    public static class Engine {
                        public void onVisibilityChanged(boolean visible) {
                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package android.support.wearable.watchface;

                public class CanvasWatchFaceService extends WatchFaceService {
                    public static class Engine extends WatchFaceService.Engine {
                        public void onVisibilityChanged(boolean visible) {
                            super.onVisibilityChanged(visible);
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testKotlinMissing() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.view.View
                class MissingSuperCallLibrary(context: Context) : View(context) {
                    override fun onDetachedFromWindow() {
                    }
                }"""
            ).indented()
        ).incremental().run().expect(
            """
            src/test/pkg/MissingSuperCallLibrary.kt:6: Error: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]
                override fun onDetachedFromWindow() {
                             ~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testKotlinOk() {
        lint().files(
            kotlin(
                """package test.pkg

                import android.content.Context
                import android.view.View
                class MissingSuperCallLibrary(context: Context) : View(context) {
                    override fun onDetachedFromWindow() {
                        super.onDetachedFromWindow();
                    }
                }"""
            ).indented()
        ).incremental().run().expectClean()
    }

    fun testMultipleSuperCalls() {
        // Regression test for
        //  37133950: new Lint check: calling the same super function more than once
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.app.Activity
                import android.os.Bundle
                class MyActivity(context: Context) : Activity(context) {
                    private var suspend = false
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState) // OK
                        super.onCreate(savedInstanceState) // ERROR
                    }
                }

                class MyActivity2(context: Context) : Activity(context) {
                    private var suspend = false
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState) // OK
                        if (!suspend) {
                            super.onResume()
                            super.onCreate(savedInstanceState) // ERROR
                        }
                    }
                }

                class MyActivity3(context: Context) : Activity(context) {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        if (savedInstanceState != null) {
                            super.onCreate(savedInstanceState) // OK
                        } else {
                            super.onCreate(savedInstanceState) // OK
                        }
                    }
                }
                """
            ).indented()
        ).incremental().run().expect(
            """
            src/test/pkg/MyActivity.kt:10: Error: Calling super.onCreate more than once can lead to crashes [MissingSuperCall]
                    super.onCreate(savedInstanceState) // ERROR
                    ~~~~~
            src/test/pkg/MyActivity.kt:20: Error: Calling super.onCreate more than once can lead to crashes [MissingSuperCall]
                        super.onCreate(savedInstanceState) // ERROR
                        ~~~~~
            2 errors, 0 warnings
            """
        )
    }
}
