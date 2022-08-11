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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.model.NativeBuildBenchmarkProject
import com.android.build.gradle.internal.cxx.configure.ConfigureType
import com.android.build.gradle.internal.cxx.configure.decodeConfigureInvalidationState
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests that probe abseil build benchmark so that issues might be caught earlier
 * than the next perfgate run.
 */
@RunWith(Parameterized::class)
class AbseilTest(
    private val setupDiff : String
) {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data() = arrayOf(
            "notNoop.diff",
        )
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var project: NativeBuildBenchmarkProject

    @Before
    fun before() {
        project = NativeBuildBenchmarkProject(
            relativeBuildRoot = "android",
            workingFolder = temporaryFolder.newFolder(),
            buildbenchmark = "abseil-cpp.731689ff",
            setupDiff = setupDiff
        )
    }

    @Test
    fun `simulate Abseil_cleanBuild`() : Unit = with(project) {
        addArgument("-Pandroid.injected.build.abi=arm64-v8a")

        // Warm up
        run("assembleDebug")
        run("clean")

        // Actual test
        enableCxxStructuredLogging()
        run("assembleDebug")

        // Expect no C/C++ configure
        val configure = readStructuredLogs(::decodeConfigureInvalidationState).single()
        assertThat(configure.configureType)
            .named("$configure")
            .isEqualTo(ConfigureType.NO_CONFIGURE)
    }
}
