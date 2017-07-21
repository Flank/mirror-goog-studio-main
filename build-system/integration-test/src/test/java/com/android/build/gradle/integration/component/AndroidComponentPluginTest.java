package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/** Test AndroidComponentModelPlugin. */
public class AndroidComponentPluginTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().useExperimentalGradleVersion(true).create();

    @Test
    public void assemble() throws IOException, InterruptedException {

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "import com.android.build.gradle.model.AndroidComponentModelPlugin\n"
                        + "apply plugin: AndroidComponentModelPlugin\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        buildTypes {\n"
                        + "            create(\"custom\")\n"
                        + "        }\n"
                        + "        productFlavors {\n"
                        + "            create(\"flavor1\")\n"
                        + "            create(\"flavor2\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.execute("assemble");
    }

    @Test
    public void multiFlavor() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "import com.android.build.gradle.model.AndroidComponentModelPlugin\n"
                        + "apply plugin: AndroidComponentModelPlugin\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        productFlavors {\n"
                        + "            create(\"free\") {\n"
                        + "                dimension \"cost\"\n"
                        + "            }\n"
                        + "            create(\"premium\") {\n"
                        + "                dimension \"cost\"\n"
                        + "            }\n"
                        + "            create(\"blue\") {\n"
                        + "                dimension \"color\"\n"
                        + "            }\n"
                        + "            create(\"red\") {\n"
                        + "                dimension \"color\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // Gradle creates a task for each binary in the form <component_name><flavor><buildType>.
        // <component_name> is "android".
        List<String> tasks = project.model().getTaskList();
        assertThat(tasks)
                .containsAllOf(
                        "androidBlueFreeDebug",
                        "androidBluePremiumDebug",
                        "androidRedFreeDebug",
                        "androidRedPremiumDebug",
                        "androidBlueFreeRelease",
                        "androidBluePremiumRelease",
                        "androidRedFreeRelease",
                        "androidRedPremiumRelease");
    }

    @Test
    public void checkFlavorOrder() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "import com.android.build.gradle.model.AndroidComponentModelPlugin\n"
                        + "apply plugin: AndroidComponentModelPlugin\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        productFlavors {\n"
                        + "            create(\"e\") {\n"
                        + "                dimension \"e\"\n"
                        + "            }\n"
                        + "            create(\"a\") {\n"
                        + "                dimension \"a\"\n"
                        + "            }\n"
                        + "            create(\"d\") {\n"
                        + "                dimension \"d\"\n"
                        + "            }\n"
                        + "            create(\"b\") {\n"
                        + "                dimension \"b\"\n"
                        + "            }\n"
                        + "            create(\"c\") {\n"
                        + "                dimension \"c\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        List<String> tasks = project.model().getTaskList();
        assertThat(tasks).containsAllOf("androidABCDEDebug", "androidABCDERelease");
    }
}
