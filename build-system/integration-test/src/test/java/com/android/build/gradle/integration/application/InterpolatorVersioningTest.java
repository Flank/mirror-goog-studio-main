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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.io.File;
import java.nio.file.Files;
import org.junit.ClassRule;
import org.junit.Test;

/** Checks that the interpolars do not get auto versioned. See b/62211148. */
public class InterpolatorVersioningTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("basic").create();

    @Test
    public void testInterpolatorIsNotAutoVersioned() throws Exception {
        File interpolatorDir =
                FileUtils.join(project.getTestDir(), "src", "main", "res", "interpolator");
        FileUtils.mkdirs(interpolatorDir);

        String contents =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<pathInterpolator\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:controlX1=\"0.4\"\n"
                        + "    android:controlY1=\"0\"\n"
                        + "    android:controlX2=\"1\"\n"
                        + "    android:controlY2=\"1\" />";
        Files.write(new File(interpolatorDir, "my_interpol.xml").toPath(), contents.getBytes());

        project.execute("assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);

        assertThat(apk).contains("res/interpolator/my_interpol.xml");
        assertThat(apk).doesNotContain("res/interpolator-v21/my_interpol.xml");
    }
}
