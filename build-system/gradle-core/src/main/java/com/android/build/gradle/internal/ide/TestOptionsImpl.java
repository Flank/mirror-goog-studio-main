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

package com.android.build.gradle.internal.ide;

import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.gradle.internal.dsl.*;
import com.android.builder.model.TestOptions;
import com.android.builder.model.TestOptions.Execution;
import java.io.Serializable;

/** Implementation of the {@link TestOptions} model */
@Immutable
final class TestOptionsImpl implements TestOptions, Serializable {
    private static final long serialVersionUID = 1L;
    private final boolean animationsDisabled;

    @Nullable private final Execution executionEnum;

    public TestOptionsImpl(boolean animationsDisabled, @Nullable Execution executionEnum) {
        this.animationsDisabled = animationsDisabled;
        this.executionEnum = executionEnum;
    }

    @Override
    public boolean getAnimationsDisabled() {
        return animationsDisabled;
    }

    @Override
    @Nullable
    public Execution getExecutionEnum() {
        return executionEnum;
    }
}
