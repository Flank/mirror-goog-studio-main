package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for DSL AAPT options. */
public class AaptOptionsTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws IOException {
        FileUtils.createFile(project.file("src/main/res/raw/ignored"), "ignored");
        FileUtils.createFile(project.file("src/main/res/raw/kept"), "kept");
    }

    @Test
    public void testAaptOptionsFlagsWithAapt2() throws IOException, InterruptedException {
        File ids = temporaryFolder.newFile();

        String idsFilePath = ids.getAbsolutePath();
        String windowsFriendlyFilePath = idsFilePath.replace("\\", "\\\\");
        String additionalParams = "additionalParameters \"--emit-ids\", \""
                + windowsFriendlyFilePath
                + "\"";

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  aaptOptions {\n"
                        + "    "
                        + additionalParams
                        + "\n"
                        + "  }\n"
                        + "}\n");

        project.executor().run("clean", "assembleDebug");

        // Check that ids file is generated
        assertThat(ids).exists();
        assertThat(ids).contains("raw/kept");
        FileUtils.delete(ids);

        TestFileUtils.searchAndReplace(project.getBuildFile(), additionalParams, "");

        project.executor().run("assembleDebug");

        // Check that ids file is not generated
        assertThat(ids).doesNotExist();
    }

    @Test
    public void emptyNoCompressList() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  aaptOptions {\n"
                        + "    noCompress \"\"\n"
                        + "  }\n"
                        + "}\n");

        // Should execute without failure.
        project.executor().run("clean", "assembleDebug");
    }
}
