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

package com.android.build.gradle.internal;

import com.android.annotations.Nullable;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

public final class NonFinalPluginExpiry {

    /** default retirement age in days since its inception date for RC or beta versions. */
    private static final int DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE_IN_DAYS = 40;

    private NonFinalPluginExpiry() {}

    /**
     * Verify that this plugin execution is within its public time range.
     *
     * @throws RuntimeException if the plugin is a non final plugin older than 40 days.
     */
    public static void verifyRetirementAge() {

        Manifest manifest;
        URLClassLoader cl = (URLClassLoader) NonFinalPluginExpiry.class.getClassLoader();
        try {
            URL url = cl.findResource("META-INF/MANIFEST.MF");
            manifest = new Manifest(url.openStream());
        } catch (IOException ignore) {
            return;
        }

        int retirementAgeInDays =
                getRetirementAgeInDays(manifest.getMainAttributes().getValue("Plugin-Version"));

        // if this plugin version will never be outdated, return.
        if (retirementAgeInDays == -1) {
            return;
        }

        String inceptionDateAttr = manifest.getMainAttributes().getValue("Inception-Date");
        // when running in unit tests, etc... the manifest entries are absent.
        if (inceptionDateAttr == null) {
            return;
        }
        List<String> items = ImmutableList.copyOf(Splitter.on(':').split(inceptionDateAttr));
        GregorianCalendar inceptionDate =
                new GregorianCalendar(
                        Integer.parseInt(items.get(0)),
                        Integer.parseInt(items.get(1)),
                        Integer.parseInt(items.get(2)));

        Calendar now = GregorianCalendar.getInstance();
        long nowTimestamp = now.getTimeInMillis();
        long inceptionTimestamp = inceptionDate.getTimeInMillis();
        long days = TimeUnit.DAYS.convert(nowTimestamp - inceptionTimestamp, TimeUnit.MILLISECONDS);
        if (days > retirementAgeInDays) {
            // this plugin is too old.
            String dailyOverride = System.getenv("ANDROID_DAILY_OVERRIDE");
            final MessageDigest crypt;
            try {
                crypt = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                return;
            }
            crypt.reset();
            // encode the day, not the current time.
            try {
                crypt.update(
                        String.format(
                                        "%1$s:%2$s:%3$s",
                                        now.get(Calendar.YEAR),
                                        now.get(Calendar.MONTH),
                                        now.get(Calendar.DATE))
                                .getBytes("utf8"));
            } catch (UnsupportedEncodingException e) {
                return;
            }
            String overrideValue = new BigInteger(1, crypt.digest()).toString(16);
            if (dailyOverride == null) {
                String message =
                        "Plugin is too old, please update to a more recent version, or "
                                + "set ANDROID_DAILY_OVERRIDE environment variable to \""
                                + overrideValue
                                + '"';
                System.err.println(message);
                throw new RuntimeException(message);
            } else {
                if (!dailyOverride.equals(overrideValue)) {
                    String message =
                            "Plugin is too old and ANDROID_DAILY_OVERRIDE value is "
                                    + "also outdated, please use new value :\""
                                    + overrideValue
                                    + '"';
                    System.err.println(message);
                    throw new RuntimeException(message);
                }
            }
        }
    }

    /**
     * Returns the retirement age for this plugin depending on its version string, or -1 if this
     * plugin version will never become obsolete
     *
     * @param version the plugin full version, like 1.3.4-preview5 or 1.0.2 or 1.2.3-beta4
     * @return the retirement age in days or -1 if no retirement
     */
    private static int getRetirementAgeInDays(@Nullable String version) {
        if (version == null
                || version.contains("rc")
                || version.contains("beta")
                || version.contains("alpha")
                || version.contains("preview")) {
            return DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE_IN_DAYS;
        }
        return -1;
    }
}
