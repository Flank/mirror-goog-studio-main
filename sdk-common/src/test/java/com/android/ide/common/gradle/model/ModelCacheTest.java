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
package com.android.ide.common.gradle.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.android.annotations.NonNull;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ModelCache}. */
public class ModelCacheTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() {
        myModelCache = new ModelCache();
    }

    @Test
    public void computeIfAbsent() {
        Person person1 = myModelCache.computeIfAbsent("firstPerson", o -> new Person("1", "a"));
        assertSame(person1, myModelCache.getData().get("firstPerson"));

        person1 = myModelCache.computeIfAbsent("firstPerson", o -> new Person("1", "b"));
        assertEquals("a", person1.name); // The cached value should not have changed.

        Person person2 = myModelCache.computeIfAbsent("secondPerson", o -> new Person("2", "b"));
        assertSame(person2, myModelCache.getData().get("secondPerson"));

        assertSame(
                person1, myModelCache.getData().get("firstPerson")); // data should not be removed.
    }

    @Test
    public void computeIfAbsentReentrant() {
        Person person1;
        person1 =
                myModelCache.computeIfAbsent(
                        "firstPerson",
                        o -> {
                            myModelCache.computeIfAbsent("secondPerson", p -> new Person("2", "b"));
                            return new Person("1", "a");
                        });
        assertSame(person1, myModelCache.getData().get("firstPerson"));
        assertEquals("b", ((Person) myModelCache.getData().get("secondPerson")).name);
    }

    private static class Person {
        @NonNull final String id;
        @NonNull final String name;

        private Person(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Person)) {
                return false;
            }
            Person person = (Person) o;
            return Objects.equals(id, person.id) && Objects.equals(name, person.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }
}
