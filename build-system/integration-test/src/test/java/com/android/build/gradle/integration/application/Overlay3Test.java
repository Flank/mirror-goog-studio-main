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

package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ImageHelper;
import com.android.builder.model.AndroidProject;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for overlay3. */
@RunWith(Parameterized.class)
public class Overlay3Test {

    @Parameterized.Parameters(name = "enableAapt2 = {0}")
    public static Collection<Boolean> data() {
        return ImmutableList.of(false, true);
    }

    @Parameterized.Parameter
    public boolean useAapt2;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("overlay3").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkImageColor() throws IOException, InterruptedException {
        project.executor().withEnabledAapt2(useAapt2).run("clean", "assembleDebug");

        // "no_overlay.png" consists of only green pixels, all others only of red pixels.
        int GREEN = ImageHelper.GREEN;
        int RED = ImageHelper.RED;

        if (!useAapt2) {
            File drawableOutput =
                    project.file(
                            "build/"
                                    + AndroidProject.FD_INTERMEDIATES
                                    + "/res/merged/freeBeta/debug/drawable");

            // First image should remain unchanged, all images in free beta variants should be
            // overlaid with the first image (first pixel turns from red to green). Others should
            // not be changed (remain red).
            ImageHelper.checkImageColor(drawableOutput, "no_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "beta_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "free_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "free_beta_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "free_beta_debug_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "free_normal_overlay.png", RED);

            drawableOutput =
                    project.file(
                            "build/"
                                    + AndroidProject.FD_INTERMEDIATES
                                    + "/res/merged/freeNormal/debug/drawable");

            // First image should remain unchanged, all images in free normal variants should be
            // overlaid with the first image (first pixel turns from red to green). Others should
            // not be changed (remain red).
            ImageHelper.checkImageColor(drawableOutput, "no_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "beta_overlay.png", RED);
            ImageHelper.checkImageColor(drawableOutput, "free_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "free_beta_overlay.png", RED);
            ImageHelper.checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED);
            ImageHelper.checkImageColor(drawableOutput, "free_normal_overlay.png", GREEN);

            drawableOutput =
                    project.file(
                            "build/"
                                    + AndroidProject.FD_INTERMEDIATES
                                    + "/res/merged/paidBeta/debug/drawable");

            // First image should remain unchanged, all images in paid beta variants should be
            // overlaid with the first image (first pixel turns from red to green). Others should
            // not be changed (remain red).
            ImageHelper.checkImageColor(drawableOutput, "no_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "beta_overlay.png", GREEN);
            ImageHelper.checkImageColor(drawableOutput, "free_overlay.png", RED);
            ImageHelper.checkImageColor(drawableOutput, "free_beta_overlay.png", RED);
            ImageHelper.checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED);
            ImageHelper.checkImageColor(drawableOutput, "free_normal_overlay.png", RED);
        } else {
            // Only check that the intermediate files exist. We verify their correctness later, by
            // checking the contents of the APK.

            File resOutput =
                    project.file(
                            "build/"
                                    + AndroidProject.FD_INTERMEDIATES
                                    + "/res/merged/freeBeta/debug");
            assertThat(new File(resOutput, "drawable_no_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_debug_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_beta_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_beta_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_beta_debug_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_normal_overlay.png.flat")).exists();

            resOutput =
                    project.file(
                            "build/"
                                    + AndroidProject.FD_INTERMEDIATES
                                    + "/res/merged/freeNormal/debug");
            assertThat(new File(resOutput, "drawable_no_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_debug_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_beta_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_beta_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_beta_debug_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_normal_overlay.png.flat")).exists();

            resOutput =
                    project.file(
                            "build/"
                                    + AndroidProject.FD_INTERMEDIATES
                                    + "/res/merged/paidBeta/debug");
            assertThat(new File(resOutput, "drawable_no_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_debug_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_beta_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_beta_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_beta_debug_overlay.png.flat")).exists();
            assertThat(new File(resOutput, "drawable_free_normal_overlay.png.flat")).exists();
        }

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "free", "beta");
        assertThat(apk).isNotNull();
        // First image should remain unchanged, all images in free beta variants should be overlaid
        // with the first image (first pixel turns from red to green). Others should not be changed
        // (remain red).
        ImageHelper.checkImageColor(apk.getResource("drawable/no_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/debug_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/beta_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_beta_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_beta_debug_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_normal_overlay.png"), RED);

        apk = project.getApk(GradleTestProject.ApkType.DEBUG, "free", "normal");
        assertThat(apk).isNotNull();
        // First image should remain unchanged, all images in free normal variants should be
        // overlaid with the first image (first pixel turns from red to green). Others should not be
        // changed (remain red).
        ImageHelper.checkImageColor(apk.getResource("drawable/no_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/debug_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/beta_overlay.png"), RED);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_beta_overlay.png"), RED);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_beta_debug_overlay.png"), RED);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_normal_overlay.png"), GREEN);

        apk = project.getApk(GradleTestProject.ApkType.DEBUG, "paid", "beta");
        assertThat(apk).isNotNull();
        // First image should remain unchanged, all images in paid beta variants should be overlaid
        // with the first image (first pixel turns from red to green). Others should not be changed
        // (remain red).
        ImageHelper.checkImageColor(apk.getResource("drawable/no_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/debug_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/beta_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_overlay.png"), RED);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_beta_overlay.png"), RED);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_beta_debug_overlay.png"), RED);
        ImageHelper.checkImageColor(apk.getResource("drawable/free_normal_overlay.png"), RED);
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
