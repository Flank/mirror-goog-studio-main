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

    public void CallNativeToString() {
        if (!NativeToString(new Integer(1)).equals("1")) {
            throw new RuntimeException ("CallNativeToString FAIL: ToString(1)");
        }
        if (!NativeToString("str").equals("str")) {
            throw new RuntimeException ("CallNativeToString FAIL: ToString(str)");
        }
        if (!NativeToString(new Object()).startsWith("java.lang.Object")) {
            throw new RuntimeException ("CallNativeToString FAIL: ToString(object)");
        }
        System.out.println("CallNativeToString - ok");
    }

    public void CreateSomeGlobalRefs() {
      CreateAndFreeGlobalRefs(1000, 1000);
      CreateAndFreeGlobalRefs(100, 80);
      System.out.println("CreateSomeGlobalRefs - ok");
    }

    private Object CreateObject(int seed) {
        switch (seed % 10) {
          case 0:
            return new Object();
          case 1:
            return "text #" + seed;
          case 2:
            return new Integer(seed);
          case 3:
            return new Boolean(true);
          case 4:
            return new int[100];
          case 5:
            return getClass();
          default:
            return this;
        }
    }

    private void CreateAndFreeGlobalRefs(int refsToCreate, int refsToFree) {
        if (refsToFree > refsToCreate) {
            throw new RuntimeException("refsToFree > refsToCreate");
        }
        List<Integer> ids = new ArrayList<Integer>();
        for (int i = 0; i < Math.max(refsToCreate, refsToFree); ++i) {
            if (i < refsToCreate) {
                Object o = CreateObject(i);
                Integer id = AllocateGlobalRef(o);
                ids.add(id);
            }
            if (i < refsToFree) {
                if (!FreeGlobalRef(ids.get(i))) {
                  throw new RuntimeException ("FreeGlobalRef failed.");
                }
            }
        }
    }

    private static native String NativeToString(Object o);
    private static native int AllocateGlobalRef(Object o);
    private static native boolean FreeGlobalRef(int id);
}
