package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.fixture.SourceSetContainerUtils;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.SourceProviderHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.VariantType;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.ide.common.process.ProcessException;
import com.android.utils.StringHelper;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for basicMultiFlavors */
public class BasicMultiFlavorTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("basicMultiFlavors").create();

    @Test
    public void checkResourcesResolution() {
        project.execute("assembleFreeBetaDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "free", "beta"))
                .containsResource("drawable/free.png");
    }
}
