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

import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v03.Api03Config;

import java.nio.charset.Charset;

import javax.annotation.Nonnull;

/**
 * A configuration for API level 04 of the Jack compiler compatible with API level 03
 */
public interface Api04Config extends Api03Config {
    /**
     * Set the default {@link Charset} used when no charset is specified by file with
     * {@link HasCharset}.
     *
     * @param charset the default charset
     * @throws ConfigurationException if something is wrong in Jack's configuration
     */
    void setDefaultCharset(@Nonnull Charset charset) throws  ConfigurationException;
}