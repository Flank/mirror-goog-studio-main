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

import com.android.annotations.NonNull;
import com.android.tools.deploy.interpreter.MethodDescription;
import com.android.tools.deploy.interpreter.Value;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

public class BackPorterEval extends AndroidEval {

    private static final String BP_PKG_NAME = "com/android/tools/deploy/liveedit/backported";
    private static final HashMap<String, MethodDescription> backported = new HashMap<>();
    private static int apiLevel;

    static {
        detectAPILevel();
        addBackPortedMethods();
    }

    public BackPorterEval(ClassLoader classloader) {
        super(classloader);
    }

    // We must be able to run on non-Android environment and test environment as well
    // therefore we use introspection to access Android runtime API.
    private static void detectAPILevel() {
        try {
            Class clazz = Class.forName("android.os.Build$VERSION");
            Field field = clazz.getDeclaredField("SDK_INT");
            apiLevel = field.getInt(null);
        } catch (Exception e) {
            apiLevel = 0;
        }
    }

    private static void addBackPortedMethods() {
        if (apiLevel < 31) {
            addBackPorted31();
        }
        // Add here future APIs
    }

    // List extract from:
    // com.android.tools.r8.ir.desugar.BackportedMethodRewriter.initializeAndroidSMethodProviders
    // implementations are found in:
    // https://github.com/AndroidSDKSources/android-sdk-sources-for-api-level-31/tree/master/java
    private static void addBackPorted31() {
        addBackported("java/lang", "Byte", "compareUnsigned", "(BB)I");
        addBackported("java/lang", "Short", "compareUnsigned", "(SS)I");
        String[] maths = {"Math", "StrictMath"};
        for (String math : maths) {
            addBackported("java/lang", math, "multiplyExact", "(JI)J");
            addBackported("java/lang", math, "multiplyFull", "(II)J");
            addBackported("java/lang", math, "multiplyHigh", "(JJ)J");
            addBackported("java/lang", math, "floorDiv", "(JI)J");
            addBackported("java/lang", math, "floorMod", "(JI)I");

            BackportedMethod bp =
                    BackportedMethod.as("(ILjava/lang/Object;)V")
                            .from("android/util/SparseArray", "set")
                            .to("android/util/SparseArray", "put")
                            .build();
            backported.put(bp.key(), bp.target());
        }
        addBackported("java/util", "List", "copyOf", "(Ljava/util/Collection;)Ljava/util/List;");
        addBackported("java/util", "Set", "copyOf", "(Ljava/util/Collection;)Ljava/util/Set;");
        addBackported("java/util", "Map", "copyOf", "(Ljava/util/Map;)Ljava/util/Map;");
    }

    private static void addBackported(String pkg, String className, String name, String desc) {
        BackportedMethod bp =
                BackportedMethod.as(desc)
                        .from(pkg + "/" + className, name)
                        .to(BP_PKG_NAME + "/" + className, name)
                        .build();
        backported.put(bp.key(), bp.target());
    }

    @NonNull
    @Override
    public Value invokeStaticMethod(MethodDescription md, @NonNull List<? extends Value> args) {
        String key = BackportedMethod.genKey(md);
        if (backported.containsKey(key)) {
            md = backported.get(key);
        }
        return super.invokeStaticMethod(md, args);
    }

    @NonNull
    @Override
    public Value invokeSpecial(
            @NonNull Value target, MethodDescription md, @NonNull List<? extends Value> args) {
        String key = BackportedMethod.genKey(md);
        if (backported.containsKey(key)) {
            md = backported.get(key);
        }
        return super.invokeSpecial(target, md, args);
    }

    @NonNull
    @Override
    public Value invokeMethod(
            @NonNull Value target, MethodDescription md, @NonNull List<? extends Value> args) {
        String key = BackportedMethod.genKey(md);
        if (backported.containsKey(key)) {
            md = backported.get(key);
        }
        return super.invokeMethod(target, md, args);
    }
}
