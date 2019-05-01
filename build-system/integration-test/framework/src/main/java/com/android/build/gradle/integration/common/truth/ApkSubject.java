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

import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.StdLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;

/** Truth support for apk files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public final class ApkSubject extends AbstractDexAndroidSubject<ApkSubject, Apk> {

    public static Subject.Factory<ApkSubject, Apk> apks() {
        //noinspection resource
        return ApkSubject::new;
    }

    private static final Pattern PATTERN_MAX_SDK_VERSION =
            Pattern.compile("^maxSdkVersion\\W*:\\W*'(.+)'$");
    private static final byte[] APK_SIG_BLOCK_MAGIC = {
        0x41, 0x50, 0x4b, 0x20, 0x53, 0x69, 0x67, 0x20, 0x42, 0x6c, 0x6f, 0x63, 0x6b, 0x20, 0x34,
        0x32
    };

    public ApkSubject(@NonNull FailureMetadata failureMetadata, @NonNull Apk subject) {
        super(failureMetadata, subject);
    }

    @NonNull
    public static ApkSubject assertThat(@Nullable Apk apk) {
        return assertAbout(apks()).that(apk);
    }

    @NonNull
    private static ApkInfoParser.ApkInfo getApkInfo(@NonNull Path apk) {
        ProcessExecutor processExecutor =
                new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(TestUtils.getAapt2().toFile(), processExecutor);
        try {
            return parser.parseApk(apk.toFile());
        } catch (ProcessException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @NonNull
    public static List<String> getConfigurations(@NonNull Path apk) {
        ProcessExecutor processExecutor =
                new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(TestUtils.getAapt2().toFile(), processExecutor);
        try {
            return parser.getConfigurations(apk.toFile());
        } catch (ProcessException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @NonNull
    public static List<String> getBadging(@NonNull File apk) {
        return getBadging(apk.toPath());
    }

    @NonNull
    public static List<String> getBadging(@NonNull Path apk) {
        ProcessExecutor processExecutor =
                new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(TestUtils.getAapt2().toFile(), processExecutor);
        try {
            return parser.getAaptOutput(apk.toFile());
        } catch (ProcessException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @NonNull
    public static List<String> getManifestContent(@NonNull Path apk) {
        ProcessExecutor processExecutor =
                new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(TestUtils.getAapt2().toFile(), processExecutor);
        try {
            return parser.getManifestContent(apk.toFile());
        } catch (ProcessException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @NonNull
    public IterableSubject locales() {
        File apk = actual().getFile().toFile();
        List<String> locales = ApkHelper.getLocales(apk);

        if (locales == null) {
            Assert.fail(String.format("locales not found in badging output for %s", apk));
        }

        return check().that(locales);
    }

    public void hasPackageName(@NonNull String packageName) {
        Path apk = actual().getFile();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualPackageName = apkInfo.getPackageName();

        if (!actualPackageName.equals(packageName)) {
            failWithBadResults("has packageName", packageName, "is", actualPackageName);
        }
    }

    public void hasVersionCode(int versionCode) {
        Path apk = actual().getFile();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        Integer actualVersionCode = apkInfo.getVersionCode();
        if (actualVersionCode == null) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("Unable to query %s for versionCode", actualAsString())));
        }

        if (!apkInfo.getVersionCode().equals(versionCode)) {
            failWithBadResults("has versionCode", versionCode, "is", actualVersionCode);
        }
    }

    public void hasManifestContent(Pattern pattern) {
        Path apk = actual().getFile();

        List<String> manifestContent = getManifestContent(apk);
        Optional<String> matchingLine =
                manifestContent
                        .stream()
                        .filter(line -> pattern.matcher(line).matches())
                        .findFirst();
        if (!matchingLine.isPresent()) {
            failWithBadResults(
                    "has manifest content", pattern, "is", Joiner.on("\n").join(manifestContent));
        }
    }

    public void hasVersionName(@NonNull String versionName) {
        Path apk = actual().getFile();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualVersionName = apkInfo.getVersionName();
        if (actualVersionName == null) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("Unable to query %s for versionName", actualAsString())));
        }

        if (!apkInfo.getVersionName().equals(versionName)) {
            failWithBadResults("has versionName", versionName, "is", actualVersionName);
        }
    }

    public void hasMaxSdkVersion(int maxSdkVersion) {

        List<String> output = getBadging(actual().getFile().toFile().toPath());

        checkMaxSdkVersion(output, maxSdkVersion);
    }

    /**
     * Asserts that the APK file contains an APK Signing Block (the block which may contain APK
     * Signature Scheme v2 signatures).
     */
    public void containsApkSigningBlock() {
        if (!hasApkSigningBlock()) {
            failWithoutActual(Fact.simpleFact("APK does not contain APK Signing Block"));
        }
    }

    /**
     * Asserts that the APK file does not contain an APK Signing Block (the block which may contain
     * APK Signature Scheme v2 signatures).
     */
    public void doesNotContainApkSigningBlock() {
        if (hasApkSigningBlock()) {
            failWithoutActual(Fact.simpleFact("APK contains APK Signing Block"));
        }
    }

    /** Returns {@code true} if this APK contains an APK Signing Block. */
    private boolean hasApkSigningBlock() {
        // IMPLEMENTATION NOTE: To avoid having to implement too much parsing, this method does not
        // parse the APK to locate the APK Signing Block. Instead, it simply scans the file for the
        // APK Signing Block magic bitstring. If the string is there in the file, it's assumed to
        // contain an APK Signing Block.
        try {
            byte[] contents = Files.readAllBytes(actual().getFile());
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
            throw new UncheckedIOException(
                    "Failed to check for APK Signing Block presence in " + actual(), e);
        }
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
                    failWithActual(
                            Fact.simpleFact(
                                    Strings.lenientFormat(
                                            "maxSdkVersion in badging for %s is not a number: %s",
                                            actualAsString(), actual, e)));
                }
            }
        }

        failWithoutActual(
                Fact.simpleFact(
                        String.format(
                                "maxSdkVersion not found in badging output for %s",
                                actualAsString())));
    }

}
