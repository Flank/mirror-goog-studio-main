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

class TestTarget {

    private static int getStaticTwo() {
        return 2;
    }

    public static int getInstanceNine() {
        return 9;
    }

    private String field = "A_FIEID";

    private int six = 6;

    private static int ten = 10;

    public int returnFive() {
        return 5;
    }

    private int memberInt = 0;
    private int staticInt = 0;

    public int returnSeventeen() {
        memberInt = 0;
        staticInt = 1;
        int[] x = new int[1];
        x[0] = ten;
        staticInt = memberInt = six + x.length + x[0] + staticInt;
        staticInt += this.hashCode();
        return memberInt + getStaticTwo() + getInstanceNine() + memberInt + staticInt;
    }

    public String returnHappiness() {
        return "Happiness";
    }

    public String returnField() {
        return field;
    }

    public int returnPlusOne(int input) {
        return input + 1;
    }
}
