/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;

public class DensityBasedResourceValue extends ResourceValue {

    private final Density mDensity;

    public DensityBasedResourceValue(
            @NonNull ResourceReference reference,
            @Nullable String value,
            @NonNull Density mDensity) {
        super(reference, value);
        this.mDensity = mDensity;
    }

    public DensityBasedResourceValue(
            @NonNull ResourceReference reference,
            @Nullable String value,
            @NonNull Density mDensity,
            @Nullable String libraryName) {
        super(reference, value, libraryName);
        this.mDensity = mDensity;
    }


    /**
     * Returns the density for which this resource is configured.
     * @return the density.
     */
    public Density getResourceDensity() {
        return mDensity;
    }

    @Override
    public String toString() {
        return "DensityBasedResourceValue ["
                + getResourceType() + "/" + getName() + " = " + getValue()
                + " (density:" + mDensity +", framework:" + isFramework() + ")]";
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mDensity == null) ? 0 : mDensity.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DensityBasedResourceValue other = (DensityBasedResourceValue) obj;
        if (mDensity == null) {
            if (other.mDensity != null)
                return false;
        } else if (!mDensity.equals(other.mDensity))
            return false;
        return true;
    }
}
