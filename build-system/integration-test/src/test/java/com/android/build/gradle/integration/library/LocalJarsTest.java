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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.builder.model.level2.DependencyGraphs
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA
import static org.junit.Assert.assertNotNull
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Assemble tests for localJars.
 */
@CompileStatic
class LocalJarsTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("localJars")
            .create()
    static ModelContainer<AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.executor().allowStderr(false).run("clean", "assembleDebug")
        models = project.model().getMulti();
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    void testModel() throws Exception {
        AndroidProject libModel = models.getModelMap().get(":baseLibrary")
        assertNotNull("Module app null-check", libModel)

        Collection<Variant> variants = libModel.getVariants()

        Variant releaseVariant = ModelHelper.getVariant(variants, "release")

        DependencyGraphs graph = releaseVariant.getMainArtifact().getDependencyGraphs()
        assertNotNull(graph)

        LibraryGraphHelper helper = new LibraryGraphHelper(models)

        assertThat(helper.on(graph).withType(JAVA).asList()).hasSize(2);
    }
}
