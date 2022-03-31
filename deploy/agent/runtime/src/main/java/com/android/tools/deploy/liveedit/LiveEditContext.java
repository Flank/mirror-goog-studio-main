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

import com.android.annotations.VisibleForTesting;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LiveEditContext holds the current application's LiveEdit state and provides the interface for
 * LiveEdit to interact with that state. The state consists of the classes that have been Live
 * Edited, as well as the application classloader that should be used to resolve classes and define
 * new proxies.
 */
public class LiveEditContext {

    // Map of class name to class info for all classes for which we have interpretable bytcode.
    // Class names are expected in the java internal form with class elements separated by slashes.
    // For example, com/android/tools/deploy/liveedit/LiveEditContext.
    private final ConcurrentHashMap<String, LiveEditClass> classes;

    // The class loader that should be used for class resolution and defining proxy classes.
    private ClassLoader classLoader;

    public LiveEditContext(ClassLoader classLoader) {
        this.classes = new ConcurrentHashMap<>();
        this.classLoader = classLoader;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public LiveEditClass getClass(String name) {
        return classes.get(name);
    }

    public LiveEditClass addClass(String internalName, byte[] bytecode, boolean isProxyClass) {
        LiveEditClass clazz = new LiveEditClass(this, bytecode, isProxyClass);
        classes.put(internalName, clazz);
        return clazz;
    }

    @VisibleForTesting
    public void removeClass(String name) {
        classes.remove(name);
    }
}
