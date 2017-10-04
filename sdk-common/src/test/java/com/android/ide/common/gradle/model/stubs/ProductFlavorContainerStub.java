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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Objects;

public class ProductFlavorContainerStub extends BaseStub implements ProductFlavorContainer {
    @NonNull private final ProductFlavor myProductFlavor;
    @NonNull private final SourceProvider mySourceProvider;
    @NonNull private final Collection<SourceProviderContainer> myExtraSourceProviders;

    public ProductFlavorContainerStub() {
        this(
                new ProductFlavorStub(),
                new SourceProviderStub(),
                Lists.newArrayList(new SourceProviderContainerStub()));
    }

    public ProductFlavorContainerStub(
            @NonNull ProductFlavor flavor,
            @NonNull SourceProvider provider,
            @NonNull Collection<SourceProviderContainer> providers) {
        myProductFlavor = flavor;
        mySourceProvider = provider;
        myExtraSourceProviders = providers;
    }

    @Override
    @NonNull
    public ProductFlavor getProductFlavor() {
        return myProductFlavor;
    }

    @Override
    @NonNull
    public SourceProvider getSourceProvider() {
        return mySourceProvider;
    }

    @Override
    @NonNull
    public Collection<SourceProviderContainer> getExtraSourceProviders() {
        return myExtraSourceProviders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProductFlavorContainer)) {
            return false;
        }
        ProductFlavorContainer container = (ProductFlavorContainer) o;
        return Objects.equals(getProductFlavor(), container.getProductFlavor())
                && Objects.equals(getSourceProvider(), container.getSourceProvider())
                && Objects.equals(getExtraSourceProviders(), container.getExtraSourceProviders());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProductFlavor(), getSourceProvider(), getExtraSourceProviders());
    }

    @Override
    public String toString() {
        return "ProductFlavorContainerStub{"
                + "myProductFlavor="
                + myProductFlavor
                + ", mySourceProvider="
                + mySourceProvider
                + ", myExtraSourceProviders="
                + myExtraSourceProviders
                + "}";
    }
}
