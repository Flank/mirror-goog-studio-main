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

package com.android.build.gradle.integration.packaging;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.TestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test incremental behavior when a library changes. */
public class IncrementalLibChangeTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @NonNull
    private static final String LIBRARY_DEP =
            "implementation 'com.android.support:support-v4:"
                    + GradleTestProject.SUPPORT_LIB_VERSION
                    + "'";

    @NonNull private static final String COMMENTED_LIBRARY_DEP = "// " + LIBRARY_DEP;

    @Before
    public void setUp() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "android \\{",
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        applicationId \"com.example.incrementallibchange\"\n"
                        + "        minSdkVersion 20\n"
                        + "    }\n"
                        + "\n"
                        + "    compileOptions {\n"
                        + "        sourceCompatibility JavaVersion.VERSION_1_8\n"
                        + "        targetCompatibility JavaVersion.VERSION_1_8\n"
                        + "    }\n"
                        + "\n"
                        + "    dependencies {\n"
                        + "        "
                        + COMMENTED_LIBRARY_DEP
                        + "\n"
                        + "    }\n");
    }

    @Test
    public void checkLibChangedTest() throws Exception {
        project.executor()
                .with(BooleanOption.ENABLE_D8, true)
                .with(BooleanOption.ENABLE_D8_DESUGARING, true)
                .run("clean", ":assembleDebug");

        Path relativeDexPath = Paths.get("com", "example", "helloworld", "HelloWorld.dex");
        Stream<Path> found =
                Files.find(
                        project.getIntermediatesDir().toPath(),
                        20,
                        (path, attributes) ->
                                attributes.isRegularFile() && path.endsWith(relativeDexPath));
        Path helloWorldDex =
                found.reduce(
                                (a, b) -> {
                                    throw new AssertionError("Several matches found");
                                })
                        .orElseThrow(AssertionError::new);
        long originalTime = Files.getLastModifiedTime(helloWorldDex).toMillis();

        // Uncomment library dependency
        TestFileUtils.searchAndReplace(project.getBuildFile(), COMMENTED_LIBRARY_DEP, LIBRARY_DEP);

        TestUtils.waitForFileSystemTick();

        project.executor()
                .with(BooleanOption.ENABLE_D8, true)
                .with(BooleanOption.ENABLE_D8_DESUGARING, true)
                .run(":assembleDebug");

        assertThat(originalTime).isEqualTo(Files.getLastModifiedTime(helloWorldDex).toMillis());
    }
}
