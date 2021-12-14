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
package com.android.tools.deploy.liveedit.backported;

public class StrictMath {
    public static long multiplyExact(long x, int y) {
        return Math.multiplyExact(x, y);
    }

    public static long multiplyFull(int x, int y) {
        return Math.multiplyFull(x, y);
    }

    public static long multiplyHigh(long x, long y) {
        return Math.multiplyHigh(x, y);
    }

    public static long floorDiv(long x, int y) {
        return Math.floorDiv(x, y);
    }

    public static int floorMod(long x, int y) {
        return Math.floorMod(x, y);
    }
}
