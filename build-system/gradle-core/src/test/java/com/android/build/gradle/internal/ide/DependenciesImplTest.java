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

package com.android.build.gradle.internal.ide;

import com.android.builder.dependency.DependenciesMutableData;
import com.android.builder.model.Library;
import java.util.HashMap;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class DependenciesImplTest {

    @Test
    public void equals() throws Exception {
        EqualsVerifier.forClass(DependenciesImpl.class)
                .withPrefabValues(DependenciesMutableData.class, new DependenciesMutableData() {

                    @Override public void skip(Library library) {

                    }

                    @Override public boolean isSkipped(Library library) {
                        return false;
                    }
                }, new DependenciesMutableData() {
                    Map<Library, Boolean> values  = new HashMap<>();
                    @Override public void skip(Library library) {
                        values.put(library, Boolean.TRUE);
                    }

                    @Override public boolean isSkipped(Library library) {
                        return values.getOrDefault(library, Boolean.FALSE);
                    }
                })
                .verify();
    }
}