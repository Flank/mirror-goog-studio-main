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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Test AndroidComponentModelPlugin.
 */
@CompileStatic
class AndroidComponentPluginTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .useExperimentalGradleVersion(true)
            .create();

    @Test
    public void assemble() {

        project.buildFile << """
import com.android.build.gradle.model.AndroidComponentModelPlugin
apply plugin: AndroidComponentModelPlugin

model {
    android {
        buildTypes {
            create("custom")
        }
        productFlavors {
            create("flavor1")
            create("flavor2")
        }
    }
}
"""
        project.execute("assemble")
    }

    @Test
    public void multiFlavor() {
        project.buildFile << """
import com.android.build.gradle.model.AndroidComponentModelPlugin
apply plugin: AndroidComponentModelPlugin

model {
    android {
        productFlavors {
            create("free") {
                dimension "cost"
            }
            create("premium") {
                dimension "cost"
            }
            create("blue") {
                dimension "color"
            }
            create("red") {
                dimension "color"
            }
        }
    }
}
"""

        // Gradle creates a task for each binary in the form <component_name><flavor><buildType>.
        // <component_name> is "android".
        List<String> tasks = project.model().getTaskList();
        assertThat(tasks).containsAllOf(
                "androidBlueFreeDebug",
                "androidBluePremiumDebug",
                "androidRedFreeDebug",
                "androidRedPremiumDebug",
                "androidBlueFreeRelease",
                "androidBluePremiumRelease",
                "androidRedFreeRelease",
                "androidRedPremiumRelease")
    }

    @Test
    public void checkFlavorOrder() {
        project.buildFile << """
import com.android.build.gradle.model.AndroidComponentModelPlugin
apply plugin: AndroidComponentModelPlugin

model {
    android {
        productFlavors {
            create("e") {
                dimension "e"
            }
            create("a") {
                dimension "a"
            }
            create("d") {
                dimension "d"
            }
            create("b") {
                dimension "b"
            }
            create("c") {
                dimension "c"
            }
        }
    }
}
"""
        List<String> tasks = project.model().getTaskList();
        assertThat(tasks).containsAllOf(
                "androidABCDEDebug",
                "androidABCDERelease")
    }

}
