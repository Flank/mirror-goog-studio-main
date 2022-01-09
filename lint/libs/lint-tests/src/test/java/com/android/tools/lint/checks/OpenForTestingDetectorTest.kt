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

class OpenForTestingDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return OpenForTestingDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import androidx.annotation.OpenForTesting

                // Annotation on class
                @OpenForTesting
                open class Builder1 {
                    open fun someMethod(arg: Int) { }
                }

                class MyBuilder1 : Builder1() {  // ERROR 1
                    override fun someMethod(arg: Int) { }
                }

                // Annotation on specific method
                open class Builder2 {
                    @OpenForTesting
                    open fun someMethod(arg: Int) { }
                    open fun someOtherMethod(arg: Int) { }
                }

                class MyBuilder2 : Builder2() {
                    override fun someMethod(arg: Int) { } // ERROR 2
                    override fun someOtherMethod(arg: Int) { }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                class MyBuilder3 extends Builder1 { // ERROR 3
                    @Override void someMethod(int arg) { }
                }
                """
            ).indented(),
            // test/ prefix makes it a test folder entry:
            java(
                "test/test/pkg/MyBuilder4.java",
                """
                package test.pkg;
                class MyBuilder4 extends Builder1 { // OK: In unit test
                    @Override void someMethod(int arg) { }
                }
                """
            ).indented(),
            java(
                "test/test/pkg/MyBuilder5.java",
                """
                package test.pkg;
                class MyBuilder5 extends Builder2 { // OK: In unit test
                    @Override void someMethod(int arg) { }
                }
                """
            ).indented(),
            openForTestingStub
        ).run().expect(
            """
            src/test/pkg/Builder1.kt:10: Error: Builder1 should only be subclassed from tests [OpenForTesting]
            class MyBuilder1 : Builder1() {  // ERROR 1
                               ~~~~~~~~
            src/test/pkg/Builder1.kt:22: Error: Builder2.someMethod should only be overridden from tests [OpenForTesting]
                override fun someMethod(arg: Int) { } // ERROR 2
                             ~~~~~~~~~~
            src/test/pkg/MyBuilder3.java:2: Error: Builder1 should only be subclassed from tests [OpenForTesting]
            class MyBuilder3 extends Builder1 { // ERROR 3
                                     ~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }
}

val openForTestingStub: TestFile = kotlin(
    """
    package androidx.annotation
    import kotlin.annotation.AnnotationRetention.BINARY
    import kotlin.annotation.AnnotationTarget.CLASS
    import kotlin.annotation.AnnotationTarget.FUNCTION
    import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
    import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
    @MustBeDocumented
    @Retention(BINARY)
    @Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, CLASS)
    annotation class OpenForTesting
    """
).indented()
