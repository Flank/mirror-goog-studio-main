/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.common.truth;

import static com.android.SdkConstants.FN_APK_CLASSES_N_DEX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.DexUtils;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.testutils.truth.IndirectSubject;
import com.android.utils.StdLogger;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.SubjectFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.junit.Assert;

/**
 * Truth support for apk files.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class ApkSubject extends AbstractAndroidSubject<ApkSubject> {

    private static final Pattern PATTERN_MAX_SDK_VERSION = Pattern.compile(
            "^maxSdkVersion\\W*:\\W*'(.+)'$");

    public static final SubjectFactory<ApkSubject, File> FACTORY =
            new SubjectFactory<ApkSubject, File>() {
                @Override
                public ApkSubject getSubject(
                        @NonNull FailureStrategy failureStrategy,
                        @NonNull File subject) {
                    return new ApkSubject(failureStrategy, subject);
                }
            };


    public ApkSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull File subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    public IndirectSubject<DexBackedDexFileSubject> hasMainDexFile() throws IOException {
        contains("classes.dex");
        return () -> {
            byte[] dexBytes = extractContentAsByte("classes.dex");
            DexBackedDexFile dexBackedDexFile = DexUtils.loadDex(dexBytes);
            return DexBackedDexFileSubject.FACTORY.getSubject(failureStrategy, dexBackedDexFile);
        };
    }

    @NonNull
    public IterableSubject<? extends IterableSubject<?, String, List<String>>,
            String, List<String>> locales() throws ProcessException {
        File apk = getSubject();
        List<String> locales = ApkHelper.getLocales(apk);

        if (locales == null) {
            Assert.fail(String.format("locales not found in badging output for %s", apk));
        }

        return check().that(locales);
    }

    public void hasPackageName(@NonNull String packageName) throws ProcessException {
        File apk = getSubject();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualPackageName = apkInfo.getPackageName();

        if (!actualPackageName.equals(packageName)) {
            failWithBadResults("has packageName", packageName, "is", actualPackageName);
        }
    }

    public void hasVersionCode(int versionCode) throws ProcessException {
        File apk = getSubject();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        Integer actualVersionCode = apkInfo.getVersionCode();
        if (actualVersionCode == null) {
            failWithRawMessage("Unable to query %s for versionCode", getDisplaySubject());
        }

        if (!apkInfo.getVersionCode().equals(versionCode)) {
            failWithBadResults("has versionCode", versionCode, "is", actualVersionCode);
        }
    }

    public void hasVersionName(@NonNull String versionName) throws ProcessException {
        File apk = getSubject();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualVersionName = apkInfo.getVersionName();
        if (actualVersionName == null) {
            failWithRawMessage("Unable to query %s for versionName", getDisplaySubject());
        }

        if (!apkInfo.getVersionName().equals(versionName)) {
            failWithBadResults("has versionName", versionName, "is", actualVersionName);
        }
    }

    public void hasMaxSdkVersion(int maxSdkVersion) throws ProcessException {

        List<String> output = ApkHelper.getApkBadging(getSubject());

        checkMaxSdkVersion(output, maxSdkVersion);
    }

    @NonNull
    public IndirectSubject<DexClassSubject> hasClass(
            @NonNull final String expectedClassName,
            @NonNull final ClassFileScope scope) throws ProcessException, IOException {
        DexBackedClassDef dexBackedClassDef = getDexClass(expectedClassName, scope);
        if (dexBackedClassDef == null) {
            fail("contains class", expectedClassName);
        }
        return () -> DexClassSubject.FACTORY.getSubject(failureStrategy, dexBackedClassDef);
    }

    /**
     * Asserts that the APK file contains an APK Signing Block (the block which may contain APK
     * Signature Scheme v2 signatures).
     */
    public void containsApkSigningBlock() throws ProcessException {
        if (!hasApkSigningBlock()) {
            failWithRawMessage("APK does not contain APK Signing Block");
        }
    }

    /**
     * Asserts that the APK file does not contain an APK Signing Block (the block which may contain
     * APK Signature Scheme v2 signatures).
     */
    public void doesNotContainApkSigningBlock() throws ProcessException {
        if (hasApkSigningBlock()) {
            failWithRawMessage("APK contains APK Signing Block");
        }
    }

    private static final byte[] APK_SIG_BLOCK_MAGIC =
            {0x41, 0x50, 0x4b, 0x20, 0x53, 0x69, 0x67, 0x20, 0x42, 0x6c, 0x6f, 0x63, 0x6b, 0x20,
                    0x34, 0x32};

    /**
     * Returns {@code true} if this APK contains an APK Signing Block.
     */
    private boolean hasApkSigningBlock() throws ProcessException {
        // IMPLEMENTATION NOTE: To avoid having to implement too much parsing, this method does not
        // parse the APK to locate the APK Signing Block. Instead, it simply scans the file for the
        // APK Signing Block magic bitstring. If the string is there in the file, it's assumed to
        // contain an APK Signing Block.
        try {
            byte[] contents = Files.toByteArray(getSubject());
            outer:
            for (int contentsOffset = contents.length - APK_SIG_BLOCK_MAGIC.length;
                    contentsOffset >= 0; contentsOffset--) {
                for (int magicOffset = 0; magicOffset < APK_SIG_BLOCK_MAGIC.length; magicOffset++) {
                    if (contents[contentsOffset + magicOffset]
                            != APK_SIG_BLOCK_MAGIC[magicOffset]) {
                        continue outer;
                    }
                }
                // Found at offset contentsOffset
                return true;
            }
            // Not found
            return false;
        } catch (IOException e) {
            throw new ProcessException(
                    "Failed to check for APK Signing Block presence in " + getSubject(), e);
        }
    }

    /**
     * Creates an {@link ZipEntryAction} that will consider each extracted entry as a zip file, will
     * enumerate such zip file entries and call an delegated action on each entry.
     */
    @NonNull
    protected static <T> ZipEntryAction<T> allEntriesAction(final ZipEntryAction<T> action) {
        return entry -> {
            try (ZipInputStream zipInputStream =
                         new ZipInputStream(new ByteArrayInputStream(entry))) {
                while (zipInputStream.getNextEntry() != null) {
                    byte[] entryBytes = ByteStreams.toByteArray(zipInputStream);
                    T result = action.doOnZipEntry(entryBytes);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (IOException e) {
                throw new ProcessException(e);
            }
            return null;
        };
    }

    /**
     * Returns true if the provided class is present in the file.
     *
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     * @param scope             the scope in which to search for the class.
     */
    @Override
    protected boolean checkForClass(
            @NonNull final String expectedClassName,
            @NonNull final ClassFileScope scope)
            throws ProcessException, IOException {

        return getDexClass(expectedClassName, scope) != null;
    }


    @Nullable
    private DexBackedClassDef getDexClass(
            @NonNull final String className,
            @NonNull final ClassFileScope scope) throws IOException, ProcessException {
        if (!className.startsWith("L") || !className.endsWith(";")) {
            throw new IllegalArgumentException("class name must be in the format Lcom/foo/Main;");
        }

        switch (scope) {
            case MAIN:
                byte[] classesDex = extractContentAsByte("classes.dex");
                if (classesDex == null) {
                    return null;
                }
                return getDexClass(classesDex, className);
            case INSTANT_RUN:
                // check first in the instant-run.zip file.
                return extractEntryAndRunAction("instant-run.zip",
                        allEntriesAction(bytes -> getDexClass(bytes, className)));
            case SECONDARY:
                // while dexdump supports receiving directly an apk, this doesn't work for
                // multi-dex.
                // We're going to extract all the classes<N>.dex we find until one of them
                // contains the class we're searching for.
                try (ZipFile zipFile = new ZipFile(getSubject())) {
                    int index = 2;
                    String dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    while (zipFile.getEntry(dexFileName) != null) {
                        DexBackedClassDef result = extractEntryAndRunAction(
                                dexFileName,
                                bytes -> getDexClass(bytes, className));
                        if (result != null) {
                            return result;
                        }
                        // not found? switch to next index.
                        index++;
                        dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    }
                }
                return null;
            case MAIN_AND_SECONDARY:
                DexBackedClassDef dexClass = getDexClass(className, ClassFileScope.MAIN);
                if (dexClass != null) {
                    return dexClass;
                }
                return getDexClass(className, ClassFileScope.SECONDARY);
            default:
                throw new IllegalArgumentException("unknown class file scope " + scope);
        }
    }

    @Override
    protected boolean checkForJavaResource(@NonNull String resourcePath)
            throws ProcessException, IOException {
        try (ZipFile zipFile = new ZipFile(getSubject())) {
            return zipFile.getEntry(resourcePath) != null;
        }
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified String
     * content.
     *
     * Content is trimmed when compared.
     */
    @Override
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull String content)
            throws IOException, ProcessException {
        containsFileWithContent(path, content);
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified byte array
     * content.
     */
    @Override
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull byte[] content)
            throws IOException, ProcessException {
        containsFileWithContent(path, content);
    }

    @Nullable
    private static DexBackedClassDef getDexClass(@NonNull byte[] classesDex, @NonNull String name) {
        DexBackedDexFile dexFile = DexUtils.loadDex(classesDex);
        for (DexBackedClassDef clazz : dexFile.getClasses()) {
            if (clazz.getType().equals(name)) {
                return clazz;
            }
        }
        return null;
    }

    @NonNull
    private static ApkInfoParser.ApkInfo getApkInfo(@NonNull File apk) throws ProcessException {
        ProcessExecutor processExecutor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(SdkHelper.getAapt(), processExecutor);
        return parser.parseApk(apk);
    }

    @VisibleForTesting
    void checkMaxSdkVersion(@NonNull List<String> output, int maxSdkVersion) {
        for (String line : output) {
            Matcher m = PATTERN_MAX_SDK_VERSION.matcher(line.trim());
            if (m.matches()) {
                String actual = m.group(1);
                try {
                    Integer i = Integer.parseInt(actual);
                    if (!i.equals(maxSdkVersion)) {
                        failWithBadResults("has maxSdkVersion", maxSdkVersion, "is", i);
                    }
                    return;
                } catch (NumberFormatException e) {
                    failureStrategy.fail(
                            String.format(
                                    "maxSdkVersion in badging for %s is not a number: %s",
                                    getDisplaySubject(), actual),
                            e);
                }
            }
        }

        failWithRawMessage("maxSdkVersion not found in badging output for %s", getDisplaySubject());
    }

    @Override
    public CustomTestVerb check() {
        return new CustomTestVerb(failureStrategy);
    }
}
