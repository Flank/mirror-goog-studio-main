/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer.model;

import com.android.tools.deploy.proto.Deploy;
import com.google.common.collect.ImmutableList;

public class DexClass {
    public final String name;
    public final long checksum;
    public final byte[] code;
    public final ApkEntry dex;
    public final ImmutableList<Deploy.ClassDef.FieldReInitState> variableStates;

    public DexClass(String name, long checksum, byte[] code, ApkEntry dex) {
        this(name, checksum, code, dex, ImmutableList.of());
    }

    public DexClass(
            String name,
            long checksum,
            byte[] code,
            ApkEntry dex,
            ImmutableList<Deploy.ClassDef.FieldReInitState> variableStates) {
        this.name = name;
        this.checksum = checksum;
        this.code = code;
        this.dex = dex;
        this.variableStates = variableStates;
    }

    public DexClass(DexClass old, ImmutableList<Deploy.ClassDef.FieldReInitState> variableStates) {
        this(old.name, old.checksum, old.code, old.dex, variableStates);
    }
}
