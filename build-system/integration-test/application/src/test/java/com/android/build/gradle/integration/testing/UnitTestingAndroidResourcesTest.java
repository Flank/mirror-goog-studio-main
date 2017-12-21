package com.android.build.gradle.integration.testing;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apkzlib.utils.IOExceptionFunction;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Variant;
import com.android.utils.SdkUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Checks that the test_config.properties object is generated correctly. */
@RunWith(Parameterized.class)
public class UnitTestingAndroidResourcesTest {

    public static final String PLATFORM_JAR_NAME = "android-all-7.0.0_r1-robolectric-0.jar";

    enum Plugin {
        LIBRARY,
        APPLICATION
    }

    enum RClassStrategy {
        COMPILE_SOURCES,
        GENERATE_JAR,
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTestingAndroidResources").create();

    @Parameterized.Parameters(name = "plugin={0}, aaptGeneration={1}, rClassStrategy={2}")
    public static Object[][] data() {
        return new Object[][] {
            {Plugin.APPLICATION, null},
            {Plugin.LIBRARY, RClassStrategy.COMPILE_SOURCES},
            {Plugin.LIBRARY, RClassStrategy.GENERATE_JAR},
        };
    }

    @Parameterized.Parameter public Plugin plugin;

    @Parameterized.Parameter(value = 1)
    public RClassStrategy rClassStrategy;

    @Before
    public void changePlugin() throws Exception {
        if (plugin == Plugin.LIBRARY) {
            TestFileUtils.searchAndReplace(
                    project.getBuildFile(), "com.android.application", "com.android.library");
        }
    }

    /**
     * Copies the Robolectric platform jar into the project directory, so we can run a fully offline
     * build.
     */
    @Before
    public void copyPlatformJar() throws Exception {
        boolean found = false;
        for (Path path : GradleTestProject.getLocalRepositories()) {
            Path platformJar =
                    path.resolve(
                            "org/robolectric/android-all/7.0.0_r1-robolectric-0/"
                                    + PLATFORM_JAR_NAME);
            if (Files.exists(platformJar)) {
                found = true;
                Path robolectricLibs = project.file("robolectric-libs").toPath();
                Files.createDirectory(robolectricLibs);
                Files.copy(platformJar, robolectricLibs.resolve(PLATFORM_JAR_NAME));
                break;
            }
        }

        if (!found) {
            Assert.fail("Failed to find Robolectric platform jar in prebuilts.");
        }
    }

    @Test
    public void runUnitTests() throws Exception {

        GradleTaskExecutor runGradleTasks =
                project.executor()
                        .with(
                                BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION,
                                rClassStrategy == RClassStrategy.GENERATE_JAR);

        runGradleTasks.run("testDebugUnitTest");

        Files.write(project.file("src/main/assets/foo.txt").toPath(), "CHANGE".getBytes());
        GradleBuildResult result = runGradleTasks.run("testDebugUnitTest");

        assertThat(result.getNotUpToDateTasks()).contains(":testDebugUnitTest");

        // Sanity check: make sure we're actually executing Robolectric code.
        File xmlResults =
                project.file(
                        "build/test-results/testDebugUnitTest/"
                                + "TEST-com.android.tests.WelcomeActivityTest.xml");
        assertThat(xmlResults).isFile();

        runGradleTasks.run("clean");

        // Check that the model contains the generated file
        AndroidProject model =
                project.model()
                        .with(
                                BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION,
                                rClassStrategy == RClassStrategy.GENERATE_JAR)
                        .getMulti()
                        .getModelMap()
                        .get(":");
        Variant debug = ModelHelper.getVariant(model.getVariants(), "debug");
        JavaArtifact debugUnitTest =
                ModelHelper.getJavaArtifact(
                        debug.getExtraJavaArtifacts(), AndroidProject.ARTIFACT_UNIT_TEST);

        ImmutableList.Builder<String> commands = ImmutableList.builder();

        commands.add(":" + debug.getMainArtifact().getSourceGenTaskName());
        assertThat(debug.getExtraJavaArtifacts()).contains(debugUnitTest);

        for (String taskName : debugUnitTest.getIdeSetupTaskNames()) {
            commands.add(":" + taskName);
        }
        commands.add(debugUnitTest.getCompileTaskName());
        runGradleTasks.run(commands.build());



        Path configFile = getConfigFile(debugUnitTest.getAdditionalClassesFolders());
        assertNotNull(configFile);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        }
        properties.forEach(
                (name, value) -> {
                    if (name.equals("android_custom_package")) {
                        try (URLClassLoader cl =
                                makeClassloader(debug.getMainArtifact(), debugUnitTest)) {
                            try {
                                cl.loadClass(value + ".R");
                            } catch (ClassNotFoundException e) {
                                throw new AssertionError(
                                        "expected R class at "
                                                + value
                                                + ".R, with classpath \n    -"
                                                + Arrays.stream(cl.getURLs())
                                                        .map(Object::toString)
                                                        .collect(Collectors.joining("\n    - ")),
                                        e);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    } else {
                        assertThat(Paths.get(value.toString())).exists();
                    }
                });

        // Check the tests see the assets from dependencies, even in the library case where they
        // would not otherwise be merged.
        List<String> filenames =
                Files.walk(Paths.get(properties.getProperty("android_merged_assets")))
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());

        assertThat(filenames).containsExactly("foo.txt", "bar.txt");
    }

    @Nullable
    private static Path getConfigFile(@NonNull Iterable<File> directories) {
        for (File dir : directories) {
            Path candidateConfigFile =
                    dir.toPath()
                            .resolve("com")
                            .resolve("android")
                            .resolve("tools")
                            .resolve("test_config.properties");
            if (Files.isRegularFile(candidateConfigFile)) {
                return candidateConfigFile;
            }
        }
        return null;
    }

    private static URLClassLoader makeClassloader(
            @NonNull AndroidArtifact main, @NonNull JavaArtifact test) {
        ImmutableList.Builder<File> files = ImmutableList.builder();
        files.add(main.getClassesFolder(), main.getJavaResourcesFolder());
        files.addAll(main.getAdditionalClassesFolders());
        files.add(test.getClassesFolder(), test.getJavaResourcesFolder());
        files.addAll(test.getAdditionalClassesFolders());
        List<URL> urls =
                files.build()
                        .stream()
                        .map(IOExceptionFunction.asFunction(SdkUtils::fileToUrl))
                        .collect(Collectors.toList());
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }
}
