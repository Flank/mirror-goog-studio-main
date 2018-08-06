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
package com.android.tools.deploy.swapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for invoking the corresponding API to redefine a class in ART.
 *
 * <p>The implementation is transaction based where classes redefinition requests are queued up with
 * {@link #redefineClass(String, byte[])}
 */
abstract class ClassRedefiner {
    private boolean committed = false;
    protected final Map<String, byte[]> classesToRedefine = new HashMap<>();

    void redefineClass(String className, byte[] dexCode) {
        classesToRedefine.put(className, dexCode);
    }

    protected void commit() {
        if (committed) {
            throw new IllegalStateException("ClassRedefiner transaction already committed.");
        }
        committed = true;
        classesToRedefine.clear();
    }
}
