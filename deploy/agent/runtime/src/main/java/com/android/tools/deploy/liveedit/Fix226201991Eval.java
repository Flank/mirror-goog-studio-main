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

import com.android.annotations.NonNull;
import com.android.tools.deploy.interpreter.FieldDescription;
import com.android.tools.deploy.interpreter.Value;
import java.util.HashMap;
import java.util.Map;

// Temporary fix for b/226201991. Delete this class ASAP!
// kotlinc generates invalide bytecode when using "static final" primitive types
// with inlining disabled.

public class Fix226201991Eval extends AndroidEval {

    // See classes to use in
    // https://github.com/JetBrains/kotlin/blob/5f1977083d15afb441bf822ed0780dfebd591f9c/libraries/stdlib/jvm/runtime/kotlin/jvm/internal/PrimitiveCompanionObjects.kt
    // TODO: When we move to Java 9, use Map.of instead
    Map<String, String> renamed =
            new HashMap<String, String>() {
                {
                    put("D", "kotlin/jvm.internal/DoubleCompanionObject");
                    put("F", "kotlin/jvm.internal/FloatCompanionObject");
                    put("I", "kotlin/jvm.internal/IntCompanionObject");
                    put("J", "kotlin/jvm.internal/LongCompanionObject");
                    put("S", "kotlin/jvm.internal/ShortCompanionObject");
                    put("B", "kotlin/jvm.internal/ByteCompanionObject");
                    put("C", "kotlin/jvm.internal/CharCompanionObject");
                    put("Z", "kotlin/jvm.internal/BooleanCompanionObject");
                }
            };

    public Fix226201991Eval(ClassLoader classloader) {
        super(classloader);
    }

    public void setStaticField(@NonNull FieldDescription descor, @NonNull Value v) {
        String owner = descor.getOwnerInternalName().replace('/', '.');
        if (renamed.containsKey(owner)) {
            descor = new FieldDescription(renamed.get(owner), descor.getName(), descor.getDesc());
        }
        super.setStaticField(descor, v);
    }

    @NonNull
    @Override
    public Value getStaticField(FieldDescription descor) {
        String owner = descor.getOwnerInternalName().replace('/', '.');
        if (renamed.containsKey(owner)) {
            descor = new FieldDescription(renamed.get(owner), descor.getName(), descor.getDesc());
        }
        return super.getStaticField(descor);
    }
}
