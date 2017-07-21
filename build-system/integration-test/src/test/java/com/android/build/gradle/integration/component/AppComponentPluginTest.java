package com.android.build.gradle.integration.component;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.testutils.truth.MoreTruth;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Basic integration test for AppComponentModelPlugin. */
@Category(SmokeTests.class)
public class AppComponentPluginTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .useExperimentalGradleVersion(true)
                    .withoutNdk()
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.application\"\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void basicAssemble() throws IOException, InterruptedException {
        AndroidProject model = project.executeAndReturnModel("assemble").getOnlyModel();
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(project.getName());
        assertThat(model.getBuildTypes()).hasSize(2);
        assertThat(model.getProductFlavors()).hasSize(0);
        assertThat(model.getVariants()).hasSize(2);
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact();
            Collection<File> outputFiles =
                    artifact.getOutputs()
                            .stream()
                            .flatMap(e -> e.getOutputs().stream())
                            .map(OutputFile::getOutputFile)
                            .collect(Collectors.toList());

            GradleTestProject.ApkType apkType =
                    GradleTestProject.ApkType.of(variant.getBuildType(), artifact.isSigned());

            File onlyOutput = Iterables.getOnlyElement(outputFiles);
            MoreTruth.assertThat(onlyOutput).exists();
            assertThat(onlyOutput).isEqualTo(project.getApk(apkType).getFile().toFile());
        }
    }

    @Test
    public void flavors() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        buildTypes {\n"
                        + "            create(\"b1\")\n"
                        + "        }\n"
                        + "        productFlavors {\n"
                        + "            create(\"f1\") {\n"
                        + "                dimension \"foo\"\n"
                        + "            }\n"
                        + "            create(\"f2\") {\n"
                        + "                dimension \"foo\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        // Ensure all combinations of assemble* tasks are created.
        List<String> tasks = project.model().getTaskList();
        assertThat(tasks)
                .containsAllOf(
                        "assemble",
                        "assembleB1",
                        "assembleDebug",
                        "assembleF1",
                        "assembleF1B1",
                        "assembleF1Debug",
                        "assembleF1Release",
                        "assembleF2",
                        "assembleF2B1",
                        "assembleF2Debug",
                        "assembleF2Release",
                        "assembleRelease",
                        "assembleAndroidTest",
                        "assembleF1DebugAndroidTest",
                        "assembleF2DebugAndroidTest");

        AndroidProject model = project.executeAndReturnModel("assemble").getOnlyModel();
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(project.getName());
        assertThat(model.getBuildTypes()).hasSize(3);
        assertThat(model.getProductFlavors()).hasSize(2);
        assertThat(model.getVariants()).hasSize(6);
    }

    @Test
    public void generationInModel() throws IOException {
        AndroidProject model = project.model().getSingle().getOnlyModel();
        assertThat(model.getPluginGeneration())
                .named("Plugin Generation")
                .isEqualTo(AndroidProject.GENERATION_COMPONENT);
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
