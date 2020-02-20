package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.VariantBuildInformation;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

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
        Map<String, AndroidProject> outputModels =
                project.model().fetchAndroidProjects().getOnlyModelMap();
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
        outputModels = project.model().fetchAndroidProjects().getOnlyModelMap();
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
            Map<String, AndroidProject> models, String permission) {
        assertThat(models).containsKey(":app");
        final AndroidProject projectModel = models.get(":app");
        assertThat(projectModel).isNotNull();

        Collection<VariantBuildInformation> variantBuildOutputs =
                projectModel.getVariantsBuildInformation();
        assertThat(variantBuildOutputs).hasSize(2);

        // select the debug variant
        VariantBuildInformation debugBuildOutput =
                ProjectBuildOutputUtils.getVariantBuildInformation(projectModel, "flavorDebug");
        File apk = new File(ProjectBuildOutputUtils.getSingleOutputFile(debugBuildOutput));
        List<String> apkBadging = ApkSubject.getBadging(apk.toPath());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" + permission)) {
                return true;
            }

        }

        return false;
    }
}
