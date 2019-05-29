/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import org.junit.Assert.fail

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import org.junit.Rule
import org.junit.Test

/** Assemble tests for androidManifestInTest.  */
class AndroidManifestInTestTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder().fromTestProject("androidManifestInTest").create()

    @Test
    fun testUserProvidedTestAndroidManifest() {
        project.execute("assembleDebugAndroidTest")
        val testApk = project.testApk

        val output = ApkSubject.getManifestContent(testApk.file)

        var foundPermission = false
        var foundMetadata = false
        var isDebuggable = false
        for (line in output) {
            if (line.contains("foo.permission-group.COST_MONEY")) {
                foundPermission = true
            }
            if (line.contains("meta-data")) {
                foundMetadata = true
            }
            if (line.contains("android:debuggable")) {
                isDebuggable = true
            }
        }
        if (!foundPermission) {
            fail("Could not find user-specified permission group.")
        }
        if (!foundMetadata) {
            fail("Could not find meta-data under instrumentation ")
        }
        if (!isDebuggable) {
            fail("Generated apk is not debuggable ")
        }
    }
}
