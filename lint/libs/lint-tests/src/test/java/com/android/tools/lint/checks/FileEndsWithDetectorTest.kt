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

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class FileEndsWithDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return FileEndsWithDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                import java.io.File

                // This does not work -- it will return false for "foo/bar.xml", but true for "foo/.xml"
                fun File.isXml() = endsWith(".xml")

                // This does not work -- the extension property does not include the leading dot
                fun File.isJson() = extension == ".json"
                fun isWebp(path: File) = path.extension.startsWith(".webp")

                fun File.isText() = path.endsWith(".txt") // OK
                fun File.isPng() = extension == "png" // OK
                """
            ).indented()
        ).testModes(TestMode.DEFAULT).run().expect(
            """
            src/test.kt:4: Warning: File.endsWith compares whole filenames, not just file extensions; did you mean file.path.endsWith(".xml") ? [FileEndsWithExt]
            fun File.isXml() = endsWith(".xml")
                               ~~~~~~~~~~~~~~~~
            src/test.kt:7: Warning: File.extension does not include the leading dot; did you mean "json" ? [FileEndsWithExt]
            fun File.isJson() = extension == ".json"
                                              ~~~~~
            src/test.kt:8: Warning: File.extension does not include the leading dot; did you mean "webp" ? [FileEndsWithExt]
            fun isWebp(path: File) = path.extension.startsWith(".webp")
                                                                ~~~~~
            0 errors, 3 warnings
            """
        )
    }

    // Also test having a different variable name (not file extension) and some false positives for non-extension strings, long strings, strings that contain spaces, etc

    fun testFalsePositives() {
        lint().files(
            kotlin(
                """
                import java.io.File

                fun File.isXml1() = endsWith("xml") // OK 1
                fun File.isXml2() = endsWith(". . .") // OK 2
                fun File.isXml3() = endsWith("../foo") // OK 3
                fun File.isJson() = extension == "json" // OK 4
                fun isWebp(path: File) = path.extension.startsWith("webp") // OK 5
                """
            ).indented()
        ).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testFalseNegatives() {
        lint().files(
            kotlin(
                """
                import java.io.File

                val EXT = ".xml"
                fun File.isXml1() = endsWith(EXT) // ERROR 1
                fun isPng(path: File) = path.endsWith(".webp") // ERROR 2
                fun isPng(path: File) = path.parentFile?.endsWith(".webp") // ERROR 3
                fun isWebp1(path: File) = path.parentFile.extension.startsWith(".webp") // ERROR 4
                fun isWebp2(path: File) = path.parentFile.parentFile.extension.startsWith(".webp") // ERROR 5
                fun isWebp3(path: File) = path.parentFile!!.extension.startsWith(".webp") // ERROR 6
                """
            ).indented()
        ).testModes(TestMode.DEFAULT).run().expect(
            """
            src/test.kt:4: Warning: File.endsWith compares whole filenames, not just file extensions; did you mean file.path.endsWith(".xml") ? [FileEndsWithExt]
            fun File.isXml1() = endsWith(EXT) // ERROR 1
                                ~~~~~~~~~~~~~
            src/test.kt:5: Warning: File.endsWith compares whole filenames, not just file extensions; did you mean path.path.endsWith(".webp") ? [FileEndsWithExt]
            fun isPng(path: File) = path.endsWith(".webp") // ERROR 2
                                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test.kt:6: Warning: File.endsWith compares whole filenames, not just file extensions; did you mean file.path.endsWith(".webp") ? [FileEndsWithExt]
            fun isPng(path: File) = path.parentFile?.endsWith(".webp") // ERROR 3
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test.kt:7: Warning: File.extension does not include the leading dot; did you mean "webp" ? [FileEndsWithExt]
            fun isWebp1(path: File) = path.parentFile.extension.startsWith(".webp") // ERROR 4
                                                                            ~~~~~
            src/test.kt:8: Warning: File.extension does not include the leading dot; did you mean "webp" ? [FileEndsWithExt]
            fun isWebp2(path: File) = path.parentFile.parentFile.extension.startsWith(".webp") // ERROR 5
                                                                                       ~~~~~
            src/test.kt:9: Warning: File.extension does not include the leading dot; did you mean "webp" ? [FileEndsWithExt]
            fun isWebp3(path: File) = path.parentFile!!.extension.startsWith(".webp") // ERROR 6
                                                                              ~~~~~
            0 errors, 6 warnings
            """
        )
    }
}
