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

package com.android.build.gradle.shrinker;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.io.Serializable;

/**
 * Edge in the shrinker graph.
 */
public final class Dependency<T> implements Serializable {
    @NonNull
    final T target;

    @NonNull
    final DependencyType type;

    public Dependency(@NonNull T target, @NonNull DependencyType type) {
        this.target = checkNotNull(target);
        this.type = checkNotNull(type);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Dependency) {
            Dependency<?> that = (Dependency<?>) object;
            return Objects.equal(target, that.target) && type == that.type;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(target, type);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("target", target)
                .add("type", type)
                .toString();
    }
}
