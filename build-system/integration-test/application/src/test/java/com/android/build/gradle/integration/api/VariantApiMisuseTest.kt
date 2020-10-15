/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class VariantApiMisuseTest {
    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun noWiringTest() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
}

abstract class SomeTask extends DefaultTask {

    abstract DirectoryProperty getInputDir()

    @TaskAction
    void taskAction() {}
}

androidComponents {
    onVariants(selector().all(), {
      TaskProvider outputTask = tasks.register(it.getName() + 'SomeTask', SomeTask) { task ->
        task.getVariantName().set(it.getName())
      }
      artifacts.use(outputTask)
    })
}
""")
        val result = project.executor().expectFailure().run("clean", "debugSomeTask")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "Task debugSomeTask was passed to Artifacts::use method without wiring any input " +
                    "and/or output to an artifact."
            )
        }
    }

    @Test
    fun wiredOutputWithoutOperation() {
        TestFileUtils.appendToFile(
                project.buildFile,
                """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
}

abstract class SomeTask extends DefaultTask {

    abstract DirectoryProperty getOutputDir()

    void taskAction() { }
}

androidComponents {
    onVariants(selector().all(), {
          TaskProvider outputTask = tasks.register(it.getName() + 'SomeTask', SomeTask) { task ->
            task.getVariantName().set(it.getName())
          }
          artifacts.use(outputTask).wiredWith({ it.getOutputDir })
    })
}
""")
        val result = project.executor().expectFailure().run("clean", "debugSomeTask")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                    "was wired with an output but neither toAppend or toCreate methods were " +
                            "invoked."
            )
        }
    }

    @Test
    fun wiredInputAndOutputDirectoriesWithoutTransform() {
        TestFileUtils.appendToFile(
                project.buildFile,
                """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
}

abstract class SomeTask extends DefaultTask {

    abstract DirectoryProperty getInputDir()

    abstract DirectoryProperty getOutputDir()

    void taskAction() {}
}

androidComponents {
    onVariants(selector().all(), {
          TaskProvider outputTask = tasks.register(it.getName() + 'SomeTask', SomeTask) { task ->
            task.getVariantName().set(it.getName())
          }
          artifacts.use(outputTask).wiredWithDirectories({ it.getInputDir }, { it.getOutputDir })
    })
}
""")
        val result = project.executor().expectFailure().run("clean", "debugSomeTask")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                    "was wired with an Input and an Output but " +
                            "toTransform or toTransformMany methods were never invoked"
            )
        }
    }

    @Test
    fun wiredInputAndOutputFilesWithoutTransform() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
}

abstract class SomeTask extends DefaultTask {

    abstract RegularFileProperty getInputFile()

    abstract RegularFileProperty getOutputFile()

    void taskAction() { }
}

androidComponents {
    onVariants(selector().all(), {
      TaskProvider outputTask = tasks.register(it.getName() + 'SomeTask', SomeTask) { task ->
        task.getVariantName().set(it.getName())
      }
      artifacts.use(outputTask).wiredWithFiles({ it.getInputFile }, { it.getOutputFile })
    })
}
""")
        val result = project.executor().expectFailure().run("clean", "debugSomeTask")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "was wired with an Input and an Output but " +
                        "toTransform method was never invoked"
            )
        }
    }

    @Test
    fun wiredInputAndOutputWithoutCombine() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
}

abstract class SomeTask extends DefaultTask {

    abstract ListProperty<Directory> getInputDira()

    abstract DirectoryProperty getOutputDir()

    void taskAction() { }
}

androidComponents {
    onVariants(selector().all(), {
          TaskProvider outputTask = tasks.register(it.getName() + 'SomeTask', SomeTask) { task ->
            task.getVariantName().set(it.getName())
          }
          artifacts.use(outputTask).wiredWith({ it.getInputDirs }, { it.getOutputDir })
    })
}
""")
        val result = project.executor().expectFailure().run("clean", "debugSomeTask")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "was wired to combine multiple inputs into an output but " +
                        "toTransform method was never invoked"
            )
        }
    }

    @Test
    fun beforeVariantsActionAddedFromOldVariantApiBlock() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
}

android {
    applicationVariants.all {
        androidComponents.beforeVariants(androidComponents.selector().all(), {
            System.out.println("This should not execute !")
        })
    }
}
""")
        val result = project.executor().expectFailure().run("clean", "assembleDebug")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "It is too late to add actions as the callbacks already executed."
            )
        }
    }

    @Test
    fun onVariantsActionAddedFromOldVariantApiBlock() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
}

android {
    applicationVariants.all {
        androidComponents.onVariants(androidComponents.selector().all(), {
            System.out.println("This should not execute !")
        })
    }
}
""")
        val result = project.executor().expectFailure().run("clean", "assembleDebug")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "It is too late to add actions as the callbacks already executed."
            )
        }
    }

}
