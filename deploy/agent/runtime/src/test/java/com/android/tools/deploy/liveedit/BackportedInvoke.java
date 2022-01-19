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

public class BackportedInvoke {
    public static int compareByteUnsigned(byte x, byte y) {
        return Byte.compareUnsigned(x, y);
    }

    public static int compareShortUnsigned(short x, short y) {
        return Short.compareUnsigned(x, y);
    }

    public static long mathMultiplyExact(long x, int y) {
        return Math.multiplyExact(x, y);
    }

    public static long strictMathMultiplyExact(long x, int y) {
        return StrictMath.multiplyExact(x, y);
    }

    public static long mathMultiplyFull(int x, int y) {
        return Math.multiplyFull(x, y);
    }

    public static long strictMathMultiplyFull(int x, int y) {
        return StrictMath.multiplyFull(x, y);
    }

    public static long mathMultiplyHigh(long x, long y) {
        return Math.multiplyHigh(x, y);
    }

    public static long strictMathMultiplyHigh(long x, long y) {
        return StrictMath.multiplyHigh(x, y);
    }

    public static long mathFloorDiv(long x, int y) {
        return Math.floorDiv(x, y);
    }

    public static long strictMathFloorDiv(long x, int y) {
        return StrictMath.floorDiv(x, y);
    }

    public static int mathFloorMod(long x, int y) {
        return Math.floorMod(x, y);
    }

    public static int strictMathFloorMod(long x, int y) {
        return StrictMath.floorMod(x, y);
    }

    public static java.util.Map copyOfMap(java.util.Map map) {
        return java.util.Map.copyOf(map);
    }

    public static java.util.Set copyOfSet(java.util.Collection set) {
        return java.util.Set.copyOf(set);
    }

    public static java.util.List copyOfList(java.util.Collection list) {
        return java.util.List.copyOf(list);
    }
}
