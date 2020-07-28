/*
 * Copyright (C) 2015 The Android Open Source Project
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

class UnsafeNativeCodeDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return UnsafeNativeCodeDetector()
    }

    fun testLoad() {
        lint().files(
            java(
                "src/test/pkg/Load.java",
                """
                package test.pkg;

                import java.lang.NullPointerException;
                import java.lang.Runtime;
                import java.lang.SecurityException;
                import java.lang.System;
                import java.lang.UnsatisfiedLinkError;

                public class Load {
                    public static void foo() {
                        try {
                            Runtime.getRuntime().load("/data/data/test.pkg/files/libhello.so");
                            Runtime.getRuntime().loadLibrary("hello"); // ok
                            System.load("/data/data/test.pkg/files/libhello.so");
                            System.loadLibrary("hello"); // ok
                        } catch (SecurityException ignore) {
                        } catch (UnsatisfiedLinkError ignore) {
                        } catch (NullPointerException ignore) {
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/Load.java:12: Warning: Dynamically loading code using load is risky, please use loadLibrary instead when possible [UnsafeDynamicallyLoadedCode]
                        Runtime.getRuntime().load("/data/data/test.pkg/files/libhello.so");
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Load.java:14: Warning: Dynamically loading code using load is risky, please use loadLibrary instead when possible [UnsafeDynamicallyLoadedCode]
                        System.load("/data/data/test.pkg/files/libhello.so");
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testNativeCode() {
        val bytesWithElfHeader = byteArrayOf(
            0x7F.toByte(),
            0x45.toByte(),
            0x4C.toByte(),
            0x46.toByte(),
            0
        )
        lint().files(
            bytes("res/raw/hello", bytesWithElfHeader),
            bytes("res/raw/libhello-jni.so", bytesWithElfHeader),
            bytes("assets/hello", bytesWithElfHeader),
            bytes("assets/libhello-jni.so", bytesWithElfHeader),
            bytes("lib/armeabi/hello", bytesWithElfHeader),
            bytes("lib/armeabi/libhello-jni.so", bytesWithElfHeader)
        ).run().expect(
            """
            assets/hello: Warning: Embedding non-shared library native executables into applications should be avoided when possible, as there is an increased risk that the executables could be tampered with after installation. Instead, native code should be placed in a shared library, and the features of the development environment should be used to place the shared library in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]
            res/raw/hello: Warning: Embedding non-shared library native executables into applications should be avoided when possible, as there is an increased risk that the executables could be tampered with after installation. Instead, native code should be placed in a shared library, and the features of the development environment should be used to place the shared library in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]
            assets/libhello-jni.so: Warning: Shared libraries should not be placed in the res or assets directories. Please use the features of your development environment to place shared libraries in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]
            res/raw/libhello-jni.so: Warning: Shared libraries should not be placed in the res or assets directories. Please use the features of your development environment to place shared libraries in the lib directory of the compiled APK. [UnsafeNativeCodeLocation]
            0 errors, 4 warnings
            """
        )
    }

    fun testNoWorkInInteractiveMode() {
        val bytesWithElfHeader = byteArrayOf(
            0x7F.toByte(),
            0x45.toByte(),
            0x4C.toByte(),
            0x46.toByte(),
            0
        )

        // Make sure we don't scan through all resource folders when just incrementally
        // editing a Java file
        lint().files(
            java(
                "src/test/pkg/Load.java",
                "package test.pkg;\npublic class Load { }\n"
            ),
            bytes("res/raw/hello", bytesWithElfHeader),
            bytes("res/raw/libhello-jni.so", bytesWithElfHeader),
            bytes("assets/hello", bytesWithElfHeader),
            bytes("assets/libhello-jni.so", bytesWithElfHeader),
            bytes("lib/armeabi/hello", bytesWithElfHeader),
            bytes("lib/armeabi/libhello-jni.so", bytesWithElfHeader)
        ).incremental("src/test/pkg/Load.java").run().expectClean()
    }
}
