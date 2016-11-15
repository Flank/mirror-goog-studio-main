/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.example.basic;

public enum Enums {

    VALUE_0("zero"),
    VALUE_1("one") {
        @Override
        public String getValue() {
            return "patched+overriden:" + super.getValue() + otherMethod();
        }

        public String otherMethod() {
            return ":other+patched";
        }
    };

    private String value;

    Enums(String argument) {
        value = argument;
    }

    public String getValue() {
        return value + ":patched";
    }
}
