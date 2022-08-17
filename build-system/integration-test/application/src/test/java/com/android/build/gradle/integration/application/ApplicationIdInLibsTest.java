package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for @{applicationId} placeholder presence in library manifest files. Such placeholders
 * should be left intact until the library is merged into a consuming application with a known
 * application Id.
 */
public class ApplicationIdInLibsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("applicationIdInLibsTest").create();

    @Test
    public void testLibPlaceholderSubstitutionInFinalApk() throws Exception {
        project.execute("clean", "app:assembleDebug");
        ModelContainerV2 outputModels =
                project.modelV2().fetchModels("debug", null).getContainer();
        assertThat(
                        checkPermissionPresent(
                                outputModels,
                                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))
                .isTrue();

        TestFileUtils.searchAndReplace(
                project.file("app/build.gradle"),
                "manifest_merger_example.flavor",
                "manifest_merger_example.change");

        project.execute("clean", "app:assembleDebug");
        outputModels = project.modelV2().fetchModels("debug", null).getContainer();
        assertThat(
                        checkPermissionPresent(
                                outputModels,
                                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))
                .isFalse();
        assertThat(
                        checkPermissionPresent(
                                outputModels,
                                "'com.example.manifest_merger_example.change.permission.C2D_MESSAGE'"))
                .isTrue();
    }

    private static boolean checkPermissionPresent(
            ModelContainerV2 modelContainer, String permission) {
        assertThat(modelContainer.getInfoMaps().get(":")).containsKey(":app");
        final AndroidProject projectModel =
                modelContainer.getProject(":app", ":").getAndroidProject();
        assertThat(projectModel).isNotNull();

        Collection<Variant> variantBuildOutputs = projectModel.getVariants();
        assertThat(variantBuildOutputs).hasSize(2);

        // select the debug variant
        Variant debugBuildOutput =
                AndroidProjectUtilsV2.getVariantByName(projectModel, "flavorDebug");
        File apk = new File(ProjectBuildOutputUtilsV2.getSingleOutputFile(debugBuildOutput));
        List<String> apkBadging = ApkSubject.getBadging(apk.toPath());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" + permission)) {
                return true;
            }

        }

        return false;
    }
}
