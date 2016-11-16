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

import com.android.annotations.NonNull;
import com.android.builder.dependency.level2.Dependency;
import com.android.build.gradle.internal.dependency.MutableDependencyDataMap;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class DependenciesConverterTest {

    @Test
    public void equals() throws Exception {
        EqualsVerifier.forClass(DependenciesConverter.DependenciesImpl.class)
                .withPrefabValues(MutableDependencyDataMap.class, new MutableDependencyDataMap() {

                    @Override
                    public void skip(Dependency dependency) {
                    }

                    @Override
                    public boolean isSkipped(Dependency library) {
                        return false;
                    }

                    @Override
                    public void setProvided(Dependency dependency) {

                    }

                    @Override
                    public boolean isProvided(Dependency dependency) {
                        return false;
                    }

                    @NonNull
                    @Override
                    public List<String> getProvidedList() {
                        return ImmutableList.of();
                    }

                    @NonNull
                    @Override
                    public List<String> getSkippedList() {
                        return ImmutableList.of();
                    }
                }, new MutableDependencyDataMap() {
                    Map<Dependency, Boolean> skipped = new HashMap<>();
                    Map<Dependency, Boolean> provided = new HashMap<>();
                    @Override
                    public void skip(Dependency dependency) {
                        skipped.put(dependency, Boolean.TRUE);
                    }
                    @Override
                    public boolean isSkipped(Dependency dependency) {
                        return skipped.getOrDefault(dependency, Boolean.FALSE);
                    }
                    @Override
                    public void setProvided(Dependency dependency) {
                        provided.put(dependency, Boolean.TRUE);
                    }
                    @Override
                    public boolean isProvided(Dependency dependency) {
                        return provided.getOrDefault(dependency, Boolean.FALSE);
                    }
                    @NonNull
                    @Override
                    public List<String> getProvidedList() {
                        return ImmutableList.of();
                    }

                    @NonNull
                    @Override
                    public List<String> getSkippedList() {
                        return ImmutableList.of();
                    }
                })
                .verify();
    }
}