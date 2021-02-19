/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.testing.unit;

import static com.android.SdkConstants.FN_R_CLASS_JAR;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.android.testutils.truth.PathSubject.assertThat;
import static java.util.stream.Collectors.toList;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.Variant;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.truth.Truth;
import java.io.File;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/** Tests for the unit-tests related parts of the builder model. */
public class UnitTestingModelTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTestingComplexProject").create();

    @Test
    public void unitTestingArtifactsAreIncludedInTheModel() throws Exception {
        // Build the project, so we can verify paths in the model exist.
        project.executor().run("test");

        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModelMap().get(":app");

        Truth.assertThat(
                        model.getExtraArtifacts()
                                .stream()
                                .map(ArtifactMetaData::getName)
                                .collect(toList()))
                .containsExactly(AndroidProject.ARTIFACT_ANDROID_TEST, ARTIFACT_UNIT_TEST);

        ArtifactMetaData unitTestMetadata =
                model.getExtraArtifacts()
                        .stream()
                        .filter(it -> it.getName().equals(ARTIFACT_UNIT_TEST))
                        .findFirst()
                        .get();

        Truth.assertThat(unitTestMetadata.isTest()).isTrue();
        Truth.assertThat(unitTestMetadata.getType()).isEqualTo(ArtifactMetaData.TYPE_JAVA);

        for (Variant variant : model.getVariants()) {
            Truth.assertThat(variant.getMainArtifact().getAdditionalClassesFolders())
                    .containsExactly(
                            new File(
                                    ArtifactTypeUtil.getOutputDir(
                                            InternalArtifactType
                                                    .COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                                                    .INSTANCE,
                                            project.getSubproject("app").getBuildDir()),
                                    variant.getName() + "/" + FN_R_CLASS_JAR),
                            project.file("app/build/tmp/kotlin-classes/" + variant.getName()));

            List<JavaArtifact> unitTestArtifacts =
                    variant.getExtraJavaArtifacts()
                            .stream()
                            .filter(it -> it.getName().equals(ARTIFACT_UNIT_TEST))
                            .collect(toList());
            Truth.assertThat(unitTestArtifacts).hasSize(1);

            JavaArtifact unitTestArtifact = unitTestArtifacts.get(0);
            Truth.assertThat(unitTestArtifact.getName()).isEqualTo(ARTIFACT_UNIT_TEST);
            Truth.assertThat(unitTestArtifact.getAssembleTaskName()).contains("UnitTest");
            Truth.assertThat(unitTestArtifact.getAssembleTaskName())
                    .contains(StringHelper.usLocaleCapitalize(variant.getName()));
            Truth.assertThat(unitTestArtifact.getCompileTaskName()).contains("UnitTest");
            Truth.assertThat(unitTestArtifact.getCompileTaskName())
                    .contains(StringHelper.usLocaleCapitalize(variant.getName()));

            // No per-variant source code.
            Truth.assertThat(unitTestArtifact.getVariantSourceProvider()).isNull();
            assertThat(unitTestArtifact.getMultiFlavorSourceProvider()).isNull();

            assertThat(variant.getMainArtifact().getClassesFolder()).isDirectory();
            assertThat(variant.getMainArtifact().getJavaResourcesFolder()).isDirectory();
            assertThat(unitTestArtifact.getClassesFolder()).isDirectory();
            assertThat(unitTestArtifact.getJavaResourcesFolder()).isDirectory();

            assertThat(unitTestArtifact.getClassesFolder())
                    .isNotEqualTo(variant.getMainArtifact().getClassesFolder());
            assertThat(unitTestArtifact.getJavaResourcesFolder())
                    .isNotEqualTo(variant.getMainArtifact().getJavaResourcesFolder());

            assertThat(unitTestArtifact.getAdditionalClassesFolders())
                    .containsExactly(
                            project.file(
                                    "app/build/tmp/kotlin-classes/"
                                            + variant.getName()
                                            + "UnitTest"),
                            project.file(
                                    "app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/"
                                            + variant.getName()
                                            + "/R.jar"));
        }

        SourceProvider sourceProvider =
                model.getDefaultConfig()
                        .getExtraSourceProviders()
                        .stream()
                        .filter(it -> it.getArtifactName().equals(ARTIFACT_UNIT_TEST))
                        .findFirst()
                        .get()
                        .getSourceProvider();

        Truth.assertThat(sourceProvider.getJavaDirectories()).hasSize(1);
        Truth.assertThat(sourceProvider.getJavaDirectories().iterator().next().getAbsolutePath())
                .endsWith(FileUtils.join("test", "java"));
        Truth.assertThat(sourceProvider.getKotlinDirectories()).hasSize(2);
        Truth.assertThat(sourceProvider.getKotlinDirectories())
                .containsExactly(
                        project.file("app/src/test/java"), project.file("app/src/test/kotlin"));
    }

    @Test
    public void flavors() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "android {\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors { paid; free }\n"
                        + "}");
        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModelMap().get(":app");

        assertThat(model.getProductFlavors()).hasSize(2);

        for (ProductFlavorContainer flavor : model.getProductFlavors()) {
            SourceProvider sourceProvider =
                    flavor.getExtraSourceProviders()
                            .stream()
                            .filter(it -> it.getArtifactName().equals(ARTIFACT_UNIT_TEST))
                            .findAny()
                            .get()
                            .getSourceProvider();

            assertThat(sourceProvider.getJavaDirectories()).hasSize(1);
            String flavorDir =
                    StringHelper.appendCapitalized("test", flavor.getProductFlavor().getName());
            assertThat(sourceProvider.getJavaDirectories().iterator().next().getAbsolutePath())
                    .endsWith(flavorDir + File.separator + "java");
            Truth.assertThat(sourceProvider.getKotlinDirectories())
                    .containsExactly(
                            project.file("app/src/" + flavorDir + "/java"),
                            project.file("app/src/" + flavorDir + "/kotlin"));
        }
    }
}
