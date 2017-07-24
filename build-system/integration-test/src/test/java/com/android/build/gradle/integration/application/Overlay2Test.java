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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ImageHelper
import com.android.builder.model.AndroidProject
import com.android.testutils.apk.Apk
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.testutils.truth.MoreTruth.assertThat

/**
 * Assemble tests for overlay2.
 */
@CompileStatic
@RunWith(Parameterized.class)
class Overlay2Test {
    @Parameterized.Parameters(name = "enableAapt2 = {0}")
    public static Collection<Object> data() {
        return [(Object) false, (Object) true];
    }

    @Parameterized.Parameter
    public boolean useAapt2;

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("overlay2")
            .create()

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check image color"() {
        project.executor().withEnabledAapt2(useAapt2).run("clean", "assembleDebug")

        int GREEN = ImageHelper.GREEN

        if (!useAapt2) {
            File drawableOutput = project.file(
                    "build/" + AndroidProject.FD_INTERMEDIATES + "/res/merged/one/debug/drawable")

            //First picture should remain unchanged (first pixel remains green), while all the
            //others should have the first image overlay them (first pixel turns from red to green).
            ImageHelper.checkImageColor(drawableOutput, "no_overlay.png", GREEN)
            ImageHelper.checkImageColor(drawableOutput, "type_overlay.png", GREEN)
            ImageHelper.checkImageColor(drawableOutput, "flavor_overlay.png", GREEN)
            ImageHelper.checkImageColor(drawableOutput, "type_flavor_overlay.png", GREEN)
            ImageHelper.checkImageColor(drawableOutput, "variant_type_flavor_overlay.png", GREEN)
        } else {
            File resOutput = project.
                    file("build/" + AndroidProject.FD_INTERMEDIATES + "/res/merged/one/debug")
            assertThat(new File(resOutput, "drawable_no_overlay.png.flat")).exists()
            assertThat(new File(resOutput, "drawable_type_overlay.png.flat")).exists()
            assertThat(new File(resOutput, "drawable_flavor_overlay.png.flat")).exists()
            assertThat(new File(resOutput, "drawable_type_flavor_overlay.png.flat")).exists()
            assertThat(
                    new File(resOutput, "drawable_variant_type_flavor_overlay.png.flat")).exists()
        }

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "one")
        //First picture should remain unchanged (first pixel remains green), while all the
        //others should have the first image overlay them (first pixel turns from red to green).
        ImageHelper.checkImageColor(apk.getResource("drawable/no_overlay.png"), GREEN)
        ImageHelper.checkImageColor(apk.getResource("drawable/type_overlay.png"), GREEN)
        ImageHelper.checkImageColor(apk.getResource("drawable/flavor_overlay.png"), GREEN)
        ImageHelper.checkImageColor(apk.getResource("drawable/type_flavor_overlay.png"), GREEN)
        ImageHelper.checkImageColor(
                apk.getResource("drawable/variant_type_flavor_overlay.png"), GREEN)
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
