package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test BaseExtension.getBootClasspath can be use before afterEvaluate.  */
class BootClasspathTest {

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """|
               |apply plugin: 'com.android.application'
               |
               |android {
               |    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
               |
               |    buildToolsVersion '${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}'
               |}
               |
               |task checkBootClasspath {
               |    assert android.getBootClasspath() != null
               |}""".trimMargin("|")
        )
    }

    @Test
    fun checkBootClasspathCanBeCalled() {
        project.execute("checkBootClasspath")
    }
}
