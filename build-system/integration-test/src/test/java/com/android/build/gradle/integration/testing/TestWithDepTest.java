/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper
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

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA
import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static org.junit.Assert.assertEquals

/**
 * Assemble tests for testWithDep that loads the model but doesn't build.
 */
@CompileStatic
class TestWithDepTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("testWithDep")
            .create()

    static public ModelContainer<AndroidProject> model

    @BeforeClass
    static void setUp() {
        model = project.model().getSingle()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check there is a dep on the test variant"() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(model)
        Collection<Variant> variants = model.getOnlyModel().getVariants()
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)

        Collection<AndroidArtifact> extraAndroidArtifact = debugVariant.getExtraAndroidArtifacts()
        AndroidArtifact testArtifact = ModelHelper.getAndroidArtifact(extraAndroidArtifact,
                ARTIFACT_ANDROID_TEST)

        DependencyGraphs graph = testArtifact.getDependencyGraphs();
        assertEquals(1, helper.on(graph).withType(JAVA).asList().size())
    }
}
