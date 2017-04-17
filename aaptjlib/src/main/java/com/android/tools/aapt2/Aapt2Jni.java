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

package com.android.tools.aapt2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code aapt2} JNI interface. When first loaded, this class will load the native library
 * containing the {@code aapt2} native code. The native library is loaded from a resource.
 *
 * <p>To use this class simply create a new instance and invoke one of the public static methods.
 * {@code aapt2} is thread-safe and bugfree.
 */
public final class Aapt2Jni {

    /*
     * Load aapt2 when the class is initialized.
     */
    static {
        if (!isLoaded()) {
            load();
        }
    }

    /**
     * Checks if the {@code aapt2} native library has been loaded.
     *
     * @return has it been loaded?
     */
    private static boolean isLoaded() {
        try {
            ping();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Loads the {@code aapt2} native library, extracting it from the resources and loading it. */
    private static void load() {
        String resourcePath;

        /*
         * Figure out what the OS name is and what the architecture is. The OS name also defines the
         * extension for the library files.
         */
        String osName;
        String libExt;
        String osNameProperty = System.getProperty("os.name");
        if (osNameProperty.startsWith("Windows")) {
            osName = "win";
            libExt = "dll";
        } else if (osNameProperty.startsWith("Mac OS X")) {
            osName = "mac";
            libExt = "dylib";
        } else if (osNameProperty.startsWith("Linux") || osNameProperty.startsWith("LINUX")) {
            osName = "linux";
            libExt = "so";
        } else {
            throw new Aapt2Exception("Unknown OS: " + osNameProperty);
        }

        boolean is64Bit = System.getProperty("os.arch").contains("64");

        /*
         * Build the suffix for the libraries. We know all libraries to be loaded will be in
         * a directory with the name "libDirName". Because we can't enumerate all resources in
         * a directory, we will enumerate all possible libraries and will only load those that
         * exist. Note that the library order is relevant as libraries are loaded in order.
         *
         * The main JNI library must, of course, exist.
         */
        String libDirName = osName + (is64Bit ? "64" : "32");
        String pfx = "/" + libDirName + "/";
        loadSharedLibrariesFromResources(
                pfx + "libaapt2_jni." + libExt,
                pfx + "libwinpthread-1." + libExt,
                pfx + "libc++." + libExt);
    }

    /**
     * Loads a shared library from a java resource. This method will extract the resource with path
     * {@code resource} into directory {@code dir} and load it as a shared library. It will return
     * the file created.
     *
     * <p>If the resource does not exist and {@code optional} is {@code true}, then {@code null} is
     * returned.
     *
     * <p>If the resource does not exist and {@code optional} is {@code false}, then an exception is
     * thrown.
     *
     * @param resource the path of the resource to load; it must be an absolute resource path
     * @param dir an existing directory that will receive the file extracted
     * @param optional is the resource optional? If {@code true} and the resource does not exist,
     *     then {@code null} is returned and no library is loaded
     * @throws Aapt2Exception if the resource does not exist and {@code optional} is {@code false}
     *     or if the resource exists but writing fails or the library fails to load
     */
    @Nullable
    private static File loadLibraryFromResource(
            @Nonnull String resource, @Nonnull File dir, boolean optional) throws Aapt2Exception {

        int lastSlash = resource.lastIndexOf('/');
        if (lastSlash == -1) {
            throw new IllegalArgumentException("Resource should be an absolute path");
        }

        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(
                    "'" + dir.getAbsolutePath() + "' is not a directory");
        }

        String fname = resource.substring(lastSlash + 1);
        File extracted = new File(dir, fname);

        try (InputStream is = Aapt2Jni.class.getResourceAsStream(resource)) {
            if (is == null) {
                if (optional) {
                    return null;
                } else {
                    throw new Aapt2Exception("Resource '" + resource + "' not found");
                }
            }

            Files.copy(is, extracted.toPath());
        } catch (IOException e) {
            throw new Aapt2Exception(
                    "Failed copying resource '"
                            + resource
                            + "' to '"
                            + extracted.getAbsolutePath()
                            + "'",
                    e);
        }

        try {
            System.load(extracted.getAbsolutePath());
        } catch (Throwable t) {
            throw new Aapt2Exception(
                    "Failed to load shared library '" + extracted.getAbsolutePath() + "'", t);
        }

        return extracted;
    }

    /**
     * Loads a shared library from a java resource with its required resources loaded first. This
     * method will extract a shared library from a resource and load it. If required resources are
     * defined, then these are loaded before the library.
     *
     * <p>It is OK of some required resources are not found. This method will skip them. No error
     * will be thrown if required resources cannot be found. However, if the resources are found,
     * they are expected to load correctly.
     *
     * @param resource the java resource with the main library; the same file name will be used when
     *     extracted so a resource named as {@code /foo/bar.so} will be extracted to a file named
     *     {@code bar.so}
     * @param requiredResources optional libraries to load before the main library; the same file
     *     name will be used when extracted
     * @throws Aapt2Exception failed to load the shared libraries
     */
    private static void loadSharedLibrariesFromResources(
            @Nonnull String resource, @Nonnull String... requiredResources) throws Aapt2Exception {

        /*
         * Create a temporary directory, extract and load all libraries.
         */

        File tempDir;
        try {
            tempDir = File.createTempFile("aapt2_", ".dir");
        } catch (IOException e) {
            throw new Aapt2Exception("Failed creating temporary file to hold aapt2 shared library");
        }

        if (!tempDir.delete() || !tempDir.mkdir()) {
            throw new Aapt2Exception("Failed to create directory at " + tempDir.getAbsolutePath());
        }

        final List<File> files = new ArrayList<>();
        for (String r : requiredResources) {
            File f = loadLibraryFromResource(r, tempDir, true);
            if (f != null) {
                files.add(f);
            }
        }

        files.add(loadLibraryFromResource(resource, tempDir, false));

        /*
         * Add a hook to delete the directory and all files when the JVM exits. We can't do that
         * before because of the DLL being locked on Windows.
         */
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    files.forEach(File::delete);
                                    tempDir.delete();
                                }));
    }

    /** We cannot build instances of this class, only static methods are available. */
    private Aapt2Jni() {}

    /**
     * Invokes {@code aapt2} to perform resource compilation.
     *
     * @param arguments arguments for compilation (see {@code Compile.cpp})
     */
    @CheckReturnValue
    public static int compile(@Nonnull List<String> arguments) {
        return nativeCompile(arguments);
    }

    /**
     * Invokes {@code aapt2} to perform linking.
     *
     * @param arguments arguments for linking (see {@code Link.cpp})
     */
    @CheckReturnValue
    public static int link(@Nonnull List<String> arguments) {
        return nativeLink(arguments);
    }

    /**
     * JNI call for a method that does nothing, but allows checking whether the shared library has
     * been loaded.
     *
     * <p>Even if this class is loaded multiple times with multiple class loaders we want to load
     * the shared library only once to avoid increasing the memory footprint. If this method can be
     * called successfully, then we know we the library has already been loaded.
     */
    private static native void ping();

    /**
     * JNI call.
     *
     * @param arguments arguments for compilation (see {@code Compile.cpp})
     */
    private static native int nativeCompile(@Nonnull List<String> arguments);

    /**
     * JNI call.
     *
     * @param arguments arguments for linking (see {@code Link.cpp})
     */
    private static native int nativeLink(@Nonnull List<String> arguments);
}
