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
package com.activity;

import android.app.Activity;
import java.util.*;

public class NativeCodeActivity extends Activity {
    public NativeCodeActivity() {
        super("NativeCodeActivity");
        System.loadLibrary("nativetest");
    }

    public static class JNITestEntity {};

    List<Long> refs = new ArrayList<Long>();

    public void createRefs() {
        final int refCount = Integer.parseInt(System.getProperty("jni.refcount"));
        assert (refCount > 0);
        for (int i = 0; i < refCount; i++) {
            Object o = new JNITestEntity();
            Long ref = AllocateGlobalRef(o);
            System.out.printf("JNI ref created %d\n", ref);
            refs.add(ref);
        }
        System.out.println("createRefs");
    }

    public void deleteRefs() {
        for (Long ref : refs) {
            System.out.printf("JNI ref deleted %d\n", ref);
            FreeGlobalRef(ref);
        }
        refs.clear();
        System.out.println("deleteRefs");
    }

    private static native String NativeToString(long ref);

    private static native long AllocateGlobalRef(Object o);

    private static native void FreeGlobalRef(long ref);
}
