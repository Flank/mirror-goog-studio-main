/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.jack.api.v04;

import java.io.File;
import java.nio.charset.Charset;

import javax.annotation.Nonnull;

/**
 * A {@link File} with a {@link Charset} different from the default one (
 * {@link Api04Config#setDefaultCharset(Charset)}) must implement that interface.
 */
public interface HasCharset {
    /**
     * @return the {@link Charset}
     */
    @Nonnull
    Charset getCharset();
}