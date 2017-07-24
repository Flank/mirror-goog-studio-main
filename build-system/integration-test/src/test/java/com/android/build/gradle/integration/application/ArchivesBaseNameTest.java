package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Ensures that archivesBaseName setting on android project is used when choosing the apk file names
 */
@RunWith(FilterableParameterized.class)
public class ArchivesBaseNameTest {

    private static final String OLD_NAME = "random_name";
    private static final String NEW_NAME = "changed_name";
    @Rule public GradleTestProject project;
    private String extension;

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return ImmutableList.of(
                new Object[] {"com.android.application", "apk"},
                new Object[] {"com.android.library", "aar"});
    }

    public ArchivesBaseNameTest(String plugin, String extension) {
        this.project =
                GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin(plugin)).create();
        this.extension = extension;
    }

    @Test
    public void testArtifactName() throws IOException, InterruptedException {
        checkApkName("project", extension);

        TestFileUtils.appendToFile(
                project.getBuildFile(), "\narchivesBaseName = \'" + OLD_NAME + "\'");
        checkApkName(OLD_NAME, extension);

        TestFileUtils.searchAndReplace(project.getBuildFile(), OLD_NAME, NEW_NAME);
        checkApkName(NEW_NAME, extension);
    }

    private void checkApkName(String name, String extension)
            throws IOException, InterruptedException {
        GetAndroidModelAction.ModelContainer<AndroidProject> model =
                project.executeAndReturnModel("assembleDebug");
        Variant debug =
                model.getOnlyModel()
                        .getVariants()
                        .stream()
                        .filter(v -> v.getName().equals("debug"))
                        .findFirst()
                        .orElseThrow(AssertionError::new);
        AndroidArtifactOutput androidArtifactOutput =
                debug.getMainArtifact()
                        .getOutputs()
                        .stream()
                        .findFirst()
                        .orElseThrow(AssertionError::new);
        File outputFile = androidArtifactOutput.getMainOutputFile().getOutputFile();

        TruthHelper.assertThat(outputFile.getName()).isEqualTo(name + "-debug." + extension);
        TruthHelper.assertThat(outputFile).isFile();
    }
}
