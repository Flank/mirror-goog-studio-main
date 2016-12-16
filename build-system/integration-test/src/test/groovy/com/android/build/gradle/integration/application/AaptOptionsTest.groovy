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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.testutils.apk.Apk
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.utils.TestFileUtils.searchAndReplace
import static com.android.utils.FileUtils.createFile
/**
 * General Model tests
 */
@CompileStatic
class AaptOptionsTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin('com.android.application'))
            .create()

    @Before
    public void setUp() {
        createFile(project.file("src/main/res/raw/ignored"), "ignored")
        createFile(project.file("src/main/res/raw/kept"), "kept")
    }

    @Test
    public void "test aaptOptions flags"() {
        project.getBuildFile() << """
android {
    aaptOptions {
        additionalParameters "--ignore-assets", "!ignored*"
    }
}
"""
        project.execute("clean", "assembleDebug")
        Apk apk = project.getApk("debug")
        assertThat(apk).containsFileWithContent("res/raw/kept", "kept")
        assertThat(apk).doesNotContain("res/raw/ignored")

        createFile(project.file("src/main/res/raw/ignored2"), "ignored2")
        createFile(project.file("src/main/res/raw/kept2"), "kept2")

        project.execute("assembleDebug")
        apk = project.getApk("debug")
        assertThat(apk).containsFileWithContent("res/raw/kept2", "kept2")
        assertThat(apk).doesNotContain("res/raw/ignored2")

        searchAndReplace(
                project.buildFile,
                'additionalParameters "--ignore-assets", "!ignored\\*"',
                "")

        project.execute("assembleDebug")
        apk = project.getApk("debug")
        assertThat(apk).containsFileWithContent("res/raw/kept", "kept")
        assertThat(apk).containsFileWithContent("res/raw/ignored", "ignored")
        assertThat(apk).containsFileWithContent("res/raw/kept2", "kept2")
        assertThat(apk).containsFileWithContent("res/raw/ignored2", "ignored2")
    }
}
