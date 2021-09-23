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
package com.android.tools.deploy.interpreter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JNI {
    static {
        JNI.loadLibrary();
    }

    static boolean isAndroid() {
        return "Dalvik".equals(System.getProperty("java.vm.name"));
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    private static void loadLibrary() {
        // On Android the native methods are in the agent which is already loaded at this point.
        if (isAndroid()) {
            return;
        }

        // We cannot use loadLibrary because bazel generates .so on darwin instead of the expected
        // dylib extension.
        String pathProperty = System.getProperty("java.library.path");
        String[] paths = pathProperty.split(":");
        for (String path : paths) {
            path = path.trim();
            String dso = "libjni_dispatch_dso.so";
            if (isWindows()) {
                dso = "jni_dispatch_dso.dll";
            }
            Path p = Paths.get(path + "/" + dso);
            if (!Files.exists(p)) {
                continue;
            }

            System.load(p.toAbsolutePath().toString());
            break;
        }
    }

    public static native void invokespecial(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native Object invokespecialL(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native int invokespecialI(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native long invokespecialJ(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native short invokespecialS(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native byte invokespecialB(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native char invokespecialC(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native boolean invokespecialZ(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native float invokespecialF(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);

    public static native double invokespecialD(
            Object obj, Class cls, String name, String desc, Object[] args, int[] unbox);
}
