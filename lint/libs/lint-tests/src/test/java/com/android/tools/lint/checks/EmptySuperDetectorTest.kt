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

class EmptySuperDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return EmptySuperDetector()
    }

    fun testDocumentationExample() {
        @Suppress("RedundantOverride")
        lint().files(
            kotlin(

                """
                import androidx.annotation.EmptySuper

                open class ParentClass {
                    @EmptySuper
                    open fun someMethod(arg: Int) {
                        // ...
                    }

                    @EmptySuper
                    open fun someOtherMethod(arg: Int) {
                        // ...
                    }
                }

                class MyClass : ParentClass() {
                    override fun someMethod(arg: Int) {
                        // Not calling the overridden (@EmptySuper) method.
                        println(super.toString()) // but invoking other super methods is fine
                    }

                    override fun someOtherMethod(arg: Int) {
                        super.someOtherMethod(arg) // ERROR
                    }
                }
                """
            ).indented(),
            emptySuperStub
        ).run().expect(
            """
            src/ParentClass.kt:22: Warning: No need to call super.someOtherMethod; the super method is defined to be empty [EmptySuperCall]
                    super.someOtherMethod(arg) // ERROR
                          ~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}

val emptySuperStub: TestFile = kotlin(
    """
    package androidx.annotation
    import kotlin.annotation.AnnotationTarget.FUNCTION
    @MustBeDocumented
    @Retention(AnnotationRetention.BINARY)
    @Target(FUNCTION)
    annotation class EmptySuper
    """
).indented()
