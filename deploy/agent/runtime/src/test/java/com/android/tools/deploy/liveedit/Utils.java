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

import com.android.tools.idea.util.StudioPathManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    boolean isClassLoaded(String binaryName)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        java.lang.reflect.Method m =
                ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] {String.class});
        m.setAccessible(true);
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        return m.invoke(cl, binaryName) != null;
    }

    static byte[] buildClass(String binaryClassName) throws IOException, ClassNotFoundException {
        Class klass = Class.forName(binaryClassName);
        return buildClass(klass);
    }
    /**
     * Extract the bytecode data from a class file from its name. If this is run from intellij, it
     * searches for .class file in the idea out director. If this is run from a jar file, it
     * extracts the class file from the jar.
     */
    static byte[] buildClass(Class clazz) throws IOException {
        InputStream in = null;
        String pathToSearch = "/" + clazz.getName().replaceAll("\\.", "/") + ".class";
        if (StudioPathManager.isRunningFromSources()) {
            Path file =
                    Paths.get(
                            StudioPathManager.getSourcesRoot(),
                            "/tools/adt/idea/out/test/android.sdktools.deployer.deployer-runtime-support",
                            pathToSearch);
            if (Files.exists(file)) {
                in = Files.newInputStream(file);
            } else {
                in = clazz.getResourceAsStream(pathToSearch);
            }
        } else {
            in = clazz.getResourceAsStream(pathToSearch);
        }

        if (in == null) {
            throw new IllegalStateException(
                    "Unable to load '" + clazz + "' from classLoader " + clazz.getClassLoader());
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len = in.read(buffer); len != -1; len = in.read(buffer)) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }
}
