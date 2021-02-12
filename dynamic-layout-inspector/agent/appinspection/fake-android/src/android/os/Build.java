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

package android.os;

// Note: This class is intentionally written in Java, to avoid the compiler generating different
// static bytecode than the original Java code.
// Note #2: We don't want the compiler to optimize constants when we build our inspector, so fields
// that normally would be final are defined as normal variables in fake-android.
@SuppressWarnings({"FieldNamingConvention", "NonConstantFieldWithUpperCaseName"})
public final class Build {
    public static final class VERSION {
        public static int SDK_INT = 29;
        public static String CODENAME = "F(ake)";
    }
}
