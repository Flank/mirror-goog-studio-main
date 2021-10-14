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

public class IfTarget {
    public static boolean testIfEq(int i1) {
        // Don't simplify, this also test correct jump.
        if (i1 == 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIfNeq(int i1) {
        // Don't simplify, this also test correct jump.
        if (i1 != 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIfLe(int i1) {
        // Don't simplify, this also test correct jump.
        if (i1 <= 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIfLt(int i1) {
        // Don't simplify, this also test correct jump.
        if (i1 < 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIfGe(int i1) {
        // Don't simplify, this also test correct jump.
        if (i1 >= 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIfGt(int i1) {
        // Don't simplify, this also test correct jump.
        if (i1 > 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIfNull(Object o) {
        // Don't simplify, this also test correct jump.
        if (o == null) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean testIfNonNull(Object o) {
        // Don't simplify, this also test correct jump.
        if (o != null) {
            return true;
        } else {
            return false;
        }
    }
}
