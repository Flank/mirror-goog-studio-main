/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION;
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for extracting RS enabled annotations. */
public class RsEnabledAnnotationTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("extractRsEnabledAnnotations")
                    // TODO(159233213) Turn to ON when release configuration is cacheable
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
                    .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
                    .setWithCmakeDirInLocalProp(true)
                    .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                    .create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkExtractAnnotation() throws Exception {
        // check the resulting .aar file to ensure annotations.zip inclusion.
        project.testAar(
                "debug",
                it -> {
                    it.contains("annotations.zip");
                    it.doesNotContain("libs/renderscript-v8.zip");
                });
    }
}
