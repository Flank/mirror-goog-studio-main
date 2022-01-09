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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector

class ReturnThisDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ReturnThisDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                import androidx.annotation.ReturnThis

                @ReturnThis
                open class Builder {
                    open fun someMethod(arg: Int): Builder {
                        return this
                    }
                    open fun someOtherMethod(arg: Int): Builder {
                        return this
                    }
                }

                class MyClass : Builder() {
                    override fun someMethod(arg: Int): Builder {
                        return this // OK
                    }
                    override fun someOtherMethod(arg: Int): Builder {
                        return MyClass()
                    }
                }
                """
            ).indented(),
            returnThisStub
        ).run().expect(
            """
            src/Builder.kt:18: Error: This method should return this (because it has been annotated with @ReturnThis) [ReturnThis]
                    return MyClass()
                    ~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testSkipNested() {
        @Suppress("ObjectLiteralToLambda", "ConstantConditionIf")
        lint().files(
            kotlin(
                """
                import androidx.annotation.ReturnThis

                open class Builder {
                    @ReturnThis
                    open fun someMethod(arg: Int): Builder {
                        return Builder() // ERROR 1
                    }
                }

                class MyClass : Builder() {
                    override fun someMethod(arg: Int): Builder {
                        fun print(): String {
                            return "" // OK 1
                        }
                        val listOf = listOf("")
                        listOf.filter { if (true) return this else true } // OK 2
                        listOf.filter { if (true) return this@MyClass else true } // OK 3
                        listOf.filter { if (true) return MyClass() else true } // ERROR
                        listOf.filter { if (true) return@filter false else true } // OK 4

                        listOf("").sortedWith(object : Comparator<String> {
                            override fun compare(p0: String, p1: String): Int {
                                return p0.length - p1.length // OK 5
                            }
                        })

                        return this // OK 4
                    }
                }
                """
            ).indented(),
            returnThisStub
        ).run().expect(
            """
            src/Builder.kt:6: Error: This method should return this (because it has been annotated with @ReturnThis) [ReturnThis]
                    return Builder() // ERROR 1
                    ~~~~~~~~~~~~~~~~
            src/Builder.kt:18: Error: This method should return this (because it has been annotated with @ReturnThis) [ReturnThis]
                    listOf.filter { if (true) return MyClass() else true } // ERROR
                                              ~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }
}

val returnThisStub: TestFile = kotlin(
    """
    package androidx.annotation
    import kotlin.annotation.AnnotationTarget.CLASS
    import kotlin.annotation.AnnotationTarget.FUNCTION
    @MustBeDocumented
    @Retention(AnnotationRetention.BINARY)
    @Target(FUNCTION, CLASS)
    annotation class ReturnThis
    """
).indented()
