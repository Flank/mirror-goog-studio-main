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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class ExternalAnnotationsDetectorTest {
    @Test
    fun forbiddenMethods() {
        @Suppress("ClassNameDiffersFromFileName") // For language injection below.
        studioLint()
            .files(
                java(
                    """
                    package com.intellij.psi;

                    public interface PsiAnnotation {
                        String getQualifiedName();
                    }
                    """
                ),
                java(
                    """
                    package com.intellij.psi;

                    public interface PsiModifierListOwner {
                        PsiAnnotation[] getAnnotations();
                    }
                    """
                ),
                java(
                    """
                    package com.intellij.psi;

                    public interface PsiMethod extends PsiModifierListOwner {}
                    """
                ),
                kotlin(
                    """
                    package org.jetbrains.uast

                    interface UAnnotation {
                        val qualifiedName: String?
                    }
                    """
                ),
                kotlin(
                    """
                    package org.jetbrains.uast

                    interface UAnnotated {
                        val annotations: List<UAnnotation>
                    }
                    """
                ),
                kotlin(
                    """
                    package org.jetbrains.uast

                    interface UMethod : UAnnotated, PsiMethod
                    """
                ),
                java(
                    """
                        package com.android.tools.lint.checks;

                        import com.intellij.psi.PsiAnnotation;
                        import com.intellij.psi.PsiMethod;
                        import org.jetbrains.uast.UAnnotation;
                        import org.jetbrains.uast.UMethod;

                        public class SomeDetectorInJava {
                            // ...

                            private boolean isSpecial(PsiMethod method) {
                                for (PsiAnnotation ann : method.getAnnotations()) {
                                    if (ann.getQualifiedName.equals("com.example.Special")) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            private boolean isSpecial(UMethod method) {
                                for (UAnnotation ann : method.getAnnotations()) {
                                    if (ann.getQualifiedName.equals("com.example.Special")) {
                                        return true;
                                    }
                                }
                                return false;
                            }
                        }
                    """
                ),
                kotlin(
                    """
                        package com.android.tools.lint.checks

                        import com.intellij.psi.PsiAnnotation
                        import com.intellij.psi.PsiMethod
                        import org.jetbrains.uast.UAnnotation
                        import org.jetbrains.uast.UMethod

                        class SomeDetectorInKotlin {
                            fun isSpecial(method: PsiMethod) {
                                return method.annotations.any { it.qualifiedName == "com.example.Special" }
                            }

                            fun isSpecial(method: UMethod) {
                                return method.annotations.any { it.qualifiedName == "com.example.Special" }
                            }
                        }
                    """
                )
            )
            .issues(ExternalAnnotationsDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/android/tools/lint/checks/SomeDetectorInJava.java:13: Error: getAnnotations used instead of JavaContext.getAllAnnotations. [ExternalAnnotations]
                                                for (PsiAnnotation ann : method.getAnnotations()) {
                                                                         ~~~~~~~~~~~~~~~~~~~~~~~
                src/com/android/tools/lint/checks/SomeDetectorInJava.java:22: Error: getAnnotations used instead of JavaContext.getAllAnnotations. [ExternalAnnotations]
                                                for (UAnnotation ann : method.getAnnotations()) {
                                                                       ~~~~~~~~~~~~~~~~~~~~~~~
                src/com/android/tools/lint/checks/SomeDetectorInKotlin.kt:11: Error: getAnnotations used instead of JavaContext.getAllAnnotations. [ExternalAnnotations]
                                                return method.annotations.any { it.qualifiedName == "com.example.Special" }
                                                       ~~~~~~~~~~~~~~~~~~
                src/com/android/tools/lint/checks/SomeDetectorInKotlin.kt:15: Error: getAnnotations used instead of JavaContext.getAllAnnotations. [ExternalAnnotations]
                                                return method.annotations.any { it.qualifiedName == "com.example.Special" }
                                                              ~~~~~~~~~~~
                4 errors, 0 warnings
                """
            )
    }
}
