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

import com.android.tools.lint.checks.BidirectionalTextDetector.Companion.LRI
import com.android.tools.lint.checks.BidirectionalTextDetector.Companion.PDF
import com.android.tools.lint.checks.BidirectionalTextDetector.Companion.PDI
import com.android.tools.lint.checks.BidirectionalTextDetector.Companion.RLE
import com.android.tools.lint.checks.BidirectionalTextDetector.Companion.RLO
import com.android.tools.lint.detector.api.Detector

class BidirectionalTextDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return BidirectionalTextDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            java(
                """
                // From https://github.com/nickboucher/trojan-source/blob/main/Java/StretchedString.java
                public class StretchedString {
                    public static void main(String[] args) {
                        String accessLevel = "user";
                        if (accessLevel != "user$RLO $LRI// Check if admin$PDI $LRI") {
                            System.out.println("You are an admin.");
                        }
                    }
                }
              """
            ).indented(),
            java(
                """
                // From https://github.com/nickboucher/trojan-source/blob/main/Java/CommentingOut.java
                public class CommentingOut {
                    public static void main(String[] args) {
                        boolean isAdmin = false;
                        /*$RLO } ${LRI}if (isAdmin)$PDI $LRI begin admins only */
                            System.out.println("You are an admin.");
                        /* end admins only $RLO { $LRI*/
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                /* Comment $RLO // OK
                 * and $LRI // OK
                 */
                val valid1 = "Left${RLO}Right${PDF}Left" // OK
                val valid2 = "Left${RLO}Right${LRI}Nested Left${PDI}${PDF}Left" // OK
                """
            )
        )
            .run()
            .expect(
                """
                src/CommentingOut.java:5: Error: Comment contains misleading Unicode bidirectional text [BidiSpoofing]
                        /*$RLO } ${LRI}if (isAdmin)$PDI $LRI begin admins only */
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/CommentingOut.java:7: Error: Comment contains misleading Unicode bidirectional text [BidiSpoofing]
                        /* end admins only $RLO { $LRI*/
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/StretchedString.java:5: Error: String contains misleading Unicode bidirectional text [BidiSpoofing]
                        if (accessLevel != "user$RLO $LRI// Check if admin$PDI $LRI") {
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors, 0 warnings
                """
            )
    }

    fun testSuppress() {
        // Make sure warnings in comments can be suppressed
        lint().files(
            java(
                """
                @SuppressWarnings("BidiSpoofing")
                /** javadoc */
                public enum LanguageInfo {
                  ARABIC(LanguageCode.ARABIC, "\u202B\u0627\u0644\u0639\u0631\u0628\u064A\u0629") // ${RLE}العربية
                }
                """
            ).indented(),
            java(
                """
                public enum LanguageInfo2 {
                  @SuppressWarnings("BidiSpoofing")
                  ARABIC(LanguageCode.ARABIC, "\u202B\u0627\u0644\u0639\u0631\u0628\u064A\u0629") // ${RLE}العربية
                }
                """
            ).indented(),
            java(
                """
                public enum LanguageInfo3 {
                  ARABIC(LanguageCode.ARABIC,
                    //noinspection BidiSpoofing
                    "\u202B\u0627\u0644\u0639\u0631\u0628\u064A\u0629") // ${RLE}العربية
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAllowUnicodes() {
        // Allow using the unicode escape syntax in source files; this doesn't have
        // the same confusing behavior
        lint().files(
            java(
                "" +
                    "public class Test {\n" +
                    "  private static final String CHARACTER_PATTERN = \"[\" +\n" +
                    "                                                  \"\\u0000-\\u0008\" + // Control codes\n" +
                    "                                                  \"\\u000E-\\u001F\" + // Control codes\n" +
                    "                                                  \"\\u007F-\\u0084\" + // Control codes\n" +
                    "                                                  \"\\u0086-\\u009F\" + // Control codes\n" +
                    "                                                  \"\\u200C-\\u200F\" + // ZERO WIDTH NON-JOINER..RIGHT-TO-LEFT MARK\n" +
                    "                                                  \"\\u202A-\\u202E\" + // LEFT-TO-RIGHT EMBEDDING..RIGHT-TO-LEFT OVERRIDE\n" +
                    "                                                  \"\\u2060-\\u206F\" + // WORD JOINER..NOMINAL DIGIT SHAPES\n" +
                    "                                                  \"\\uFEFF\" +        // ZERO WIDTH NO-BREAK SPACE\n" +
                    "                                                  \"\\uFFF0-\\uFFFB\" + // Format Controls\n" +
                    "                                                  \"]\";\n" +
                    "}"
            )
        ).run().expectClean()
    }
}
