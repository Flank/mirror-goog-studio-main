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
package androidx.compose.runtime.internal;

/**
 * Mock of JetPack Compose's Runtime.
 *
 * In particular, updateLiveLiteralValue is creawted to print out its inputs.
 */
public final class LiveLiteralKt {

    public static final void updateLiveLiteralValue(String name, Object value) {
        if (isLiveLiteralsEnabled) {
            System.out.print("updateLiveLiteralValue(");
            System.out.print(name);
            System.out.print(", ");
            System.out.print(value.getClass());
            System.out.print(", ");
            System.out.print(value);
            System.out.println(")");
        }
    }

    public static boolean isLiveLiteralsEnabled = false;

    public static void enableLiveLiterals() {
        isLiveLiteralsEnabled = true;
    }
}
