/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.annotationprocessor

import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.collect.ImmutableList
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.function.Consumer

@RunWith(Parameterized::class)
class AutoServiceTest(private val pluginName: String) {
    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin(pluginName))
        .create()

    @Before
    fun addAutoService() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                    |dependencies {
                    |    compileOnly 'com.google.auto.service:auto-service:1.0-rc2'
                    |    annotationProcessor 'com.google.auto.service:auto-service:1.0-rc2'
                    |}
                    |""".trimMargin("|")
        )

        Files.write(
            project.file("src/main/java/com/example/helloworld/MyService.java").toPath(),
            listOf(
                "package com.example.helloworld;",
                "",
                "public interface MyService {",
                "    void doSomething();",
                "}",
                ""
            ),
            StandardCharsets.UTF_8
        )

        Files.write(
            project.file("src/main/java/com/example/helloworld/MyServiceImpl.java").toPath(),
            listOf(
                "package com.example.helloworld;",
                "",
                "@com.google.auto.service.AutoService(MyService.class)",
                "public class MyServiceImpl implements MyService {",
                "    public void doSomething() {}",
                "}",
                ""
            ),
            StandardCharsets.UTF_8
        )
    }

    @Test
    fun checkAutoServiceResourceIncluded() {
        project.executor().run("assembleDebug")
        if (pluginName == "com.android.application") {
            assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsJavaResource("META-INF/services/com.example.helloworld.MyService")
        } else {

            project.assertThatAar("debug") {
                containsJavaResource("META-INF/services/com.example.helloworld.MyService")
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun plugin(): List<String> {
            return ImmutableList.of("com.android.application", "com.android.library")
        }
    }
}

