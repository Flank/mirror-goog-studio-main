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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.internal.DependencyManager;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Assemble tests for flavorlib.
 */
public class FlavorlibTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("flavorlib")
            .create();
    public static GetAndroidModelAction.ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() {
        modelContainer = project.executeAndReturnMultiModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void lint() {
        project.execute("lint");
    }

    @Test
    public void report() {
        project.execute("androidDependencies", "signingReport");
    }

    @Test
    public void checkExplodedAar() {
        File intermediates = FileUtils.join(project.getTestDir(), "app", "build", "intermediates");
        assertThat(intermediates).isDirectory();
        assertThat(new File(intermediates, DependencyManager.EXPLODED_AAR)).doesNotExist();
    }

    @Test
    public void testModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        Map<String, AndroidProject> models = modelContainer.getModelMap();

        AndroidProject appModel = models.get(":app");
        assertThat(appModel).named("app model").isNotNull();

        assertThat(appModel.isLibrary()).named("App isLibrary()").isFalse();
        assertThat(appModel.getProjectType()).named("App Project Type").isEqualTo(AndroidProject.PROJECT_TYPE_APP);

        Collection<Variant> variants = appModel.getVariants();
        Collection<ProductFlavorContainer> productFlavors = appModel.getProductFlavors();

        ProductFlavorContainer flavor1 = ModelHelper.getProductFlavor(productFlavors, "flavor1");

        Variant flavor1Debug = ModelHelper.getVariant(variants, "flavor1Debug");
        assertThat(flavor1Debug).named("flavor1Debug variant").isNotNull();

        DependencyGraphs flavor1Graph = flavor1Debug.getMainArtifact().getDependencyGraphs();
        assertThat(flavor1Graph).named("flavor 1 graph").isNotNull();
        assertThat(helper.on(flavor1Graph).withType(MODULE).mapTo(GRADLE_PATH))
                .named("flavor 1 android lib deps")
                .containsExactly(":lib1");

        ProductFlavorContainer flavor2 = ModelHelper.getProductFlavor(productFlavors, "flavor2");

        Variant flavor2Debug = ModelHelper.getVariant(variants, "flavor2Debug");
        assertThat(flavor2Debug).named("flavor2Debug variant").isNotNull();

        DependencyGraphs flavor2Graph = flavor2Debug.getMainArtifact().getDependencyGraphs();
        assertThat(flavor2Graph).named("flavor 2 graph").isNotNull();
        assertThat(helper.on(flavor2Graph).withType(MODULE).mapTo(GRADLE_PATH))
                .named("flavor 2 android lib deps")
                .containsExactly(":lib2");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() {
        project.executeConnectedCheck();
    }
}
