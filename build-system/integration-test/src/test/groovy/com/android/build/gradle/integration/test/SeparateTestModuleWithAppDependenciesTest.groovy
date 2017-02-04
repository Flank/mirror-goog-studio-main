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

package com.android.build.gradle.integration.test

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class SeparateTestModuleWithAppDependenciesTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("separateTestModule")
            .withDependencyChecker(false)  // TODO: Fix for test plugin.
            .create()

    static ModelContainer<AndroidProject> models

    @BeforeClass
    static void setup() {
        project.getSubproject("app").getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion rootProject.latestCompileSdk
    buildToolsVersion = rootProject.buildToolsVersion

    publishNonDefault true

    defaultConfig {
        minSdkVersion 9
    }
}
dependencies {
    compile 'com.google.android.gms:play-services-base:$GradleTestProject.PLAY_SERVICES_VERSION'
    compile 'com.android.support:appcompat-v7:$GradleTestProject.SUPPORT_LIB_VERSION'
}
        """

        File srcDir = project.getSubproject("app").getMainSrcDir();
        srcDir = new File(srcDir, "foo");
        srcDir.mkdirs();
        new File(srcDir, "FooActivity.java") << """
package foo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

public class FooActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
"""


        project.getSubproject("test").getBuildFile() << """
dependencies {
    compile 'com.android.support.test:rules:$GradleTestProject.TEST_SUPPORT_LIB_VERSION'
    compile 'com.android.support:support-annotations:$GradleTestProject.SUPPORT_LIB_VERSION'
}
        """

        srcDir = project.getSubproject("test").getMainSrcDir();
        srcDir = new File(srcDir, "foo");
        srcDir.mkdirs();
        new File(srcDir, "FooActivityTest.java") << """
package foo;

public class FooActivityTest {
    @org.junit.Rule 
    android.support.test.rule.ActivityTestRule<foo.FooActivity> activityTestRule =
            new android.support.test.rule.ActivityTestRule<>(foo.FooActivity.class);
}
"""

        models = project.executeAndReturnMultiModel("clean", "test:assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check model"() throws Exception {
        // check the content of the test model.
    }
}
