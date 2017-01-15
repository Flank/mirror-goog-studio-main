/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

public final class FileUtils {

    private FileUtils() {
    }

    public static void mkdirs(File folder) {
        // attempt to create first.
        // if failure only throw if folder does not exist.
        // This makes this method able to create the same folder(s) from different thread
        if (!folder.mkdirs() && !folder.exists()) {
            throw new RuntimeException("Cannot create directory " + folder);
        }
    }

    public static void delete(File file) throws IOException {
        boolean result = file.delete();
        if (!result) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
        }
    }


    /**
     * Computes the relative of a file or directory with respect to a directory.
     *
     * @param file the file or directory, which must exist in the filesystem
     * @param dir  the directory to compute the path relative to
     * @return the relative path from {@code dir} to {@code file}; if {@code file} is a directory
     * the path comes appended with the file separator (see documentation on {@code relativize} on
     * java's {@code URI} class)
     */
    public static String relativePath(File file, File dir) {
        String path = dir.toURI().relativize(file.toURI()).getPath();
        return toSystemDependentPath(path);
    }

    /**
     * Converts a /-based path into a path using the system dependent separator.
     *
     * @param path the system independent path to convert
     * @return the system dependent path
     */
    public static String toSystemDependentPath(String path) {
        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }
        return path;
    }

    public static Iterable<File> getAllFiles(File dir) throws IOException {
        Stream<File> stream = Files.walk(dir.toPath()).map(Path::toFile).filter(File::isFile);
        return new Iterable<File>() {
            @Override
            public Iterator<File> iterator() {
                return stream.iterator();
            }
        };
    }

    public static void copyFile(File input, File output) throws
    IOException {
        Files.createDirectories(output.getParentFile().toPath());
        FileInputStream fis = new FileInputStream(input);
        FileOutputStream fos = new FileOutputStream(output);
        copyStreams(fis,fos);
        fos.flush();
        fos.close();
    }

    static void copyStreams(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        while((read = is.read(buffer)) > -1) {
            os.write(buffer, 0, read);
        }
    }
}
