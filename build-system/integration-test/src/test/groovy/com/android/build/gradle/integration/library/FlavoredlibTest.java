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

package com.android.build.gradle.integration.library;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.internal.DependencyManager;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.android.utils.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Assemble tests for flavoredlib.
 */
public class FlavoredlibTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("flavoredlib")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() {
        models = project.executeAndReturnMultiModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void lint() {
        project.execute("lint");
    }

    @Test
    public void checkExplodedAar() {
        File intermediates = FileUtils.join(project.getTestDir(), "app", "build", "intermediates");
        assertThat(intermediates).isDirectory();
        assertThat(new File(intermediates, DependencyManager.EXPLODED_AAR)).doesNotExist();
    }

    @Test
    public void testModel() {
        AndroidProject appModel = models.get(":app");
        assertThat(appModel).named("app model").isNotNull();

        assertThat(appModel.isLibrary()).named("App isLibrary()").isFalse();
        assertThat(appModel.getProjectType())
                .named("App Project Type")
                .isEqualTo(AndroidProject.PROJECT_TYPE_APP);

        Collection<Variant> variants = appModel.getVariants();
        Collection<ProductFlavorContainer> productFlavors = appModel.getProductFlavors();

        ProductFlavorContainer flavor1 = ModelHelper.getProductFlavor(productFlavors, "flavor1");
        assertThat(flavor1).named("flavor1 PFC").isNotNull();

        Variant flavor1Debug = ModelHelper.getVariant(variants, "flavor1Debug");
        assertThat(flavor1).named("flavor1 PFC").isNotNull();

        Dependencies dependencies = flavor1Debug.getMainArtifact().getCompileDependencies();
        assertThat(dependencies).named("flavor 1 deps").isNotNull();
        Collection<AndroidLibrary> libs = dependencies.getLibraries();
        assertThat(libs).named("flavor 1 android libs").isNotNull();
        assertThat(libs).named("flavor 1 android libs").hasSize(1);

        AndroidLibrary androidLibrary = libs.iterator().next();
        assertThat(androidLibrary).named("flavor 1 androidLib").isNotNull();
        assertThat(androidLibrary.getProject())
                .named("flavor 1 androidLib.getProject")
                .isEqualTo(":lib");
        assertThat(androidLibrary.getProjectVariant())
                .named("flavor 1 androidLib.getProjectVariant")
                .isEqualTo("flavor1Release");
        // check that the folder name is located inside the lib project's intermediate staging folder
        // reconstruct the path
        File staging = FileUtils.join(project.getTestDir(),
                "lib", "build", "intermediates", "bundles", "flavor1Release");
        assertThat(androidLibrary.getFolder())
                .named("flavor 1 androidLib.getFolder")
                .isEqualTo(staging);

        ProductFlavorContainer flavor2 = ModelHelper.getProductFlavor(productFlavors, "flavor2");
        assertThat(flavor2).named("flavor2 PFC").isNotNull();

        Variant flavor2Debug = ModelHelper.getVariant(variants, "flavor2Debug");
        assertThat(flavor2Debug).named("flavor2Debug variant").isNotNull();

        dependencies = flavor2Debug.getMainArtifact().getCompileDependencies();
        assertThat(dependencies).named("flavor 2 deps").isNotNull();
        libs = dependencies.getLibraries();
        assertThat(libs).named("flavor 2 android libs").isNotNull();
        assertThat(libs).named("flavor 2 android libs").hasSize(1);
        androidLibrary = libs.iterator().next();
        assertThat(androidLibrary).named("flavor 2 androidLib").isNotNull();
        assertThat(androidLibrary.getProject())
                .named("flavor 2 androidLib.getProject")
                .isEqualTo(":lib");
        assertThat(androidLibrary.getProjectVariant())
                .named("flavor 2 androidLib.getProjectVariant")
                .isEqualTo("flavor2Release");

        // check that the folder name is located inside the lib project's intermediate staging folder
        // reconstruct the path
        staging = FileUtils.join(project.getTestDir(),
                "lib", "build", "intermediates", "bundles", "flavor2Release");
        assertThat(androidLibrary.getFolder())
                .named("flavor 2 androidLib.getFolder")
                .isEqualTo(staging);
    }


    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() {
        project.executeConnectedCheck();
    }
}
