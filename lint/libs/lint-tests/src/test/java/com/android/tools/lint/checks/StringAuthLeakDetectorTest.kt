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

class StringAuthLeakDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return StringAuthLeakDetector()
    }

    fun testStringAuthLeak() {
        lint().files(
            java(
                """
                public class AuthDemo {
                  private static final String AUTH_IP = "scheme://user:pwd@127.0.0.1:8000"; // WARN 1
                  private static final String AUTH_NO_LEAK = "scheme://user:%s@www.google.com"; // OK 1
                  private static final String LEAK = "http://someuser:%restofmypass@example.com"; // WARN 2
                  private static final String URL = "http://%-05s@example.com"; // OK 2
                }
                """
            ).indented()
        ).run().expect(
            """
            src/AuthDemo.java:2: Warning: Possible credential leak [AuthLeak]
              private static final String AUTH_IP = "scheme://user:pwd@127.0.0.1:8000"; // WARN 1
                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/AuthDemo.java:4: Warning: Possible credential leak [AuthLeak]
              private static final String LEAK = "http://someuser:%restofmypass@example.com"; // WARN 2
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }
}
