/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.app.inspection;

import androidx.inspection.InspectorEnvironment;
import java.util.Arrays;
import java.util.List;

class InspectorEnvironmentImpl implements InspectorEnvironment {
    private final long mAppInspectionServicePtr;

    InspectorEnvironmentImpl(long mAppInspectionServicePtr) {
        this.mAppInspectionServicePtr = mAppInspectionServicePtr;
    }

    @Override
    public <T> List<T> findInstances(Class<T> clazz) {
        return Arrays.asList(nativeFindInstances(mAppInspectionServicePtr, clazz));
    }

    @Override
    public void registerEntryHook(Class<?> originClass, String originMethod, EntryHook entryHook) {
        AppInspectionService.addEntryHook(originClass, originMethod, entryHook);
        nativeRegisterEntryHook(mAppInspectionServicePtr, originClass, originMethod);
    }

    @Override
    public <T> void registerExitHook(
            Class<?> originClass, String originMethod, ExitHook<T> exitHook) {
        AppInspectionService.addExitHook(originClass, originMethod, exitHook);
        nativeRegisterExitHook(mAppInspectionServicePtr, originClass, originMethod);
    }

    private static native <T> T[] nativeFindInstances(long servicePtr, Class<T> clazz);

    private static native void nativeRegisterEntryHook(
            long servicePtr, Class<?> originClass, String originMethod);

    private static native void nativeRegisterExitHook(
            long servicePtr, Class<?> originClass, String originMethod);
}
