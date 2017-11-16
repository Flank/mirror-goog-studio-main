package com.android.build.gradle.integration.testing;

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for testWithDep that loads the model but doesn't build. */
public class TestWithDepTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("testWithDep").create();

    public static ModelContainer<AndroidProject> model;

    @BeforeClass
    public static void setUp() throws IOException {
        model = project.model().getSingle();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkThereIsADepOnTheTestVariant() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);
        Collection<Variant> variants = model.getOnlyModel().getVariants();
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG);

        Collection<AndroidArtifact> extraAndroidArtifact = debugVariant.getExtraAndroidArtifacts();
        AndroidArtifact testArtifact =
                ModelHelper.getAndroidArtifact(extraAndroidArtifact, ARTIFACT_ANDROID_TEST);

        DependencyGraphs graph = testArtifact.getDependencyGraphs();
        Assert.assertEquals(1, helper.on(graph).withType(JAVA).asList().size());
    }
}
