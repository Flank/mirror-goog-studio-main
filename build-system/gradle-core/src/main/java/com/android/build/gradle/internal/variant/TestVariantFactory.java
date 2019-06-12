/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE_ONLY;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_TESTED_APKS;

import com.android.annotations.NonNull;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;

/** Customization of {@link ApplicationVariantFactory} for test-only projects. */
public class TestVariantFactory extends ApplicationVariantFactory {

    public TestVariantFactory(@NonNull GlobalScope globalScope) {
        super(globalScope);
    }

    @Override
    public boolean hasTestScope() {
        return false;
    }

    @Override
    public void preVariantWork(final Project project) {
        super.preVariantWork(project);

        TestAndroidConfig testExtension = (TestAndroidConfig) globalScope.getExtension();

        String path = testExtension.getTargetProjectPath();
        if (path == null) {
            throw new GradleException(
                    "targetProjectPath cannot be null in test project " + project.getName());
        }

        DependencyHandler handler = project.getDependencies();
        Map<String, String> projectNotation = ImmutableMap.of("path", path);
        // Add the tested project to compileOnly. This cannot be 'implementation' because of the
        // following:
        //
        // The tested project itself only publishes to api, however its transitive library module
        // dependencies are published to both api and runtime elements and would be seen in our
        // RuntimeClasspath here otherwise.
        handler.add(CONFIG_NAME_COMPILE_ONLY, handler.project(projectNotation));

        // Create a custom configuration that will be used to consume only the APK from the
        // tested project's RuntimeElements published configuration.
        Configuration testedApks = project.getConfigurations().maybeCreate(CONFIG_NAME_TESTED_APKS);
        testedApks.setCanBeConsumed(false);
        testedApks.setCanBeResolved(false);
        handler.add(CONFIG_NAME_TESTED_APKS, handler.project(projectNotation));
    }

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // don't call super as we don't want the default app version.
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(BuilderConstants.DEBUG);
        buildTypes.create(BuilderConstants.DEBUG);
    }

    @NonNull
    public static String getTestedApksConfigurationName(@NonNull String variantName) {
        return StringHelper.appendCapitalized(variantName, CONFIG_NAME_TESTED_APKS);
    }

    @NonNull
    @Override
    public Collection<VariantType> getVariantConfigurationTypes() {
        return ImmutableList.of(VariantTypeImpl.TEST_APK);
    }
}
