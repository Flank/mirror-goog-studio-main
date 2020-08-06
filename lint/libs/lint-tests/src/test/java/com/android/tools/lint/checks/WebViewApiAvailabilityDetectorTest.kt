/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector

class WebViewApiAvailabilityDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return WebViewApiAvailabilityDetector()
    }

    fun testUnguardedMethods() {
        lint().files(
            LintDetectorTest.java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Bundle;
                import android.webkit.WebView;

                public class WebViewActivity extends Activity {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        WebView.startSafeBrowsing(this, null);
                        WebView webView = findViewById(R.id.webview);
                        webView.loadUrl("https://www.example.com");
                    }
                }
                """.trimIndent()
            ),
            LintDetectorTest.kotlin(
                """
                package test.pkg

                import android.app.Activity
                import android.os.Bundle
                import android.webkit.WebView

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        WebView.startSafeBrowsing(this, null)
                        val webView = findViewById<WebView>(R.id.webview)
                        webView.loadUrl("https://www.example.com")
                    }
                }
                """.trimIndent()
            )
        )
            .issues(WebViewApiAvailabilityDetector.ISSUE)
            .run()
            .expectClean()
    }

    fun testGuardedFrameworkOnlyMethods() {
        lint().files(
            LintDetectorTest.java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Build;
                import android.os.Bundle;
                import android.view.autofill.AutofillValue;
                import android.webkit.WebView;

                public class WebViewActivity extends Activity {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        if (Build.VERSION.SDK_INT > 28) {
                            WebView webView = findViewById(R.id.webview);
                            webView.getAccessibilityClassName();
                            webView.onProvideVirtualStructure(null);
                            webView.autofill(AutofillValue.forDate(0));
                            webView.getRendererPriorityWaivedWhenNotVisible();
                            webView.getRendererRequestedPriority();
                            webView.onProvideAutofillVirtualStructure(null, 0);
                            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
                            webView.getTextClassifier();
                            webView.setTextClassifier(null);
                            WebView.getWebViewClassLoader();
                            WebView.disableWebView();
                            WebView.setDataDirectorySuffix("");
                            webView.getWebViewLooper();
                            webView.isVisibleToUserForAutofill(0);
                        }
                    }
                }
                """.trimIndent()
            ),
            LintDetectorTest.kotlin(
                """
                package test.pkg

                import android.app.Activity
                import android.os.Build
                import android.os.Bundle
                import android.view.autofill.AutofillValue
                import android.webkit.WebView

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        if (Build.VERSION.SDK_INT > 28) {
                            val webView = findViewById<WebView>(R.id.webview)
                            webView.accessibilityClassName
                            webView.onProvideVirtualStructure(null)
                            webView.autofill(AutofillValue.forDate(0))
                            webView.rendererPriorityWaivedWhenNotVisible
                            webView.rendererRequestedPriority
                            webView.onProvideAutofillVirtualStructure(null, 0)
                            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                            webView.textClassifier
                            WebView.getWebViewClassLoader()
                            WebView.disableWebView()
                            WebView.setDataDirectorySuffix("")
                            webView.webViewLooper
                            webView.isVisibleToUserForAutofill(0)
                        }
                    }
                }
                """.trimIndent()
            )
        )
            .issues(WebViewApiAvailabilityDetector.ISSUE)
            .run()
            .expectClean()
    }

    fun testGuardedAndroidXAvailableMethods() {
        val expected =
            """
            src/test/pkg/WebViewActivity.java:14: Warning: Consider using WebViewCompat.createWebMessageChannel instead which will support more devices. [WebViewApiAvailability]
                        webView.createWebMessageChannel();
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:15: Warning: Consider using WebViewCompat.postVisualStateCallback instead which will support more devices. [WebViewApiAvailability]
                        webView.postVisualStateCallback(0, null);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:16: Warning: Consider using WebViewCompat.postWebMessage instead which will support more devices. [WebViewApiAvailability]
                        webView.postWebMessage(null, null);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:17: Warning: Consider using WebViewCompat.getCurrentWebViewPackage instead which will support more devices. [WebViewApiAvailability]
                        WebView.getCurrentWebViewPackage();
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:18: Warning: Consider using WebViewCompat.getWebChromeClient instead which will support more devices. [WebViewApiAvailability]
                        webView.getWebChromeClient();
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:19: Warning: Consider using WebViewCompat.getWebViewClient instead which will support more devices. [WebViewApiAvailability]
                        webView.getWebViewClient();
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:20: Warning: Consider using WebViewCompat.getSafeBrowsingPrivacyPolicyUrl instead which will support more devices. [WebViewApiAvailability]
                        WebView.getSafeBrowsingPrivacyPolicyUrl();
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:21: Warning: Consider using WebViewCompat.setSafeBrowsingWhitelist instead which will support more devices. [WebViewApiAvailability]
                        WebView.setSafeBrowsingWhitelist(null, null);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WebViewActivity.java:22: Warning: Consider using WebViewCompat.startSafeBrowsing instead which will support more devices. [WebViewApiAvailability]
                        WebView.startSafeBrowsing(this, null);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 9 warnings
            """.trimIndent()

        lint().files(
            LintDetectorTest.java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Build;
                import android.os.Bundle;
                import android.webkit.WebView;

                public class WebViewActivity extends Activity {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        if (Build.VERSION.SDK_INT > 28) {
                            WebView webView = findViewById(R.id.webview);
                            webView.createWebMessageChannel();
                            webView.postVisualStateCallback(0, null);
                            webView.postWebMessage(null, null);
                            WebView.getCurrentWebViewPackage();
                            webView.getWebChromeClient();
                            webView.getWebViewClient();
                            WebView.getSafeBrowsingPrivacyPolicyUrl();
                            WebView.setSafeBrowsingWhitelist(null, null);
                            WebView.startSafeBrowsing(this, null);
                        }
                    }
                }
                """.trimIndent()
            )
        )
            .issues(WebViewApiAvailabilityDetector.ISSUE)
            .run()
            .expect(expected)
    }

    fun testGuardedAndroidXAvailableMethodsKotlin() {
        val expected =
            """
            src/test/pkg/MainActivity.kt:13: Warning: Consider using WebViewCompat.createWebMessageChannel instead which will support more devices. [WebViewApiAvailability]
                        webView.createWebMessageChannel()
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:14: Warning: Consider using WebViewCompat.postVisualStateCallback instead which will support more devices. [WebViewApiAvailability]
                        webView.postVisualStateCallback(0, null)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:15: Warning: Consider using WebViewCompat.postWebMessage instead which will support more devices. [WebViewApiAvailability]
                        webView.postWebMessage(null, null)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:16: Warning: Consider using WebViewCompat.getCurrentWebViewPackage instead which will support more devices. [WebViewApiAvailability]
                        WebView.getCurrentWebViewPackage()
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:17: Warning: Consider using WebViewCompat.getWebChromeClient instead which will support more devices. [WebViewApiAvailability]
                        webView.webChromeClient
                        ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:18: Warning: Consider using WebViewCompat.getWebViewClient instead which will support more devices. [WebViewApiAvailability]
                        webView.webViewClient
                        ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:19: Warning: Consider using WebViewCompat.getSafeBrowsingPrivacyPolicyUrl instead which will support more devices. [WebViewApiAvailability]
                        WebView.getSafeBrowsingPrivacyPolicyUrl()
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:20: Warning: Consider using WebViewCompat.setSafeBrowsingWhitelist instead which will support more devices. [WebViewApiAvailability]
                        WebView.setSafeBrowsingWhitelist(null, null)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:21: Warning: Consider using WebViewCompat.startSafeBrowsing instead which will support more devices. [WebViewApiAvailability]
                        WebView.startSafeBrowsing(this, null)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 9 warnings
            """.trimIndent()

        lint().files(
            LintDetectorTest.kotlin(
                """
                package test.pkg

                import android.app.Activity
                import android.os.Build
                import android.os.Bundle
                import android.webkit.WebView

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        if (Build.VERSION.SDK_INT > 28) {
                            val webView = findViewById<WebView>(R.id.webview)
                            webView.createWebMessageChannel()
                            webView.postVisualStateCallback(0, null)
                            webView.postWebMessage(null, null)
                            WebView.getCurrentWebViewPackage()
                            webView.webChromeClient
                            webView.webViewClient
                            WebView.getSafeBrowsingPrivacyPolicyUrl()
                            WebView.setSafeBrowsingWhitelist(null, null)
                            WebView.startSafeBrowsing(this, null)
                        }
                    }
                }
                """.trimIndent()
            )
        )
            .issues(WebViewApiAvailabilityDetector.ISSUE)
            .run()
            .expect(expected)
    }
}
