/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.perflib.heap.ext;

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;

/**
 * In O+, portion of the memory of some object instances (e.g. Bitmap's buffer) has been moved to
 * native. While these are accounted for in the NativeAllocationRegistry in Java space, they do not
 * get blamed on the actualy object instances that are responsible for them. The postprocessor look
 * for these entries in the registry and find their corresponding "owning" instance and add it to
 * their native size.
 */
public class NativeRegistryPostProcessor implements SnapshotPostProcessor {

    static final String CLEANER_CLASS = "sun.misc.Cleaner";
    static final String CLEANER_THUNK_CLASS = "libcore.util.NativeAllocationRegistry$CleanerThunk";
    static final String NATIVE_REGISTRY_CLASS = "libcore.util.NativeAllocationRegistry";

    private boolean myHasNativeAllocations;

    public boolean getHasNativeAllocations() {
        return myHasNativeAllocations;
    }

    @Override
    public void postProcess(@NonNull Snapshot snapshot) {
        /**
         * Native allocations can be identified by looking at instances of
         * libcore.util.NativeAllocationRegistry$CleanerThunk. The "owning" Java object is the
         * "referent" field of the "sun.misc.Cleaner" instance with a hard reference to the
         * CleanerThunk.
         *
         * <p>The size is in the 'size' field of the libcore.util.NativeAllocationRegistry instance
         * that the CleanerThunk has a pointer to. The native pointer is in the 'nativePtr' field of
         * the CleanerThunk. The hprof does not include the native bytes pointed to.
         */
        ClassObj cleanerClass = snapshot.findClass(CLEANER_CLASS);
        if (cleanerClass == null) {
            return;
        }

        for (Instance cleanerInst : cleanerClass.getInstancesList()) {
            ClassInstance cleaner = (ClassInstance) cleanerInst;
            Object referent = getField(cleaner, "referent");
            if (!(referent instanceof Instance)) {
                continue;
            }

            Instance inst = (Instance) referent;
            Object thunkValue = getField(cleaner, "thunk");
            if (!(thunkValue instanceof ClassInstance)) {
                continue;
            }

            ClassInstance thunk = (ClassInstance) thunkValue;
            ClassObj thunkClass = thunk.getClassObj();
            if (thunkClass == null || !CLEANER_THUNK_CLASS.equals(thunkClass.getClassName())) {
                continue;
            }

            for (ClassInstance.FieldValue thunkField : thunk.getValues()) {
                if (!(thunkField.getValue() instanceof ClassInstance)) {
                    continue;
                }

                ClassInstance registry = (ClassInstance) thunkField.getValue();
                ClassObj registryClass = registry.getClassObj();
                if (registryClass == null
                        || !NATIVE_REGISTRY_CLASS.equals(registryClass.getClassName())) {
                    continue;
                }

                Object sizeValue = getField(registry, "size");
                if (sizeValue instanceof Long) {
                    inst.setNativeSize((long) sizeValue);
                    myHasNativeAllocations = true;
                    break;
                }
            }
        }
    }

    /**
     * Helper function to read a single field from a perflib class instance. Returns null if field
     * not found. Note there is no way to distinguish between field not found an a field value of
     * null.
     */
    private static Object getField(ClassInstance cls, String name) {
        for (ClassInstance.FieldValue field : cls.getValues()) {
            if (name.equals(field.getField().getName())) {
                return field.getValue();
            }
        }
        return null;
    }
}
