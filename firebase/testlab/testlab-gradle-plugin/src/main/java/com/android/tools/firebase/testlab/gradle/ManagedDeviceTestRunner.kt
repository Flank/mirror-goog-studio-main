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

package com.android.tools.firebase.testlab.gradle

import com.android.build.api.dsl.Device
import com.android.build.api.instrumentation.StaticTestData
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.google.firebase.testlab.gradle.ManagedDevice
import java.io.File
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider

/**
 * A test runner implementation for Firebase TestLab Gradle Managed Device.
 */
class ManagedDeviceTestRunner(
    private val testLabBuildService: Provider<TestLabBuildService>
) : com.android.build.api.instrumentation.ManagedDeviceTestRunner {
    override fun runTests(
        managedDevice: Device,
        runId: String,
        outputDirectory: File,
        coverageOutputDirectory: File,
        additionalTestOutputDir: File?,
        projectPath: String,
        variantName: String,
        testData: StaticTestData,
        additionalInstallOptions: List<String>,
        helperApks: Set<File>,
        logger: Logger,
    ): Boolean {
        testLabBuildService.get().runTestsOnDevice(
            managedDevice as ManagedDevice,
            testData,
            outputDirectory,
        )
        // TODO: retrieve test results and return false when a test fails.
        return true
    }
}
