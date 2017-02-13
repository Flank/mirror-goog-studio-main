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
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.testutils.apk.SplitApks;
import com.android.tools.fd.client.InstantRunBuildInfo;
import java.util.List;

/** Helper class for testing cold-swap scenarios. */
class ColdSwapTester {
    private final GradleTestProject mProject;

    public ColdSwapTester(GradleTestProject project) {
        mProject = project;
    }

    void testDalvik(Steps steps) throws Exception {
        doTest(steps, 19);
    }

    private void doTest(Steps steps, int apiLevel) throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.doInitialBuild(mProject, apiLevel);

        steps.checkApks(InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel));

        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        String startBuildId = initialContext.getTimeStamp();

        steps.makeChange();

        mProject.executor()
                .withInstantRun(apiLevel)
                .run("assembleDebug");

        InstantRunBuildContext buildContext =
                InstantRunTestUtils.loadBuildContext(apiLevel, instantRunModel);

        InstantRunBuildContext.Build lastBuild = buildContext.getLastBuild();
        assertNotNull(lastBuild);
        assertThat(lastBuild.getBuildId()).isNotEqualTo(startBuildId);

        steps.checkVerifierStatus(lastBuild.getVerifierStatus());
        steps.checkBuildMode(lastBuild.getBuildMode());
        steps.checkArtifacts(lastBuild.getArtifacts());
    }

    void testMultiApk(Steps steps) throws Exception {
        doTest(steps, 24);
    }

    interface Steps {
        void checkApks(@NonNull SplitApks apks) throws Exception;

        void makeChange() throws Exception;

        void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception;

        void checkBuildMode(@NonNull InstantRunBuildMode buildMode) throws Exception;

        void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts)
                throws Exception;
    }
}
