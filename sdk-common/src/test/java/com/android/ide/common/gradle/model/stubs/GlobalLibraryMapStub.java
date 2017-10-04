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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.Library;
import java.util.Map;
import java.util.Objects;

public final class GlobalLibraryMapStub extends BaseStub implements GlobalLibraryMap {
    private final int myHashCode;
    @NonNull private final Map<String, Library> myMap;

    public GlobalLibraryMapStub(@NonNull Map<String, Library> map) {
        myMap = map;
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public Map<String, Library> getLibraries() {
        return myMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GlobalLibraryMapStub)) {
            return false;
        }
        GlobalLibraryMapStub item = (GlobalLibraryMapStub) o;
        return Objects.equals(myMap, item.myMap);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myMap);
    }

    @Override
    public String toString() {
        return "GlobalLibraryMapStub{" + "myMap=" + myMap + '}';
    }
}
