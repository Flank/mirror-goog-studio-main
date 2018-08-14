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

package com.android.tools.profiler.support.event;

import com.android.tools.profiler.support.util.StudioLog;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class FragmentWrapper {

    private static native void sendFragmentAdded(String name, int hashCode, int activityHash);

    private static native void sendFragmentRemoved(String name, int hashCode, int activityHash);

    public static void wrapOnResume(Object fragment) {
        try {
            Method method = fragment.getClass().getMethod("getActivity");
            Object activity = method.invoke(fragment);
            sendFragmentAdded(
                    fragment.getClass().getSimpleName(), fragment.hashCode(), activity.hashCode());
        } catch (NoSuchMethodException e) {
            StudioLog.e("Failed to get method getActivity from Fragment class");
        } catch (IllegalAccessException e) {
            StudioLog.e("Insufficient privileges to get activity information");
        } catch (InvocationTargetException e) {
            StudioLog.e("Failed to call method getActivity");
        }
    }

    public static void wrapOnPause(Object fragment) {
        try {
            Method method = fragment.getClass().getMethod("getActivity");
            Object activity = method.invoke(fragment);
            sendFragmentRemoved(
                    fragment.getClass().getSimpleName(), fragment.hashCode(), activity.hashCode());
        } catch (NoSuchMethodException e) {
            StudioLog.e("Failed to get method getActivity from Fragment class");
        } catch (IllegalAccessException e) {
            StudioLog.e("Insufficient privileges to get activity information");
        } catch (InvocationTargetException e) {
            StudioLog.e("Failed to call method getActivity");
        }
    }
}
