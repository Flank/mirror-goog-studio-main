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

package com.android.build.api.dsl.extension

import org.gradle.api.Incubating

/**
 * Base interface for all Android extensions.
 *
 * This includes extensions for plugins that build (com.android.application/library/feature) and
 * those that don't (com.android.reporting).
 *
 * this can be used to verify that an extension called 'android' is in fact an Android extension.
 */
@Incubating
interface AndroidExtension
