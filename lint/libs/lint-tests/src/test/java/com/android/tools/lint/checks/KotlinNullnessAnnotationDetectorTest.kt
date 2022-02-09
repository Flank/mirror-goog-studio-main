/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class KotlinNullnessAnnotationDetectorTest : AbstractCheckTest() {
    override fun lint(): TestLintTask {
        return super.lint()
            // Our warning messages are picking up specific strings from the type expressions, which means
            // the test results would vary for type aliases
            .skipTestModes(TestMode.TYPE_ALIAS)
    }

    override fun getDetector(): Detector {
        return KotlinNullnessAnnotationDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import androidx.annotation.NonNull
                import androidx.annotation.Nullable

                // Here we have an already non-null string annotated with @NonNull;
                // warn, since this is redundant
                fun testWarning(@NonNull string: String) { }

                // Here we have a non-null string which has been annotated with @Nullable,
                // which is totally misleading; the annotation is wrong
                fun testError(@Nullable string: String) { }

                // Here we have a nullable string which has been annotated with @NonNull,
                // which is totally misleading; the annotation is wrong.
                fun testError(@NonNull number: Number?) { }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:11: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type String not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
            fun testError(@Nullable string: String) { }
                          ~~~~~~~~~
            src/test/pkg/test.kt:15: Error: Do not use @NonNull in Kotlin; the nullability is determined by the Kotlin type Number? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
            fun testError(@NonNull number: Number?) { }
                          ~~~~~~~~
            src/test/pkg/test.kt:7: Warning: Do not use @NonNull in Kotlin; the nullability is already implied by the Kotlin type String not ending with ? [KotlinNullnessAnnotation]
            fun testWarning(@NonNull string: String) { }
                            ~~~~~~~~
            2 errors, 1 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/test.kt line 11: Delete `@Nullable`:
            @@ -11 +11
            - fun testError(@Nullable string: String) { }
            + fun testError(string: String) { }
            Fix for src/test/pkg/test.kt line 15: Delete `@NonNull`:
            @@ -15 +15
            - fun testError(@NonNull number: Number?) { }
            @@ -16 +15
            + fun testError(number: Number?) { }
            Fix for src/test/pkg/test.kt line 7: Delete `@NonNull`:
            @@ -7 +7
            - fun testWarning(@NonNull string: String) { }
            + fun testWarning(string: String) { }
            """
        )
    }

    fun testAll() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.NonNull
                import androidx.annotation.Nullable
                import androidx.annotation.VisibleForTesting

                fun testOk(a: String, b: String?, c: List<String>, d: List<String>?) { } // OK
                fun testWarning(
                    @NonNull a: String,         // WARN 1
                    @Nullable b: String?,       // WARN 2
                    @NonNull c: List<String>,   // WARN 3
                    @Nullable d: List<String>?  // WARN 4
                    ) { }
                fun testError(
                    @Nullable a: String,        // ERROR 1
                    @NonNull b: String?,        // ERROR 2
                    @Nullable c: List<String>,  // ERROR 3
                    @NonNull d: List<String>?   // ERROR 4
                    ) { }

                class Test1(@Nullable s: String)             // ERROR 5
                class Test2(list: List<@Nullable String>)    // OK (we're not visiting inside types currently)
                class Test3 {
                  @Nullable val string1: String              // ERROR 6
                  @NonNull                                   // ERROR 7
                  val string2: String?
                  @JvmField @NonNull @VisibleForTesting var foo: Number? = null // ERROR 8
                }
                @Nullable fun test1(): String = ""           // ERROR 9
                @NonNull fun test2(): String? = null         // ERROR 10

                // Suppressed
                @Suppress("KotlinNullnessAnnotation")
                open class Parent {
                    @Nullable open fun test(@Nullable param: String?): String? = null
                }
                class Child : Parent() {
                    // Make sure we're not inheriting annotations here
                    override fun test(param: String?): String? = null
                }
                """
            ).indented(),
            java(
                // Nothing should be flagged in Java
                """
                package test.pkg;

                import androidx.annotation.Nullable;
                import androidx.annotation.NonNull;
                public class JavaTest {
                    public void test(@Nullable String s) { } // OK
                    public void test(@NonNull Number n) { }  // OK
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Test1.kt:15: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type String not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @Nullable a: String,        // ERROR 1
                ~~~~~~~~~
            src/test/pkg/Test1.kt:16: Error: Do not use @NonNull in Kotlin; the nullability is determined by the Kotlin type String? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @NonNull b: String?,        // ERROR 2
                ~~~~~~~~
            src/test/pkg/Test1.kt:17: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type List<String> not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @Nullable c: List<String>,  // ERROR 3
                ~~~~~~~~~
            src/test/pkg/Test1.kt:18: Error: Do not use @NonNull in Kotlin; the nullability is determined by the Kotlin type List<String>? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @NonNull d: List<String>?   // ERROR 4
                ~~~~~~~~
            src/test/pkg/Test1.kt:21: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type String not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
            class Test1(@Nullable s: String)             // ERROR 5
                        ~~~~~~~~~
            src/test/pkg/Test1.kt:24: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type String not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
              @Nullable val string1: String              // ERROR 6
              ~~~~~~~~~
            src/test/pkg/Test1.kt:25: Error: Do not use @NonNull in Kotlin; the nullability is determined by the Kotlin type String? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
              @NonNull                                   // ERROR 7
              ~~~~~~~~
            src/test/pkg/Test1.kt:27: Error: Do not use @NonNull in Kotlin; the nullability is determined by the Kotlin type Number? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
              @JvmField @NonNull @VisibleForTesting var foo: Number? = null // ERROR 8
                        ~~~~~~~~
            src/test/pkg/Test1.kt:29: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type String not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
            @Nullable fun test1(): String = ""           // ERROR 9
            ~~~~~~~~~
            src/test/pkg/Test1.kt:30: Error: Do not use @NonNull in Kotlin; the nullability is determined by the Kotlin type String? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
            @NonNull fun test2(): String? = null         // ERROR 10
            ~~~~~~~~
            src/test/pkg/Test1.kt:9: Warning: Do not use @NonNull in Kotlin; the nullability is already implied by the Kotlin type String not ending with ? [KotlinNullnessAnnotation]
                @NonNull a: String,         // WARN 1
                ~~~~~~~~
            src/test/pkg/Test1.kt:10: Warning: Do not use @Nullable in Kotlin; the nullability is already implied by the Kotlin type String? ending with ? [KotlinNullnessAnnotation]
                @Nullable b: String?,       // WARN 2
                ~~~~~~~~~
            src/test/pkg/Test1.kt:11: Warning: Do not use @NonNull in Kotlin; the nullability is already implied by the Kotlin type List<String> not ending with ? [KotlinNullnessAnnotation]
                @NonNull c: List<String>,   // WARN 3
                ~~~~~~~~
            src/test/pkg/Test1.kt:12: Warning: Do not use @Nullable in Kotlin; the nullability is already implied by the Kotlin type List<String>? ending with ? [KotlinNullnessAnnotation]
                @Nullable d: List<String>?  // WARN 4
                ~~~~~~~~~
            10 errors, 4 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/Test1.kt line 15: Delete `@Nullable`:
            @@ -15 +15
            -     @Nullable a: String,        // ERROR 1
            +     a: String,        // ERROR 1
            Fix for src/test/pkg/Test1.kt line 16: Delete `@NonNull`:
            @@ -16 +16
            -     @NonNull b: String?,        // ERROR 2
            +     b: String?,        // ERROR 2
            Fix for src/test/pkg/Test1.kt line 17: Delete `@Nullable`:
            @@ -17 +17
            -     @Nullable c: List<String>,  // ERROR 3
            +     c: List<String>,  // ERROR 3
            Fix for src/test/pkg/Test1.kt line 18: Delete `@NonNull`:
            @@ -18 +18
            -     @NonNull d: List<String>?   // ERROR 4
            +     d: List<String>?   // ERROR 4
            Fix for src/test/pkg/Test1.kt line 21: Delete `@Nullable`:
            @@ -21 +21
            - class Test1(@Nullable s: String)             // ERROR 5
            + class Test1(s: String)             // ERROR 5
            Fix for src/test/pkg/Test1.kt line 24: Delete `@Nullable`:
            @@ -24 +24
            -   @Nullable val string1: String              // ERROR 6
            +   val string1: String              // ERROR 6
            Fix for src/test/pkg/Test1.kt line 25: Delete `@NonNull`:
            @@ -25 +25
            -   @NonNull                                   // ERROR 7
            +                                     // ERROR 7
            Fix for src/test/pkg/Test1.kt line 27: Delete `@NonNull`:
            @@ -27 +27
            -   @JvmField @NonNull @VisibleForTesting var foo: Number? = null // ERROR 8
            +   @JvmField @VisibleForTesting var foo: Number? = null // ERROR 8
            Fix for src/test/pkg/Test1.kt line 29: Delete `@Nullable`:
            @@ -29 +29
            - @Nullable fun test1(): String = ""           // ERROR 9
            + fun test1(): String = ""           // ERROR 9
            Fix for src/test/pkg/Test1.kt line 30: Delete `@NonNull`:
            @@ -30 +30
            - @NonNull fun test2(): String? = null         // ERROR 10
            + fun test2(): String? = null         // ERROR 10
            Autofix for src/test/pkg/Test1.kt line 9: Delete `@NonNull`:
            @@ -9 +9
            -     @NonNull a: String,         // WARN 1
            +     a: String,         // WARN 1
            Autofix for src/test/pkg/Test1.kt line 10: Delete `@Nullable`:
            @@ -10 +10
            -     @Nullable b: String?,       // WARN 2
            +     b: String?,       // WARN 2
            Autofix for src/test/pkg/Test1.kt line 11: Delete `@NonNull`:
            @@ -11 +11
            -     @NonNull c: List<String>,   // WARN 3
            +     c: List<String>,   // WARN 3
            Autofix for src/test/pkg/Test1.kt line 12: Delete `@Nullable`:
            @@ -12 +12
            -     @Nullable d: List<String>?  // WARN 4
            +     d: List<String>?  // WARN 4
            """
        )
    }

    fun testFullyQualifiedNames() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import androidx.annotation.NonNull
                fun testWarning(@androidx.annotation.NonNull a: String) { } // ERROR
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).testModes(TestMode.DEFAULT).run().expect(
            """
            src/test/pkg/test.kt:3: Warning: Do not use @NonNull in Kotlin; the nullability is already implied by the Kotlin type String not ending with ? [KotlinNullnessAnnotation]
            fun testWarning(@androidx.annotation.NonNull a: String) { } // ERROR
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/test.kt line 3: Delete `@NonNull`:
            @@ -3 +3
            - fun testWarning(@androidx.annotation.NonNull a: String) { } // ERROR
            @@ -4 +3
            + fun testWarning(a: String) { } // ERROR
            """
        )
    }

    fun testJetBrainsAnnotations() {
        // Make sure we don't confuse explicitly annotated org.jetbrains nullability annotations with the
        // builtin JetBrains ones.
        lint().files(
            kotlin(
                """
                package test.pkg
                import org.jetbrains.annotations.Nullable
                import org.jetbrains.annotations.NotNull
                fun testError(
                    @Nullable string: String,   // ERROR 1
                    @NotNull string2: String?   // ERROR 2
                    ) { }
                """
            ).indented(),
            // Stubs
            java(
                """
                package org.jetbrains.annotations;
                public @interface Nullable { }
                """
            ).indented(),
            java(
                """
                package org.jetbrains.annotations;
                public @interface NotNull { }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:5: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type String not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @Nullable string: String,   // ERROR 1
                ~~~~~~~~~
            src/test/pkg/test.kt:6: Error: Do not use @NotNull in Kotlin; the nullability is determined by the Kotlin type String? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @NotNull string2: String?   // ERROR 2
                ~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testJavaxAnnotations() {
        // javax.annotation is using runtime retention so theoretically these annotations
        // might be used by frameworks; for now we're only flagging contradictory annotations,
        // not redundant ones
        lint().files(
            kotlin(
                """
                package test.pkg
                import javax.annotation.Nullable
                import javax.annotation.Nonnull
                fun testError(
                    @Nonnull string1: String,    // OK 1
                    @Nullable string2: String?,  // OK 2
                    @Nullable string3: String,   // ERROR 1
                    @Nonnull string4: String?    // ERROR 2
                    ) { }
                """
            ).indented(),
            // Stubs
            java(
                """
                package javax.annotation;
                public @interface Nullable { }
                """
            ).indented(),
            java(
                """
                package javax.annotation;
                public @interface Nonnull { }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:7: Error: Do not use @Nullable in Kotlin; the nullability is determined by the Kotlin type String not ending with ? which declares it not nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @Nullable string3: String,   // ERROR 1
                ~~~~~~~~~
            src/test/pkg/test.kt:8: Error: Do not use @Nonnull in Kotlin; the nullability is determined by the Kotlin type String? ending with ? which declares it nullable, contradicting the annotation [KotlinNullnessAnnotation]
                @Nonnull string4: String?    // ERROR 2
                ~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }
}
