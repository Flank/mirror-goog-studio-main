package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test incremental compilation for NDK. */
public class NdkIncrementalTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Before
    public void setUp() throws IOException, InterruptedException {
        project.file("src/main/jni/empty.c").createNewFile();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.execute("assembleDebug");
    }

    @Test
    public void checkAddingFile() throws IOException, InterruptedException {
        File helloJniO =
                Iterables.getOnlyElement(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/hello-jni\\.o")));
        long helloJniTimestamp = helloJniO.lastModified();

        // check new-file.o does not exist.
        assertThat(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/new-file\\.o")))
                .hasSize(0);

        TestFileUtils.appendToFile(project.file("src/main/jni/new-file.c"), " ");

        project.execute("assembleDebug");

        GradleBuildResult buildResult = project.getBuildResult();
        buildResult.assertThatFile(project.file("src/main/jni/new-file.c")).hasBeenAdded();
        buildResult.assertThatFile(project.file("src/main/jni/hello-jni.c")).hasNotBeenChanged();

        assertThat(helloJniO).wasModifiedAt(helloJniTimestamp);

        assertThat(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/new-file\\.o")))
                .hasSize(1);
    }

    @Test
    public void checkRemovingFile() throws IOException, InterruptedException {
        File helloJniO =
                Iterables.getOnlyElement(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/hello-jni\\.o")));
        long helloJniTimestamp = helloJniO.lastModified();

        assertThat(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/empty\\.o")))
                .hasSize(1);

        project.file("src/main/jni/empty.c").delete();

        project.execute("assembleDebug");

        GradleBuildResult result = project.getBuildResult();
        result.assertThatFile(project.file("src/main/jni/empty.c")).hasBeenRemoved();
        result.assertThatFile(project.file("src/main/jni/hello-jni.c")).hasNotBeenChanged();

        assertThat(helloJniO).wasModifiedAt(helloJniTimestamp);

        assertThat(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/empty\\.o")))
                .hasSize(0);
    }

    @Test
    public void checkChangingFile() throws IOException, InterruptedException {
        File helloJniO =
                Iterables.getOnlyElement(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/hello-jni\\.o")));
        long helloJniTimestamp = helloJniO.lastModified();

        File emptyO =
                Iterables.getOnlyElement(
                        FileUtils.find(
                                project.file("build/intermediates/objectFiles"),
                                Pattern.compile("X86Debug.*/empty\\.o")));
        long emptyTimestamp = emptyO.lastModified();

        TestFileUtils.appendToFile(project.file("src/main/jni/empty.c"), " ");

        project.execute("assembleDebug");

        GradleBuildResult result = project.getBuildResult();
        result.assertThatFile(project.file("src/main/jni/empty.c")).hasChanged();
        result.assertThatFile(project.file("src/main/jni/hello-jni.c")).hasNotBeenChanged();

        assertThat(helloJniO).wasModifiedAt(helloJniTimestamp);
        assertThat(emptyO).isNewerThan(emptyTimestamp);
    }
}
