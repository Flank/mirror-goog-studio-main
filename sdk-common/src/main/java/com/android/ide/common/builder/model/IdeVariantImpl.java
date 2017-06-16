/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.ide.common.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.gradle.tooling.model.UnsupportedMethodException;

/** Creates a deep copy of a {@link Variant}. */
public final class IdeVariantImpl extends IdeModel implements IdeVariant {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final String myName;
    @NonNull private final String myDisplayName;
    @NonNull private final IdeAndroidArtifact myMainArtifact;
    @NonNull private final Collection<AndroidArtifact> myExtraAndroidArtifacts;
    @NonNull private final Collection<JavaArtifact> myExtraJavaArtifacts;
    @NonNull private final String myBuildType;
    @NonNull private final List<String> myProductFlavors;
    @NonNull private final ProductFlavor myMergedFlavor;
    @NonNull private final Collection<TestedTargetVariant> myTestedTargetVariants;
    private final int myHashCode;

    public IdeVariantImpl(
            @NonNull Variant variant,
            @NonNull ModelCache modelCache,
            @NonNull IdeLevel2DependenciesFactory dependenciesFactory,
            @Nullable GradleVersion modelVersion) {
        super(variant, modelCache);
        myName = variant.getName();
        myDisplayName = variant.getDisplayName();
        myMainArtifact =
                modelCache.computeIfAbsent(
                        variant.getMainArtifact(),
                        artifact ->
                                new IdeAndroidArtifactImpl(
                                        artifact, modelCache, dependenciesFactory, modelVersion));
        myExtraAndroidArtifacts =
                copy(
                        variant.getExtraAndroidArtifacts(),
                        modelCache,
                        artifact ->
                                new IdeAndroidArtifactImpl(
                                        artifact, modelCache, dependenciesFactory, modelVersion));
        myExtraJavaArtifacts =
                copy(
                        variant.getExtraJavaArtifacts(),
                        modelCache,
                        (Function<JavaArtifact, JavaArtifact>)
                                artifact ->
                                        new IdeJavaArtifact(
                                                artifact,
                                                modelCache,
                                                dependenciesFactory,
                                                modelVersion));
        myBuildType = variant.getBuildType();
        myProductFlavors = ImmutableList.copyOf(variant.getProductFlavors());
        myMergedFlavor =
                modelCache.computeIfAbsent(
                        variant.getMergedFlavor(),
                        flavor -> new IdeProductFlavor(flavor, modelCache));
        myTestedTargetVariants = getTestedTargetVariants(variant, modelCache);

        myHashCode = calculateHashCode();
    }

    @NonNull
    private static Collection<TestedTargetVariant> getTestedTargetVariants(
            @NonNull Variant variant, @NonNull ModelCache modelCache) {
        try {
            return copy(
                    variant.getTestedTargetVariants(),
                    modelCache,
                    targetVariant -> new IdeTestedTargetVariant(targetVariant, modelCache));
        } catch (UnsupportedMethodException e) {
            return Collections.emptyList();
        }
    }

    private static void forEachArtifact(
            @NonNull Collection<? extends BaseArtifact> artifacts,
            @NonNull Consumer<IdeBaseArtifact> action) {
        for (BaseArtifact artifact : artifacts) {
            action.accept((IdeBaseArtifact) artifact);
        }
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return myDisplayName;
    }

    @Override
    @NonNull
    public IdeAndroidArtifact getMainArtifact() {
        return myMainArtifact;
    }

    @Override
    @NonNull
    public Collection<AndroidArtifact> getExtraAndroidArtifacts() {
        return myExtraAndroidArtifacts;
    }

    @Override
    @NonNull
    public Collection<JavaArtifact> getExtraJavaArtifacts() {
        return myExtraJavaArtifacts;
    }

    @Override
    @NonNull
    public String getBuildType() {
        return myBuildType;
    }

    @Override
    @NonNull
    public List<String> getProductFlavors() {
        return myProductFlavors;
    }

    @Override
    @NonNull
    public ProductFlavor getMergedFlavor() {
        return myMergedFlavor;
    }

    @Override
    @NonNull
    public Collection<TestedTargetVariant> getTestedTargetVariants() {
        return myTestedTargetVariants;
    }

    @Override
    @NonNull
    public Collection<IdeBaseArtifact> getTestArtifacts() {
        ImmutableSet.Builder<IdeBaseArtifact> testArtifacts = ImmutableSet.builder();
        Consumer<IdeBaseArtifact> action =
                artifact -> {
                    if (artifact.isTestArtifact()) {
                        testArtifacts.add(artifact);
                    }
                };
        forEachArtifact(myExtraAndroidArtifacts, action);
        forEachArtifact(myExtraJavaArtifacts, action);
        return testArtifacts.build();
    }

    @Override
    @Nullable
    public IdeAndroidArtifact getAndroidTestArtifact() {
        for (AndroidArtifact artifact : myExtraAndroidArtifacts) {
            IdeAndroidArtifactImpl ideArtifact = (IdeAndroidArtifactImpl) artifact;
            if (ideArtifact.isTestArtifact()) {
                return ideArtifact;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public IdeJavaArtifact getUnitTestArtifact() {
        for (JavaArtifact artifact : myExtraJavaArtifacts) {
            IdeJavaArtifact ideArtifact = (IdeJavaArtifact) artifact;
            if (ideArtifact.isTestArtifact()) {
                return ideArtifact;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeVariantImpl)) {
            return false;
        }
        IdeVariantImpl variant = (IdeVariantImpl) o;
        return Objects.equals(myName, variant.myName)
                && Objects.equals(myDisplayName, variant.myDisplayName)
                && Objects.equals(myMainArtifact, variant.myMainArtifact)
                && Objects.equals(myExtraAndroidArtifacts, variant.myExtraAndroidArtifacts)
                && Objects.equals(myExtraJavaArtifacts, variant.myExtraJavaArtifacts)
                && Objects.equals(myBuildType, variant.myBuildType)
                && Objects.equals(myProductFlavors, variant.myProductFlavors)
                && Objects.equals(myMergedFlavor, variant.myMergedFlavor)
                && Objects.equals(myTestedTargetVariants, variant.myTestedTargetVariants);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myName,
                myDisplayName,
                myMainArtifact,
                myExtraAndroidArtifacts,
                myExtraJavaArtifacts,
                myBuildType,
                myProductFlavors,
                myMergedFlavor,
                myTestedTargetVariants);
    }

    @Override
    public String toString() {
        return "IdeVariant{"
                + "myName='"
                + myName
                + '\''
                + ", myDisplayName='"
                + myDisplayName
                + '\''
                + ", myMainArtifact="
                + myMainArtifact
                + ", myExtraAndroidArtifacts="
                + myExtraAndroidArtifacts
                + ", myExtraJavaArtifacts="
                + myExtraJavaArtifacts
                + ", myBuildType='"
                + myBuildType
                + '\''
                + ", myProductFlavors="
                + myProductFlavors
                + ", myMergedFlavor="
                + myMergedFlavor
                + ", myTestedTargetVariants="
                + myTestedTargetVariants
                + "}";
    }
}
