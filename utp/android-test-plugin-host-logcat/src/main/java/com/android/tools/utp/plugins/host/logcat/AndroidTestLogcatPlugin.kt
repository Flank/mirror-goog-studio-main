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
package com.android.tools.utp.plugins.host.logcat

import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult

/**
 * A plugin to write logcat in test results.
 */
class AndroidTestLogcatPlugin : HostPlugin {
    override fun configure(config: Config) {}

    override fun beforeAll(deviceController: DeviceController) {}

    override fun beforeEach(
            testCase: TestCase?,
            deviceController: DeviceController
    ) {}

    override fun afterEach(
            testResult: TestResult,
            deviceController: DeviceController
    ): TestResult = testResult

    override fun afterAll(
            testSuiteResult: TestSuiteResult,
            deviceController: DeviceController
    ): TestSuiteResult = testSuiteResult

    override fun canRun(): Boolean = true

    override fun cancel(): Boolean = false
}
