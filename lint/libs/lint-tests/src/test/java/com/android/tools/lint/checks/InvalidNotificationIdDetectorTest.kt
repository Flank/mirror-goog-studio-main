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

import com.android.tools.lint.detector.api.Detector

class InvalidNotificationIdDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return InvalidNotificationIdDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.app.Notification
                import android.app.Service

                private const val MY_ID = 0

                class ServiceTest {
                    fun test(service: Service, notification: Notification, unknownId: Int) {
                        service.startForeground(unknownId, notification) // OK: don't know
                        service.startForeground(-1, notification) // OK: valid id
                        service.startForeground(1, notification, 0) // OK: valid id
                        service.startForeground(0, notification) // ERROR 1: cannot be zero
                        service.startForeground(MY_ID, notification, 1) // ERROR 2: cannot be zero
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/ServiceTest.kt:13: Error: The notification id cannot be 0 [NotificationId0]
                    service.startForeground(0, notification) // ERROR 1: cannot be zero
                                            ~
            src/test/pkg/ServiceTest.kt:14: Error: The notification id cannot be 0 [NotificationId0]
                    service.startForeground(MY_ID, notification, 1) // ERROR 2: cannot be zero
                                            ~~~~~
            2 errors, 0 warnings
            """
        )
    }
}
