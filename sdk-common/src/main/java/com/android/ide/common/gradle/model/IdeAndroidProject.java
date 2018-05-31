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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import java.io.Serializable;
import java.util.Collection;
import java.util.function.Consumer;

public interface IdeAndroidProject extends Serializable, AndroidProject {
    @Nullable
    GradleVersion getParsedModelVersion();

    void forEachVariant(@NonNull Consumer<IdeVariant> action);

    /**
     * Add variant models obtained from Variant-Only Sync.
     *
     * @param variants List of Variant models obtained by Variant-Only Sync.
     * @param factory IdeDependenciesFactory that handles GlobalLibraryMap for DependencyGraph.
     */
    void addVariants(
            @NonNull Collection<Variant> variants, @NonNull IdeDependenciesFactory factory);

    /**
     * Add sync issues from Variant-Only Sync.
     *
     * @param syncIssues List of SyncIssue from the AndroidProject model obtained by Variant-Only
     *     Sync.
     */
    void addSyncIssues(@NonNull Collection<SyncIssue> syncIssues);
}
