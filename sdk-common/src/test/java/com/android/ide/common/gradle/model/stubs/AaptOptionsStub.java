/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AaptOptions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class AaptOptionsStub extends BaseStub implements AaptOptions {
    @Nullable private final String ignoreAssets;
    @Nullable private final Collection<String> noCompress;
    private final boolean failOnMissingConfigEntry;
    @NonNull private final List<String> additionalParameters;
    @NonNull private final Namespacing namespacing;

    public AaptOptionsStub() {
        this(
                "ignore",
                ImmutableList.<String>builder().add("a").add("b").build(),
                false,
                ImmutableList.<String>builder().add("a1").add("b1").build(),
                Namespacing.DISABLED);
    }

    public AaptOptionsStub(
            @Nullable String ignoreAssets,
            @Nullable Collection<String> noCompress,
            boolean failOnMissingConfigEntry,
            @NonNull List<String> additionalParameters,
            @NonNull Namespacing namespacing) {
        this.ignoreAssets = ignoreAssets;
        this.noCompress = noCompress;
        this.failOnMissingConfigEntry = failOnMissingConfigEntry;
        this.additionalParameters = additionalParameters;
        this.namespacing = namespacing;
    }

    @Override
    @Nullable
    public String getIgnoreAssets() {
        return ignoreAssets;
    }

    @Override
    @Nullable
    public Collection<String> getNoCompress() {
        return noCompress;
    }

    @Override
    public boolean getFailOnMissingConfigEntry() {
        return failOnMissingConfigEntry;
    }

    @Override
    @NonNull
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    @Override
    @NonNull
    public Namespacing getNamespacing() {
        return namespacing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AaptOptions)) {
            return false;
        }
        AaptOptions that = (AaptOptions) o;
        return getFailOnMissingConfigEntry() == that.getFailOnMissingConfigEntry()
                && Objects.equals(getIgnoreAssets(), that.getIgnoreAssets())
                && Objects.equals(getNoCompress(), that.getNoCompress())
                && Objects.equals(getAdditionalParameters(), that.getAdditionalParameters())
                && getNamespacing() == that.getNamespacing();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getIgnoreAssets(),
                getNoCompress(),
                getFailOnMissingConfigEntry(),
                getAdditionalParameters(),
                getNamespacing());
    }
}
