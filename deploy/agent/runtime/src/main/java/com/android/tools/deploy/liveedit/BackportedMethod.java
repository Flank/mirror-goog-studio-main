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
package com.android.tools.deploy.liveedit;

import com.android.tools.deploy.interpreter.MethodDescription;

public class BackportedMethod {

    private final String key;
    private final MethodDescription target;

    private BackportedMethod(String key, MethodDescription target) {
        this.key = key;
        this.target = target;
    }

    static Builder as(String desc) {
        return new Builder(desc);
    }

    static String genKey(String internalClassName, String name, String desc) {
        return internalClassName + "." + name + desc;
    }

    static String genKey(MethodDescription md) {
        return genKey(md.getOwnerInternalName(), md.getName(), md.getDesc());
    }

    String key() {
        return key;
    }

    MethodDescription target() {
        return target;
    }

    static class Builder {

        private String key = null;
        private final String desc;
        private MethodDescription target = null;

        Builder(String desc) {
            this.desc = desc;
        }

        Builder from(String internalClassName, String newName) {
            this.key = genKey(internalClassName, newName, desc);
            return this;
        }

        Builder to(String internalClassName, String newName) {
            this.target = new MethodDescription(internalClassName, newName, desc);
            return this;
        }

        BackportedMethod build() {
            if (this.target == null || this.key == null) {
                throw new IllegalStateException("Cannot build backport without a target or key");
            }
            return new BackportedMethod(key, target);
        }
    }
}
