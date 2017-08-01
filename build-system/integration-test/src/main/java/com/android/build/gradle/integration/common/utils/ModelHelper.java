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

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.FilterData;
import com.android.build.VariantOutput;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * @param variants the list of variants
     * @param variantName the name of the variant to return
     * @return the output file, always, or assert before.
     */
    @NonNull
    public static File findOutputFileByVariantName(
            @NonNull Collection<Variant> variants,
            @NonNull String variantName) {

        Variant variant = findVariantByName(variants, variantName);
        assertNotNull(
                "variant '" + variantName + "' null-check",
                variant);

        AndroidArtifact artifact = variant.getMainArtifact();
        assertNotNull(
                "variantName '" + variantName + "' main artifact null-check",
                artifact);

        Collection<AndroidArtifactOutput> variantOutputs = artifact.getOutputs();
        assertNotNull(
                "variantName '" + variantName + "' outputs null-check",
                variantOutputs);
        // we only support single output artifact in this helper method.
        assertEquals(
                "variantName '" + variantName + "' outputs size check",
                1,
                variantOutputs.size());

        AndroidArtifactOutput output = variantOutputs.iterator().next();
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

        // test the main instrumentTest source provider
        SourceProviderContainer testSourceProviders = getSourceProviderContainer(
                defaultConfig.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);

        new SourceProviderHelper(model.getName(), projectDir,
                ANDROID_TEST.getPrefix(), testSourceProviders.getSourceProvider())
                .test();

        // test the source provider for the build types
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertEquals("Build Type Count", 2, buildTypes.size());

        for (BuildTypeContainer btContainer : model.getBuildTypes()) {
            new SourceProviderHelper(
                    model.getName(),
                    projectDir,
                    btContainer.getBuildType().getName(),
                    btContainer.getSourceProvider())
                    .test();

            // For every build type there's the unit test source provider.
            assertEquals(1, btContainer.getExtraSourceProviders().size());
        }
    }

    public static void compareDebugAndReleaseOutput(@NonNull AndroidProject model) {
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // debug variant
        Variant debugVariant = getVariant(variants, DEBUG);

        // debug artifact
        AndroidArtifact debugMainInfo = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainInfo);

        Collection<AndroidArtifactOutput> debugMainOutputs = debugMainInfo.getOutputs();
        assertNotNull("Debug main output null-check", debugMainOutputs);

        // release variant
        Variant releaseVariant = getVariant(variants, "release");

        AndroidArtifact relMainInfo = releaseVariant.getMainArtifact();
        assertNotNull("Release main info null-check", relMainInfo);

        Collection<AndroidArtifactOutput> relMainOutputs = relMainInfo.getOutputs();
        assertNotNull("Rel Main output null-check", relMainOutputs);

        File debugFile = debugMainOutputs.iterator().next().getOutputFile();
        File releaseFile = relMainOutputs.iterator().next().getOutputFile();

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

    @NonNull
    public static Collection<JavaArtifact> getExtraJavaArtifacts(@NonNull AndroidProject project) {
        return getDebugVariant(project).getExtraJavaArtifacts();
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
        return searchForExistingItem(items, name, flavor -> flavor.getProductFlavor().getName(),
                "ArtifactMetaData");
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
}
