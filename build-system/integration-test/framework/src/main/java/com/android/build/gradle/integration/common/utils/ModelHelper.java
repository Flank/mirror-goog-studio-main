/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility helper to help read/test the AndroidProject Model.
 */
public class ModelHelper {

    /**
     * Returns a Variant object from a given name
     * @param variants the list of variants
     * @param variantName the name of the variant to return
     * @return the matching variant or null if not found
     */
    @Nullable
    public static Variant findVariantByName(
            @NonNull Collection<Variant> variants,
            @NonNull String variantName) {
        for (Variant item : variants) {
            if (variantName.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    /**
     * Returns the APK file for a single-output variant.
     *
     * @param variantOutputs the list of variants from the post-build model
     * @param variantName the name of the variant to return
     * @return the output file, always, or assert before.
     */
    @NonNull
    public static File findOutputFileByVariantName(
            @NonNull Collection<VariantBuildOutput> variantOutputs, @NonNull String variantName) {

        VariantBuildOutput variantOutput =
                ModelHelper.getVariantBuildOutput(variantOutputs, variantName);
        assertNotNull("variant '" + variantName + "' null-check", variantOutput);

        Collection<OutputFile> variantOutputFiles = variantOutput.getOutputs();
        assertNotNull("variantName '" + variantName + "' outputs null-check", variantOutputFiles);
        // we only support single output artifact in this helper method.
        assertEquals(
                "variantName '" + variantName + "' outputs size check",
                1,
                variantOutputFiles.size());

        OutputFile output = variantOutputFiles.iterator().next();
        assertNotNull(
                "variantName '" + variantName + "' single output null-check",
                output);

        File outputFile = output.getOutputFile();
        assertNotNull("variantName '" + variantName + "' mainOutputFile null-check", outputFile);

        return outputFile;
    }

    public static void testDefaultSourceSets(
            @NonNull AndroidProject model,
            @NonNull File projectDir) {
        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        // test the main source provider
        new SourceProviderHelper(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .test();

        // test the main androidTest source provider
        SourceProviderContainer androidTestSourceProviders =
                getSourceProviderContainer(
                        defaultConfig.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);

        new SourceProviderHelper(
                        model.getName(),
                        projectDir,
                        ANDROID_TEST.getPrefix(),
                        androidTestSourceProviders.getSourceProvider())
                .test();

        // test the source provider for the build types
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertThat(buildTypes).named("build types").hasSize(2);

        String testedBuildType = findTestedBuildType(model);

        for (BuildTypeContainer btContainer : model.getBuildTypes()) {
            new SourceProviderHelper(
                    model.getName(),
                    projectDir,
                    btContainer.getBuildType().getName(),
                    btContainer.getSourceProvider())
                    .test();

            // For every build type there's the unit test source provider and the android test
            // one (optional).
            final Set<String> extraSourceProviderNames =
                    btContainer
                            .getExtraSourceProviders()
                            .stream()
                            .map(SourceProviderContainer::getArtifactName)
                            .collect(Collectors.toSet());

            if (btContainer.getBuildType().getName().equals(testedBuildType)) {
                assertThat(extraSourceProviderNames)
                        .containsExactly(ARTIFACT_ANDROID_TEST, ARTIFACT_UNIT_TEST);
            } else {
                assertThat(extraSourceProviderNames).containsExactly(ARTIFACT_UNIT_TEST);
            }
        }
    }

    public static void compareDebugAndReleaseOutput(@NonNull ProjectBuildOutput model) {
        Collection<VariantBuildOutput> variants = model.getVariantsBuildOutput();
        assertEquals("Variant Count", 2, variants.size());

        // debug variant
        VariantBuildOutput debugVariant = getVariantBuildOutput(variants, DEBUG);

        // release variant
        VariantBuildOutput releaseVariant = getVariantBuildOutput(variants, RELEASE);

        File debugFile = Iterables.getOnlyElement(debugVariant.getOutputs()).getOutputFile();
        File releaseFile = Iterables.getOnlyElement(releaseVariant.getOutputs()).getOutputFile();

        assertFalse("debug: " + debugFile + " / release: " + releaseFile,
                debugFile.equals(releaseFile));
    }


    @NonNull
    public static Variant getVariant(
            @NonNull Collection<Variant> items,
            @NonNull String name) {
        return searchForExistingItem(items, name, Variant::getName, "Variant");
    }

    @NonNull
    public static Variant getDebugVariant(@NonNull AndroidProject project) {
        return getVariant(project.getVariants(), "debug");
    }

    @NonNull
    public static AndroidArtifact getDebugArtifact(@NonNull AndroidProject project) {
        return getDebugVariant(project).getMainArtifact();
    }

    public static AndroidArtifact getAndroidTestArtifact(
            @NonNull AndroidProject project, @NonNull String variantName) {
        return getAndroidArtifact(
                getVariant(project.getVariants(), variantName).getExtraAndroidArtifacts(),
                ARTIFACT_ANDROID_TEST);
    }

    public static JavaArtifact getUnitTestArtifact(
            @NonNull AndroidProject project, @NonNull String variantName) {
        return getJavaArtifact(
                getVariant(project.getVariants(), variantName).getExtraJavaArtifacts(),
                ARTIFACT_UNIT_TEST);
    }

    /** deprecated Use {@link #getAndroidTestArtifact(AndroidProject, String)} */
    @Deprecated
    @NonNull
    public static AndroidArtifact getAndroidTestArtifact(@NonNull AndroidProject project) {
        return getAndroidTestArtifact(project, "debug");
    }

    /** @deprecated Use {@link #getUnitTestArtifact(AndroidProject, String)} */
    @Deprecated
    @NonNull
    public static JavaArtifact getUnitTestArtifact(@NonNull AndroidProject project) {
        return getUnitTestArtifact(project, "debug");
    }

    /**
     * Gets the VariantBuildOutput with the given name.
     *
     * @param items the build outputs to search
     * @param name the name to match, e.g. {@link com.android.builder.core.BuilderConstants#DEBUG}
     * @return the only item with the given name
     * @throws AssertionError if no items match or if multiple items match
     */
    @NonNull
    public static VariantBuildOutput getVariantBuildOutput(
            @NonNull Collection<VariantBuildOutput> items, @NonNull String name) {
        return searchForExistingItem(
                items, name, VariantBuildOutput::getName, "VariantBuildOutput");
    }

    /**
     * return the only item with the given name, or throw an exception if 0 or 2+ items match
     */
    @NonNull
    public static AndroidArtifact getAndroidArtifact(
            @NonNull Collection<AndroidArtifact> items,
            @NonNull String name) {
        return searchForExistingItem(items, name, AndroidArtifact::getName, "AndroidArtifact");
    }

    /**
     * Gets the java artifact with the given name.
     *
     * @param items the java artifacts to search
     * @param name the name to match, e.g. {@link AndroidProject#ARTIFACT_UNIT_TEST}
     * @return the only item with the given name
     * @throws AssertionError if no items match or if multiple items match
     */
    @NonNull
    public static JavaArtifact getJavaArtifact(
            @NonNull Collection<JavaArtifact> items, @NonNull String name) {
        return searchForExistingItem(items, name, JavaArtifact::getName, "JavaArtifact");
    }

    /**
     * search for an item matching the name and return it if found.
     *
     */
    @Nullable
    public static AndroidArtifact getOptionalAndroidArtifact(
            @NonNull Collection<AndroidArtifact> items,
            @NonNull String name) {
        return searchForOptionalItem(items, name, AndroidArtifact::getName);
    }

    @NonNull
    public static SigningConfig getSigningConfig(
            @NonNull Collection<SigningConfig> items,
            @NonNull String name) {
        return searchForExistingItem(items, name, SigningConfig::getName, "SigningConfig");
    }

    @NonNull
    public static SourceProviderContainer getSourceProviderContainer(
            @NonNull Collection<SourceProviderContainer> items,
            @NonNull String name) {
        return searchForExistingItem(
                items, name, SourceProviderContainer::getArtifactName, "SourceProviderContainer");
    }

    @Nullable
    public static String getFilter(
            @NonNull VariantOutput variantOutput, @NonNull String filterType) {
        for (FilterData filterData : variantOutput.getFilters()) {
            if (filterData.getFilterType().equals(filterType)) {
                return filterData.getIdentifier();
            }
        }
        return null;
    }

    @NonNull
    public static ArtifactMetaData getArtifactMetaData(
            @NonNull Collection<ArtifactMetaData> items,
            @NonNull String name) {
        return searchForExistingItem(items, name, ArtifactMetaData::getName,
                "ArtifactMetaData");
    }

    @NonNull
    public static ProductFlavorContainer getProductFlavor(
            @NonNull Collection<ProductFlavorContainer> items,
            @NonNull String name) {
        return searchForExistingItem(
                items,
                name,
                flavor -> flavor.getProductFlavor().getName(),
                "ProductFlavorContainer");
    }

    @Nullable
    public static String findTestedBuildType(@NonNull AndroidProject project) {
        return project.getVariants()
                .stream()
                .filter(
                        variant ->
                                getOptionalAndroidArtifact(
                                                variant.getExtraAndroidArtifacts(),
                                                ARTIFACT_ANDROID_TEST)
                                        != null)
                .map(Variant::getBuildType)
                .findAny()
                .orElse(null);
    }

    /**
     * Returns the generates sources commands for all projects for the debug variant.
     *
     * <p>These are the commands studio will call after sync.
     *
     * <p>For example, for a project with a single app subproject these might be:
     *
     * <ul>
     * <li>:app:generateDebugSources
     * <li>:app:generateDebugAndroidTestSources
     * <li>:app:mockableAndroidJar
     * <li>:app:prepareDebugUnitTestDependencies
     * </ul>
     */
    @NonNull
    public static List<String> getDebugGenerateSourcesCommands(
            @NonNull Map<String, AndroidProject> model) {
        return getGenerateSourcesCommands(model, project -> "debug");
    }

    @NonNull
    public static List<String> getGenerateSourcesCommands(
            @NonNull Map<String, AndroidProject> model,
            @NonNull Function<String, String> projectToVariantName) {

        ImmutableList.Builder<String> commands = ImmutableList.builder();
        for (Map.Entry<String, AndroidProject> entry : model.entrySet()) {
            Variant debug =
                    ModelHelper.getVariant(
                            entry.getValue().getVariants(),
                            projectToVariantName.apply(entry.getKey()));
            commands.add(entry.getKey() + ":" + debug.getMainArtifact().getSourceGenTaskName());
            for (AndroidArtifact artifact : debug.getExtraAndroidArtifacts()) {
                commands.add(entry.getKey() + ":" + artifact.getSourceGenTaskName());
            }
            for (JavaArtifact artifact : debug.getExtraJavaArtifacts()) {
                for (String taskName : artifact.getIdeSetupTaskNames()) {
                    commands.add(entry.getKey() + ":" + taskName);
                }
            }
        }
        return commands.build();
    }

    @Nullable
    private static <T> T searchForOptionalItem(
            @NonNull Collection<T> items,
            @NonNull String name,
            @NonNull Function<T, String> nameFunction) {
        return searchForSingleItemInList(items, name, nameFunction).orElse(null);

    }

    @NonNull
    private static <T> T searchForExistingItem(
            @NonNull Collection<T> items,
            @NonNull String name,
            @NonNull Function<T, String> nameFunction,
            @NonNull String className) {
        return searchForSingleItemInList(items, name, nameFunction)
                .orElseThrow(() -> new AssertionError(
                        "Unable to find " + className + " '" + name + "'. Options are: " + items.stream()
                                .map(nameFunction)
                                .collect(Collectors.toList())));
    }

    @VisibleForTesting
    @NonNull
    static <T> Optional<T> searchForSingleItemInList(
            @NonNull Collection<T> items,
            @NonNull String name,
            @NonNull Function<T, String> nameFunction) {
        return items.stream()
                .filter(item -> name.equals(nameFunction.apply(item)))
                .reduce(toSingleItem());
    }

    /**
     * The goal of this operator is not to reduce anything but to ensure that
     * there is a single item in the list. If it gets called it means
     * that there are two object in the list that had the same name, and this is an error.
     *
     * @see #searchForSingleItemInList(Collection, String, Function)
     */
    private static <T> BinaryOperator<T> toSingleItem() {
        return (name1, name2) -> {
            throw new IllegalArgumentException("Duplicate objects with name: " + name1);
        };
    }

    /**
     * Searches the given collection of OutputFiles and returns the single item with outputType
     * equal to {@link VariantOutput#MAIN}.
     *
     * @param outputFiles the outputFiles to search
     * @return the single item with type MAIN
     * @throws AssertionError if none of the outputFiles has type MAIN
     * @throws IllegalArgumentException if multiple items have type MAIN
     */
    public static OutputFile getMainOutputFile(Collection<OutputFile> outputFiles) {
        return outputFiles
                .stream()
                .filter(file -> file.getOutputType().equals(VariantOutput.MAIN))
                .reduce(toSingleItem())
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Unable to find main output file. Options are: "
                                                + outputFiles)); // Unsure about this
    }

    /**
     * Convenience method to verify that the given ProjectBuildOutput contains exactly two variants,
     * then return the "debug" variant. This is most useful for integration tests building projects
     * with no extra buildTypes and no specified productFlavors.
     *
     * @param model the post-build model
     * @return the build output for the "debug" variant
     * @throws AssertionError if the model contains more than two variants, or does not have a
     *     "debug" variant
     */
    public static VariantBuildOutput getDebugVariantBuildOutput(ProjectBuildOutput model) {
        Collection<VariantBuildOutput> variantBuildOutputs = model.getVariantsBuildOutput();
        assertThat(variantBuildOutputs).hasSize(2);
        VariantBuildOutput debugVariantOutput = getVariantBuildOutput(variantBuildOutputs, DEBUG);
        assertThat(debugVariantOutput).isNotNull();
        return debugVariantOutput;
    }
}
