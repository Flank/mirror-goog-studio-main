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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.AndroidBuilder;
import org.gradle.internal.reflect.Instantiator;

/** Common superclass for all {@link VariantFactory} implementations. */
public abstract class BaseVariantFactory implements VariantFactory {

    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final Instantiator instantiator;
    @NonNull protected final AndroidConfig extension;
    @NonNull protected final AndroidBuilder androidBuilder;

    public BaseVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull Instantiator instantiator,
            @NonNull AndroidConfig extension) {
        this.globalScope = globalScope;
        this.androidBuilder = androidBuilder;
        this.instantiator = instantiator;
        this.extension = extension;
    }
}
