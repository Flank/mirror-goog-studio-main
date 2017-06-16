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

package com.android.ide.common.builder.model;

import com.android.annotations.NonNull;
import com.android.builder.model.JavaCompileOptions;
import java.util.Objects;

/** Creates a deep copy of a {@link JavaCompileOptions}. */
public final class IdeJavaCompileOptions extends IdeModel implements JavaCompileOptions {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final String myEncoding;
    @NonNull private final String mySourceCompatibility;
    @NonNull private final String myTargetCompatibility;
    private final int myHashCode;

    public IdeJavaCompileOptions(
            @NonNull JavaCompileOptions options, @NonNull ModelCache modelCache) {
        super(options, modelCache);
        myEncoding = options.getEncoding();
        mySourceCompatibility = options.getSourceCompatibility();
        myTargetCompatibility = options.getTargetCompatibility();

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getEncoding() {
        return myEncoding;
    }

    @Override
    @NonNull
    public String getSourceCompatibility() {
        return mySourceCompatibility;
    }

    @Override
    @NonNull
    public String getTargetCompatibility() {
        return myTargetCompatibility;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeJavaCompileOptions)) {
            return false;
        }
        IdeJavaCompileOptions options = (IdeJavaCompileOptions) o;
        return Objects.equals(myEncoding, options.myEncoding)
                && Objects.equals(mySourceCompatibility, options.mySourceCompatibility)
                && Objects.equals(myTargetCompatibility, options.myTargetCompatibility);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myEncoding, mySourceCompatibility, myTargetCompatibility);
    }

    @Override
    public String toString() {
        return "IdeJavaCompileOptions{"
                + "myEncoding='"
                + myEncoding
                + '\''
                + ", mySourceCompatibility='"
                + mySourceCompatibility
                + '\''
                + ", myTargetCompatibility='"
                + myTargetCompatibility
                + '\''
                + "}";
    }
}
