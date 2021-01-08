/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.IdeSourceProvider;
import com.android.ide.common.gradle.model.stubs.SourceProviderStub;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.android.ide.common.gradle.model.impl.IdeSourceProviderImpl}. */
public class IdeSourceProviderTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void model1_dot_5() {
        SourceProvider original =
                new SourceProviderStub() {
                    @Override
                    @NonNull
                    public Collection<File> getShadersDirectories() {
                        throw new UnsupportedOperationException("getShadersDirectories()");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getManifestFile(),
                                getJavaDirectories(),
                                getResourcesDirectories(),
                                getAidlDirectories(),
                                getRenderscriptDirectories(),
                                getResDirectories(),
                                getAssetsDirectories(),
                                getJniLibsDirectories());
                    }
                };
        IdeSourceProvider sourceProvider = myModelCache.sourceProviderFrom(original);
        assertThat(sourceProvider.getShadersDirectories()).isEmpty();
    }
}
