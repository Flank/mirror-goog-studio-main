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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE
import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
/**
 * Assemble tests for tictactoe.
 */
@CompileStatic
class  TictactoeTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("tictactoe")
            .create()
    static ModelContainer<AndroidProject> models

    @BeforeClass
    static void setUp() {
        models = project.executeAndReturnMultiModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    public void testModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(models)

        AndroidProject libModel = models.getModelMap().get(":lib")
        assertNotNull("lib module model null-check", libModel)
        assertTrue("lib module library flag", libModel.isLibrary())
        assertEquals("Project Type", AndroidProject.PROJECT_TYPE_LIBRARY, libModel.getProjectType())

        AndroidProject appModel = models.getModelMap().get(":app")
        assertNotNull("app module model null-check", appModel)

        Collection<Variant> variants = appModel.getVariants()
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)

        DependencyGraphs graph = debugVariant.getMainArtifact().getDependencyGraphs();
        assertThat(helper.on(graph).withType(MODULE).mapTo(GRADLE_PATH)).containsExactly(":lib")
    }
}
