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
package com.android.ide.common.gradle.model.level2;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.singletonList;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Dependencies.ProjectIdentifier;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.stubs.*;
import com.android.ide.common.gradle.model.stubs.level2.AndroidLibraryStub;
import com.android.ide.common.gradle.model.stubs.level2.JavaLibraryStub;
import com.android.ide.common.gradle.model.stubs.level2.ModuleLibraryStub;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeDependenciesFactory}. */
public class IdeDependenciesFactoryTest {
    private IdeDependenciesFactory myDependenciesFactory;

    @Before
    public void setUp() throws Exception {
        myDependenciesFactory = new IdeDependenciesFactory();
    }

    @Test
    public void createFromDependencyGraph() {
        GraphItemStub javaGraphItem = new GraphItemStub("javaLibrary", Collections.emptyList(), "");
        GraphItemStub androidGraphItem =
                new GraphItemStub("androidLibrary", Collections.emptyList(), "");
        GraphItemStub moduleGraphItem = new GraphItemStub("module", Collections.emptyList(), "");

        Library level2JavaLibrary =
                new JavaLibraryStub() {
                    @Override
                    @NonNull
                    public String getArtifactAddress() {
                        return "javaLibrary";
                    }
                };
        Library level2AndroidLibrary =
                new AndroidLibraryStub() {
                    @Override
                    @NonNull
                    public String getArtifactAddress() {
                        return "androidLibrary";
                    }
                };
        Library level2ModuleLibrary =
                new ModuleLibraryStub() {
                    @Override
                    @NonNull
                    public String getArtifactAddress() {
                        return "module";
                    }
                };

        DependencyGraphsStub dependencyGraphsStub =
                new DependencyGraphsStub(
                        Arrays.asList(javaGraphItem, androidGraphItem, moduleGraphItem),
                                Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList());

        myDependenciesFactory.setUpGlobalLibraryMap(
                singletonList(
                        new GlobalLibraryMapStub(
                                ImmutableMap.of(
                                        "javaLibrary", level2JavaLibrary,
                                        "androidLibrary", level2AndroidLibrary,
                                        "moduleLibrary", level2ModuleLibrary))));
        IdeDependencies level2Dependencies =
                myDependenciesFactory.createFromDependencyGraphs(dependencyGraphsStub);

        assertThat(level2Dependencies.getAndroidLibraries()).hasSize(1);
        assertThat(level2Dependencies.getAndroidLibraries().iterator().next().getArtifactAddress())
                .isEqualTo("androidLibrary");

        assertThat(level2Dependencies.getJavaLibraries()).hasSize(1);
        assertThat(level2Dependencies.getJavaLibraries().iterator().next().getArtifactAddress())
                .isEqualTo("javaLibrary");

        assertThat(level2Dependencies.getModuleDependencies()).hasSize(1);
        assertThat(
                        level2Dependencies
                                .getModuleDependencies()
                                .iterator()
                                .next()
                                .getArtifactAddress())
                .isEqualTo("module");
    }

    @Test
    public void createFromDependencies() {
        JavaLibrary javaLibraryA =
                new com.android.ide.common.gradle.model.stubs.JavaLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return null;
                    }

                    @Override
                    @NonNull
                    public MavenCoordinates getResolvedCoordinates() {
                        return new MavenCoordinatesStub("com", "java", "A", "jar");
                    }
                };
        JavaLibrary javaLibraryB =
                new com.android.ide.common.gradle.model.stubs.JavaLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return null;
                    }

                    @Override
                    @NonNull
                    public List<? extends JavaLibrary> getDependencies() {
                        return singletonList(javaLibraryA);
                    }

                    @Override
                    @NonNull
                    public MavenCoordinates getResolvedCoordinates() {
                        return new MavenCoordinatesStub("com", "java", "B", "jar");
                    }
                };

        ProjectIdentifier identifier1 =
                new ProjectIdentifier() {
                    @NonNull
                    @Override
                    public String getBuildId() {
                        return "/root/project1";
                    }

                    @NonNull
                    @Override
                    public String getProjectPath() {
                        return ":";
                    }
                };

        ProjectIdentifier identifier2 =
                new ProjectIdentifier() {
                    @NonNull
                    @Override
                    public String getBuildId() {
                        return "/root/project2";
                    }

                    @NonNull
                    @Override
                    public String getProjectPath() {
                        return ":";
                    }
                };

        DependenciesStub dependenciesStub =
                new DependenciesStub(
                        Collections.emptyList(),
                        singletonList(javaLibraryB),
                        Collections.emptyList(),
                        Lists.newArrayList(identifier1, identifier2));

        BaseArtifactStub baseArtifactStub =
                new BaseArtifactStub() {
                    @Override
                    @NonNull
                    public Dependencies getDependencies() {
                        return dependenciesStub;
                    }
                };

        IdeDependencies level2Dependencies =
                myDependenciesFactory.create(baseArtifactStub, GradleVersion.parse("2.3.0"));

        assertThat(level2Dependencies.getAndroidLibraries()).hasSize(0);

        assertThat(level2Dependencies.getJavaLibraries()).hasSize(2);
        assertThat(
                        level2Dependencies
                                .getJavaLibraries()
                                .stream()
                                .map(Library::getArtifactAddress)
                                .collect(Collectors.toList()))
                .containsExactly("com:java:A@jar", "com:java:B@jar");

        assertThat(level2Dependencies.getModuleDependencies()).hasSize(2);
        assertThat(
                        level2Dependencies
                                .getModuleDependencies()
                                .stream()
                                .map(Library::getArtifactAddress)
                                .collect(Collectors.toList()))
                .containsExactly("/root/project1@@:", "/root/project2@@:");
    }
}
