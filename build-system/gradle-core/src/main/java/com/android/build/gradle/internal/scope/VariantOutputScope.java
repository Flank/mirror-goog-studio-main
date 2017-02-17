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

package com.android.build.gradle.internal.scope;

import static com.android.build.gradle.internal.TaskManager.ATOM_SUFFIX;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantType;
import com.android.utils.StringHelper;
import java.io.File;
import java.util.Collection;
import org.gradle.api.DefaultTask;

/**
 * A scope containing data for a specific variant.
 */
public class VariantOutputScope implements TransformVariantScope {

    @NonNull
    private VariantScope variantScope;
    @NonNull
    private BaseVariantOutputData variantOutputData;

    // Tasks
    private AndroidTask<DefaultTask> assembleTask;

    public VariantOutputScope(
            @NonNull VariantScope variantScope,
            @NonNull BaseVariantOutputData variantOutputData) {
        this.variantScope = variantScope;
        this.variantOutputData = variantOutputData;
    }

    @NonNull
    @Override
    public SplitScope getSplitScope() {
        return variantScope.getSplitScope();
    }

    @Override
    @NonNull
    public GlobalScope getGlobalScope() {
        return variantScope.getGlobalScope();
    }

    @NonNull
    public VariantScope getVariantScope() {
        return variantScope;
    }

    @NonNull
    public BaseVariantOutputData getVariantOutputData() {
        return variantOutputData;
    }

    @NonNull
    @Override
    public String getDirName() {
        // this is here as a safety net in the Transform manager which handles either VariantScope
        // or VariantOutputScope. Should this ever be called we'll need to compute this properly.
        throw new UnsupportedOperationException("dir name per output scope not yet supported");
    }

    @NonNull
    @Override
    public Collection<String> getDirectorySegments() {
        // this is here as a safety net in the Transform manager which handles either VariantScope
        // or VariantOutputScope. Should this ever be called we'll need to compute this properly.
        throw new UnsupportedOperationException("dir name per output scope not yet supported");
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return variantScope.getFullVariantName();
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix) {
        return getTaskName(prefix, "");
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        if (getVariantScope().getVariantData().getType() == VariantType.ATOM)
            suffix = ATOM_SUFFIX + suffix;
        return prefix + StringHelper.capitalize(getVariantOutputData().getFullName()) + suffix;
    }

    @NonNull
    public File getManifestOutputFile() {
        switch(variantScope.getVariantConfiguration().getType()) {
            case DEFAULT:
            case INSTANTAPP:
                return new File(getGlobalScope().getIntermediatesDir(),
                        "/manifests/full/"  + variantOutputData.getDirName()
                                + "/AndroidManifest.xml");
            case LIBRARY:
            case ATOM:
                return new File(variantScope.getBaseBundleDir(), "AndroidManifest.xml");
            case ANDROID_TEST:
                return new File(getGlobalScope().getIntermediatesDir(),
                        "manifest/" + variantScope.getVariantConfiguration().getDirName()
                                + "/AndroidManifest.xml");
            default:
                throw new RuntimeException(
                        "getManifestOutputFile called for an unexpected variant.");
        }
    }

    // Tasks
    public AndroidTask<DefaultTask> getAssembleTask() {
        return assembleTask;
    }

    public void setAssembleTask(@NonNull AndroidTask<DefaultTask> assembleTask) {
        this.assembleTask = assembleTask;
    }

    @NonNull
    public ApkOutputFile getMainOutputFile() {
        return getVariantOutputData().getMainOutputFile();
    }
}
