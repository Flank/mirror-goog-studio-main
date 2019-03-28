/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.gradle.internal.component.AmbiguousConfigurationSelectionException
import org.gradle.tooling.BuildException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class MisplacedMissingDimensionStrategyTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("projectWithModules").create()


    @Before
    fun setUp() {
        Files.asCharSink(project.settingsFile, Charsets.UTF_8)
            .write("include 'app', 'library'")

        // make the app depend on the library
        val appProject = project.getSubproject("app")
        appendToFile(
            appProject.buildFile,
            "\n"
                    + "dependencies {\n"
                    + "    implementation project(\":library\")\n"
                    + "}\n"
        )

        // make the library have flavors and misplace the missing dimension strategy
        val library = project.getSubproject("library")

        appendToFile(
            library.buildFile,
            "\n"
                    + "android {\n"
                    + "    defaultConfig {\n"
                    + "        missingDimensionStrategy 'libdim', 'foo'\n"
                    + "    }\n"
                    + "    flavorDimensions 'libdim'\n"
                    + "    productFlavors {\n"
                    + "        foo {\n"
                    + "            dimension 'libdim'\n"
                    + "        }\n"
                    + "        bar {\n"
                    + "            dimension 'libdim'\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
        )
    }

    @Test
    fun checkCorrectError() {
        val exception = assertFailsWith(BuildException::class) {
            project.executor().run(":app:assembleDebug")
        }

        exception.checkCause(AmbiguousConfigurationSelectionException::class.java)
    }
}

fun <T : Throwable> Exception.checkCause(causeClass: Class<T>) {
    val eName = causeClass.name
    var theCause: Throwable? = cause
    while (theCause != null) {
        // must compare fqcn as the actual class is coming via RMI and is not going to match the
        // one that is loaded in the test.
        if (theCause.javaClass.name == eName) {
            return
        }

        theCause = theCause.cause
    }

    throw RuntimeException("Not true that cause is of type $causeClass", this)
}
