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

package com.android.build.gradle.internal.dsl;

import static com.google.common.base.Verify.verifyNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** DSL object for configuring postprocessing: removing dead code, obfuscating etc. */
public class PostprocessingOptions {
    private static final String AUTO = "auto";

    private boolean removeUnusedCode;
    private boolean removeUnusedResources;
    private boolean obfuscate;
    private boolean optimizeCode;

    private List<File> proguardFiles = new ArrayList<>();
    private List<File> testProguardFiles = new ArrayList<>();
    private List<File> consumerProguardFiles = new ArrayList<>();

    @Nullable private CodeShrinker codeShrinker;

    public void initWith(PostprocessingOptions that) {
        this.removeUnusedCode = that.isRemoveUnusedCode();
        this.removeUnusedResources = that.isRemoveUnusedResources();
        this.obfuscate = that.isObfuscate();
        this.optimizeCode = that.isOptimizeCode();
        this.proguardFiles = Lists.newArrayList(that.getProguardFiles());
        this.testProguardFiles = Lists.newArrayList(that.getTestProguardFiles());
        this.consumerProguardFiles = Lists.newArrayList(that.getConsumerProguardFiles());
        this.codeShrinker = that.getCodeShrinkerEnum();
    }

    public boolean isRemoveUnusedCode() {
        return removeUnusedCode;
    }

    public void setRemoveUnusedCode(boolean removeUnusedCode) {
        this.removeUnusedCode = removeUnusedCode;
    }

    public boolean isRemoveUnusedResources() {
        return removeUnusedResources;
    }

    public void setRemoveUnusedResources(boolean removeUnusedResources) {
        this.removeUnusedResources = removeUnusedResources;
    }

    public boolean isObfuscate() {
        return obfuscate;
    }

    public void setObfuscate(boolean obfuscate) {
        this.obfuscate = obfuscate;
    }

    public boolean isOptimizeCode() {
        return optimizeCode;
    }

    public void setOptimizeCode(boolean optimizeCode) {
        this.optimizeCode = optimizeCode;
    }

    public List<File> getProguardFiles() {
        return proguardFiles;
    }

    public void setProguardFiles(List<File> proguardFiles) {
        this.proguardFiles = proguardFiles;
    }

    public List<File> getTestProguardFiles() {
        return testProguardFiles;
    }

    public void setTestProguardFiles(List<File> testProguardFiles) {
        this.testProguardFiles = testProguardFiles;
    }

    public List<File> getConsumerProguardFiles() {
        return consumerProguardFiles;
    }

    public void setConsumerProguardFiles(List<File> consumerProguardFiles) {
        this.consumerProguardFiles = consumerProguardFiles;
    }

    @NonNull
    public String getCodeShrinker() {
        if (codeShrinker == null) {
            return AUTO;
        } else {
            return verifyNotNull(
                    StringToEnumConverters.forClass(CodeShrinker.class)
                            .reverse()
                            .convert(codeShrinker));
        }
    }

    public void setCodeShrinker(@NonNull String name) {
        if (name.equals(AUTO)) {
            codeShrinker = null;
        } else {
            codeShrinker = StringToEnumConverters.forClass(CodeShrinker.class).convert(name);
        }
    }

    /** For Gradle code, not to be used in the DSL. */
    @Nullable
    public CodeShrinker getCodeShrinkerEnum() {
        return codeShrinker;
    }
}
