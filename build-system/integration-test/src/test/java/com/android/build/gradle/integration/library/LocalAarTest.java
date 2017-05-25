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

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for localAarTest. */
public class LocalAarTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("localAarTest").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void build() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
    }

    @Test
    public void model() throws IOException {
        GetAndroidModelAction.ModelContainer<AndroidProject> models = project.model().getMulti();

        AndroidProject appModel = models.getModelMap().get(":app");

        Variant debugVariant = ModelHelper.getVariant(appModel.getVariants(), "debug");

        DependencyGraphs dependencyGraphs = debugVariant.getMainArtifact().getDependencyGraphs();

        LibraryGraphHelper helper = new LibraryGraphHelper(models);

        final LibraryGraphHelper.Items compileDependencyItems = helper.on(dependencyGraphs);
        assertThat(compileDependencyItems.withType(ANDROID).asLibraries()).hasSize(1);
        assertThat(compileDependencyItems.withType(MODULE).asLibraries()).isEmpty();
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }
}
