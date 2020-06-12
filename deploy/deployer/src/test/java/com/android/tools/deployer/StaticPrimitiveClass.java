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
package com.android.tools.deployer;

public class StaticPrimitiveClass {
    public static final int int1 = 1;
    public static final boolean boolFalse = false;
    public static final byte byte3 = 3;
    public static final char charK = 'k';
    public static final double double15 = 15.0d;
    public static final float float13 = 13.0f;
    public static final long long17 = 17l;
    public static final short short22 = 22;

    public final int notStatic = 1;
    public static int notFinal;

    public static final int invokedFunction = (int) (Math.random() * 100);
}
