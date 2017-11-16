package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for class densitySplitInL . */
public class DensitySplitInLTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplitInL").create();

    private static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void setUp() throws Exception {
        outputModel =
                project.executeAndReturnModel(ProjectBuildOutput.class, "clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        outputModel = null;
    }

    @Test
    public void checkSplitOutputs() throws Exception {
        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add(null);
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");

        Collection<? extends OutputFile> outputs = getOutputs(outputModel);
        assertThat(outputs).hasSize(5);
        for (OutputFile outputFile : outputs) {
            String densityFilter = ModelHelper.getFilter(outputFile, OutputFile.DENSITY);
            if (densityFilter == null) {
                assertThat(outputFile.getOutputType()).contains(OutputFile.MAIN);
            } else {
                assertThat(outputFile.getOutputType()).contains(OutputFile.SPLIT);
            }
            expected.remove(densityFilter);
        }

        // this checks we didn't miss any expected output.
        assertThat(expected).isEmpty();
    }

    @Test
    public void checkAddingDensityIncrementally() throws Exception {
        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        final Map<String, Long> lastModifiedTimePerDensity =
                getApkModifiedTimePerDensity(getOutputs(outputModel));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\"",
                            "exclude \"ldpi\", \"tvdpi\"");
                    ProjectBuildOutput incrementalModel =
                            project.executeAndReturnModel(
                                    ProjectBuildOutput.class, "assembleDebug");

                    Collection<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    assertThat(outputs).hasSize(6);
                    boolean foundAddedAPK = false;
                    for (OutputFile output : outputs) {
                        String filter = ModelHelper.getFilter(output, OutputFile.DENSITY);

                        if (filter != null && filter.matches("^xxxhdpi$")) {
                            // found our added density, done.
                            foundAddedAPK = true;
                        } else {
                            // check that the APK was not rebuilt.
                            String key = output.getOutputType() + filter;
                            Long initialApkModifiedTime = lastModifiedTimePerDensity.get(key);
                            assertThat(initialApkModifiedTime).isNotNull();
                            assertThat(initialApkModifiedTime)
                                    .isEqualTo(output.getOutputFile().lastModified());
                        }
                    }

                    assertThat(foundAddedAPK).isTrue();
                });
    }

    @Test
    public void checkDeletingDensityIncrementally() throws Exception {
        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        final Map<String, Long> lastModifiedTimePerDensity =
                getApkModifiedTimePerDensity(getOutputs(outputModel));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\"",
                            "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\", \"xxhdpi\"");
                    ProjectBuildOutput incrementalModel =
                            project.executeAndReturnModel(
                                    ProjectBuildOutput.class, "assembleDebug");

                    Collection<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    assertThat(outputs).hasSize(4);
                    for (OutputFile output : outputs) {
                        String filter = ModelHelper.getFilter(output, OutputFile.DENSITY);
                        if (filter == null) {
                            continue;
                        }
                        assertThat(filter).doesNotMatch("^xxhdpi$");

                        // check that the APK was not rebuilt.
                        String key = output.getOutputType() + filter;
                        Long initialApkModifiedTime = lastModifiedTimePerDensity.get(key);
                        assertThat(initialApkModifiedTime).isNotNull();
                        assertThat(initialApkModifiedTime)
                                .isEqualTo(output.getOutputFile().lastModified());
                    }
                });
    }

    private static Collection<? extends OutputFile> getOutputs(ProjectBuildOutput outputModel)
            throws IOException {
        VariantBuildOutput debugOutput = ModelHelper.getDebugVariantBuildOutput(outputModel);

        Collection<OutputFile> outputFiles = debugOutput.getOutputs();

        // with pure splits, all split have the same version code.
        outputFiles.forEach(
                output -> {
                    assertThat(output.getVersionCode()).isEqualTo(12);
                });

        return outputFiles;
    }

    @NonNull
    private static Map<String, Long> getApkModifiedTimePerDensity(
            Collection<? extends OutputFile> outputs) {
        ImmutableMap.Builder<String, Long> builder = ImmutableMap.builder();
        for (OutputFile output : outputs) {
            String key = output.getOutputType() + ModelHelper.getFilter(output, OutputFile.DENSITY);
            builder.put(key, output.getOutputFile().lastModified());
        }

        return builder.build();
    }
}
