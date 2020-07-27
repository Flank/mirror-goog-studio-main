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

class RequiresFeatureDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = RequiresFeatureDetector()

    fun testScenarios() {
        val expected =
            """
            src/test/pkg/CheckFeatures.java:10: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                    api.someMethod(); // 1 - ERROR
                    ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:12: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                        api.someMethod(); // 2 - ERROR
                        ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:33: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                        api.someMethod(); // 6 - ERROR - wrong name
                        ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:39: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                        api.someMethod(); // 7 - ERROR - inverted logic
                        ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:52: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                    return FeatureChecker.hasFeature("wrong.name") && api.someMethod(); // 10 - ERROR
                                                                      ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:56: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                    return FeatureChecker.hasFeature("wrong.name") || api.someMethod(); // 11 - ERROR
                                                                      ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:70: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                    api.someMethod(); // 13 - ERROR: inverted logic in earl exit
                    ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:92: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                        api.someMethod(); // 16 - ERROR
                        ~~~~~~~~~~~~~~~~
            src/test/pkg/CheckFeatures.java:99: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                        api.someMethod(); // 17 - ERROR
                        ~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:11: Warning: someMethod should only be called if the feature some.name is present; to check call test.framework.pkg.FeatureChecker#hasFeature [RequiresFeature]
                    someMethod() // ERROR - not checked in lambda
                    ~~~~~~~~~~~~
            0 errors, 10 warnings
            """
        lint().files(
            java(
                """
                    package test.pkg;

                    import test.framework.pkg.FeatureChecker;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "unused", "WeakerAccess", "ConstantIfStatement", "MethodMayBeStatic", "ConstantConditions", "StatementWithEmptyBody"})
                    public class CheckFeatures {
                        public static final String SOME_NAME_CONSTANT = "some.name";

                        private void testError1(SomeApi api) {
                            api.someMethod(); // 1 - ERROR
                            if (true) {
                                api.someMethod(); // 2 - ERROR
                            }
                        }

                        private void testOkConditional(SomeApi api) {
                            if (FeatureChecker.hasFeature("some.name")) {
                                api.someMethod(); // 3 - OK
                                if (true) {
                                    api.someMethod(); // 4 - OK
                                }
                            }
                        }

                        private void testOkConditionalViaConstant(SomeApi api) {
                            if (FeatureChecker.hasFeature(SOME_NAME_CONSTANT)) {
                                api.someMethod(); // 5 - OK
                            }
                        }

                        private void testWrongNameConditional(SomeApi api) {
                            if (FeatureChecker.hasFeature("wrong.name")) {
                                api.someMethod(); // 6 - ERROR - wrong name
                            }
                        }

                        private void testInvertedConditional(SomeApi api) {
                            if (!FeatureChecker.hasFeature("some.name")) {
                                api.someMethod(); // 7 - ERROR - inverted logic
                            }
                        }

                        private boolean testCorrectAndedExpressions(SomeApi api) {
                            return FeatureChecker.hasFeature("some.name") && api.someMethod(); // 8 - OK
                        }

                        private boolean testCorrectOredExpressions(SomeApi api) {
                            return !FeatureChecker.hasFeature("some.name") || api.someMethod(); // 9 - OK
                        }

                        private boolean testIncorrectAndedExpressions(SomeApi api) {
                            return FeatureChecker.hasFeature("wrong.name") && api.someMethod(); // 10 - ERROR
                        }

                        private boolean testIncorrectOredExpressions(SomeApi api) {
                            return FeatureChecker.hasFeature("wrong.name") || api.someMethod(); // 11 - ERROR
                        }

                        private void testValidEarlyReturn(SomeApi api) {
                            if (!FeatureChecker.hasFeature("some.name")) {
                                return;
                            }
                            api.someMethod(); // 12 - OK exited above
                        }

                        private void testInvalidEarlyReturn(SomeApi api) {
                            if (FeatureChecker.hasFeature("some.name")) {
                                return;
                            }
                            api.someMethod(); // 13 - ERROR: inverted logic in earl exit
                        }

                        public static boolean hasSomeNameFeatureUtilityMethod() {
                            return FeatureChecker.hasFeature("some.name");
                        }

                        public static boolean hasGenericFeatureUtilityMethod(String name) {
                            return FeatureChecker.hasFeature(name);
                        }

                        private void testOkViaSpecificUtilityMethod(SomeApi api) {
                            if (hasSomeNameFeatureUtilityMethod()) {
                                api.someMethod(); // 14 - OK
                            }
                        }

                        private void testOkViaGenericUtilityMethod(SomeApi api) {
                            if (hasGenericFeatureUtilityMethod("some.name")) {
                                api.someMethod(); // 15 - OK
                            }
                            if (hasGenericFeatureUtilityMethod("wrong.name")) {
                                api.someMethod(); // 16 - ERROR
                            }
                        }

                        private void testOkConditional(SomeApi api) {
                            if (FeatureChecker.hasFeature("some.name")) {
                            } else {
                                api.someMethod(); // 17 - ERROR
                            }
                        }
                    }
                """
            ).indented(),
            kotlin(
                "src/test/pkg/test.kt",
                """
                package test.pkg

                import test.framework.pkg.FeatureChecker

                fun testLambdas() {
                    val api = SomeApi()
                    api.applyIfHasFeatureX {
                        someMethod() // OK - checked in lambda
                    }
                    api.applyIfHasFeatureY {
                        someMethod() // ERROR - not checked in lambda
                    }
                }

                inline fun <T> T.applyIfHasFeatureX(block: T.() -> Unit): T {
                    if (FeatureChecker.hasFeature("some.name")) {
                        block()
                    }
                    return this
                }

                inline fun <T> T.applyIfHasFeatureY(block: T.() -> Unit): T {
                    if (FeatureChecker.hasFeature("some.other.feature")) {
                        block()
                    }
                    return this
                }

                fun testPropertySyntax() {
                    val api = SomeOtherApi()
                    if (api.supportsFeatureX) {
                        SomeApi().someMethod() // OK - checked via utility method accessed via property syntax
                    }
                }

                class SomeOtherApi {
                    val supportsFeatureX get() = FeatureChecker.hasFeature("some.name")
                }

                """
            ).indented(),
            java(
                """
                package test.framework.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class FeatureChecker {
                    public static boolean hasFeature(String name) { return true; }
                }"""
            ).indented(),
            java(
                """
                package test.pkg;

                import android.support.annotation.RequiresFeature;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SomeApi {
                    @RequiresFeature(name = "some.name", enforcement = "test.framework.pkg.FeatureChecker#hasFeature")
                    public boolean someMethod() { return true; }
                }"""
            ).indented(),
            requiresFeatureAnnotationSource, // until part of support library prebuilt
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    companion object {
        // Until this is part of the support library prebuilt
        val requiresFeatureAnnotationSource: TestFile = java(
            """
                package android.support.annotation;

                import static java.lang.annotation.ElementType.*;
                import static java.lang.annotation.RetentionPolicy.SOURCE;
                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                @Retention(SOURCE)
                @Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
                public @interface RequiresFeature {
                    String name();
                    String enforcement();
                }
                """
        ).indented()
    }
}
