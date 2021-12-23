/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit.backported;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class List {
    public static <E> java.util.List<E> copyOf(Collection<? extends E> coll) {
        // TODO: Use java 9 List.of when it becomes available to us.
        // return (java.util.List<E>)java.util.List.of(coll.toArray());
        java.util.List list = new ArrayList<>(coll);
        return Collections.unmodifiableList(list);
    }
}
