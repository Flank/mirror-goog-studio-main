package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
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
    public void testLibPlaceholderSubstitutaionInFinalApk() throws Exception {
        GetAndroidModelAction.ModelContainer<AndroidProject> models =
                project.executeAndReturnMultiModel("clean", "app:assembleDebug");
        assertThat(
                        checkPermissionPresent(
                                models,
                                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))
                .isTrue();

        TestFileUtils.searchAndReplace(
                project.file("app/build.gradle"),
                "manifest_merger_example.flavor",
                "manifest_merger_example.change");

        models = project.executeAndReturnMultiModel("clean", "app:assembleDebug");
        assertThat(
                        checkPermissionPresent(
                                models,
                                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))
                .isFalse();
        assertThat(
                        checkPermissionPresent(
                                models,
                                "'com.example.manifest_merger_example.change.permission.C2D_MESSAGE'"))
                .isTrue();
    }

    private static boolean checkPermissionPresent(
            GetAndroidModelAction.ModelContainer<AndroidProject> models, String permission) {
        // Load the custom model for the project
        Collection<Variant> variants = models.getModelMap().get(":app").getVariants();
        assertThat(variants).hasSize(2);

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, "flavorDebug");
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertThat(debugMainArtifact).named("Debug main info null-check").isNotNull();

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
        assertThat(debugOutputs).isNotNull();

        assertThat(debugOutputs).hasSize(1);
        AndroidArtifactOutput output = Iterables.getOnlyElement(debugOutputs);
        assertThat(output.getOutputs()).hasSize(1);

        List<String> apkBadging =
                ApkHelper.getApkBadging(
                        Iterables.getOnlyElement(output.getOutputs()).getOutputFile());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" + permission)) {
                return true;
            }

        }

        return false;
    }
}
