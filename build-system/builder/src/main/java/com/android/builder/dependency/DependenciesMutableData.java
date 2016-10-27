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

package com.android.builder.dependency;

import com.android.annotations.NonNull;
import com.android.builder.model.Library;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for all android dependencies mutable data
 */
public interface DependenciesMutableData {

    static DependenciesMutableData newInstance() {
        return new DependenciesMutableData() {
            Map<Library, DependencyMutableData> mutableDependencyData = new HashMap<>();

            @NonNull
            private synchronized DependencyMutableData getFor(Library library) {
                DependencyMutableData dependencyMutableData = mutableDependencyData.get(library);
                if (dependencyMutableData == null) {
                    dependencyMutableData = new DependencyMutableData();
                    mutableDependencyData.put(library, dependencyMutableData);
                }
                return dependencyMutableData;
            }

            @Override public boolean isSkipped(Library library) {
                DependencyMutableData dependencyMutableData = mutableDependencyData.get(library);
                return dependencyMutableData != null && dependencyMutableData.isSkipped();
            }

            @Override
            public void skip(Library library) {
                getFor(library).skip();
            }
        };
    }

    void skip(Library library);
    boolean isSkipped(Library library);

    DependenciesMutableData EMPTY = new DependenciesMutableData() {

            @Override public void skip(Library library) {
                throw new RuntimeException(String.format("cannot set skipped attribute "
                        + "on a library with another scope than Package : %1$s", library));
            }

            @Override public boolean isSkipped(Library library) {
                return false;
            };
    };
}
