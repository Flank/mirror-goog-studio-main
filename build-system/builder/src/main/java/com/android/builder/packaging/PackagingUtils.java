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

package com.android.builder.packaging;


import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Utility class for packaging.
 */
public class PackagingUtils {

    /**
     * Set of file formats which are already compressed, or don't compress well, same as the one
     * used by aapt.
     */
    public static final ImmutableSet<String> DEFAULT_DONT_COMPRESS_EXTENSIONS =
            ImmutableSet.of(
                    ".jpg", ".jpeg", ".png", ".gif", ".wav", ".mp2", ".mp3", ".ogg", ".aac", ".mpg",
                    ".mpeg", ".mid", ".midi", ".smf", ".jet", ".rtttl", ".imy", ".xmf", ".mp4",
                    ".m4a", ".m4v", ".3gp", ".3gpp", ".3g2", ".3gpp2", ".amr", ".awb", ".wma",
                    ".wmv", ".webm", ".mkv");


    /**
     * Checks a file to make sure it should be packaged as standard resources.
     * @param filePath OS-independent path of the file (including extension), relative to the
     *                 archive
     * @param allowClassFiles whether to allow java class files
     * @return true if the file should be packaged as standard java resources
     */
    public static boolean checkFileForApkPackaging(
            @NonNull String filePath, boolean allowClassFiles) {
        String fileName = new File(filePath).getName();

        // ignore hidden files and backup files
        return !isOfNonResourcesExtensions(Files.getFileExtension(fileName), allowClassFiles)
                && !filePath.equals("META-INF/MANIFEST.MF")
                && !isUsedForSigning(filePath)
                && !isMavenMetadata(filePath);
    }

    private static boolean isMavenMetadata(String filePath) {
        return filePath.startsWith("META-INF/maven");
    }

    private static boolean isUsedForSigning(String filePath) {
        if (!"META-INF".equals(new File(filePath).getParent())) {
            return false;
        }

        String fileExtension = Files.getFileExtension(filePath);
        for (String extension : SIGNING_EXTENSIONS) {
            if (fileExtension.equalsIgnoreCase(extension)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isOfNonResourcesExtensions(
            @NonNull String extension,
            boolean allowClassFiles) {
        for (String ext : NON_RESOURCES_EXTENSIONS) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }

        return !allowClassFiles && SdkConstants.EXT_CLASS.equals(extension);
    }

    /**
     * List of file extensions that represents non resources files.
     */
    private static final ImmutableList<String> NON_RESOURCES_EXTENSIONS =
            ImmutableList.<String>builder()
                    .add("aidl")            // Aidl files
                    .add("rs")              // RenderScript files
                    .add("fs")              // FilterScript files
                    .add("rsh")             // RenderScript header files
                    .add("d")               // Dependency files
                    .add("java")            // Java files
                    .add("scala")           // Scala files
                    .add("scc")             // VisualSourceSafe
                    .add("swp")             // vi swap file
                    .build();

    /**
     * List of file extensions that are used for jar signing.
     */
    public static final ImmutableList<String> SIGNING_EXTENSIONS =
            ImmutableList.of("SF", "RSA", "DSA", "EC");

    @NonNull
    public static Predicate<String> getDefaultNoCompressPredicate() {
        return getNoCompressPredicateForExtensions(DEFAULT_DONT_COMPRESS_EXTENSIONS);
    }

    @NonNull
    public static Predicate<String> getNoCompressPredicate(
            @Nullable Collection<String> aaptOptionsNoCompress,
            @NonNull ManifestAttributeSupplier manifest) {
        NativeLibrariesPackagingMode packagingMode =
                getNativeLibrariesLibrariesPackagingMode(manifest);
        PackageEmbeddedDex useEmbeddedDex = getUseEmbeddedDex(manifest);

        return getNoCompressPredicateForExtensions(
                getAllNoCompressExtensions(aaptOptionsNoCompress, packagingMode, useEmbeddedDex));
    }

    @NonNull
    public static List<String> getNoCompressGlobsForBundle(
            @NonNull Collection<String> aaptOptionsNoCompress) {
        return getAllNoCompressExtensions(
                        aaptOptionsNoCompress,
                        NativeLibrariesPackagingMode.COMPRESSED,
                        PackageEmbeddedDex.DEFAULT)
                .stream()
                .map(s -> "**" + s)
                .sorted()
                .collect(ImmutableList.toImmutableList());
    }

    @NonNull
    public static NativeLibrariesPackagingMode getNativeLibrariesLibrariesPackagingMode(
            @NonNull ManifestAttributeSupplier manifest) {
        Boolean extractNativeLibs = manifest.getExtractNativeLibs();

        // The default is "true", so we only package *.so files differently if the user explicitly
        // set this to "false".
        if (Boolean.FALSE.equals(extractNativeLibs)) {
            return NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED;
        } else {
            return NativeLibrariesPackagingMode.COMPRESSED;
        }
    }

    @NonNull
    public static PackageEmbeddedDex getUseEmbeddedDex(
            @NonNull ManifestAttributeSupplier manifest) {
        Boolean useEmbeddedDex = manifest.getUseEmbeddedDex();

        if (useEmbeddedDex == null) {
            return PackageEmbeddedDex.DEFAULT;
        } else if (Boolean.TRUE.equals(useEmbeddedDex)) {
            return PackageEmbeddedDex.UNCOMPRESSED;
        } else {
            return PackageEmbeddedDex.COMPRESSED;
        }
    }

    @NonNull
    private static Predicate<String> getNoCompressPredicateForExtensions(
            @NonNull Iterable<String> noCompressExtensions) {
        return name -> {
            for (String extension : noCompressExtensions) {
                if (name.toLowerCase(Locale.US).endsWith(extension)) {
                    return true;
                }
            }
            return false;
        };
    }

    @NonNull
    private static List<String> getAllNoCompressExtensions(
            @Nullable Collection<String> aaptOptionsNoCompress,
            @NonNull NativeLibrariesPackagingMode nativeLibrariesPackagingMode,
            PackageEmbeddedDex useEmbeddedDex) {
        List<String> result = Lists.newArrayList(DEFAULT_DONT_COMPRESS_EXTENSIONS);

        if (nativeLibrariesPackagingMode == NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED) {
            result.add(SdkConstants.DOT_NATIVE_LIBS);
        }
        if (!useEmbeddedDex.isCompressed()) {
            result.add(SdkConstants.DOT_DEX);
        }

        if (aaptOptionsNoCompress != null) {
            result.addAll(aaptOptionsNoCompress);
        }
        return result;
    }
}
