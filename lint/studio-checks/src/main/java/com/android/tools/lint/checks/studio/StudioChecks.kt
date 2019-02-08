/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Platform
import java.util.EnumSet

// Category for UI responsiveness problems
val UI_RESPONSIVENESS = Category.create("UI Responsiveness", 35)

// Category for cross platform problems
val CROSS_PLATFORM = Category.create("Cross Platform", 75)

// Temporarily broken: fix test infrastructure
// val STUDIO_PLATFORMS = Platform.JDK_SET
val STUDIO_PLATFORMS: EnumSet<Platform>? = null
