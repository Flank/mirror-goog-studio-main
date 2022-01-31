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

class DeprecatedSinceApiDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return DeprecatedSinceApiDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            manifest().minSdk(24),
            kotlin(
                """
                package test.pkg
                import androidx.annotation.DeprecatedSinceApi

                class Api {
                    @DeprecatedSinceApi(api = 21)
                    fun someMethod1(arg: Int) { }
                    @DeprecatedSinceApi(api = 25)
                    fun someMethod2(arg: Int) { }
                    @DeprecatedSinceApi(api = 30, message = "Use AlarmManager.notify instead")
                    fun someMethod3(arg: Int) { }
                }

                @DeprecatedSinceApi(api = 28)
                class Api2 {
                    fun someMethod1(arg: Int) { }
                    @DeprecatedSinceApi(api = 30)
                    fun someMethod2(arg: Int) { }
                }

                @DeprecatedSinceApi(30)
                const val MY_CONSTANT = "test"

                class Test {
                    fun test(api: Api, api2: Api2) {
                      api.someMethod1(0)         // OK
                      api.someMethod2(0)         // WARN 1
                      api.someMethod3(0)         // WARN 2
                      val c = MY_CONSTANT        // WARN 3
                      api2.someMethod1(0)        // WARN 4
                      api2.someMethod2(0)        // WARN 5
                      val clz = Api2::class.java // WARN 6
                      println(api::someMethod2)  // WARN 7
                    }
                }
                """
            ).indented(),
            deprecatedSdkVersionStub
        ).run().expect(
            """
            src/test/pkg/Api.kt:26: Warning: This method is deprecated as of API level 25 [DeprecatedSinceApi]
                  api.someMethod2(0)         // WARN 1
                  ~~~~~~~~~~~~~~~~~~
            src/test/pkg/Api.kt:27: Warning: This method is deprecated as of API level 30; Use AlarmManager.notify instead [DeprecatedSinceApi]
                  api.someMethod3(0)         // WARN 2
                  ~~~~~~~~~~~~~~~~~~
            src/test/pkg/Api.kt:28: Warning: This field is deprecated as of API level 30 [DeprecatedSinceApi]
                  val c = MY_CONSTANT        // WARN 3
                          ~~~~~~~~~~~
            src/test/pkg/Api.kt:29: Warning: This class is deprecated as of API level 28 [DeprecatedSinceApi]
                  api2.someMethod1(0)        // WARN 4
                  ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Api.kt:30: Warning: This method is deprecated as of API level 30 [DeprecatedSinceApi]
                  api2.someMethod2(0)        // WARN 5
                  ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Api.kt:31: Warning: This class is deprecated as of API level 28 [DeprecatedSinceApi]
                  val clz = Api2::class.java // WARN 6
                            ~~~~~~~~~~~
            src/test/pkg/Api.kt:32: Warning: This method is deprecated as of API level 25 [DeprecatedSinceApi]
                  println(api::someMethod2)  // WARN 7
                          ~~~~~~~~~~~~~~~~
            0 errors, 7 warnings
            """
        )
    }
}

val deprecatedSdkVersionStub: TestFile = kotlin(
    """
    package androidx.annotation
    import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
    import kotlin.annotation.AnnotationTarget.CLASS
    import kotlin.annotation.AnnotationTarget.CONSTRUCTOR
    import kotlin.annotation.AnnotationTarget.FUNCTION
    import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
    import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
    @MustBeDocumented
    @Retention(AnnotationRetention.BINARY)
    @Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, ANNOTATION_CLASS, CLASS, CONSTRUCTOR)
    annotation class DeprecatedSinceApi(
        val api: Int,
        val message: String = ""
    )
    """
).indented()
