/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.lint.checks.infrastructure.GradleModelMocker;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Collection;
import junit.framework.TestCase;

public class LintInspectionBridgeTest extends TestCase {
    public static void checkFindLibrary(boolean useBuildCache) {
        File dir = new File("some" + File.separator + "dir");

        GradleModelMocker mocker = new GradleModelMocker(""
                + "apply plugin: 'com.android.application'\n"
                + "\n"
                + "dependencies {\n"
                + "    compile \"com.android.support:appcompat-v7:25.0.1\"\n"
                + "    compile \"com.android.support.constraint:constraint-layout:1.0.0-beta3\"\n"
                + "}")
                .withDependencyGraph(""
                        + "+--- com.android.support:appcompat-v7:25.0.1\n"
                        + "|    +--- com.android.support:support-v4:25.0.1\n"
                        + "|    |    +--- com.android.support:support-compat:25.0.1\n"
                        + "|    |    |    \\--- com.android.support:support-annotations:25.0.1\n"
                        + "|    |    +--- com.android.support:support-media-compat:25.0.1\n"
                        + "|    |    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                        + "|    |    +--- com.android.support:support-core-utils:25.0.1\n"
                        + "|    |    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                        + "|    |    +--- com.android.support:support-core-ui:25.0.1\n"
                        + "|    |    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                        + "|    |    \\--- com.android.support:support-fragment:25.0.1\n"
                        + "|    |         +--- com.android.support:support-compat:25.0.1 (*)\n"
                        + "|    |         +--- com.android.support:support-media-compat:25.0.1 (*)\n"
                        + "|    |         +--- com.android.support:support-core-ui:25.0.1 (*)\n"
                        + "|    |         \\--- com.android.support:support-core-utils:25.0.1 (*)\n"
                        + "|    +--- com.android.support:support-vector-drawable:25.0.1\n"
                        + "|    |    \\--- com.android.support:support-compat:25.0.1 (*)\n"
                        + "|    \\--- com.android.support:animated-vector-drawable:25.0.1\n"
                        + "|         \\--- com.android.support:support-vector-drawable:25.0.1 (*)\n"
                        + "+--- com.android.support.constraint:constraint-layout:1.0.0-beta3\n"
                        + "|    \\--- com.android.support.constraint:constraint-layout-solver:1.0.0-beta3\n")
                .withProjectDir(dir)
                .withBuildCache(useBuildCache);

        Dependencies dependencies = mocker.getVariant().getMainArtifact().getDependencies();

        LintInspectionBridge bridge = new LibraryFinderBridge(dependencies);

        // Android library
        Library lib = findLibrary(dependencies.getLibraries(), "com.android.support",
                "support-core-ui");
        assertThat(lib).isNotNull();
        String jar = ((AndroidLibrary)lib).getJarFile().getPath();
        if (useBuildCache) {
            assertThat(jar).doesNotContain("exploded-aar");
        } else {
            assertThat(jar).contains("exploded-aar");
        }
        Library library = bridge.findOwnerLibrary(jar);
        assertThat(library).isNotNull();
        MavenCoordinates coordinates = library.getResolvedCoordinates();
        assertThat(coordinates.getGroupId()).isEqualTo("com.android.support");
        assertThat(coordinates.getArtifactId()).isEqualTo("support-core-ui");
        assertThat(coordinates.getVersion()).isEqualTo("25.0.1");

        // Java library
        lib = findLibrary(dependencies.getLibraries(), "com.android.support",
                "support-annotations");
        assertThat(lib).isNotNull();
        jar = ((JavaLibrary)lib).getJarFile().getPath();
        assertThat(jar).doesNotContain("exploded-aar"); // both with and without cache
        library = bridge.findOwnerLibrary(jar);
        assertThat(library).isNotNull();
        coordinates = library.getResolvedCoordinates();
        assertThat(coordinates.getGroupId()).isEqualTo("com.android.support");
        assertThat(coordinates.getArtifactId()).isEqualTo("support-annotations");
        assertThat(coordinates.getVersion()).isEqualTo("25.0.1");
    }

    @Nullable
    private static Library findLibrary(Collection<? extends Library> collection, String group,
            String artifact) {
        for (Library library : collection) {
            MavenCoordinates coordinates = library.getResolvedCoordinates();
            if (coordinates.getGroupId().equals(group)
                    && coordinates.getArtifactId().equals(artifact)) {
                return library;
            }
            if (library instanceof AndroidLibrary) {
                AndroidLibrary androidLibrary = (AndroidLibrary) library;
                library = findLibrary(androidLibrary.getLibraryDependencies(), group, artifact);
                if (library != null) {
                    return library;
                }
                library = findLibrary(androidLibrary.getJavaDependencies(), group, artifact);
                if (library != null) {
                    return library;
                }
            } else if (library instanceof JavaLibrary) {
                JavaLibrary javaLibrary = (JavaLibrary) library;
                library = findLibrary(javaLibrary.getDependencies(), group, artifact);
                if (library != null) {
                    return library;
                }
            }

        }
        return null;
    }

    public void testFindOwnerLibraryWithoutBuildCache() {
        checkFindLibrary(false);
    }

    public void testFindOwnerLibraryWithBuildCache() {
        checkFindLibrary(true);
    }

    private static class LibraryFinderBridge extends LintInspectionBridge {

        private final Dependencies dependencies;

        public LibraryFinderBridge(Dependencies dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public void report(@NonNull Issue issue, @NonNull PsiElement locationNode,
                @NonNull PsiElement scopeNode, @NonNull String message) {
            fail("Not used in this test");
        }

        @Override
        public boolean isTestSource() {
            fail("Not used in this test");
            return false;
        }

        @Nullable
        @Override
        public Dependencies getDependencies() {
            return dependencies;
        }

        @NonNull
        @Override
        public JavaEvaluator getEvaluator() {
            fail("Not used in this test");
            return null;
        }
    }
}