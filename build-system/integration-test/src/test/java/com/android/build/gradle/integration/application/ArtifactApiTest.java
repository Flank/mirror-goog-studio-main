package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for artifactApi. */
public class ArtifactApiTest {

    private static final int DEFAULT_EXTRA_JAVA_ARTIFACTS = 1;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("artifactApi").create();

    private static GetAndroidModelAction.ModelContainer<AndroidProject> model;

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
    public void checkMetadataInfoInModel() {
        // check the Artifact Meta Data
        Collection<ArtifactMetaData> extraArtifacts = model.getOnlyModel().getExtraArtifacts();
        assertNotNull("Extra artifact collection null-check", extraArtifacts);
        TruthHelper.assertThat(extraArtifacts).hasSize((int) DEFAULT_EXTRA_JAVA_ARTIFACTS + 2);

        // query to validate presence
        ArtifactMetaData metaData =
                ModelHelper.getArtifactMetaData(extraArtifacts, ARTIFACT_ANDROID_TEST);

        // get the custom one.
        ArtifactMetaData extraArtifactMetaData =
                ModelHelper.getArtifactMetaData(extraArtifacts, "__test__");
        assertFalse("custom extra meta data is Test check", extraArtifactMetaData.isTest());
        assertEquals(
                "custom extra meta data type check",
                ArtifactMetaData.TYPE_JAVA,
                extraArtifactMetaData.getType());
    }

    @Test
    public void checkBuildTypesContainExtraSourceProviderArtifacts() {
        // check the extra source provider on the build Types.
        for (BuildTypeContainer btContainer : model.getOnlyModel().getBuildTypes()) {
            String name = btContainer.getBuildType().getName();
            Collection<SourceProviderContainer> extraSourceProviderContainers =
                    btContainer.getExtraSourceProviders();
            assertNotNull(
                    "Extra source provider containers for build type '" + name + "' null-check",
                    extraSourceProviderContainers);
            assertEquals(
                    "Extra source provider containers for build type size '" + name + "' check",
                    (long) DEFAULT_EXTRA_JAVA_ARTIFACTS + 1,
                    extraSourceProviderContainers.size());

            SourceProviderContainer sourceProviderContainer =
                    extraSourceProviderContainers.iterator().next();
            assertNotNull(
                    "Extra artifact source provider for " + name + " null check",
                    sourceProviderContainer);

            assertEquals(
                    "Extra artifact source provider for " + name + " name check",
                    "__test__",
                    sourceProviderContainer.getArtifactName());

            assertEquals(
                    "Extra artifact source provider for " + name + " value check",
                    "buildType:" + name,
                    sourceProviderContainer.getSourceProvider().getManifestFile().getPath());
        }

    }

    @Test
    public void checkProductFlavorsContainExtraSourceProvider() {
        // check the extra source provider on the product flavors.
        for (ProductFlavorContainer pfContainer : model.getOnlyModel().getProductFlavors()) {
            String name = pfContainer.getProductFlavor().getName();
            Collection<SourceProviderContainer> extraSourceProviderContainers =
                    pfContainer.getExtraSourceProviders();
            assertNotNull(
                    "Extra source provider container for product flavor '" + name + "' null-check",
                    extraSourceProviderContainers);
            assertEquals(
                    "Extra artifact source provider container for product flavor size '"
                            + name
                            + "' check",
                    3,
                    extraSourceProviderContainers.size());

            // query to validate presence
            ModelHelper.getSourceProviderContainer(
                    extraSourceProviderContainers, ARTIFACT_ANDROID_TEST);

            SourceProviderContainer sourceProviderContainer =
                    ModelHelper.getSourceProviderContainer(
                            extraSourceProviderContainers, "__test__");
            assertNotNull(
                    "Custom source provider container for " + name + " null check",
                    sourceProviderContainer);

            assertEquals(
                    "Custom artifact source provider for " + name + " name check",
                    "__test__",
                    sourceProviderContainer.getArtifactName());

            assertEquals(
                    "Extra artifact source provider for " + name + " value check",
                    "productFlavor:" + name,
                    sourceProviderContainer.getSourceProvider().getManifestFile().getPath());
        }

    }

    @Test
    public void checkExtraArtifactIsInVariant() {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        for (Variant variant : model.getOnlyModel().getVariants()) {
            String name = variant.getName();
            Collection<JavaArtifact> javaArtifacts = variant.getExtraJavaArtifacts();
            TruthHelper.assertThat(javaArtifacts).hasSize((int) DEFAULT_EXTRA_JAVA_ARTIFACTS + 1);
            JavaArtifact javaArtifact =
                    javaArtifacts
                            .stream()
                            .filter(e -> e.getName().equals("__test__"))
                            .findFirst()
                            .orElseThrow(AssertionError::new);
            assertEquals("assemble:" + name, javaArtifact.getAssembleTaskName());
            assertEquals("compile:" + name, javaArtifact.getCompileTaskName());
            assertEquals(new File("classesFolder:" + name), javaArtifact.getClassesFolder());

            SourceProvider variantSourceProvider = javaArtifact.getVariantSourceProvider();
            assertNotNull(variantSourceProvider);
            assertEquals("provider:" + name, variantSourceProvider.getManifestFile().getPath());

            DependencyGraphs graph = javaArtifact.getDependencyGraphs();
            TruthHelper.assertThat(helper.on(graph).withType(JAVA).asList()).isNotEmpty();
        }

    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        TruthHelper.assertThat(
                        TestFileUtils.sha1NormalizedLineEndings(project.file("build.gradle")))
                .isEqualTo("075b7b983ad2d77a378536f181f3cf17a758380c");
    }
}
