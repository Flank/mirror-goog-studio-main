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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.category.SmokeTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar

/**
 * Basic integration test for LibraryComponentModelPlugin.
 */
@Category(SmokeTests.class)
@CompileStatic
class LibraryComponentPluginTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldLibraryApp())
            .useExperimentalGradleVersion(true)
            .create();


    @Test
    void "check build config file is included"() {
        project.getSubproject("app").buildFile << """
apply plugin: "com.android.model.application"

dependencies {
    compile project(":lib")
}

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""

        project.getSubproject("lib").buildFile << """
apply plugin: "com.android.model.library"

dependencies {
    /* Depend on annotations to trigger the creation of the ExtractAnnotations task */
    compile 'com.android.support:support-annotations:$GradleTestProject.SUPPORT_LIB_VERSION'
}

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""
        project.execute("assemble")
        File releaseAar = project.getSubproject("lib").getAar("release");
        assertThatAar(releaseAar).containsClass("Lcom/example/helloworld/BuildConfig;");
    }

    @Test
    void "check multi flavor dependencies"() {
        project.getSubproject("app").buildFile << """
apply plugin: "com.android.model.application"

dependencies {
    compile project(path: ":lib", configuration: "freeDebug")
}

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""

        project.getSubproject("lib").buildFile << """
apply plugin: "com.android.model.library"

configurations {
    freeDebug
}

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
        publishNonDefault true

        productFlavors {
            create("free")
            create("premium")
        }
    }
}
"""
        project.execute(":app:assembleDebug")
        assertThat(project.getSubproject("lib").file("build/intermediates/bundles/freeDebug")).isDirectory()
    }
}
