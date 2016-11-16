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

package com.android.builder.model.level2;

import com.android.annotations.NonNull;
import java.util.List;
import java.util.Map;

/**
 * A dependency Graph.
 *
 * It contains a fairly lightweight graph with each artifact node being mostly an address, children,
 * and modifiers that are specific to this particular usage of the artifact rather than
 * artifact properties.
 *
 * It also contains a map of address to artifact, for external libraries (android and Java) only.
 * sub-modules are just referenced by their gradle path only and are not provided in any other
 * way (besides the modifiers in each graph node).
 */
public interface LibraryGraph {

    @NonNull
    List<GraphItem> getDependencies();

    @NonNull
    List<String> getProvidedLibraries();

    @NonNull
    List<String> getSkippedLibraries();

}
