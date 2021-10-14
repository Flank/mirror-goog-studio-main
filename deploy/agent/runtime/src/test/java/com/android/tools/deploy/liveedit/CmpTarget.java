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

public class CmpTarget {

    public static boolean testIcmpEq(int i1, int i2) {
        // Don't simplify, this also test correct jump.
        if (i1 == i2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIcmpNeq(int i1, int i2) {
        // Don't simplify, this also test correct jump.
        if (i1 != i2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIcmpGe(int i1, int i2) {
        // Don't simplify, this also test correct jump.
        if (i1 >= i2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIcmpGt(int i1, int i2) {
        // Don't simplify, this also test correct jump.
        if (i1 > i2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIcmpLe(int i1, int i2) {
        // Don't simplify, this also test correct jump.
        if (i1 <= i2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIcmpLt(int i1, int i2) {
        // Don't simplify, this also test correct jump.
        if (i1 < i2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIcmpLe(Object o1, Object o2) {
        // Don't simplify, this also test correct jump.
        if (o1 == o2) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIcmpNe(Object o1, Object o2) {
        // Don't simplify, this also test correct jump.
        if (o1 != o2) {
            return true;
        } else {
            return false;
        }
    }
}
