/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.ui.tooling.inspector

/**
 * During testing this is used instead of the version in androidx-ui-tooling, since that library
 * is not available during tests.
 */
class InspectorNode(
    /**
     * The associated render node id or 0.
     */
    val id: Long,

    /**
     * The name of the Composable.
     */
    val name: String,

    /**
     * The fileName where the Composable was called.
     */
    val fileName: String,

    /**
     * The line number where the Composable was called.
     */
    val lineNumber: Int,

    /**
     * The name of the function that called the Composable.
     */
    val functionName: String,

    /**
     * Left side of the Composable in pixels.
     */
    val left: Int,

    /**
     * Top of the Composable in pixels.
     */
    val top: Int,

    /**
     * Width of the Composable in pixels.
     */
    val width: Int,

    /**
     * Width of the Composable in pixels.
     */
    val height: Int,

    /**
     * The parameters of this Composable.
     */
    val parameters: List<NodeParameter>,

    /**
     * The children nodes of this Composable.
     */
    val children: List<InspectorNode>
)
