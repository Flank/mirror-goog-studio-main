/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

public class ProxyTestClasses {

    public static interface Function2<A, B, R> {
        R apply(A a, B b);
    }

    public static interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    public static class Pythagorean implements Function3<Long, Long, String, Long> {

        public Long apply(Long sideA, Long sideB, String word) {
            long value = Math.round(Math.sqrt(square(sideA) + square(sideB)));
            return value + word.length();
        }

        Long square(Long value) {
            return value * value;
        }
    }

    public static class Driver {
        public static long liveEditedMethod(long a, long b) {
            Pythagorean func = new Pythagorean();
            return func.apply(a, b, "string");
        }

        public static long notLiveEditedMethod(Function2<Long, Long, Long> func) {
            return func.apply(3L, 4L);
        }

        public static int liveEditedMethod() {
            return ModifyStatic.proxiedStatic();
        }
    }

    public static class AddedMethods {
        public static int callsAddedMethod() {
            return addedMethod();
        }

        public static int addedMethod() {
            return 1;
        }
    }

    public static class ModifyStatic {
        public static int proxiedStatic() {
            return 5;
        }
    }
}
