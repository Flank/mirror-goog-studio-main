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

package com.android.builder.dependency.level2;

import com.android.builder.dependency.MavenCoordinatesImpl;
import com.google.common.collect.ImmutableList;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class DependencyNodeTest {

    @Test
    public void equals() throws Exception {
        // because of recursive data structure, equalsVerifier needs prefab values.
        EqualsVerifier.forClass(DependencyNode.class)
                .withRedefinedSuperclass()
                .withCachedHashCode("hashCode", "computeHashCode", getRedValue())
                .withPrefabValues(DependencyNode.class, getRedValue(), getBlackValue())
                .suppress(Warning.NULL_FIELDS)
                .verify();
    }

    private static DependencyNode getRedValue() {
        return new DependencyNode(
                MavenCoordinatesImpl.create(string -> string, "red", "", ""),
                DependencyNode.NodeType.ANDROID,
                ImmutableList.of(),
                MavenCoordinatesImpl.create(string -> string, "", "", ""));
    }

    private static DependencyNode getBlackValue() {
        return new DependencyNode(
                MavenCoordinatesImpl.create(string -> string, "black", "", ""),
                DependencyNode.NodeType.ANDROID,
                ImmutableList.of(),
                MavenCoordinatesImpl.create(string -> string, "", "", ""));
    }
}