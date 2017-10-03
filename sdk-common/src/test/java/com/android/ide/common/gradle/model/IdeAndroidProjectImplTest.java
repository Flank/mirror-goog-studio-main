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

import static com.android.ide.common.gradle.model.IdeModelTestUtils.*;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub;
import java.io.Serializable;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeAndroidProjectImpl}. */
public class IdeAndroidProjectImplTest {
    private ModelCache myModelCache;
    private IdeDependenciesFactory myDependenciesFactory;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
        myDependenciesFactory = new IdeDependenciesFactory();
    }

    @Test
    public void serializable() {
        assertThat(IdeAndroidProjectImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeAndroidProject androidProject =
                new IdeAndroidProjectImpl(
                        new AndroidProjectStub("2.4.0"), myModelCache, myDependenciesFactory);
        assertEquals("2.4.0", androidProject.getParsedModelVersion().toString());
        byte[] bytes = Serialization.serialize(androidProject);
        Object o = Serialization.deserialize(bytes);
        assertEquals(androidProject, o);
        assertEquals("2.4.0", ((IdeAndroidProject) o).getParsedModelVersion().toString());
    }

    @Test
    public void model1_dot_5() {
        AndroidProjectStub original =
                new AndroidProjectStub("1.5.0") {
                    @Override
                    @NonNull
                    public String getBuildToolsVersion() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: AndroidProject.getBuildToolsVersion()");
                    }

                    @Override
                    public int getPluginGeneration() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: AndroidProject.getPluginGeneration()");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getModelVersion(),
                                getName(),
                                getDefaultConfig(),
                                getBuildTypes(),
                                getProductFlavors(),
                                getSyncIssues(),
                                getVariants(),
                                getFlavorDimensions(),
                                getCompileTarget(),
                                getBootClasspath(),
                                getNativeToolchains(),
                                getSigningConfigs(),
                                getLintOptions(),
                                getUnresolvedDependencies(),
                                getJavaCompileOptions(),
                                getBuildFolder(),
                                getResourcePrefix(),
                                getApiVersion(),
                                isLibrary(),
                                getProjectType(),
                                isBaseSplit());
                    }
                };
        IdeAndroidProject androidProject =
                new IdeAndroidProjectImpl(original, myModelCache, myDependenciesFactory);
        expectUnsupportedOperationException(androidProject::getBuildToolsVersion);
        expectUnsupportedOperationException(androidProject::getPluginGeneration);
    }

    @Test
    public void constructor() throws Throwable {
        AndroidProject original = new AndroidProjectStub("2.4.0");
        IdeAndroidProjectImpl copy =
                new IdeAndroidProjectImpl(original, myModelCache, myDependenciesFactory);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeAndroidProjectImpl.class).verify();
    }
}
