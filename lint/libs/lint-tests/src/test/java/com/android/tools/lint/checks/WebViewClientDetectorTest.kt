/*
 * Copyright (C) 2021 The Android Open Source Project
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

class WebViewClientDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return WebViewClientDetector()
    }

    @Suppress("LintDocExample")
    fun testOnReceivedSslError_expectWarnings() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name=".MainActivity" />
                    </application>

                </manifest>
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.net.http.SslError;
                import android.os.Bundle;
                import android.util.Log;
                import android.webkit.SslErrorHandler;
                import android.webkit.WebView;
                import android.webkit.WebViewClient;

                import androidx.annotation.Nullable;

                public class MainActivity extends Activity {
                    protected void loadWebpage(Webview webView, String url) {
                        WebView webView = (WebView) findViewById(R.id.webview);
                        webView.setWebViewClient(new MyWebViewClient());
                        webView.loadUrl(url);
                    }

                    public static class MyWebViewClient extends WebViewClient {
                        @Override
                        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                            Log.d("MainActivity", "Bad SSL cert happened!");
                            if (error.getPrimaryError() != SslError.SSL_UNTRUSTED) {
                                handler.proceed();
                            }
                            proceed();
                        }
                        private void proceed() {}
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/MainActivity.java:25: Warning: Permitting connections with SSL-related errors could allow eavesdroppers to intercept data sent by your app, which impacts the privacy of your users. Consider canceling the connections by invoking SslErrorHandler#cancel(). [WebViewClientOnReceivedSslError]
                            handler.proceed();
                            ~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testOnReceivedSslError_expectNoWarnings() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name=".MainActivity" />
                    </application>

                </manifest>
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.net.http.SslError;
                import android.os.Bundle;
                import android.util.Log;
                import android.webkit.SslErrorHandler;
                import android.webkit.WebView;
                import android.webkit.WebViewClient;

                import androidx.annotation.Nullable;

                public class MainActivity extends Activity {
                    protected void loadWebpage(Webview webView, String url) {
                        WebView webView = (WebView) findViewById(R.id.webview);
                        webView.setWebViewClient(new MyWebViewClient());
                        webView.loadUrl(url);
                    }

                    public static class MyWebViewClient extends WebViewClient {
                        @Override
                        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                            Log.d("MainActivity", "Bad SSL cert happened!");
                            proceed();
                        }
                        private void proceed() {}
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }
}
