/*
 * Copyright (C) 2012 The Android Open Source Project
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

class SetJavaScriptEnabledDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SetJavaScriptEnabledDetector()
    }

    fun test() {
        //noinspection all // Sample code
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Bundle;
                import android.webkit.WebView;

                public class SetJavaScriptEnabled extends Activity {

                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.main);
                        WebView webView = (WebView)findViewById(R.id.webView);
                        webView.getSettings().setJavaScriptEnabled(true); // bad
                        webView.getSettings().setJavaScriptEnabled(false); // good
                        webView.loadUrl("file:///android_asset/www/index.html");
                    }

                    // Test Suppress
                    // Constructor: See issue 35588
                    @android.annotation.SuppressLint("SetJavaScriptEnabled")
                    public void HelloWebApp() {
                        WebView webView = (WebView)findViewById(R.id.webView);
                        webView.getSettings().setJavaScriptEnabled(true); // bad
                        webView.getSettings().setJavaScriptEnabled(false); // good
                        webView.loadUrl("file:///android_asset/www/index.html");
                    }

                    public static final class R {
                        public static final class layout {
                            public static final int main = 0x7f0a0000;
                        }
                        public static final class id {
                            public static final int webView = 0x7f0a0001;
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/SetJavaScriptEnabled.java:14: Warning: Using setJavaScriptEnabled can introduce XSS vulnerabilities into your application, review carefully [SetJavaScriptEnabled]
                    webView.getSettings().setJavaScriptEnabled(true); // bad
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
