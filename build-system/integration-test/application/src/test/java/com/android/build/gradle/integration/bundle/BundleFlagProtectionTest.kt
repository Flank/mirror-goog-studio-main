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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.gradle.tooling.BuildException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class BundleFlagProtectionTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withoutNdk()
        .create()

    @get:Rule
    val exception = ExpectedException.none()

    @Test
    fun `bundle task is missing`() {
        exception.expect(BuildException::class.java)
        project.execute("bundleDebug")
    }

    @Test
    fun `makeApkFromBundle task is missing`() {
        exception.expect(BuildException::class.java)
        project.execute("makeApkFromBundleForDebug")
    }

    @Test
    fun `selectApks task is missing`() {
        exception.expect(BuildException::class.java)
        project.execute("selectApksForDebug")
    }
}