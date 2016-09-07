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

package com.android.testutils;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * Util class to help get testing resources.
 */
public final class TestResources {

    private TestResources() {}

    /**
     * Variant of {@link #getFile(Class, String)} that only works with absolute paths, so doesn't
     * need a "context" class.
     *
     * @see #getFile(Class, String)
     */
    public static File getFile(String name) {
        Preconditions.checkArgument(name.startsWith("/"), "'%s' is not an absolute path.", name);
        return getFile(TestResources.class, name);
    }

    /**
     * Returns a file from class resources. If original resource is not file, a temp file is created
     * and returned with resource stream content; the temp file will be deleted when program exits.
     *
     * @param clazz Test class.
     * @param name Resource name.
     * @return File with resource content.
     */
    public static File getFile(Class<?> clazz, String name) {
        URL url = Resources.getResource(clazz, name);
        if (!url.getPath().contains("jar!")) {
            return new File(url.getFile());
        }

        try {
            // Put the file in a temporary directory, so that the file itself can have its original
            // name.
            File tempDir = Files.createTempDir();
            File tempFile = new File(tempDir, name);
            tempFile.deleteOnExit();
            tempDir.deleteOnExit();

            Files.createParentDirs(tempFile);
            Resources.asByteSource(url).copyTo(Files.asByteSink(tempFile));
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Given a {@code resourceName} relative to {@code clazz}, copies the resource to a new file
     * at {@code filePath} and registers that to be deleted when the JVM exits. If there is an
     * existing file at {@code filePath}, it does nothing.
     *
     * @param clazz Resource class.
     * @param resourceName Input name to get resource from class.
     * @param filePath Output file name in specific directory.
     */
    public static void getFileInDirectory(
            Class<?> clazz, String resourceName, String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return;
        }
        URL url = Resources.getResource(clazz, resourceName);
        file.getParentFile().mkdirs();
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            Resources.copy(url, outputStream);
            file.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Variant of {@link #getDirectory(Class, String)} that only works with absolute paths, so
     * doesn't need a "context" class.
     *
     * @see #getDirectory(Class, String)
     */
    public static File getDirectory(String name) {
        Preconditions.checkArgument(name.startsWith("/"), "'%s' is not an absolute path.", name);
        return getDirectory(TestResources.class, name);
    }

    /**
     * Returns a file that is a directory from resources. If original resources is in a jar, the
     * specified directory and all files beneath are copied to system temp root directory. Those
     * copied temp files will be deleted on program exits.
     *
     * @param clazz Class with resources.
     * @param path Directory path.
     * @return Directory of given path that contains resources.
     */
    public static File getDirectory(Class<?> clazz, String path) {
        URL dirURL = Resources.getResource(clazz, path);
        switch (dirURL.getProtocol()) {
            case "file":
                return new File(dirURL.getFile());
            case "jar":
                return getDirectoryFromJar(dirURL);
            default:
                throw new UnsupportedOperationException(String.format(
                        "Unsupported protocol %s to get class %s resource directory %s",
                        dirURL.getProtocol(), clazz.getName(), path));
        }
    }

    /**
     * Returns a temp directory with the same name as given directory url. All files from that jar
     * directory are copied to the result.
     *
     * @param jarDirUrl URL of a directory in a jar.
     * @return File that is temp directory containing all files from given jar url.
     */
    private static File getDirectoryFromJar(URL jarDirUrl) {
        String dirEntryName;
        JarFile jar;
        try {
            JarURLConnection jarURLConnection = (JarURLConnection) jarDirUrl.openConnection();
            dirEntryName = jarURLConnection.getEntryName();
            jar = jarURLConnection.getJarFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Iterates all entries in jar manifest to find files under directory, then copy files.
        File root = getTempRoot();
        jar.stream().forEach(jarEntry -> {
            if (jarEntry.getName().startsWith(dirEntryName) && !jarEntry.isDirectory()) {
                File file = new File(root, jarEntry.getName());
                file.getParentFile().mkdirs();
                try (InputStream inputStream = jar.getInputStream(jarEntry)) {
                    Files.asByteSink(file).writeFrom(inputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                file.deleteOnExit();
            }
        });
        return new File(root, dirEntryName);
    }

    /**
     * Returns a directory which is the root of resources temp files.
     * @return root temp directory.
     */
    private static File getTempRoot() {
        // TODO: Find a way to get temp root without creating temp file.
        File tempFile;
        try {
            tempFile = File.createTempFile("temp", null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File root = tempFile.getParentFile();
        tempFile.delete();
        return root;
    }
}
