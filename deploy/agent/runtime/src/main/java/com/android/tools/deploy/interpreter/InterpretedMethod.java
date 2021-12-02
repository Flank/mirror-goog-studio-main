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
package com.android.tools.deploy.interpreter;

import com.android.deploy.asm.tree.MethodNode;

public class InterpretedMethod {
    private final MethodNode target;
    private final String filename;
    private final String name;
    private final String ownerName;
    private final String ownerInternalName;

    public InterpretedMethod(
            MethodNode target, String filename, String name, String ownerInternalName) {
        this.target = target;
        this.filename = filename;
        this.name = name;
        this.ownerInternalName = ownerInternalName;
        this.ownerName = ownerInternalName.replace("/", ".");
    }

    public MethodNode getTarget() {
        return target;
    }

    public String getFilename() {
        return filename;
    }

    public String getName() {
        return name;
    }

    public String getOwnerInternalName() {
        return ownerInternalName;
    }

    public String getOwnerName() {
        return ownerName;
    }
}
