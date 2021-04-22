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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.packaging.ApkCreatorType
import com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER
import com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(FilterableParameterized::class)
class NoMappingTest(val apkCreatorType: ApkCreatorType) {

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestProject("minify").create()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(APK_Z_FILE_CREATOR, APK_FLINGER)
    }

    @Before
    fun updateGradleProperties() {
        TestFileUtils.appendToFile(project.gradlePropertiesFile, getGradleProperties())
    }

    @Test
    fun checkEmptyMapping() {
        File(project.projectDir, "proguard-rules.pro").appendText("\n-dontobfuscate")

        project.executor().run("assembleMinified")
        val mappingFile = project.file("build/outputs/mapping/minified/mapping.txt")
        assertThat(mappingFile).contains("com.android.tests.basic.Main -> com.android.tests.basic.Main")
    }

    private fun getGradleProperties() = when (apkCreatorType) {
        APK_Z_FILE_CREATOR -> "${BooleanOption.USE_NEW_APK_CREATOR.propertyName}=false"
        APK_FLINGER -> "${BooleanOption.USE_NEW_APK_CREATOR.propertyName}=true"
    }
}
