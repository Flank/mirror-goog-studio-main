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

package com.android.build.gradle.integration.testing;


import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.SyncIssue;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import com.google.common.collect.Iterables;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.File;
import java.util.Collection;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

public class AndroidTestUtilTest {

    /** This test caused timeouts in the past. */
    @Rule
    public Timeout timeout = Timeout.seconds(180);

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void model() throws Exception {
        //noinspection SpellCheckingInspection
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies { \n"
                        + "androidTestUtil 'com.android.support.test:orchestrator:1.0.0'\n"
                        + "}\n");

        AndroidProject model =
                project.modelV2()
                        .fetchModels(null, null)
                        .getContainer()
                        .getProject(null, ":")
                        .getAndroidProject();
        Variant debugVariant = AndroidProjectUtilsV2.getDebugVariant(model);
        Collection<File> additionalRuntimeApks =
                debugVariant.getAndroidTestArtifact().getTestInfo().getAdditionalRuntimeApks();

        additionalRuntimeApks.forEach(apk -> assertThat(apk).isFile());
        assertThat(Iterables.transform(additionalRuntimeApks, File::getName))
                .containsExactly("orchestrator-1.0.0.apk", "test-services-1.0.0.apk");
    }

    @Test
    public void nonApkDependency() throws Exception {
        //noinspection SpellCheckingInspection
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies { \n"
                        + "androidTestUtil 'com.android.support.test:orchestrator:1.0.0'\n"
                        + "androidTestUtil 'com.google.guava:guava:19.0'\n"
                        + "}\n");

        ModelContainerV2 modelContainer =
                project.modelV2().ignoreSyncIssues()
                        .fetchModels(null, null)
                        .getContainer();
        Collection<SyncIssue> syncIssues
                = modelContainer.getProject(null, ":").getIssues().getSyncIssues();
        assertThat(syncIssues).hasSize(1);
        assertThat(syncIssues.iterator().next().getMessage()).contains("guava");
    }
}
