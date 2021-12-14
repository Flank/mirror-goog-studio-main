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

    private static final String BP_PKG_NAME = "com/android/tools/deploy/liveedit/backported";
    private final String key;
    private final MethodDescription target;

    public BackportedMethod(String pkgInternalName, String className, String name, String desc) {
        this.key = genKey(pkgInternalName + "/" + className, name, desc);
        this.target = new MethodDescription(BP_PKG_NAME + "/" + className, name, desc);
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
}
