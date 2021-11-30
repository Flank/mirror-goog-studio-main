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

import com.android.tools.lint.detector.api.Detector

class WrongConstructorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return WrongConstructorDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            java(
                """
                package test.pkg;

                public class PnrUtils {
                   public PnrUtils PnrUtils() {
                      return this;
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/PnrUtils.java:4: Warning: Method PnrUtils looks like a constructor but is a normal method [NotConstructor]
               public PnrUtils PnrUtils() {
               ^
            0 errors, 1 warnings
            """
        )
    }

    fun testBasic() {
        lint().files(
            java(
                """
                package test.pkg;

                  @SuppressWarnings({"unused", "InstantiationOfUtilityClass"})
                  public class JQPlacesObject {
                    String id;
                    int lat;
                    public void JQPlacesObject() { // WARN 1
                        this.lat = 0;
                        this.id = "";
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                @SuppressWarnings({"unused", "InstantiationOfUtilityClass"})
                public class JQPlacesObject2 {
                    public static JQPlacesObject2 JQPlacesObject2() { // OK: static
                        return new JQPlacesObject2();
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                @SuppressWarnings({"unused", "InstantiationOfUtilityClass", "MethodNameSameAsClassName"})
                public class JQPlacesObject3 {
                    public JQPlacesObject3 JQPlacesObject3() { // OK: Suppressed via IntelliJ inspection
                        return new JQPlacesObject3();
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                @file:Suppress("unused")

                import android.content.Context

                internal class TestFragmentPeer internal constructor() {
                    private lateinit var viewContext: Context

                    fun TestFragmentPeer(viewContext: Context) { // WARN 2
                        this.viewContext = viewContext
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                @file:Suppress("unused")

                class Test(i: Int) {
                    companion object {
                        fun Test(): Test { // OK
                            return Test(0)
                        }
                    }
                }

                object Test2 {
                    fun Test2(i: Int): Test2 { // OK
                        return Test2
                    }
                }

                class Test3 {
                    fun something() {
                        fun Test3(): Test3 {
                            TODO()
                        }
                    }
                }
                """
            ).indented(),
            kotlin(
                "src/test/pkg/File.kt",
                """
                package test.pkg
                fun FileKt() { } // OK
                """
            ).indented(),
            kotlin(
                """
                @file:JvmName("MyClass")
                fun MyClass() { } // OK
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/JQPlacesObject.java:7: Warning: Method JQPlacesObject looks like a constructor but is a normal method [NotConstructor]
                public void JQPlacesObject() { // WARN 1
                ^
            src/TestFragmentPeer.kt:8: Warning: Method TestFragmentPeer looks like a constructor but is a normal method [NotConstructor]
                fun TestFragmentPeer(viewContext: Context) { // WARN 2
                ^
            0 errors, 2 warnings
            """
        )
    }
}
