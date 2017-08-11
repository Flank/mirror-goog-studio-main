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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.VariantManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.BaseConfig;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.internal.reflect.Instantiator;

/** DSL object for product flavors */
public class ProductFlavor extends BaseFlavor {

    public ProductFlavor(
            @NonNull String name,
            @NonNull Project project,
            @NonNull Instantiator instantiator,
            @NonNull Logger logger,
            @NonNull ErrorReporter errorReporter) {
        super(name, project, instantiator, logger, errorReporter);
    }

    private ImmutableList<String> matchingFallbacks;

    public void setMatchingFallbacks(String... fallbacks) {
        this.matchingFallbacks = ImmutableList.copyOf(fallbacks);
    }

    public void setMatchingFallbacks(String fallback) {
        this.matchingFallbacks = ImmutableList.of(fallback);
    }

    public void setMatchingFallbacks(List<String> fallbacks) {
        this.matchingFallbacks = ImmutableList.copyOf(fallbacks);
    }

    /**
     * Fall-backs to use during variant-aware dependency resolution in case a dependency does not
     * have the current product flavor.
     *
     * @return the names of product flavors to use, in descending priority order
     */
    public List<String> getMatchingFallbacks() {
        if (matchingFallbacks == null) {
            return ImmutableList.of();
        }
        return matchingFallbacks;
    }

    @NonNull
    @Override
    protected String getRequestedValueFromList(@NonNull List<String> requestedValues) {
        return VariantManager.getModifiedName(getName());
    }

    @Override
    protected void _initWith(@NonNull BaseConfig that) {
        super._initWith(that);

        if (that instanceof ProductFlavor) {
            matchingFallbacks = ((ProductFlavor) that).matchingFallbacks;
        }
    }
}
