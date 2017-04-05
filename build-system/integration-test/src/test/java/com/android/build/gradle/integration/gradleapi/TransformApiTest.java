/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.gradleapi;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.testutils.apk.Apk;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;
import java.util.Collection;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Test for building a transform against version 1.5. */
@Ignore("http://b/37529666")
public class TransformApiTest {

    @Rule
    public GradleTestProject wholeProject = GradleTestProject.builder()
            .fromTestProject("transformApiTest")
            .create();

    @Before
    public void moveLocalProperties() throws Exception {
        // Only one of the projects is an Android project, and there is no top-level
        // settings.gradle, so local.properties ends up not being picked up. Just move
        // it to the project that needs it.
        Files.move(
                wholeProject.file(SdkConstants.FN_LOCAL_PROPERTIES),
                wholeProject
                        .getSubproject("androidproject")
                        .file(SdkConstants.FN_LOCAL_PROPERTIES));
    }

    @Test
    public void checkRepackagedGsonLibrary() throws Exception {
        wholeProject.getSubproject("plugin").execute("uploadArchives");

        AndroidProject model = wholeProject.getSubproject("androidproject")
                        .executeAndReturnModel("assembleDebug")
                        .getOnlyModel();

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG);
        assertNotNull("debug Variant null-check", debugVariant);
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArtifact);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
        assertNotNull(debugOutputs);
        assertEquals(1, debugOutputs.size());

        // make sure the Gson library has been renamed and the original one is not present.
        Apk outputFile = new Apk(Iterators.getOnlyElement(debugOutputs.iterator()).getMainOutputFile().
                getOutputFile());
        assertThatApk(outputFile).containsClass("Lcom/google/repacked/gson/Gson;");
        assertThatApk(outputFile).doesNotContainClass("Lcom/google/gson/Gson;");
    }
}
