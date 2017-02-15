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

package com.android.builder.symbols;

import com.android.annotations.NonNull;

/** Provides IDs for resource assignment. It is essentially a supplier of unique integer values. */
public interface IdProvider {

    /**
     * Provides another unique ID.
     *
     * @return an ID that has never been returned from this provider.
     */
    int next();

    /**
     * Obtains a new ID provider that provides sequential IDs.
     *
     * @return a provider
     */
    @NonNull
    static IdProvider sequential() {
        return new IdProvider() {
            int next = 1;

            @Override
            public int next() {
                int r = next;
                next++;
                return r;
            }
        };
    }
}
