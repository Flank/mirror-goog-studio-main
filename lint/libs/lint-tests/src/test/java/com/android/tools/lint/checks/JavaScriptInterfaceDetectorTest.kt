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

import com.android.tools.lint.detector.api.Detector

class JavaScriptInterfaceDetectorTest : AbstractCheckTest() {

    fun testOlderSdk() {
        lint().files(
            classpath(),
            projectProperties().compileSdk(19),
            manifest().minSdk(10),
            annotatedObject,
            inheritsFromAnnotated,
            nonAnnotatedObject,
            javaScriptTest
        ).run().expectClean()
    }

    fun testNotPublic() {
        // Regression test for issue 118464831
        lint().files(
            classpath(),
            projectProperties().compileSdk(19),
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.webkit.JavascriptInterface;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                @JavascriptInterface
                class AnnotatedObject {
                    @JavascriptInterface
                    void test1() {
                    }

                    @JavascriptInterface
                    public void test2() {
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AnnotatedObject.java:7: Error: Must be public when using @JavascriptInterface [JavascriptInterface]
            class AnnotatedObject {
                  ~~~~~~~~~~~~~~~
            src/test/pkg/AnnotatedObject.java:9: Error: Must be public when using @JavascriptInterface [JavascriptInterface]
                void test1() {
                     ~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun test() {
        val expected =
            """
            src/test/pkg/JavaScriptTest.java:11: Error: None of the methods in the added interface (NonAnnotatedObject) have been annotated with @android.webkit.JavascriptInterface; they will not be visible in API 17 [JavascriptInterface]
                    webview.addJavascriptInterface(new NonAnnotatedObject(), "myobj");
                            ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaScriptTest.java:14: Error: None of the methods in the added interface (NonAnnotatedObject) have been annotated with @android.webkit.JavascriptInterface; they will not be visible in API 17 [JavascriptInterface]
                    webview.addJavascriptInterface(o, "myobj");
                            ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaScriptTest.java:21: Error: None of the methods in the added interface (NonAnnotatedObject) have been annotated with @android.webkit.JavascriptInterface; they will not be visible in API 17 [JavascriptInterface]
                    webview.addJavascriptInterface(object2, "myobj");
                            ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaScriptTest.java:32: Error: None of the methods in the added interface (NonAnnotatedObject) have been annotated with @android.webkit.JavascriptInterface; they will not be visible in API 17 [JavascriptInterface]
                    webview.addJavascriptInterface(t, "myobj");
                            ~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """

        lint().files(
            classpath(),
            projectProperties().compileSdk(19),
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.bytecode"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="17" />

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
            ).indented(),
            annotatedObject,
            inheritsFromAnnotated,
            nonAnnotatedObject,
            javaScriptTest
        ).run().expect(expected)
    }

    private val annotatedObject = java(
        """
        package test.pkg;

        import android.webkit.JavascriptInterface;

        @SuppressWarnings("ClassNameDiffersFromFileName")
        public class AnnotatedObject {
            @JavascriptInterface
            public void test1() {
            }

            public void test2() {
            }

            @JavascriptInterface
            public void test3() {
            }
        }
        """
    ).indented()

    private val inheritsFromAnnotated = java(
        """
        package test.pkg;

        import android.webkit.JavascriptInterface;

        @SuppressWarnings("ClassNameDiffersFromFileName")
        public class InheritsFromAnnotated extends AnnotatedObject {

            @Override
            public void test1() {
            }

            @Override
            public void test2() {
            }

        }
        """
    ).indented()

    private val javaScriptTest = java(
        """
        package test.pkg;

        import android.annotation.SuppressLint;
        import android.webkit.WebView;

        @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
        public class JavaScriptTest {
            public void test(WebView webview) {
                webview.addJavascriptInterface(new AnnotatedObject(), "myobj");
                webview.addJavascriptInterface(new InheritsFromAnnotated(), "myobj");
                webview.addJavascriptInterface(new NonAnnotatedObject(), "myobj");

                Object o = new NonAnnotatedObject();
                webview.addJavascriptInterface(o, "myobj");
                o = new InheritsFromAnnotated();
                webview.addJavascriptInterface(o, "myobj");
            }

            public void test(WebView webview, AnnotatedObject object1, NonAnnotatedObject object2) {
                webview.addJavascriptInterface(object1, "myobj");
                webview.addJavascriptInterface(object2, "myobj");
            }

            @SuppressLint("JavascriptInterface")
            public void testSuppressed(WebView webview) {
                webview.addJavascriptInterface(new NonAnnotatedObject(), "myobj");
            }

            public void testLaterReassignment(WebView webview) {
                Object o = new NonAnnotatedObject();
                Object t = o;
                webview.addJavascriptInterface(t, "myobj");
                o = new AnnotatedObject();
            }
        }
        """
    ).indented()

    private val nonAnnotatedObject = java(
        """
        package test.pkg;

        @SuppressWarnings("ClassNameDiffersFromFileName")
        public class NonAnnotatedObject {
            public void test1() {
            }
            public void test2() {
            }
        }
        """
    ).indented()

    override fun getDetector(): Detector {
        return JavaScriptInterfaceDetector()
    }
}
