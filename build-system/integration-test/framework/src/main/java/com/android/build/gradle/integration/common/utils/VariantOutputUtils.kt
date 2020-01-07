/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("VariantOutputUtils")
package com.android.build.gradle.integration.common.utils

import com.android.build.VariantOutput
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.FilterConfiguration

fun VariantOutput.getFilter(filterType: String): String? =
    filters.firstOrNull { it.filterType == filterType}?.identifier


fun BuiltArtifact.getFilter(filterType: FilterConfiguration.FilterType): String? =
    filters.firstOrNull { it.filterType == filterType }?.identifier


