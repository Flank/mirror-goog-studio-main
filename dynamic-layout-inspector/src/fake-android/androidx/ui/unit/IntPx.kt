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

package androidx.ui.unit

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class IntPx(val value: Int)

/**
 * During testing this is used instead of the version in androidx-ui-unit, since that library
 * contains only stubbed out classes.
 */
data class IntPxBounds(
    val left: IntPx,
    val top: IntPx,
    val right: IntPx,
    val bottom: IntPx
)
