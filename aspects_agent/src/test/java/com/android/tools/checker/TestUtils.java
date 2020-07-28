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

package com.android.tools.checker;

public class TestUtils {
    private static final String JAVA_REFLECT_PACKAGE = findJavaReflectPackage();

    public static String getReflectInvokeMethod() {
        return String.format("%s.invoke0", getReflectMethodAccessorClass());
    }

    public static String getReflectMethodAccessorClass() {
        return String.format("%s.NativeMethodAccessorImpl", JAVA_REFLECT_PACKAGE);
    }

    private static String findJavaReflectPackage() {
        try {
            if (Float.parseFloat(System.getProperty("java.specification.version")) >= 11) {
                // After bumping our JDK to 11, we started using jdk.internal.reflect.
                return "jdk.internal.reflect";
            }
        } catch (Exception ex) {
            // If java.specification.version is not provided or has an unexpected format,
            // assume "sun.reflect".
        }
        return "sun.reflect";
    }
}
