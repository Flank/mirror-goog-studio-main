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

class CheckResultDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = CheckResultDetector()

    fun testCheckResult() {
        val expected =
                """
src/test/pkg/CheckPermissions.java:22: Warning: The result of extractAlpha is not used [CheckResult]
        bitmap.extractAlpha(); // WARNING
        ~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/Intersect.java:7: Warning: The result of intersect is not used. If the rectangles do not intersect, no change is made and the original rectangle is not modified. These methods return false to indicate that this has happened. [CheckResult]
    rect.intersect(aLeft, aTop, aRight, aBottom);
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/CheckPermissions.java:10: Warning: The result of checkCallingOrSelfPermission is not used; did you mean to call #enforceCallingOrSelfPermission(String,String)? [UseCheckPermission]
        context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // WRONG
        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/CheckPermissions.java:11: Warning: The result of checkPermission is not used; did you mean to call #enforcePermission(String,int,int,String)? [UseCheckPermission]
        context.checkPermission(Manifest.permission.INTERNET, 1, 1);
        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 4 warnings
"""
        lint().files(
                java("""
                package test.pkg;

                import android.Manifest;
                import android.content.Context;
                import android.content.pm.PackageManager;
                import android.graphics.Bitmap;

                public class CheckPermissions {
                    private void foo(Context context) {
                        context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // WRONG
                        context.checkPermission(Manifest.permission.INTERNET, 1, 1);
                        check(context.checkCallingOrSelfPermission(Manifest.permission.INTERNET)); // OK
                        int check = context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // OK
                        if (context.checkCallingOrSelfPermission(Manifest.permission.INTERNET) // OK
                                != PackageManager.PERMISSION_GRANTED) {
                            showAlert(context, "Error",
                                    "Application requires permission to access the Internet");
                        }
                    }

                    private Bitmap checkResult(Bitmap bitmap) {
                        bitmap.extractAlpha(); // WARNING
                        Bitmap bitmap2 = bitmap.extractAlpha(); // OK
                        call(bitmap.extractAlpha()); // OK
                        return bitmap.extractAlpha(); // OK
                    }

                    private void showAlert(Context context, String error, String s) {
                    }

                    private void check(int i) {
                    }
                    private void call(Bitmap bitmap) {
                    }
                }""").indented(),

                java("src/test/pkg/Intersect.java",
                        """
                package test.pkg;

                import android.graphics.Rect;

                public class Intersect {
                  void check(Rect rect, int aLeft, int aTop, int aRight, int aBottom) {
                    rect.intersect(aLeft, aTop, aRight, aBottom);
                  }
                }""").indented(),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .issues(CheckResultDetector.CHECK_RESULT, PermissionDetector.CHECK_PERMISSION)
                .run()
                .expect(expected)
    }
}