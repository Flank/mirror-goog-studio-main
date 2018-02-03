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
package com.android.ide.common.gradle.model;

import static com.android.ide.common.gradle.model.IdeLibraries.computeAddress;
import static com.android.ide.common.gradle.model.IdeLibraries.isLocalAarModule;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.gradle.model.level2.BuildFolderPaths;
import com.android.ide.common.gradle.model.stubs.AndroidLibraryStub;
import com.android.ide.common.gradle.model.stubs.JavaLibraryStub;
import com.android.ide.common.gradle.model.stubs.LibraryStub;
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub;
import java.io.File;
import org.junit.Test;

/** Tests for {@link IdeLibraries}. */
public class IdeLibrariesTest {
    @Test
    public void computeMavenAddress() {
        Library library =
                new LibraryStub() {
                    @Override
                    @NonNull
                    public MavenCoordinates getResolvedCoordinates() {
                        return new MavenCoordinatesStub("com.android.tools", "test", "2.1", "aar");
                    }
                };
        assertThat(computeAddress(library)).isEqualTo("com.android.tools:test:2.1@aar");
    }

    @Test
    public void computeMavenAddressWithModuleLibrary() {
        Library library =
                new AndroidLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return ":androidLib";
                    }

                    @Override
                    @Nullable
                    public String getProjectVariant() {
                        return "release";
                    }
                };
        assertThat(computeAddress(library)).isEqualTo(":androidLib::release");
    }

    @Test
    public void computeMavenAddressWithModuleLibraryWithBuildId() {
        Library library =
                new AndroidLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return ":androidLib";
                    }

                    @Override
                    @Nullable
                    public String getBuildId() {
                        return "/project/root";
                    }

                    @Override
                    @Nullable
                    public String getProjectVariant() {
                        return "release";
                    }
                };
        assertThat(computeAddress(library)).isEqualTo("/project/root:androidLib::release");
    }

    @Test
    public void computeMavenAddressWithNestedModuleLibrary() {
        Library library =
                new LibraryStub() {
                    @Override
                    @NonNull
                    public MavenCoordinates getResolvedCoordinates() {
                        return new MavenCoordinatesStub(
                                "myGroup", ":androidLib:subModule", "undefined", "aar");
                    }
                };
        assertThat(computeAddress(library)).isEqualTo("myGroup:androidLib.subModule:undefined@aar");
    }

    @Test
    public void computeMavenAddressWithNullCoordinate() {
        //noinspection NullableProblems
        Library library =
                new JavaLibraryStub() {
                    @Override
                    @Nullable
                    public MavenCoordinates getResolvedCoordinates() {
                        return null;
                    }
                };
        assertThat(computeAddress(library)).isEqualTo("__local_aars__:jarFile:unspecified@jar");
    }

    @Test
    public void checkIsLocalAarModule() {
        AndroidLibrary localAarLibrary =
                new AndroidLibraryStub() {
                    @Override
                    @NonNull
                    public String getProject() {
                        return ":aarModule";
                    }

                    @Override
                    @NonNull
                    public File getBundle() {
                        return new File("/ProjectRoot/aarModule/aarModule.aar");
                    }
                };
        AndroidLibrary moduleLibrary =
                new AndroidLibraryStub() {
                    @Override
                    @NonNull
                    public String getProject() {
                        return ":androidLib";
                    }

                    @Override
                    @NonNull
                    public File getBundle() {
                        return new File("/ProjectRoot/androidLib/build/androidLib.aar");
                    }
                };
        AndroidLibrary externalLibrary =
                new AndroidLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return null;
                    }
                };
        BuildFolderPaths buildFolderPaths = new BuildFolderPaths();
        buildFolderPaths.setRootBuildId("project");
        buildFolderPaths.addBuildFolderMapping(
                "project", ":aarModule", new File("/ProjectRoot/aarModule/build/"));
        buildFolderPaths.addBuildFolderMapping(
                "project", ":androidLib", new File("/ProjectRoot/androidLib/build/"));

        assertTrue(isLocalAarModule(localAarLibrary, buildFolderPaths));
        assertFalse(isLocalAarModule(moduleLibrary, buildFolderPaths));
        assertFalse(isLocalAarModule(externalLibrary, buildFolderPaths));
    }

    @Test
    public void checkIsLocalAarModuleWithCompositeBuild() {
        // simulate project structure:
        // project(root)     - aarModule
        // project(root)     - androidLib
        //      project1     - aarModule
        //      project1     - androidLib
        AndroidLibrary localAarLibraryInRootProject =
                new AndroidLibraryStub() {
                    @Override
                    @NonNull
                    public String getProject() {
                        return ":aarModule";
                    }

                    @Override
                    @NonNull
                    public File getBundle() {
                        return new File("/Project/aarModule/aarModule.aar");
                    }

                    @Override
                    @Nullable
                    public String getBuildId() {
                        return "Project";
                    }
                };
        AndroidLibrary localAarLibraryInProject1 =
                new AndroidLibraryStub() {
                    @Override
                    @NonNull
                    public String getProject() {
                        return ":aarModule";
                    }

                    @Override
                    @NonNull
                    public File getBundle() {
                        return new File("/Project1/aarModule/aarModule.aar");
                    }

                    @Override
                    @Nullable
                    public String getBuildId() {
                        return "Project1";
                    }
                };
        AndroidLibrary moduleLibraryInRootProject =
                new AndroidLibraryStub() {
                    @Override
                    @NonNull
                    public String getProject() {
                        return ":androidLib";
                    }

                    @Override
                    @NonNull
                    public File getBundle() {
                        return new File("/Project/androidLib/build/androidLib.aar");
                    }

                    @Override
                    @Nullable
                    public String getBuildId() {
                        return "Project";
                    }
                };
        AndroidLibrary moduleLibraryInProject1 =
                new AndroidLibraryStub() {
                    @Override
                    @NonNull
                    public String getProject() {
                        return ":androidLib";
                    }

                    @Override
                    @NonNull
                    public File getBundle() {
                        return new File("/Project1/androidLib/build/androidLib.aar");
                    }

                    @Override
                    @Nullable
                    public String getBuildId() {
                        return "Project1";
                    }
                };
        AndroidLibrary externalLibrary =
                new AndroidLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return null;
                    }
                };
        BuildFolderPaths buildFolderPaths = new BuildFolderPaths();
        buildFolderPaths.setRootBuildId("Project");
        buildFolderPaths.addBuildFolderMapping(
                "Project", ":aarModule", new File("/Project/aarModule/build/"));
        buildFolderPaths.addBuildFolderMapping(
                "Project", ":androidLib", new File("/Project/androidLib/build/"));
        buildFolderPaths.addBuildFolderMapping(
                "Project1", ":aarModule", new File("/Project1/aarModule/build/"));
        buildFolderPaths.addBuildFolderMapping(
                "Project1", ":androidLib", new File("/Project1/androidLib/build/"));

        assertTrue(isLocalAarModule(localAarLibraryInRootProject, buildFolderPaths));
        assertTrue(isLocalAarModule(localAarLibraryInProject1, buildFolderPaths));

        assertFalse(isLocalAarModule(moduleLibraryInRootProject, buildFolderPaths));
        assertFalse(isLocalAarModule(moduleLibraryInProject1, buildFolderPaths));
        assertFalse(isLocalAarModule(externalLibrary, buildFolderPaths));
    }
}
