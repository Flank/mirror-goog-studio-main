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

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.builder.model.AndroidProject
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
/**
 * Test various options can be set without necessarily using it.
 */
@CompileStatic
public class ComponentDslTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .useExperimentalGradleVersion(true)
            .create();

    @Before
    public void setUp() {
        project.file("proguard.txt").createNewFile()
        project.buildFile << """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
        defaultConfig {
            minSdkVersion.apiLevel $GradleTestProject.SUPPORT_LIB_MIN_SDK
        }
        ndk {
            moduleName "hello-jni"
        }
        buildTypes {
            release {
                minifyEnabled false
                proguardFiles.add(file("proguard-rules.pro"))
                externalNativeBuild {
                    ndkBuild {
                        cFlags.addAll("-DCOLOR=RED")
                        abiFilters.addAll("x86", "x86_64")
                    }
                }
            }
        }
        productFlavors {
            create("f1") {
                dimension "foo"
                proguardFiles.add(file("proguard.txt"))
                buildConfigFields.create {
                    type "String"
                    name "foo"
                    value "\\"bar\\""
                }
            }
            create("f2") {
                dimension "foo"
            }
        }
    }
}

dependencies {
    compile 'com.android.support:appcompat-v7:$GradleTestProject.SUPPORT_LIB_VERSION'
}
"""
    }

    @Test
    public void assemble() {
        AndroidProject model = project.executeAndReturnModel("assemble").getOnlyModel();
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(project.name)
        assertThat(model.getBuildTypes()).hasSize(2)
        assertThat(model.getProductFlavors()).hasSize(2)
        assertThat(model.getVariants()).hasSize(4)
        assertThat(project.getApk("f1", "debug")).exists()
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() {
        project.executeConnectedCheck();
    }
}
