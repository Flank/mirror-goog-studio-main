/*
 * Copyright (C) 2014 Google Inc.
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

package com.android.tools.perflib.heap;

import com.google.common.collect.Maps;
import java.util.Map;

public enum Type {
    // Pointer sizes are dependent on the hprof file, so set it to 0 for now.
    OBJECT(2, 0, null, null),
    BOOLEAN(4, 1, "boolean[]", "[Z"),
    CHAR(5, 2, "char[]", "[C"),
    FLOAT(6, 4, "float[]", "[F"),
    DOUBLE(7, 8, "double[]", "[D"),
    BYTE(8, 1, "byte[]", "[B"),
    SHORT(9, 2, "short[]", "[S"),
    INT(10, 4, "int[]", "[I"),
    LONG(11, 8, "long[]", "[J");

    private static Map<Integer, Type> sTypeMap = Maps.newHashMap();

    private int mId;

    private int mSize;

    private String mAndroidArrayName;

    private String mJavaArrayName;

    static {
        for (Type type : Type.values()) {
            sTypeMap.put(type.mId, type);
        }
    }

    Type(int type, int size, String androidArrayName, String javaArrayName) {
        mId = type;
        mSize = size;
        mAndroidArrayName = androidArrayName;
        mJavaArrayName = javaArrayName;
    }

    public static Type getType(int id) {
        return sTypeMap.get(id);
    }

    public int getSize() {
        return mSize;
    }

    public int getTypeId() {
        return mId;
    }

    public String getClassNameOfPrimitiveArray(boolean useJavaName) {
        if (this == OBJECT) {
            throw new IllegalArgumentException("OBJECT type is not a primitive type");
        }
        return useJavaName ? mJavaArrayName : mAndroidArrayName;
    }
}

