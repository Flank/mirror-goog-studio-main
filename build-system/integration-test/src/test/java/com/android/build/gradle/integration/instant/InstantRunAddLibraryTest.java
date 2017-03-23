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
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Check that the super classes can be found when subclassing things in provided dependencies.
 */
public class InstantRunAddLibraryTest {

    private static final AndroidTestApp TEST_APP = HelloWorldApp
            .forPlugin("com.android.application");

    static {
        TEST_APP.addFile(new TestSourceFile(
                "src/main/resources/com/example/helloworld", "someres.txt", "Content\n"));
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(TEST_APP)
                    .create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void addBlankUtilClass() throws Exception {
        writeClass("throw new RuntimeException();");
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "android.packagingOptions.exclude 'META-INF/maven/com.google.guava/guava/pom.xml'\n"
                + "android.packagingOptions.exclude 'META-INF/maven/com.google.guava/guava/pom.properties'\n");
    }

    @Before
    public void skipOnJack() throws Exception {
        // IR currently does not work with Jack - http://b.android.com/224374
        AssumeUtil.assumeNotUsingJack();
    }

    @Test
    public void checkAddedLibraryCausesColdSwap() throws Exception {
        project.execute("clean");
        InstantRun instantRunModel = InstantRunTestUtils
                .getInstantRunModel(project.model().getSingle().getOnlyModel());

        project.executor()
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");

        // Add dependency
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies { compile 'com.google.guava:guava:18.0' }\n");

        // Use that dependency
        writeClass("com.google.common.base.Strings.nullToEmpty(null);");

        project.executor().withInstantRun(new AndroidVersion(23, null)).run("assembleDebug");

        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);

        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED.toString());

        boolean foundDependencies = false;
        assertThat(context.getArtifacts()).hasSize(3);
        for (InstantRunArtifact artifact: context.getArtifacts()) {
            expect.that(artifact.type).isAnyOf(
                    InstantRunArtifactType.SPLIT, InstantRunArtifactType.SPLIT_MAIN);
            if (artifact.file.getName().contains("dependencies")) {
                foundDependencies = true;
            }
        }
        assertTrue("The dependencies split apk needs to be redeployed", foundDependencies);
    }


    public void writeClass(String action) throws Exception {
        String contents = "package com.example.helloworld;" +
                "public class Util {\n" +
                "    public static void doStuff() {\n" +
                "        " + action + "\n" +
                "    }\n" +
                "}\n";
        Files.write(contents, project.file("src/main/java/com/example/helloworld/Util.java"),
                Charsets.UTF_8);
    }
}
