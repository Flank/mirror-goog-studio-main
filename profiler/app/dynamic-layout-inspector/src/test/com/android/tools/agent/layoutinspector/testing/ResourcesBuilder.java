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

package com.android.tools.agent.layoutinspector.testing;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.res.Resources;
import java.util.HashMap;
import java.util.Map;

public class ResourcesBuilder {
    private final Map<Integer, Entry> myEntries = new HashMap<>();

    public static ResourcesBuilder create() {
        return new ResourcesBuilder();
    }

    private ResourcesBuilder() {}

    public ResourcesBuilder add(int id, String typeName, String packageName, String entryName) {
        myEntries.put(id, new Entry(typeName, packageName, entryName));
        return this;
    }

    public Resources build() {
        Resources res = mock(Resources.class);
        doAnswer(invocation -> myEntries.get(invocation.<Integer>getArgument(0)).typeName)
                .when(res)
                .getResourceTypeName(anyInt());
        doAnswer(invocation -> myEntries.get(invocation.<Integer>getArgument(0)).packageName)
                .when(res)
                .getResourcePackageName(anyInt());
        doAnswer(invocation -> myEntries.get(invocation.<Integer>getArgument(0)).entryName)
                .when(res)
                .getResourceEntryName(anyInt());
        return res;
    }

    private static class Entry {

        public Entry(String typeName, String packageName, String entryName) {
            this.typeName = typeName;
            this.packageName = packageName;
            this.entryName = entryName;
        }

        public String typeName;
        public String packageName;
        public String entryName;
    }
}
