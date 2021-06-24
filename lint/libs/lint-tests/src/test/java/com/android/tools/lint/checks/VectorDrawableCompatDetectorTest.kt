/*
 * Copyright (C) 2016 The Android Open Source Project
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
import org.intellij.lang.annotations.Language

/** Tests for [VectorDrawableCompatDetector] */
class VectorDrawableCompatDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return VectorDrawableCompatDetector()
    }

    fun testSrcCompat() {
        val expected =
            """
            src/main/res/layout/main_activity.xml:3: Error: To use VectorDrawableCompat, you need to set android.defaultConfig.vectorDrawables.useSupportLibrary = true in test_project/build.gradle [VectorDrawableCompat]
                <ImageView app:srcCompat="@drawable/foo" />
                           ~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml("src/main/res/drawable/foo.xml", vector),
            xml("src/main/res/layout/main_activity.xml", layoutSrcCompat),
            gradle(
                """
                buildscript {
                    dependencies {
                        classpath 'com.android.tools.build:gradle:2.0.0'
                    }
                }
                android.defaultConfig.vectorDrawables.useSupportLibrary = false
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testSrcCompatWithRepository() {
        val expected =
            """
            src/main/res/layout/main_activity.xml:3: Error: To use VectorDrawableCompat, you need to set android.defaultConfig.vectorDrawables.useSupportLibrary = true in test_project/build.gradle [VectorDrawableCompat]
                <ImageView app:srcCompat="@drawable/foo" />
                           ~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml("src/main/res/drawable/foo.xml", vector),
            xml("src/main/res/layout/main_activity.xml", layoutSrcCompat),
            gradle(
                """
                buildscript {
                    dependencies {
                        classpath 'com.android.tools.build:gradle:2.0.0'
                    }
                }
                android.defaultConfig.vectorDrawables.useSupportLibrary = false
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testSrc() {
        val expected =
            """
            src/main/res/layout/main_activity.xml:3: Error: When using VectorDrawableCompat, you need to use app:srcCompat [VectorDrawableCompat]
                <ImageView android:src="@drawable/foo" />
                           ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml("src/main/res/drawable/foo.xml", vector),
            xml("src/main/res/layout/main_activity.xml", layoutSrc),
            gradle(
                """
                buildscript {
                    dependencies {
                        classpath 'com.android.tools.build:gradle:2.0.0'
                    }
                }
                android.defaultConfig.vectorDrawables.useSupportLibrary = true
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testSrcWithResources() {
        val expected =
            """
            src/main/res/layout/main_activity.xml:3: Error: When using VectorDrawableCompat, you need to use app:srcCompat [VectorDrawableCompat]
                <ImageView android:src="@drawable/foo" />
                           ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml("src/main/res/drawable/foo.xml", vector),
            xml("src/main/res/layout/main_activity.xml", layoutSrc),
            gradle(
                """
                buildscript {
                    dependencies {
                        classpath 'com.android.tools.build:gradle:2.0.0'
                    }
                }
                android.defaultConfig.vectorDrawables.useSupportLibrary = true
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testAnimatedVector() {
        // Regression test for
        //  79950951: Android Studio should display lint warning when referencing an
        //   AnimatedVectorDrawable using `android:src` instead of `app:srcCompat`
        lint().files(
            xml("src/main/res/layout/main_activity.xml", layoutSrc),
            xml(
                "src/main/res/drawable/foo.xml",
                """
                <animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:aapt="http://schemas.android.com/aapt"
                    android:drawable="@drawable/vd_sample">

                    <target android:name="vector">
                        <aapt:attr name="android:animation">
                            <objectAnimator
                                android:duration="3000"
                                android:propertyName="alpha"
                                android:valueFrom="1"
                                android:valueTo="0" />
                        </aapt:attr>
                    </target>

                </animated-vector>
                """
            ).indented(),
            gradle(
                """
                buildscript {
                    dependencies {
                        classpath 'com.android.tools.build:gradle:3.2.0'
                    }
                }
                android.defaultConfig.vectorDrawables.useSupportLibrary = true
                """
            ).indented()
        ).run().expect(
            """
            src/main/res/layout/main_activity.xml:3: Error: When using VectorDrawableCompat, you need to use app:srcCompat [VectorDrawableCompat]
                <ImageView android:src="@drawable/foo" />
                           ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    @Language("XML")
    private val vector =
        """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:height="256dp"
                android:width="256dp"
                android:viewportWidth="32"
                android:viewportHeight="32">
            <path android:fillColor="#8fff"
                  android:pathData="M20.5,9.5
                                c-1.955,0,-3.83,1.268,-4.5,3
                                c-0.67,-1.732,-2.547,-3,-4.5,-3
                                C8.957,9.5,7,11.432,7,14
                                c0,3.53,3.793,6.257,9,11.5
                                c5.207,-5.242,9,-7.97,9,-11.5
                                C25,11.432,23.043,9.5,20.5,9.5z" />
        </vector>
        """.trimIndent()

    @Language("XML")
    private val layoutSrc =
        """
        <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android">

            <ImageView android:src="@drawable/foo" />
        </RelativeLayout>
        """.trimIndent()

    @Language("XML")
    private val layoutSrcCompat =
        """
        <RelativeLayout
                        xmlns:app="http://schemas.android.com/apk/res-auto">
            <ImageView app:srcCompat="@drawable/foo" />
        </RelativeLayout>
        """.trimIndent()
}
