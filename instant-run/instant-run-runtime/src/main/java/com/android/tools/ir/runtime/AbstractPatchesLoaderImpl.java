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

package com.android.tools.ir.runtime;


import static com.android.tools.ir.common.Log.logging;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public abstract class AbstractPatchesLoaderImpl implements PatchesLoader {

    private final Method get;
    private final Method set;

    public AbstractPatchesLoaderImpl() throws NoSuchMethodException {
        get = AtomicReference.class.getMethod("get");
        set = AtomicReference.class.getMethod("set", Object.class);
    }

    public abstract String[] getPatchedClasses();

    @Override
    public boolean load() {
        for (String className : getPatchedClasses()) {
            try {
                ClassLoader cl = getClass().getClassLoader();
                Class<?> aClass = cl.loadClass(className + "$override");
                Object o = aClass.newInstance();

                Class<?> originalClass = cl.loadClass(className);
                Field changeField = originalClass.getDeclaredField("$change");
                // force the field accessibility as the class might not be "visible"
                // from this package.
                changeField.setAccessible(true);

                Object previous =
                        originalClass.isInterface()
                                ? patchInterface(changeField, o)
                                : patchClass(changeField, o);

                // If there was a previous change set, mark it as obsolete:
                if (previous != null) {
                    Field isObsolete = previous.getClass().getDeclaredField("$obsolete");
                    if (isObsolete != null) {
                        isObsolete.set(null, true);
                    }
                }

                if (logging != null && logging.isLoggable(Level.FINE)) {
                    logging.log(Level.FINE, String.format("patched %s", className));
                }
            } catch (Exception e) {
                if (logging != null) {
                    logging.log(
                            Level.SEVERE,
                            String.format("Exception while patching %s", className),
                            e);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * When dealing with interfaces, the $change field is a final {@link
     * java.util.concurrent.atomic.AtomicReference} instance which contains the current patch class
     * or null if it was never patched.
     *
     * @param changeField the $change field.
     * @param patch the patch class instance.
     * @return the previous patch instance.
     */
    private Object patchInterface(Field changeField, Object patch)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {

        Object atomicReference = changeField.get(null);
        Object previous = get.invoke(atomicReference);
        set.invoke(atomicReference, patch);
        return previous;
    }

    /**
     * When dealing with classes, the $change field is the patched class instance or null if it was
     * never patched.
     *
     * @param changeField the $change field.
     * @param patch the patch class instance.
     * @return the previous patch instance.
     */
    private Object patchClass(Field changeField, Object patch) throws IllegalAccessException {
        Object previous = changeField.get(null);
        changeField.set(null, patch);
        return previous;
    }
}
