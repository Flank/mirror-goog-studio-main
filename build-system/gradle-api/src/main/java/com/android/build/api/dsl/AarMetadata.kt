/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.dsl

/**
 * DSL object for configuring metadata that is embedded in the AAR.
 *
 * This metadata is used by consumers of the AAR to control their behavior.
 */
interface AarMetadata {
    /**
     * The minimum compileSdkVersion required by any consuming module.
     *
     * For example, setting this when the AAR uses an Android resource from a new version of the
     * Android platform will alert consuming projects that they need to update their compileSdk
     * version to match, rather than getting an error when processing resources that the new
     * resource could not be found.
     */
    var minCompileSdk: Int?
}
