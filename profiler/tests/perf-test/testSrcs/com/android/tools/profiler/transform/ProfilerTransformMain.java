/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.transform;

import com.android.tools.profiler.ProfilerTransform;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class ProfilerTransformMain {

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.err.println(
                "Invalid number of args. Usage is ProfilerTransformMain [inputJar] [outputJar]");
            return;
        }
        File inputJar = new File(args[0]);
        if (!inputJar.exists()) {
            System.err.println("Input file does not exist: " + args[0]);
            return;
        }
        File outputJar = new File(args[1]);
        if (outputJar.exists()) {
            System.out.println("Output file already exist, replacing.");
        }
        try {
            JarInputStream inputStream = new JarInputStream(new FileInputStream(inputJar));
            JarOutputStream outputStream =
                new JarOutputStream(new FileOutputStream(outputJar), inputStream.getManifest());
            ProfilerTransform transform = new ProfilerTransform();
            byte[] buffer = new byte[1024 * 16]; //16kb
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                outputStream.putNextEntry(entry);
                // If the entry is a class file, lets transform it.
                if (entry.getName().endsWith(".class")) {
                    transform.accept(inputStream, outputStream);
                } else {
                    // Else if the entry has data we direct copy that data over.
                    int readSize;
                    while ((readSize = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, readSize);
                    }
                }
                outputStream.closeEntry();
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException ex) {
            System.err.println("Failed to write jar file: " + ex);
        }
    }
}
