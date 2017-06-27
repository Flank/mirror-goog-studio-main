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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class AndroidTestUtilTest {

    /** This test caused timeouts in the past. */
    @Rule public Timeout timeout = Timeout.seconds(60);

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
                "dependencies { androidTestUtil 'com.linkedin.testbutler:test-butler-app:1.3.1@apk' }\n");

        AndroidProject model = project.model().getSingle().getOnlyModel();
        Collection<File> additionalRuntimeApks =
                Iterables.getOnlyElement(
                                ModelHelper.getDebugVariant(model).getExtraAndroidArtifacts())
                        .getAdditionalRuntimeApks();

        File testButler = Iterables.getOnlyElement(additionalRuntimeApks);
        assertThat(testButler).isFile();
        assertThat(testButler.getName()).isEqualTo("test-butler-app-1.3.1.apk");
    }
}
