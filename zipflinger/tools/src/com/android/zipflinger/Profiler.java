/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.zipflinger;

public class Profiler {
    static final int NUM_RES = 2500;
    static final int RES_SIZE = 1 << 12; //  4 KiB

    static final int NUM_DEX = 10;
    static final int DEX_SIZE = 1 << 22; //  4 MiB

    private static final int TOTAL_APK_SIZE = RES_SIZE * NUM_RES + DEX_SIZE * NUM_DEX;

    public static final int WARM_UP_ITERATION = 2;

    public static void prettyPrint(String label, int value) {
        String string = String.format("  - %-17s %1s %5d", label, ":", value);
        System.out.println(string);
    }

    public static void displayParameters() {
        System.out.println("Profiling with an APK :");
        prettyPrint("Total size (MiB)", TOTAL_APK_SIZE / (1 << 20));
        prettyPrint("Num res", NUM_RES);
        prettyPrint("Size res (KiB)", RES_SIZE / (1 << 10));
        prettyPrint("Num dex", NUM_DEX);
        prettyPrint("Size dex (MiB)", DEX_SIZE / (1 << 20));
        System.out.println("Checkout your tmp folder for json traces");
    }
}
