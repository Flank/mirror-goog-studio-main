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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.Variant;

import org.gradle.api.JavaVersion;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Assemble tests for basic.
 */
@Category(SmokeTests.class)
public class BasicTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .withoutNdk()
            .create();

    @Rule
    public Adb adb = new Adb();

    public static AndroidProject model;

    @BeforeClass
    public static void getModel() {
        model = project.executeAndReturnModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void report() {
        project.execute("androidDependencies");
    }

    @Test
    public void basicModel() {
        assertFalse("Library Project", model.isLibrary());
        assertEquals(
                "Compile Target",
                "android-" + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION,
                model.getCompileTarget());
        assertFalse("Non empty bootclasspath", model.getBootClasspath().isEmpty());

        assertNotNull("aaptOptions not null", model.getAaptOptions());
        assertEquals("aaptOptions noCompress", 1, model.getAaptOptions().getNoCompress().size());
        assertTrue("aaptOptions noCompress",
                model.getAaptOptions().getNoCompress().contains("txt"));
        assertEquals(
                "aaptOptions ignoreAssetsPattern",
                "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~",
                model.getAaptOptions().getIgnoreAssets());
        assertFalse(
                "aaptOptions getFailOnMissingConfigEntry",
                model.getAaptOptions().getFailOnMissingConfigEntry());

        // Since source and target compatibility are not explicitly set in the build.gradle,
        // the default value depends on the JDK used.
        JavaVersion expected;
        if (JavaVersion.current().isJava7Compatible()) {
            expected = JavaVersion.VERSION_1_7;
        } else {
            expected = JavaVersion.VERSION_1_6;
        }

        JavaCompileOptions javaCompileOptions = model.getJavaCompileOptions();
        assertEquals(
                expected.toString(),
                javaCompileOptions.getSourceCompatibility());
        assertEquals(
                expected.toString(),
                javaCompileOptions.getTargetCompatibility());
        assertEquals("UTF-8", javaCompileOptions.getEncoding());
    }

    @Test
    public void sourceProvidersModel() {
        ModelHelper.testDefaultSourceSets(model, project.getTestDir());

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact();
            assertNull(artifact.getVariantSourceProvider());
            assertNull(artifact.getMultiFlavorSourceProvider());
        }
    }

    @Test
    public void checkDebugAndReleaseOutputHaveDifferentNames() {
        ModelHelper.compareDebugAndReleaseOutput(model);
    }

    @Test
    public void weDontFailOnLicenceDotTxtWhenPackagingDependencies() {
        project.execute("assembleAndroidTest");
    }

    @Test
    public void generationInModel() {
        AndroidProject model = project.model().getSingle();
        assertThat(model.getPluginGeneration())
                .named("Plugin Generation")
                .isEqualTo(AndroidProject.GENERATION_ORIGINAL);
    }

    @Test
    @Category(DeviceTests.class)
    public void install() throws IOException {
        adb.exclusiveAccess();
        project.execute("installDebug", "uninstallAll");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() {
        project.executeConnectedCheck();
    }
}
