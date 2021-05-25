/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.testutils.ApiTester;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class PseudoApiChangesTest {

    private static final URL API_LIST_URL =
            Resources.getResource(PseudoApiChangesTest.class, "pseudo-api.txt");

    private static final ImmutableSet<String> EXCLUDED_IMPL_CLASSES =
            ImmutableSet.of(
                    "AndroidSourceSetFactory",
                    "NoOpDeprecationReporter",
                    "VariantOutputFactory",
                    "ApkInfoParser",
                    "BuilderConstants",
                    "DefaultManifestParser",
                    "DefaultApiVersion",
                    "LibraryRequest",
                    "ManifestAttributeSupplier",
                    "StandardOutErrMessageReceiver",
                    "ToolsRevisionUtils",
                    "VariantType",
                    "VariantTypeImpl");

    @Test
    public void stableImplementationClassesTest() throws IOException {
        getApiTester().checkApiElements();
    }

    static ApiTester getApiTester() throws IOException {

        ImmutableSet<ClassPath.ClassInfo> allClasses = getPseudoApiClasses();

        List<ClassPath.ClassInfo> classes =
                allClasses.stream()
                        .filter(
                                classInfo ->
                                        !classInfo.getSimpleName().endsWith("Test")
                                                && !classInfo.getSimpleName().endsWith("TestKt")
                                                && !classInfo
                                                        .getSimpleName()
                                                        .equals("StableApiUpdater")
                                                && !classInfo
                                                        .getSimpleName()
                                                        .contains("DecoratedApiChangesUpdater"))
                        .collect(Collectors.toList());

        return new ApiTester(
                "The Android Gradle Plugin internal implementation classes.",
                classes,
                ApiTester.Filter.ALL,
                "The internal implementation classes"
                        + " have changed, either revert "
                        + "the change or re-run PseudoApiUpdater.main[] from the IDE "
                        + "to update the API file.\n"
                        + "PseudoApiUpdater will apply the following changes if run:\n",
                API_LIST_URL,
                ApiTester.Flag.OMIT_HASH);
    }

    private static ImmutableSet<ClassPath.ClassInfo> getPseudoApiClasses() throws IOException {
        ClassPath classPath = ClassPath.from(BaseExtension.class.getClassLoader());
        ImmutableSet.Builder<ClassPath.ClassInfo> builder = ImmutableSet.builder();
        builder.addAll(classPath.getTopLevelClasses("com.android.build.gradle"));
        builder.addAll(classPath.getTopLevelClassesRecursive("com.android.build.gradle.api"));
        builder.addAll(classPath.getTopLevelClasses("com.android.builder.signing").stream()
                .filter(classInfo -> classInfo.getSimpleName().equals("DefaultSigningConfig"))
                .collect(Collectors.toList()));

        Set<String> excludedImplClasses = new HashSet<>(EXCLUDED_IMPL_CLASSES);
        // Include the legacy DSL impl classes
        for (ClassPath.ClassInfo aClass :
                classPath.getTopLevelClasses("com.android.build.gradle.internal.dsl")) {
            if (aClass.getSimpleName().endsWith("Impl")
                    || excludedImplClasses.remove(aClass.getSimpleName())) {
                continue;
            }
            builder.add(aClass);
        }
        // And the legacy DSL base classes in builder.
        for (ClassPath.ClassInfo aClass : classPath.getTopLevelClasses("com.android.builder.core")) {
            if (excludedImplClasses.remove(aClass.getSimpleName())) {
                continue;
            }
            builder.add(aClass);
        }

        if (!excludedImplClasses.isEmpty()) {
            throw new IllegalStateException("Excluded classes not found: " + excludedImplClasses);
        }
        return builder.build();
    }
}
