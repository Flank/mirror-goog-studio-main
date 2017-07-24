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

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.builder.model.level2.DependencyGraphs
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Assemble tests for customArtifactDep.
 */
@CompileStatic
class CustomArtifactDepTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("customArtifactDep")
            .create()
    static ModelContainer<AndroidProject> models

    @BeforeClass
    static void setUp() {
        models = project.model().getMulti()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void testModel() {
        AndroidProject appModel = models.getModelMap().get(":app")
        assertNotNull("Module app null-check", appModel)

        Collection<Variant> variants = appModel.getVariants()
        assertEquals("Variant count", 2, variants.size())

        Variant variant = ModelHelper.getVariant(variants, "release")

        AndroidArtifact mainInfo = variant.getMainArtifact()
        assertNotNull("Main Artifact null-check", mainInfo)

        DependencyGraphs dependencyGraph = mainInfo.getDependencyGraphs();
        assertNotNull("Dependencies null-check", dependencyGraph)

        assertEquals("jar dep count", 1, dependencyGraph.getCompileDependencies().size())
    }
}
