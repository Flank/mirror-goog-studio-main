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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.testutils.apk.Apk;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Instant run test for changing Java resources.
 */
@RunWith(Parameterized.class)
public class JavaResourcesTest {

    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(new Object[][]{{19}, {21}, {24}});
    }

    @Parameterized.Parameter(0)
    public int apiLevel;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    private File resource;

    @Before
    public void setUp() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        resource = project.file("src/main/resources/foo.txt");
        FileUtils.createFile(resource, "foo");
    }

    @Test
    public void testChangingJavaResources() throws Exception {
        AndroidProject model = project.model().getSingle().getOnlyModel();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        project.executor()
                .withInstantRun(apiLevel, ColdswapMode.DEFAULT)
                .run("assembleDebug");

        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.INITIAL_BUILD.toString());

        List<InstantRunArtifact> mainArtifacts;

        if (apiLevel < 21) {
            mainArtifacts = context.getArtifacts();
        } else {
            mainArtifacts = context.getArtifacts().stream()
                    .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT_MAIN)
                    .collect(Collectors.toList());
        }
        assertThat(mainArtifacts).hasSize(1);
        assertThatApk(new Apk(Iterables.getOnlyElement(mainArtifacts).file))
                .containsFileWithContent("foo.txt", "foo");
        Files.write("bar", resource, Charsets.UTF_8);

        project.executor()
                .withInstantRun(apiLevel, ColdswapMode.DEFAULT)
                .run("assembleDebug");

        //TODO: switch back to loadContext when it no longer adds more artifacts.
        InstantRunBuildContext context2 = InstantRunTestUtils.loadBuildContext(apiLevel, instantRunModel);
        assertThat(context2.getLastBuild().getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED);
        assertThat(context2.getLastBuild().getArtifacts()).hasSize(1);

        assertThatApk(new Apk(context2.getLastBuild().getArtifacts().get(0).getLocation()))
                .containsFileWithContent("foo.txt", "bar");
    }
}
