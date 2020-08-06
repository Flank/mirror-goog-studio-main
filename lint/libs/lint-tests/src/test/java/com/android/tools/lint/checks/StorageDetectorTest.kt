/*
 * Copyright (C) 2018 The Android Open Source Project
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

class StorageDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector {
        return StorageDetector()
    }

    fun testWrong() {
        val expected =
            """
            src/test/pkg/StorageTest.java:8: Warning: Consider also using StorageManager#getAllocatableBytes and allocateBytes which will consider clearable cached data [UsableSpace]
                    return file.getUsableSpace();
                                ~~~~~~~~~~~~~~
            src/test/pkg/test.kt:8: Warning: Consider also using StorageManager#getAllocatableBytes and allocateBytes which will consider clearable cached data [UsableSpace]
                return file.usableSpace
                            ~~~~~~~~~~~
            0 errors, 2 warnings
            """

        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Build
                import android.os.storage.StorageManager
                import java.io.File

                fun getFreeSpace(file: File): Long {
                    return file.usableSpace
                }

                """
            ).indented(),
            java(
                """
                package test.pkg;

                import java.io.File;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class StorageTest {
                    public long getFreeSpace(File file) {
                        return file.getUsableSpace();
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testOk() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Build
                import android.os.storage.StorageManager
                import java.io.File

                fun getFreeSpace(file: File, manager: StorageManager): Long {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val uuid = manager.getUuidForPath(file)
                        manager.getAllocatableBytes(uuid)
                    } else {
                        file.usableSpace
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import android.os.storage.StorageManager;

                import java.io.File;
                import java.io.IOException;
                import java.util.UUID;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "RedundantThrows"})
                public class StorageTest {
                    public long getFreeSpace(File file, StorageManager manager) throws IOException {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            UUID uuid = manager.getUuidForPath(file);
                            return manager.getAllocatableBytes(uuid);
                        } else {
                            return file.getUsableSpace();
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }
}
