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

package com.android.support;

import static com.android.SdkConstants.*;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import java.util.Collection;
import java.util.logging.Logger;

public class AndroidxNameUtils {
    static final String ANDROID_SUPPORT_PKG = "android.support.";

    /** Package mappings for package that have been just renamed */
    static final ImmutableMap<String, String> ANDROIDX_PKG_MAPPING =
            ImmutableMap.<String, String>builder()
                    .put(ANDROID_SUPPORT_PKG + "design.", ANDROIDX_PKG_PREFIX)
                    .put(ANDROID_SUPPORT_PKG + "v17.", ANDROIDX_PKG_PREFIX)
                    .put(ANDROID_SUPPORT_PKG + "v14.", ANDROIDX_PKG_PREFIX)
                    .put(ANDROID_SUPPORT_PKG + "v13.", ANDROIDX_PKG_PREFIX)
                    .put(ANDROID_SUPPORT_PKG + "v8.", ANDROIDX_PKG_PREFIX)
                    .put(ANDROID_SUPPORT_PKG + "v7.", ANDROIDX_PKG_PREFIX)
                    .put(ANDROID_SUPPORT_PKG + "v4.", ANDROIDX_PKG_PREFIX)
                    .put(
                            ANDROID_SUPPORT_PKG + "customtabs.",
                            ANDROIDX_PKG_PREFIX + "browser.customtabs.")
                    .put(ANDROID_SUPPORT_PKG + "percent.", ANDROIDX_PKG_PREFIX + "widget.")
                    .put(ANDROID_SUPPORT_PKG, ANDROIDX_PKG_PREFIX)
                    .build();

    /** Mappings for class names that have been moved to a different package */
    static final ImmutableMap<String, String> ANDROIDX_FULL_CLASS_MAPPING =
            ImmutableMap.<String, String>builder()
                    .put(
                            ANDROID_SUPPORT_PKG + "v4.view.ViewPager",
                            ANDROIDX_PKG_PREFIX + "widget.ViewPager")
                    .put(
                            ANDROID_SUPPORT_PKG + "v4.view.PagerAdapter",
                            ANDROIDX_PKG_PREFIX + "widget.PagerAdapter")
                    .put(
                            ANDROID_SUPPORT_PKG + "v4.view.PagerTabStrip",
                            ANDROIDX_PKG_PREFIX + "widget.PagerTabStrip")
                    .put(
                            ANDROID_SUPPORT_PKG + "v4.view.PagerTitleStrip",
                            ANDROIDX_PKG_PREFIX + "widget.PagerTitleStrip")
                    .put(
                            ANDROID_SUPPORT_PKG + "v7.graphics.ColorCutQuantizer",
                            ANDROIDX_PKG_PREFIX + "graphics.palette.ColorCutQuantizer")
                    .put(
                            ANDROID_SUPPORT_PKG + "v7.graphics.Palette",
                            ANDROIDX_PKG_PREFIX + "graphics.palette.Palette")
                    .put(
                            ANDROID_SUPPORT_PKG + "v7.graphics.Target",
                            ANDROIDX_PKG_PREFIX + "graphics.palette.Target")
                    .build();

    private static final ImmutableBiMap<String, String> ANDROIDX_COORDINATES_MAPPING =
            ImmutableBiMap.<String, String>builder()
                    .put(
                            "com.android.support:support-vector-drawable",
                            "androidx.graphics.vectordrawable:animatedvectordrawable")
                    .put(
                            "com.android.support:animated-vector-drawable",
                            "androidx.graphics.animatedvectordrawable:vectordrawable")
                    .put(
                            "com.android.support:multidex-instrumentation",
                            "androidx.multidex:instrumentation")
                    .put(
                            "com.android.support:preference-leanback-v17",
                            "androidx.leanback:preference")
                    .put("com.android.support:appcompat", "androidx.appcompat:appcompat")
                    .put("com.android.support:design", ANDROIDX_MATERIAL_ARTIFACT)
                    .put("com.android.support:recyclerview-v7", ANDROIDX_RECYCLER_VIEW_ARTIFACT)
                    .put("com.android.support:support-annotations", ANDROIDX_ANNOTATIONS_ARTIFACT)
                    // Just for testing

                    .build();

    /** Ordered list of old android support packages sorted by decreasing length */
    static final ImmutableList<String> ANDROIDX_OLD_PKGS =
            ImmutableList.sortedCopyOf(
                    new Ordering<String>() {
                        @Override
                        public int compare(String left, String right) {
                            // Short with the longest names first
                            return Ints.compare(right.length(), left.length());
                        }
                    },
                    ANDROIDX_PKG_MAPPING.keySet());

    @NonNull
    static String getPackageMapping(@NonNull String oldPkgName, boolean strictChecking) {
        for (int i = 0, n = ANDROIDX_OLD_PKGS.size(); i < n; i++) {
            String prefix = ANDROIDX_OLD_PKGS.get(i);
            if (oldPkgName.startsWith(prefix)) {
                return ANDROIDX_PKG_MAPPING.get(prefix) + oldPkgName.substring(prefix.length());
            }
        }

        if (strictChecking) {
            assert false : "support library package not found" + oldPkgName;
            Logger.getLogger(AndroidxName.class.getName())
                    .warning("support library package not found");
        }
        return oldPkgName;
    }

    /** Returns a {@link Collection} of all the possible {@code androidx} maven coordinates */
    @NonNull
    public static Collection<String> getAllAndroidxCoordinates() {
        return ANDROIDX_COORDINATES_MAPPING.values();
    }

    /**
     * Returns the mapping of a given coordinate into the new {@code androidx} maven coordinates. If
     * the coordinate does not belong to the support library, the method will just return the passed
     * coordinate.
     */
    @NonNull
    public static String getCoordinateMapping(@NonNull String coordinate) {
        return ANDROIDX_COORDINATES_MAPPING.getOrDefault(coordinate, coordinate);
    }

    /**
     * Returns the mapping of a given {@code androidx} coordinate into the old maven coordinates. If
     * the coordinate does not belong to the support library, the method will just return the passed
     * coordinate.
     */
    @NonNull
    public static String getCoordinateReverseMapping(@NonNull String coordinate) {
        return ANDROIDX_COORDINATES_MAPPING.inverse().getOrDefault(coordinate, coordinate);
    }

    @NonNull
    public static String getNewName(@NonNull String oldName) {
        String newName = ANDROIDX_FULL_CLASS_MAPPING.get(oldName);
        if (newName != null) {
            return newName;
        }

        int lastDot = oldName.lastIndexOf('.');
        return getPackageMapping(oldName.substring(0, lastDot + 1), false)
                + oldName.substring(lastDot + 1);
    }
}
