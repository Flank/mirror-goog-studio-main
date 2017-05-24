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

package com.android.build.gradle.ndk.internal;

import com.android.build.gradle.internal.ndk.NativeToolSpecification;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Default flags that applies to all toolchains.
 */
public class DefaultNativeToolSpecification implements NativeToolSpecification {

    private static final List<String> CPP_FLAGS = ImmutableList.of("-fno-rtti", "-fno-exceptions");

    private static final List<String> LD_FLAGS =
            ImmutableList.of(
                    "-Wl,--no-undefined",
                    "-Wl,-z,noexecstack",
                    "-Wl,-z,relro",
                    "-Wl,-z,now",
                    "-Wl,--warn-shared-textrel");

    @Override
    public Iterable<String> getCFlags() {
        return ImmutableList.of();
    }

    @Override
    public Iterable<String> getCppFlags() {
        return CPP_FLAGS;
    }

    @Override
    public Iterable<String> getLdFlags() {
        return LD_FLAGS;
    }
}
