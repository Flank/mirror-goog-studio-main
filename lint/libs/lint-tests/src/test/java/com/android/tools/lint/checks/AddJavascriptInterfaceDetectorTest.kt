/*
 * Copyright (C) 2014 The Android Open Source Project
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

class AddJavascriptInterfaceDetectorTest : AbstractCheckTest() {

    fun test() {
        val expected =
            """
            src/test/pkg/AddJavascriptInterfaceTest.java:16: Warning: WebView.addJavascriptInterface should not be called with minSdkVersion < 17 for security reasons: JavaScript can use reflection to manipulate application [AddJavascriptInterface]
                        webView.addJavascriptInterface(object, string);
                                ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AddJavascriptInterfaceTest.java:23: Warning: WebView.addJavascriptInterface should not be called with minSdkVersion < 17 for security reasons: JavaScript can use reflection to manipulate application [AddJavascriptInterface]
                        webView.addJavascriptInterface(object, string);
                                ~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """

        lint().files(manifest().minSdk(10), testFile).run().expect(expected)
    }

    fun testNoWarningWhenMinSdkAt17() {

        lint().files(manifest().minSdk(17), testFile).run().expectClean()
    }

    private val testFile = java(
        "src/test/pkg/AddJavascriptInterfaceTest.java",
        """
        package test.pkg;

        import android.webkit.WebView;
        import android.content.Context;

        @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
        public class AddJavascriptInterfaceTest {
            private static class WebViewChild extends WebView {
                WebViewChild(Context context) {
                    super(context);
                }
            }

            private static class CallAddJavascriptInterfaceOnWebView {
                public void addJavascriptInterfaceToWebView(WebView webView, Object object, String string) {
                    webView.addJavascriptInterface(object, string);
                }
            }

            private static class CallAddJavascriptInterfaceOnWebViewChild {
                public void addJavascriptInterfaceToWebViewChild(
                    WebViewChild webView, Object object, String string) {
                    webView.addJavascriptInterface(object, string);
                }
            }

            private static class NonWebView {
                public void addJavascriptInterface(Object object, String string) { }
            }

            private static class CallAddJavascriptInterfaceOnNonWebView {
                public void addJavascriptInterfaceToNonWebView(
                    NonWebView webView, Object object, String string) {
                    webView.addJavascriptInterface(object, string);
                }
            }
        }"""
    ).indented()

    override fun getDetector(): Detector {
        return AddJavascriptInterfaceDetector()
    }
}
