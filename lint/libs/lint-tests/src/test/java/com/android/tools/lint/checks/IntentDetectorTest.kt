/*
 * Copyright (C) 2018 The Android Open Source Project
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

class IntentDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return IntentDetector()
    }

    fun testBasic() {
        // Regression test for https://issuetracker.google.com/36967533
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.net.Uri;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class IntentTest {
                        public void test1() {
                            // OK: Nulls are allowed
                            Intent intent = new Intent();
                            intent.setData(null); // OK
                            intent.setType(null); // OK
                        }

                        public void test2(Uri uri, String type) {
                            // Error: Cleared
                            Intent intent = new Intent();
                            intent.setData(uri); // ERROR
                            intent.setType(type); // ERROR
                        }

                        public void test3(Uri uri, String type) {
                            // Error: Cleared (reverse order from test2)
                            Intent intent = new Intent();
                            intent.setType(type); // ERROR
                            intent.setData(uri); // ERROR
                        }

                        public void test4(Uri uri, String type, boolean setData) {
                            // OK: Different code paths
                            Intent intent = new Intent();
                            if (setData) {
                                intent.setData(uri); // OK
                            } else {
                                intent.setType(type); // OK
                            }
                        }

                        public void test5(Uri uri, String type) {
                            // Ok: on different objects
                            Intent intent1 = new Intent();
                            Intent intent2 = new Intent();
                            intent1.setData(uri); // OK
                            intent2.setType(type); // OK
                        }

                        public void test6(boolean setData, Uri dataUri,
                                boolean setType, String mime) {
                            Intent intent = new Intent();
                            if (setData) {
                                intent.setData(dataUri); // OK
                            }
                            if (setType) {
                                intent.setType(mime); // OK
                            }
                        }

                        public void test7(int flavor, Uri dataUri, String mime) {
                            Intent intent = new Intent();
                            switch (flavor) {
                                case 1:
                                    intent.setData(dataUri); // OK
                                    break;
                                case 2:
                                    intent.setType(mime); // OK
                                    break;
                            }
                        }
                }"""
            ).indented()
        ).run().expect(
            """
                    src/test/pkg/IntentTest.java:19: Warning: Calling setType after calling setData will clear the data: Call setDataAndType instead? [IntentReset]
                                intent.setType(type); // ERROR
                                ~~~~~~~~~~~~~~~~~~~~
                        src/test/pkg/IntentTest.java:18: Originally set here
                    src/test/pkg/IntentTest.java:26: Warning: Calling setData after calling setType will clear the type: Call setDataAndType instead? [IntentReset]
                                intent.setData(uri); // ERROR
                                ~~~~~~~~~~~~~~~~~~~
                        src/test/pkg/IntentTest.java:25: Originally set here
                    0 errors, 2 warnings"""
        )
    }

    fun testConstructor() {
        // URI specified in Intent constructor
        // Regression test for https://issuetracker.google.com/73183202
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.content.Intent;
                import android.net.Uri;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class IntentReset extends Activity {
                    private Context mContext = null;
                    public void test(Uri uri) {
                        Intent myIntent = new Intent(Intent.ACTION_VIEW, uri);
                        myIntent.setType("text/plain");
                        myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(myIntent);
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
                src/test/pkg/IntentReset.java:13: Warning: Calling setType after setting URI in Intent constructor will clear the data: Call setDataAndType instead? [IntentReset]
                        myIntent.setType("text/plain");
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    src/test/pkg/IntentReset.java:12: Originally set here
                0 errors, 1 warnings"""
        )
    }
}
