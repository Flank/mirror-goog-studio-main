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

import java.io.File;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class JarDependencyTest {

    @Test
    public void equals() throws Exception {
        // because of recursive data structure, equalsVerifier needs prefab values.
        EqualsVerifier.forClass(JarDependency.class)
                .suppress(Warning.NONFINAL_FIELDS)
                .withPrefabValues(JarDependency.class, getRedValue(), getBlackValue())
                .verify();
    }

    private static JarDependency getRedValue() {
        return new JarDependency(new File("red"));
    }

    private static JarDependency getBlackValue() {
        return new JarDependency(new File("black"));
    }
}