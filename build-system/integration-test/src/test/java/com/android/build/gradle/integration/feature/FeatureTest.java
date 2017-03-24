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

package com.android.build.gradle.integration.feature;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test atom project with a dependency on an external library. */
public class FeatureTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("projectWithFeatures")
                    .withoutNdk()
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {}

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        // just run test for now.
        sProject.execute("assemble");

        // check the feature declaration file presence.
        GradleTestProject featureProject = sProject.getSubproject(":feature");
        File featureSplit =
                featureProject.getIntermediateFile(
                        "feature-split/declaration/release/feature-split.json");
        assertThat(featureSplit.exists());
        FeatureSplitDeclaration featureSplitDeclaration =
                FeatureSplitDeclaration.load(featureSplit);
        assertThat(featureSplitDeclaration).isNotNull();
        assertThat(featureSplitDeclaration.getUniqueIdentifier()).isEqualTo(":feature");

        // check the base feature declared the list of features and their associated IDs.
        GradleTestProject baseProject = sProject.getSubproject(":baseFeature");
        File idsList = baseProject.getIntermediateFile("feature-split/ids/debug/package_ids.json");
        assertThat(idsList.exists());
        FeatureSplitPackageIds packageIds = FeatureSplitPackageIds.load(idsList);
        assertThat(packageIds).isNotNull();
        assertThat(packageIds.getIdFor(":feature")).isEqualTo(FeatureSplitPackageIds.BASE_ID);
    }
}
