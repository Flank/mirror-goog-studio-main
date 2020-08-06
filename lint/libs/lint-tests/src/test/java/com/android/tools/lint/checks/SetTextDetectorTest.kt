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

class SetTextDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SetTextDetector()
    }

    fun test() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.widget.Button;
                import android.widget.TextView;

                class CustomScreen {

                  public CustomScreen(Context context) {
                    TextView view = new TextView(context);

                    // Should fail - hardcoded string
                    view.setText("Hardcoded");
                    // Should pass - no letters
                    view.setText("-");
                    // Should fail - concatenation and toString for numbers.
                    view.setText(Integer.toString(50) + "%");
                    view.setText(Double.toString(12.5) + " miles");

                    Button btn = new Button(context);
                    btn.setText("User " + getUserName());
                    btn.setText(String.format("%s of %s users", Integer.toString(5), Integer.toString(10)));
                  }

                  private static String getUserName() {
                    return "stub";
                  }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/CustomScreen.java:13: Warning: String literal in setText can not be translated. Use Android resources instead. [SetTextI18n]
                view.setText("Hardcoded");
                             ~~~~~~~~~~~
            src/test/pkg/CustomScreen.java:17: Warning: Do not concatenate text displayed with setText. Use resource string with placeholders. [SetTextI18n]
                view.setText(Integer.toString(50) + "%");
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/CustomScreen.java:17: Warning: Number formatting does not take into account locale settings. Consider using String.format instead. [SetTextI18n]
                view.setText(Integer.toString(50) + "%");
                             ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/CustomScreen.java:18: Warning: Do not concatenate text displayed with setText. Use resource string with placeholders. [SetTextI18n]
                view.setText(Double.toString(12.5) + " miles");
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/CustomScreen.java:18: Warning: Number formatting does not take into account locale settings. Consider using String.format instead. [SetTextI18n]
                view.setText(Double.toString(12.5) + " miles");
                             ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/CustomScreen.java:18: Warning: String literal in setText can not be translated. Use Android resources instead. [SetTextI18n]
                view.setText(Double.toString(12.5) + " miles");
                                                     ~~~~~~~~
            src/test/pkg/CustomScreen.java:21: Warning: Do not concatenate text displayed with setText. Use resource string with placeholders. [SetTextI18n]
                btn.setText("User " + getUserName());
                            ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/CustomScreen.java:21: Warning: String literal in setText can not be translated. Use Android resources instead. [SetTextI18n]
                btn.setText("User " + getUserName());
                            ~~~~~~~
            0 errors, 8 warnings
            """
        )
    }
}
