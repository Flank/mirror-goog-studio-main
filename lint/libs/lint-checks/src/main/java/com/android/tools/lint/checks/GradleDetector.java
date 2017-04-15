/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.lint.checks;

import static com.android.SdkConstants.FD_BUILD_TOOLS;
import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.SdkConstants.SUPPORT_LIB_GROUP_ID;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.android.tools.lint.checks.ManifestDetector.TARGET_NEWER;
import static com.android.tools.lint.detector.api.LintUtils.findSubstring;
import static com.android.tools.lint.detector.api.LintUtils.guessGradleLocation;
import static com.google.common.base.Charsets.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleCoordinate.RevisionComponent;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.MavenRepositories;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.Revision;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks Gradle files for potential errors
 */
public class GradleDetector extends Detector implements Detector.GradleScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            GradleDetector.class,
            Scope.GRADLE_SCOPE);

    /** Obsolete dependencies */
    public static final Issue DEPENDENCY = Issue.create(
            "GradleDependency",
            "Obsolete Gradle Dependency",
            "This detector looks for usages of libraries where the version you are using " +
            "is not the current stable release. Using older versions is fine, and there are " +
            "cases where you deliberately want to stick with an older version. However, " +
            "you may simply not be aware that a more recent version is available, and that is " +
            "what this lint check helps find.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Deprecated Gradle constructs */
    public static final Issue DEPRECATED = Issue.create(
            "GradleDeprecated",
            "Deprecated Gradle Construct",
            "This detector looks for deprecated Gradle constructs which currently work but " +
            "will likely stop working in a future update.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Incompatible Android Gradle plugin */
    public static final Issue GRADLE_PLUGIN_COMPATIBILITY = Issue.create(
            "GradlePluginVersion",
            "Incompatible Android Gradle Plugin",
            "Not all versions of the Android Gradle plugin are compatible with all versions " +
            "of the SDK. If you update your tools, or if you are trying to open a project that " +
            "was built with an old version of the tools, you may need to update your plugin " +
            "version number.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Invalid or dangerous paths */
    public static final Issue PATH = Issue.create(
            "GradlePath",
            "Gradle Path Issues",
            "Gradle build scripts are meant to be cross platform, so file paths use " +
            "Unix-style path separators (a forward slash) rather than Windows path separators " +
            "(a backslash). Similarly, to keep projects portable and repeatable, avoid " +
            "using absolute paths on the system; keep files within the project instead. To " +
            "share code between projects, consider creating an android-library and an AAR " +
            "dependency",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Constructs the IDE support struggles with */
    public static final Issue IDE_SUPPORT = Issue.create(
            "GradleIdeError",
            "Gradle IDE Support Issues",
            "Gradle is highly flexible, and there are things you can do in Gradle files which " +
            "can make it hard or impossible for IDEs to properly handle the project. This lint " +
            "check looks for constructs that potentially break IDE support.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Using + in versions */
    public static final Issue PLUS = Issue.create(
            "GradleDynamicVersion",
            "Gradle Dynamic Version",
            "Using `+` in dependencies lets you automatically pick up the latest available " +
            "version rather than a specific, named version. However, this is not recommended; " +
            "your builds are not repeatable; you may have tested with a slightly different " +
            "version than what the build server used. (Using a dynamic version as the major " +
            "version number is more problematic than using it in the minor version position.)",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Accidentally calling a getter instead of your own methods */
    public static final Issue GRADLE_GETTER = Issue.create(
            "GradleGetter",
            "Gradle Implicit Getter Call",
            "Gradle will let you replace specific constants in your build scripts with method " +
            "calls, so you can for example dynamically compute a version string based on your " +
            "current version control revision number, rather than hardcoding a number.\n" +
            "\n" +
            "When computing a version name, it's tempting to for example call the method to do " +
            "that `getVersionName`. However, when you put that method call inside the " +
            "`defaultConfig` block, you will actually be calling the Groovy getter for the "  +
            "`versionName` property instead. Therefore, you need to name your method something " +
            "which does not conflict with the existing implicit getters. Consider using " +
            "`compute` as a prefix instead of `get`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Using incompatible versions */
    public static final Issue COMPATIBILITY = Issue.create(
            "GradleCompatible",
            "Incompatible Gradle Versions",

            "There are some combinations of libraries, or tools and libraries, that are " +
            "incompatible, or can lead to bugs. One such incompatibility is compiling with " +
            "a version of the Android support libraries that is not the latest version (or in " +
            "particular, a version lower than your `targetSdkVersion`.)",

            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            IMPLEMENTATION);

    /** Using a string where an integer is expected */
    public static final Issue STRING_INTEGER = Issue.create(
            "StringShouldBeInt",
            "String should be int",

            "The properties `compileSdkVersion`, `minSdkVersion` and `targetSdkVersion` are " +
            "usually numbers, but can be strings when you are using an add-on (in the case " +
            "of `compileSdkVersion`) or a preview platform (for the other two properties).\n" +
            "\n" +
            "However, you can not use a number as a string (e.g. \"19\" instead of 19); that " +
            "will result in a platform not found error message at build/sync time.",

            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Attempting to use substitution with single quotes */
    public static final Issue NOT_INTERPOLATED = Issue.create(
          "NotInterpolated",
          "Incorrect Interpolation",

          "To insert the value of a variable, you can use `${variable}` inside " +
          "a string literal, but **only** if you are using double quotes!",

          Category.CORRECTNESS,
          8,
          Severity.ERROR,
          IMPLEMENTATION)
          .addMoreInfo("http://www.groovy-lang.org/syntax.html#_string_interpolation");

    /** A newer version is available on a remote server */
    public static final Issue REMOTE_VERSION = Issue.create(
            "NewerVersionAvailable",
            "Newer Library Versions Available",
            "This detector checks with a central repository to see if there are newer versions " +
            "available for the dependencies used by this project. " +
            "This is similar to the `GradleDependency` check, which checks for newer versions " +
            "available in the Android SDK tools and libraries, but this works with any " +
            "MavenCentral dependency, and connects to the library every time, which makes " +
            "it more flexible but also **much** slower.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION).setEnabledByDefault(false);

    /** Accidentally using octal numbers */
    public static final Issue ACCIDENTAL_OCTAL = Issue.create(
            "AccidentalOctal",
            "Accidental Octal",

            "In Groovy, an integer literal that starts with a leading 0 will be interpreted " +
            "as an octal number. That is usually (always?) an accident and can lead to " +
            "subtle bugs, for example when used in the `versionCode` of an app.",

            Category.CORRECTNESS,
            2,
            Severity.ERROR,
            IMPLEMENTATION);

    @SuppressWarnings("SpellCheckingInspection")
    public static final Issue BUNDLED_GMS = Issue.create(
            "UseOfBundledGooglePlayServices",
            "Use of bundled version of Google Play services",

            "Google Play services SDK's can be selectively included, which enables a smaller APK " +
            "size. Consider declaring dependencies on individual Google Play services SDK's. " +
            "If you are using Firebase API's (http://firebase.google.com/docs/android/setup), " +
            "Android Studio's Tools \u2192 Firebase assistant window can automatically add " +
            "just the dependencies needed for each feature.",

            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo("http://developers.google.com/android/guides/setup#split");

    /**
     * Using a versionCode that is very high
     */
    public static final Issue HIGH_APP_VERSION_CODE = Issue.create(
            "HighAppVersionCode",
            "VersionCode too high",

            "The declared `versionCode` is an Integer. Ensure that the version number is " +
            "not close to the limit. It is recommended to monotonically increase this number " +
            "each minor or major release of the app. Note that updating an app with a " +
            "versionCode over `Integer.MAX_VALUE` is not possible.",

            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo("https://developer.android.com/studio/publish/versioning.html");

    /** The Gradle plugin ID for Android applications */
    public static final String APP_PLUGIN_ID = "com.android.application";
    /** The Gradle plugin ID for Android libraries */
    public static final String LIB_PLUGIN_ID = "com.android.library";

    /** Previous plugin id for applications */
    public static final String OLD_APP_PLUGIN_ID = "android";
    /** Previous plugin id for libraries */
    public static final String OLD_LIB_PLUGIN_ID = "android-library";

    /** Group ID for GMS */
    public static final String GMS_GROUP_ID = "com.google.android.gms";
    public static final String FIREBASE_GROUP_ID = "com.google.firebase";
    public static final String GOOGLE_SUPPORT_GROUP_ID = "com.google.android.support";
    public static final String ANDROID_WEAR_GROUP_ID = "com.google.android.wearable";
    private static final String WEARABLE_ARTIFACT_ID = "wearable";

    @SuppressWarnings("ConstantConditions")
    @NonNull
    private static final GradleCoordinate PLAY_SERVICES_V650 = GradleCoordinate
            .parseCoordinateString(GMS_GROUP_ID + ":play-services:6.5.0");

    /**
     * Threshold to consider a versionCode very high and issue a warning.
     * https://developer.android.com/studio/publish/versioning.html indicates
     * that the highest value accepted by Google Play is 2100000000
     */
    private static final int VERSION_CODE_HIGH_THRESHOLD = 2000000000;

    private int minSdkVersion;
    private int compileSdkVersion;
    private int targetSdkVersion;

    // ---- Implements Detector.GradleScanner ----

    @Override
    public void visitBuildScript(@NonNull Context context, Map<String, Object> sharedData) {
    }

    @SuppressWarnings("UnusedDeclaration")
    protected static boolean isInterestingBlock(
            @NonNull String parent,
            @Nullable String parentParent) {
        return parent.equals("defaultConfig")
                || parent.equals("android")
                || parent.equals("dependencies")
                || parent.equals("repositories")
                || parentParent != null && parentParent.equals("buildTypes");
    }

    protected static boolean isInterestingStatement(
            @NonNull String statement,
            @Nullable String parent) {
        return parent == null && statement.equals("apply");
    }

    @SuppressWarnings("UnusedDeclaration")
    protected static boolean isInterestingProperty(
            @NonNull String property,
            @SuppressWarnings("UnusedParameters")
            @NonNull String parent,
            @Nullable String parentParent) {
        return property.equals("targetSdkVersion")
                || property.equals("buildToolsVersion")
                || property.equals("versionName")
                || property.equals("versionCode")
                || property.equals("compileSdkVersion")
                || property.equals("minSdkVersion")
                || property.equals("applicationIdSuffix")
                || property.equals("packageName")
                || property.equals("packageNameSuffix")
                || parent.equals("dependencies");
    }

    protected void checkOctal(
            @NonNull Context context,
            @NonNull String value,
            @NonNull Object cookie) {
        if (value.length() >= 2
                && value.charAt(0) == '0'
                && (value.length() > 2 || value.charAt(1) >= '8'
                && isInteger(value))
                && context.isEnabled(ACCIDENTAL_OCTAL)) {
            String message = "The leading 0 turns this number into octal which is probably "
                    + "not what was intended";
            try {
                long numericValue = Long.decode(value);
                message += " (interpreted as " + numericValue + ")";
            } catch (NumberFormatException nufe) {
                message += " (and it is not a valid octal number)";
            }
            report(context, cookie, ACCIDENTAL_OCTAL, message);
        }
    }

    /** Called with for example "android", "defaultConfig", "minSdkVersion", "7"  */
    @SuppressWarnings("UnusedDeclaration")
    protected void checkDslPropertyAssignment(
        @NonNull Context context,
        @NonNull String property,
        @NonNull String value,
        @NonNull String parent,
        @Nullable String parentParent,
        @NonNull Object valueCookie,
        @NonNull Object statementCookie) {
        if (parent.equals("defaultConfig")) {
            if (property.equals("targetSdkVersion")) {
                int version = getSdkVersion(value);
                if (version > 0 && version < context.getClient().getHighestKnownApiLevel()) {
                    String message =
                            "Not targeting the latest versions of Android; compatibility " +
                            "modes apply. Consider testing and updating this version. " +
                           "Consult the android.os.Build.VERSION_CODES javadoc for details.";
                    report(context, valueCookie, TARGET_NEWER, message);
                }
                if (version > 0) {
                    targetSdkVersion = version;
                    checkTargetCompatibility(context, valueCookie);
                } else {
                    checkIntegerAsString(context, value, valueCookie);
                }
            } else if (property.equals("minSdkVersion")) {
              int version = getSdkVersion(value);
              if (version > 0) {
                minSdkVersion = version;
              } else {
                checkIntegerAsString(context, value, valueCookie);
              }
            }

            if (value.startsWith("0")) {
                checkOctal(context, value, valueCookie);
            }

            if (property.equals("versionName") || property.equals("versionCode") &&
                    !isInteger(value) || !isStringLiteral(value)) {
                // Method call -- make sure it does not match one of the getters in the
                // configuration!
                if ((value.equals("getVersionCode") ||
                        value.equals("getVersionName"))) {
                    String message = "Bad method name: pick a unique method name which does not "
                            + "conflict with the implicit getters for the defaultConfig "
                            + "properties. For example, try using the prefix compute- "
                            + "instead of get-.";
                    report(context, valueCookie, GRADLE_GETTER, message);
                }
            } else if (property.equals("packageName")) {
                if (isModelOlderThan011(context)) {
                    return;
                }
                String message = "Deprecated: Replace 'packageName' with 'applicationId'";
                report(context, getPropertyKeyCookie(valueCookie), DEPRECATED, message);
            }
            if (property.equals("versionCode") && context.isEnabled(HIGH_APP_VERSION_CODE)
                    && isInteger(value)) {
                int version = getIntLiteralValue(value, -1);
                if (version >= VERSION_CODE_HIGH_THRESHOLD) {
                    String message =
                            "The 'versionCode' is very high and close to the max allowed value";
                    report(context, valueCookie, HIGH_APP_VERSION_CODE, message);
                }
            }
        } else if (property.equals("compileSdkVersion") && parent.equals("android")) {
            int version = -1;
            if (isStringLiteral(value)) {
                // Try to resolve values like "android-O"
                String hash = getStringLiteralValue(value);
                if (hash != null && !isNumberString(hash)) {
                    AndroidVersion platformVersion = AndroidTargetHash.getPlatformVersion(hash);
                    if (platformVersion != null) {
                        version = platformVersion.getFeatureLevel();
                    }
                }
            } else {
                version = getIntLiteralValue(value, -1);
            }
            if (version > 0) {
                compileSdkVersion = version;
                checkTargetCompatibility(context, valueCookie);
            } else {
                checkIntegerAsString(context, value, valueCookie);
            }
        } else if (property.equals("buildToolsVersion") && parent.equals("android")) {
            String versionString = getStringLiteralValue(value);
            if (versionString != null) {
                Revision version = parseRevisionSilently(versionString);
                if (version != null) {
                    Revision recommended = getLatestBuildTools(context.getClient(),
                            version.getMajor());
                    if (recommended != null && version.compareTo(recommended) < 0) {
                        // Keep in sync with {@link #getOldValue} and {@link #getNewValue}
                        String message = "Old buildToolsVersion " + version +
                                "; recommended version is " + recommended + " or later";
                        report(context, valueCookie, DEPENDENCY, message);
                    }

                    // 23.0.0 shipped with a serious bugs which affects program correctness
                    // (such as https://code.google.com/p/android/issues/detail?id=183180)
                    // Make developers aware of this and suggest upgrading
                    if (version.getMajor() == 23 && version.getMinor() == 0 &&
                            version.getMicro() == 0 && context.isEnabled(COMPATIBILITY)) {
                        // This specific version is actually a preview version which should
                        // not be used (https://code.google.com/p/android/issues/detail?id=75292)
                        if (recommended == null || recommended.getMajor() < 23) {
                            // First planned release to fix this
                            recommended = new Revision(23, 0, 3);
                        }
                        String message = String.format("Build Tools `23.0.0` should not be used; "
                                + "it has some known serious bugs. Use version `%1$s` "
                                + "instead.", recommended);
                        reportFatalCompatibilityIssue(context, valueCookie, message);
                    }
                }
            }
        } else if (parent.equals("dependencies")) {
            if (value.startsWith("files('") && value.endsWith("')")) {
                String path = value.substring("files('".length(), value.length() - 2);
                if (path.contains("\\\\")) {
                    String message = "Do not use Windows file separators in .gradle files; "
                            + "use / instead";
                    report(context, valueCookie, PATH, message);

                } else if (new File(path.replace('/', File.separatorChar)).isAbsolute()) {
                    String message = "Avoid using absolute paths in .gradle files";
                    report(context, valueCookie, PATH, message);
                }
            } else {
                String dependency = getStringLiteralValue(value);
                if (dependency == null) {
                    dependency = getNamedDependency(value);
                }
                // If the dependency is a GString (i.e. it uses Groovy variable substitution,
                // with a $variable_name syntax) then don't try to parse it.
                if (dependency != null) {
                    GradleCoordinate gc = GradleCoordinate.parseCoordinateString(dependency);
                    if (gc != null && dependency.contains("$")) {
                        if (value.startsWith("'") && value.endsWith("'") &&
                            context.isEnabled(NOT_INTERPOLATED)) {
                            String message = "It looks like you are trying to substitute a "
                                             + "version variable, but using single quotes ('). For Groovy "
                                             + "string interpolation you must use double quotes (\").";
                            report(context, statementCookie, NOT_INTERPOLATED, message);
                        }

                        gc = resolveCoordinate(context, gc);
                    }
                    if (gc != null) {
                        if (gc.acceptsGreaterRevisions()) {
                            String message = "Avoid using + in version numbers; can lead "
                                    + "to unpredictable and unrepeatable builds (" + dependency + ")";
                            report(context, valueCookie, PLUS, message);
                        }
                        if (!dependency.startsWith(SdkConstants.GRADLE_PLUGIN_NAME) ||
                            !checkGradlePluginDependency(context, gc, valueCookie)) {
                            checkDependency(context, gc, valueCookie);
                        }
                    }
                }
            }
        } else if (property.equals("packageNameSuffix")) {
            if (isModelOlderThan011(context)) {
                return;
            }
            String message = "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'";
            report(context, getPropertyKeyCookie(valueCookie), DEPRECATED, message);
        } else if (property.equals("applicationIdSuffix")) {
            String suffix = getStringLiteralValue(value);
            if (suffix != null && !suffix.startsWith(".")) {
                String message = "Application ID suffix should probably start with a \".\"";
                report(context, valueCookie, PATH, message);
            }
        }
    }

    private static int getSdkVersion(@NonNull String value) {
        int version = 0;
        if (isStringLiteral(value)) {
            String codeName = getStringLiteralValue(value);
            if (codeName != null) {
                if (isNumberString(codeName)) {
                    // Don't access numbered strings; should be literal numbers (lint will warn)
                    return -1;
                }
                AndroidVersion androidVersion = SdkVersionInfo.getVersion(codeName, null);
                if (androidVersion != null) {
                    version = androidVersion.getFeatureLevel();
                }
            }
        } else {
            version = getIntLiteralValue(value, -1);
        }
        return version;
    }

    @Nullable
    private static GradleCoordinate resolveCoordinate(@NonNull Context context,
            @NonNull GradleCoordinate gc) {
        assert gc.getRevision().contains("$") : gc.getRevision();
        Project project = context.getProject();
        Variant variant = project.getCurrentVariant();
        if (variant != null) {
            Dependencies dependencies = variant.getMainArtifact().getDependencies();
            for (AndroidLibrary library : dependencies.getLibraries()) {
                MavenCoordinates mc = library.getResolvedCoordinates();
                // Even though the method is annotated as non-null, this code can run
                // after a failed sync and there are observed scenarios where it returns
                // null in that ase
                //noinspection ConstantConditions
                if (mc != null
                        && mc.getGroupId().equals(gc.getGroupId())
                        && mc.getArtifactId().equals(gc.getArtifactId())) {
                    List<RevisionComponent> revisions =
                            GradleCoordinate.parseRevisionNumber(mc.getVersion());
                    if (!revisions.isEmpty()) {
                        return new GradleCoordinate(mc.getGroupId(), mc.getArtifactId(),
                                revisions, null);
                    }
                    break;
                }
            }
        }

        return null;
    }

    // Convert a long-hand dependency, like
    //    group: 'com.android.support', name: 'support-v4', version: '21.0.+'
    // into an equivalent short-hand dependency, like
    //   com.android.support:support-v4:21.0.+
    @VisibleForTesting
    @Nullable
    static String getNamedDependency(@NonNull String expression) {
        //if (value.startsWith("group: 'com.android.support', name: 'support-v4', version: '21.0.+'"))
        if (expression.indexOf(',') != -1 && expression.contains("version:")) {
            String artifact = null;
            String group = null;
            String version = null;
            Splitter splitter = Splitter.on(',').omitEmptyStrings().trimResults();
            for (String property : splitter.split(expression)) {
                int colon = property.indexOf(':');
                if (colon == -1) {
                    return null;
                }
                char quote = '\'';
                int valueStart = property.indexOf(quote, colon + 1);
                if (valueStart == -1) {
                    quote = '"';
                    valueStart = property.indexOf(quote, colon + 1);
                }
                if (valueStart == -1) {
                    // For example, "transitive: false"
                    continue;
                }
                valueStart++;
                int valueEnd = property.indexOf(quote, valueStart);
                if (valueEnd == -1) {
                    return null;
                }
                String value = property.substring(valueStart, valueEnd);
                if (property.startsWith("group:")) {
                    group = value;
                } else if (property.startsWith("name:")) {
                    artifact = value;
                } else if (property.startsWith("version:")) {
                    version = value;
                }
            }

            if (artifact != null && group != null && version != null) {
                return group + ':' + artifact + ':' + version;
            }
        }

        return null;
    }

    private void checkIntegerAsString(Context context, String value, Object valueCookie) {
        // When done developing with a preview platform you might be tempted to switch from
        //     compileSdkVersion 'android-G'
        // to
        //     compileSdkVersion '19'
        // but that won't work; it needs to be
        //     compileSdkVersion 19
        String string = getStringLiteralValue(value);
        if (isNumberString(string)) {
            String quote = Character.toString(value.charAt(0));
            String message = String.format("Use an integer rather than a string here "
                    + "(replace %1$s%2$s%1$s with just %2$s)", quote, string);
            report(context, valueCookie, STRING_INTEGER, message);
        }
    }

    /**
     * Given an error message produced by this lint detector for the given issue type,
     * returns the old value to be replaced in the source code.
     * <p>
     * Intended for IDE quickfix implementations.
     *
     * @param issue the corresponding issue
     * @param errorMessage the error message associated with the error
     * @param format the format of the error message
     * @return the corresponding old value, or null if not recognized
     */
    @Nullable
    public static String getOldValue(@NonNull Issue issue, @NonNull String errorMessage,
            @NonNull TextFormat format) {
        errorMessage = format.toText(errorMessage);

        // Consider extracting all the error strings as constants and handling this
        // using the LintUtils#getFormattedParameters() method to pull back out the information
        if (issue == DEPENDENCY) {
            // "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0"
            if (errorMessage.startsWith("A newer ")) {
                return findSubstring(errorMessage, " than ", " ");
            }
            if (errorMessage.startsWith("Old buildToolsVersion ")) {
                return findSubstring(errorMessage, "Old buildToolsVersion ", ";");
            }
            if (errorMessage.startsWith("Use Fabric Gradle ") ||
                    errorMessage.startsWith("Use BugSnag ")) {
                return findSubstring(errorMessage, "(was ", ")");
            }
        } else if (issue == STRING_INTEGER) {
            return findSubstring(errorMessage, "replace ", " with ");
        } else if (issue == DEPRECATED) {
            if (errorMessage.contains(APP_PLUGIN_ID) &&
                errorMessage.contains(OLD_APP_PLUGIN_ID)) {
                return OLD_APP_PLUGIN_ID;
            } else if (errorMessage.contains(LIB_PLUGIN_ID) &&
                       errorMessage.contains(OLD_LIB_PLUGIN_ID)) {
                return OLD_LIB_PLUGIN_ID;
            }
            // "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"
            return findSubstring(errorMessage, "Replace '", "'");
        } else if (issue == PLUS) {
          return findSubstring(errorMessage, "(", ")");
        } else if (issue == COMPATIBILITY) {
            if (errorMessage.startsWith("Version 5.2.08")) {
                return "5.2.08";
            }

            // "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)"
            return findSubstring(errorMessage, "targetSdkVersion (", ")");
        }

        return null;
    }

    /**
     * Given an error message produced by this lint detector for the given issue type,
     * returns the new value to be put into the source code.
     * <p>
     * Intended for IDE quickfix implementations.
     *
     * @param issue the corresponding issue
     * @param errorMessage the error message associated with the error
     * @param format the format of the error message
     * @return the corresponding new value, or null if not recognized
     */
    @Nullable
    public static String getNewValue(@NonNull Issue issue, @NonNull String errorMessage,
            @NonNull TextFormat format) {
        errorMessage = format.toText(errorMessage);

        if (issue == DEPENDENCY) {
            // "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0"
            if (errorMessage.startsWith("A newer ")) {
                return findSubstring(errorMessage, " is available: ", null);
            }
            if (errorMessage.startsWith("Old buildToolsVersion ")) {
                return findSubstring(errorMessage, " version is ", " ");
            }
            if (errorMessage.startsWith("Use Fabric Gradle ")) {
                return "1.21.6";
            }
            if (errorMessage.startsWith("Use BugSnag ")) {
                return "2.1.2";
            }
        } else if (issue == STRING_INTEGER) {
            return findSubstring(errorMessage, " just ", ")");
        } else if (issue == DEPRECATED) {
            if (errorMessage.contains(APP_PLUGIN_ID) &&
                errorMessage.contains(OLD_APP_PLUGIN_ID)) {
                return APP_PLUGIN_ID;
            } else if (errorMessage.contains(LIB_PLUGIN_ID) &&
                       errorMessage.contains(OLD_LIB_PLUGIN_ID)) {
                return LIB_PLUGIN_ID;
            }
            // "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"
            return findSubstring(errorMessage, " with '", "'");
        } else if (issue == COMPATIBILITY) {
            if (errorMessage.startsWith("Version 5.2.08")) {
                return findSubstring(errorMessage, "Use version ", " ");
            }

            // "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)"
            return findSubstring(errorMessage, "compileSdkVersion (", ")");
        }

        return null;
    }

    private static boolean isNumberString(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0, n = s.length(); i < n; i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    protected void checkMethodCall(
            @NonNull Context context,
            @NonNull String statement,
            @Nullable String parent,
            @NonNull Map<String, String> namedArguments,
            @SuppressWarnings("UnusedParameters")
            @NonNull List<String> unnamedArguments,
            @NonNull Object cookie) {
        String plugin = namedArguments.get("plugin");
        if (statement.equals("apply") && parent == null) {
            boolean isOldAppPlugin = OLD_APP_PLUGIN_ID.equals(plugin);
            if (isOldAppPlugin || OLD_LIB_PLUGIN_ID.equals(plugin)) {
              String replaceWith = isOldAppPlugin ? APP_PLUGIN_ID : LIB_PLUGIN_ID;
              String message = String.format("'%1$s' is deprecated; use '%2$s' instead", plugin,
                      replaceWith);
              report(context, cookie, DEPRECATED, message);
          }
        }
    }

    @Nullable
    private static Revision parseRevisionSilently(String versionString) {
        try {
            return Revision.parseRevision(versionString);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isModelOlderThan011(@NonNull Context context) {
        return LintUtils.isModelOlderThan(context.getProject(), 0, 11, 0);
    }

    private static int sMajorBuildTools;
    private static Revision sLatestBuildTools;

    /** Returns the latest build tools installed for the given major version.
     * We just cache this once; we don't need to be accurate in the sense that if the
     * user opens the SDK manager and installs a more recent version, we capture this in
     * the same IDE session.
     *
     * @param client the associated client
     * @param major the major version of build tools to look up (e.g. typically 18, 19, ...)
     * @return the corresponding highest known revision
     */
    @Nullable
    private static Revision getLatestBuildTools(@NonNull LintClient client, int major) {
        if (major != sMajorBuildTools) {
            sMajorBuildTools = major;

            List<Revision> revisions = Lists.newArrayList();
            if (major == 24) {
                revisions.add(new Revision(24, 0, 2));
            } if (major == 23) {
                revisions.add(new Revision(23, 0, 3));
            } else if (major == 22) {
                revisions.add(new Revision(22, 0, 1));
            } else if (major == 21) {
                revisions.add(new Revision(21, 1, 2));
            } else if (major == 20) {
                revisions.add(new Revision(20));
            } else if (major == 19) {
                revisions.add(new Revision(19, 1));
            } else if (major == 18) {
                revisions.add(new Revision(18, 1, 1));
            }
            // The above versions can go stale.
            // Check if a more recent one is installed. (The above are still useful for
            // people who haven't updated with the SDK manager recently.)
            File sdkHome = client.getSdkHome();
            if (sdkHome != null) {
                File[] dirs = new File(sdkHome, FD_BUILD_TOOLS).listFiles();
                if (dirs != null) {
                    for (File dir : dirs) {
                        String name = dir.getName();
                        if (!dir.isDirectory() || !Character.isDigit(name.charAt(0))) {
                            continue;
                        }
                        Revision v = parseRevisionSilently(name);
                        if (v != null && v.getMajor() == major) {
                            revisions.add(v);
                        }
                    }
                }
            }

            if (!revisions.isEmpty()) {
                sLatestBuildTools = Collections.max(revisions);
            }
        }

        return sLatestBuildTools;
    }

    private void checkTargetCompatibility(Context context, Object cookie) {
        if (compileSdkVersion > 0 && targetSdkVersion > 0
                && targetSdkVersion > compileSdkVersion) {
            // NOTE: Keep this in sync with {@link #getOldValue} and {@link #getNewValue}
            String message = "The targetSdkVersion (" + targetSdkVersion
                    + ") should not be higher than the compileSdkVersion ("
                    + compileSdkVersion + ")";
            reportNonFatalCompatibilityIssue(context, cookie, message);
        }
    }

    @Nullable
    private static String getStringLiteralValue(@NonNull String value) {
        if (value.length() > 2 && (value.startsWith("'") && value.endsWith("'") ||
                value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }

        return null;
    }

    private static int getIntLiteralValue(@NonNull String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isInteger(String token) {
        return token.matches("\\d+");
    }

    private static boolean isStringLiteral(String token) {
        return token.startsWith("\"") && token.endsWith("\"") ||
                token.startsWith("'") && token.endsWith("'");
    }

    private void checkDependency(
            @NonNull Context context,
            @NonNull GradleCoordinate dependency,
            @NonNull Object cookie) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        GradleVersion version = dependency.getVersion();

        if (groupId != null && groupId.startsWith(SUPPORT_LIB_GROUP_ID)) {
            checkSupportLibraries(context, dependency, cookie);
            if (minSdkVersion >= 14 && "appcompat-v7".equals(artifactId)
                  && compileSdkVersion >= 1 && compileSdkVersion < 21) {
                report(context, cookie, DEPENDENCY,
                    "Using the appcompat library when minSdkVersion >= 14 and "
                            + "compileSdkVersion < 21 is not necessary");
            }
            return;
        } else if ((GMS_GROUP_ID.equals(groupId)
                || FIREBASE_GROUP_ID.equals(groupId)
                || GOOGLE_SUPPORT_GROUP_ID.equals(groupId)
                || ANDROID_WEAR_GROUP_ID.equals(groupId))
                && artifactId != null) {

            // 5.2.08 is not supported; special case and warn about this
            if ("5.2.08".equals(dependency.getRevision()) && context.isEnabled(COMPATIBILITY)) {
                // This specific version is actually a preview version which should
                // not be used (https://code.google.com/p/android/issues/detail?id=75292)
                String maxVersion = "6.1.11";
                // Try to find a more recent available version, if one is available
                File sdkHome = context.getClient().getSdkHome();
                File repository = SdkMavenRepository.GOOGLE.getRepositoryLocation(sdkHome, true,
                        FileOpUtils.create());
                if (repository != null) {
                    GradleCoordinate max = MavenRepositories.getHighestInstalledVersion(
                            groupId, artifactId, repository,
                            null, false, FileOpUtils.create());
                    if (max != null) {
                        if (COMPARE_PLUS_HIGHER.compare(dependency, max) < 0) {
                            maxVersion = max.getRevision();
                        }
                    }
                }
                String message = String.format("Version `5.2.08` should not be used; the app "
                        + "can not be published with this version. Use version `%1$s` "
                        + "instead.", maxVersion);
                reportFatalCompatibilityIssue(context, cookie, message);
            } else if (context.isEnabled(BUNDLED_GMS)
                  && PLAY_SERVICES_V650.isSameArtifact(dependency)
                  && COMPARE_PLUS_HIGHER.compare(dependency, PLAY_SERVICES_V650) >= 0) {
                // Play services 6.5.0 is the first version to allow un-bundling, so if the user is
                // at or above 6.5.0, recommend un-bundling
                String message = "Avoid using bundled version of Google Play services SDK.";
                report(context, cookie, BUNDLED_GMS, message);

            } else if (GMS_GROUP_ID.equals(groupId)
                  && "play-services-appindexing".equals(artifactId)) {
                String message = "Deprecated: Replace '" + GMS_GROUP_ID
                        + ":play-services-appindexing:" + dependency.getRevision()
                        + "' with 'com.google.firebase:firebase-appindexing:10.0.0' or above. "
                        + "More info: http://firebase.google.com/docs/app-indexing/android/migrate";
                report(context, cookie, DEPRECATED, message);
            }

            if (targetSdkVersion >= 26 && version != null) {
                // When targeting O the following libraries must be using at least version 10.2.1
                // (or 0.6.0 of the jobdispatcher API)
                //com.google.android.gms:play-services-gcm:V
                //com.google.firebase:firebase-messaging:V
                if (GMS_GROUP_ID.equals(groupId)
                        && "play-services-gcm".equals(artifactId)) {
                    ensureTargetingOVersion(context, dependency, version, cookie, 10, 2, 1);
                } else if (FIREBASE_GROUP_ID.equals(groupId)
                            && "firebase-messaging".equals(artifactId)) {
                    ensureTargetingOVersion(context, dependency, version, cookie, 10, 2, 1);
                } else if ("firebase-jobdispatcher".equals(artifactId)
                        || "firebase-jobdispatcher-with-gcm-dep".equals(artifactId)) {
                    ensureTargetingOVersion(context, dependency, version, cookie, 0, 6, 0);
                }
            }

            checkPlayServices(context, dependency, cookie);
            return;
        }

        Revision newerVersion = null;
        Issue issue = DEPENDENCY;
        if ("com.android.tools.build".equals(groupId) &&
                "gradle".equals(artifactId)) {
            try {
                Revision v = Revision.parseRevision(GRADLE_PLUGIN_RECOMMENDED_VERSION);
                if (!v.isPreview()) {
                    newerVersion = getNewerRevision(dependency, v);
                }
            } catch (NumberFormatException e) {
                context.log(e, null);
            }
        } else if ("com.google.guava".equals(groupId) &&
                "guava".equals(artifactId)) {
            newerVersion = getNewerRevision(dependency, new Revision(21, 0));
        } else if ("com.google.code.gson".equals(groupId) &&
                "gson".equals(artifactId)) {
            newerVersion = getNewerRevision(dependency, new Revision(2, 8, 0));
        } else if ("org.apache.httpcomponents".equals(groupId) &&
                "httpclient".equals(artifactId)) {
            newerVersion = getNewerRevision(dependency, new Revision(4, 3, 5));
        } else if ("com.squareup.okhttp3".equals(groupId) &&
                "okhttp".equals(artifactId)) {
            newerVersion = getNewerRevision(dependency, new Revision(3, 6, 0));
        } else if ("com.github.bumptech.glide".equals(groupId) &&
                "glide".equals(artifactId)) {
            newerVersion = getNewerRevision(dependency, new Revision(3, 7, 0));
        } else if ("io.fabric.tools".equals(groupId) &&
                "gradle".equals(artifactId)) {
            GradleVersion parsed = GradleVersion.tryParse(dependency.getRevision());
            if (parsed != null && parsed.compareTo("1.21.6") < 0) {
                report(context, cookie, DEPENDENCY, "Use Fabric Gradle plugin version 1.21.6 or "
                        + "later to improve Instant Run performance (was " +
                        dependency.getRevision() + ")");
            } else {
                // From https://s3.amazonaws.com/fabric-artifacts/public/io/fabric/tools/gradle/maven-metadata.xml
                newerVersion = getNewerRevision(dependency, new Revision(1, 22, 1));
            }
        } else if ("com.bugsnag".equals(groupId) &&
                "bugsnag-android-gradle-plugin".equals(artifactId)) {
            GradleVersion parsed = GradleVersion.tryParse(dependency.getRevision());
            if (parsed != null && parsed.compareTo("2.1.2") < 0) {
                report(context, cookie, DEPENDENCY, "Use BugSnag Gradle plugin version 2.1.2 or "
                        + "later to improve Instant Run performance (was " +
                        dependency.getRevision() + ")");
            } else {
                newerVersion = getNewerRevision(dependency, new Revision(2, 4, 1));
            }
        }

        // Network check for really up to date libraries? Only done in batch mode
        if (context.getScope().size() > 1 && context.isEnabled(REMOTE_VERSION)) {
            Revision latest = getLatestVersionFromRemoteRepo(context.getClient(), dependency,
                    dependency.isPreview());
            if (latest != null && isOlderThan(dependency, latest.getMajor(), latest.getMinor(),
                    latest.getMicro())) {
                newerVersion = latest;
                issue = REMOTE_VERSION;
            }
        }

        if (newerVersion != null) {
            String message = getNewerVersionAvailableMessage(dependency, newerVersion);
            report(context, cookie, issue, message);
        }
    }

    private void ensureTargetingOVersion(@NonNull Context context,
            @NonNull GradleCoordinate dependency, @Nullable GradleVersion version,
            @NonNull Object cookie, int major, int minor, int micro) {
        if (version != null && !version.isAtLeast(major, minor, micro)) {
            Revision revision = new Revision(major, minor, micro);
            Revision newest = getNewerRevision(dependency, revision);
            if (newest != null) {
                revision = newest;
            }

            String message = String.format("Version must be at least %1$s when "
                    + "targeting O", revision);

            reportFatalCompatibilityIssue(context, cookie, message);
        }
    }

    private static String getNewerVersionAvailableMessage(GradleCoordinate dependency,
            Revision version) {
        return getNewerVersionAvailableMessage(dependency, version.toString());
    }

    private static String getNewerVersionAvailableMessage(GradleCoordinate dependency,
            String version) {
        // NOTE: Keep this in sync with {@link #getOldValue} and {@link #getNewValue}
        return "A newer version of " + dependency.getGroupId() + ":" +
                dependency.getArtifactId() + " than " + dependency.getRevision() +
                " is available: " + version;
    }

    /** TODO: Cache these results somewhere! */
    @Nullable
    public static Revision getLatestVersionFromRemoteRepo(@NonNull LintClient client,
            @NonNull GradleCoordinate dependency, boolean allowPreview) {
        return getLatestVersionFromRemoteRepo(client, dependency, true, allowPreview);
    }

    @Nullable
    private static Revision getLatestVersionFromRemoteRepo(@NonNull LintClient client,
            @NonNull GradleCoordinate dependency, boolean firstRowOnly, boolean allowPreview) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        if (groupId == null || artifactId == null) {
            return null;
        }
        StringBuilder query = new StringBuilder();
        String encoding = UTF_8.name();
        try {
            query.append("http://search.maven.org/solrsearch/select?q=g:%22");
            query.append(URLEncoder.encode(groupId, encoding));
            query.append("%22+AND+a:%22");
            query.append(URLEncoder.encode(artifactId, encoding));
        } catch (UnsupportedEncodingException ee) {
            return null;
        }
        query.append("%22&core=gav");
        if (firstRowOnly) {
            query.append("&rows=1");
        }
        query.append("&wt=json");

        String response = readUrlData(client, dependency, query.toString());
        if (response == null) {
            return null;
        }

        // Sample response:
        //    {
        //        "responseHeader": {
        //            "status": 0,
        //            "QTime": 0,
        //            "params": {
        //                "fl": "id,g,a,v,p,ec,timestamp,tags",
        //                "sort": "score desc,timestamp desc,g asc,a asc,v desc",
        //                "indent": "off",
        //                "q": "g:\"com.google.guava\" AND a:\"guava\"",
        //                "core": "gav",
        //                "wt": "json",
        //                "rows": "1",
        //                "version": "2.2"
        //            }
        //        },
        //        "response": {
        //            "numFound": 37,
        //            "start": 0,
        //            "docs": [{
        //                "id": "com.google.guava:guava:17.0",
        //                "g": "com.google.guava",
        //                "a": "guava",
        //                "v": "17.0",
        //                "p": "bundle",
        //                "timestamp": 1398199666000,
        //                "tags": ["spec", "libraries", "classes", "google", "code"],
        //                "ec": ["-javadoc.jar", "-sources.jar", ".jar", "-site.jar", ".pom"]
        //            }]
        //        }
        //    }

        // Look for version info:  This is just a cheap skim of the above JSON results
        boolean foundPreview = false;
        int index = response.indexOf("\"response\"");
        while (index != -1) {
            index = response.indexOf("\"v\":", index);
            if (index != -1) {
                index += 4;
                int start = response.indexOf('"', index) + 1;
                int end = response.indexOf('"', start + 1);
                if (end > start && start >= 0) {
                    Revision revision = parseRevisionSilently(response.substring(start, end));
                    if (revision != null) {
                        foundPreview = revision.isPreview();
                        if (allowPreview || !foundPreview) {
                            return revision;
                        }
                    }
                }
            }
        }

        if (!allowPreview && foundPreview && firstRowOnly) {
            // Recurse: search more than the first row this time to see if we can find a
            // non-preview version
            return getLatestVersionFromRemoteRepo(client, dependency, false, false);
        }

        return null;
    }

    /** Normally null; used for testing */
    @Nullable
    @VisibleForTesting
    static Map<String,String> sMockData;

    @Nullable
    private static String readUrlData(
            @NonNull LintClient client,
            @NonNull GradleCoordinate dependency,
            @NonNull String query) {
        // For unit testing: avoid network as well as unexpected new versions
        if (sMockData != null) {
            String value = sMockData.get(query);
            assert value != null : query;
            return value;
        }

        try {
            URL url = new URL(query);

            URLConnection connection = client.openConnection(url);
            if (connection == null) {
                return null;
            }
            try {
                InputStream is = connection.getInputStream();
                if (is == null) {
                    return null;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8))) {
                    StringBuilder sb = new StringBuilder(500);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                        sb.append('\n');
                    }

                    return sb.toString();
                }
            } finally {
                client.closeConnection(connection);
            }
        } catch (IOException ioe) {
            client.log(ioe, "Could not connect to maven central to look up the " +
                    "latest available version for %1$s", dependency);
            return null;
        }
    }

    private boolean checkGradlePluginDependency(Context context, GradleCoordinate dependency,
            Object cookie) {
        GradleCoordinate latestPlugin = GradleCoordinate.parseCoordinateString(
                SdkConstants.GRADLE_PLUGIN_NAME +
                        GRADLE_PLUGIN_MINIMUM_VERSION);
        if (COMPARE_PLUS_HIGHER.compare(dependency, latestPlugin) < 0) {
            String message = "You must use a newer version of the Android Gradle plugin. The "
                    + "minimum supported version is " + GRADLE_PLUGIN_MINIMUM_VERSION +
                    " and the recommended version is " + GRADLE_PLUGIN_RECOMMENDED_VERSION;
            report(context, cookie, GRADLE_PLUGIN_COMPATIBILITY, message);
            return true;
        }
        return false;
    }

    private void checkSupportLibraries(Context context, GradleCoordinate dependency,
            Object cookie) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        assert groupId != null && artifactId != null;

        // For artifacts that follow the platform numbering scheme, check that it matches the SDK
        // versions used.
        if (SUPPORT_LIB_GROUP_ID.equals(groupId)
                && !artifactId.startsWith("multidex")
                // Support annotation libraries work with any compileSdkVersion
                && !artifactId.equals("support-annotations")) {
            if (compileSdkVersion >= 18
                    && dependency.getMajorVersion() != compileSdkVersion
                    && dependency.getMajorVersion() != GradleCoordinate.PLUS_REV_VALUE
                    && context.isEnabled(COMPATIBILITY)) {
                String message = "This support library should not use a different version ("
                        + dependency.getMajorVersion() + ") than the `compileSdkVersion` ("
                        + compileSdkVersion + ")";
                reportNonFatalCompatibilityIssue(context, cookie, message);
            } else if (targetSdkVersion > 0
                    && dependency.getMajorVersion() < targetSdkVersion
                    && dependency.getMajorVersion() != GradleCoordinate.PLUS_REV_VALUE
                    && context.isEnabled(COMPATIBILITY)) {
                String message = "This support library should not use a lower version ("
                        + dependency.getMajorVersion() + ") than the `targetSdkVersion` ("
                        + targetSdkVersion + ")";
                reportNonFatalCompatibilityIssue(context, cookie, message);
            }
        }

        // Check to make sure you have the Android support repository installed
        File sdkHome = context.getClient().getSdkHome();
        File repository = SdkMavenRepository.ANDROID.getRepositoryLocation(sdkHome, true,
                FileOpUtils.create());
        if (repository == null) {
            report(context, cookie, DEPENDENCY,
                    "Dependency on a support library, but the SDK installation does not "
                            + "have the \"Extras > Android Support Repository\" installed. "
                            + "Open the SDK manager and install it.");
        } else {
            checkLocalMavenVersions(context, dependency, cookie, groupId, artifactId,
                    repository);

            if (!mCheckedSupportLibs && !artifactId.startsWith("multidex") &&
                    !artifactId.equals("support-annotations")) {
                mCheckedSupportLibs = true;
                if (!context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
                    // Incremental editing: try flagging them in this file!
                    checkConsistentSupportLibraries(context, cookie);
                }
            }
        }
    }

    /**
     * If incrementally editing a single build.gradle file, tracks whether we've already
     * transitively checked GMS versions such that we don't flag the same error on every
     * single dependency declaration
     */
    private boolean mCheckedGms;

    /**
     * If incrementally editing a single build.gradle file, tracks whether we've already
     * transitively checked support library versions such that we don't flag the same
     * error on every single dependency declaration
     */
    private boolean mCheckedSupportLibs;

    /**
     * If incrementally editing a single build.gradle file, tracks whether we've already
     * transitively checked wearable library versions such that we don't flag the same
     * error on every single dependency declaration
     */
    private boolean mCheckedWearableLibs;

    private void checkPlayServices(Context context, GradleCoordinate dependency, Object cookie) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        assert groupId != null && artifactId != null;

        File sdkHome = context.getClient().getSdkHome();
        File repository = SdkMavenRepository.GOOGLE.getRepositoryLocation(sdkHome, true,
                FileOpUtils.create());
        if (repository == null) {
            report(context, cookie, DEPENDENCY,
                    "Dependency on Play Services, but the SDK installation does not "
                            + "have the \"Extras > Google Repository\" installed. "
                            + "Open the SDK manager and install it.");
        } else {
            checkLocalMavenVersions(context, dependency, cookie, groupId, artifactId,
                    repository);
        }

        if (GMS_GROUP_ID.equals(groupId) || FIREBASE_GROUP_ID.equals(groupId)) {
            if (!mCheckedGms) {
                mCheckedGms = true;
                // Incremental analysis only? If so, tie the check to
                // a specific GMS play dependency if only, such that it's highlighted
                // in the editor
                if (!context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
                    // Incremental editing: try flagging them in this file!
                    checkConsistentPlayServices(context, cookie);
                }
            }
        } else {
            if (!mCheckedWearableLibs) {
                mCheckedWearableLibs = true;
                // Incremental analysis only? If so, tie the check to
                // a specific GMS play dependency if only, such that it's highlighted
                // in the editor
                if (!context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
                    // Incremental editing: try flagging them in this file!
                    checkConsistentWearableLibraries(context, cookie);
                }
            }
        }
    }

    private void checkConsistentSupportLibraries(@NonNull Context context,
            @Nullable Object cookie) {
        checkConsistentLibraries(context, cookie, SUPPORT_LIB_GROUP_ID, null);
    }

    private void checkConsistentPlayServices(@NonNull Context context,
            @Nullable Object cookie) {
        checkConsistentLibraries(context, cookie, GMS_GROUP_ID, FIREBASE_GROUP_ID);
    }

    private void checkConsistentWearableLibraries(@NonNull Context context,
            @Nullable Object cookie) {
        // Make sure we have both
        //   compile 'com.google.android.support:wearable:2.0.0-alpha3'
        //   provided 'com.google.android.wearable:wearable:2.0.0-alpha3'
        Project project = context.getMainProject();
        if (!project.isGradleProject()) {
            return;
        }
        Set<String> supportVersions = Sets.newHashSet();
        Set<String> wearableVersions = Sets.newHashSet();
        for (AndroidLibrary library : getAndroidLibraries(project)) {
            MavenCoordinates coordinates = library.getResolvedCoordinates();
            // Claims to be non-null but may not be after a failed gradle sync
            //noinspection ConstantConditions
            if (coordinates != null &&
                WEARABLE_ARTIFACT_ID.equals(coordinates.getArtifactId()) &&
                    GOOGLE_SUPPORT_GROUP_ID.equals(coordinates.getGroupId())) {
                supportVersions.add(coordinates.getVersion());
            }
        }
        for (JavaLibrary library : getJavaLibraries(project)) {
            MavenCoordinates coordinates = library.getResolvedCoordinates();
            // Claims to be non-null but may not be after a failed gradle sync
            //noinspection ConstantConditions
            if (coordinates != null &&
                WEARABLE_ARTIFACT_ID.equals(coordinates.getArtifactId()) &&
                    ANDROID_WEAR_GROUP_ID.equals(coordinates.getGroupId())) {
                if (!library.isProvided()) {
                    if (cookie != null) {
                        String message = "This dependency should be marked as "
                                        + "`provided`, not `compile`";

                        reportFatalCompatibilityIssue(context, cookie, message);
                    } else {
                        String message = String.format("The %1$s:%2$s dependency should be "
                                        + "marked as `provided`, not `compile`",
                                ANDROID_WEAR_GROUP_ID,
                                WEARABLE_ARTIFACT_ID);
                        reportFatalCompatibilityIssue(context,
                                guessGradleLocation(context.getProject()),
                                message);
                    }
                }
                wearableVersions.add(coordinates.getVersion());
            }
        }

        if (!supportVersions.isEmpty()) {
            if (wearableVersions.isEmpty()) {
                List<String> list = Lists.newArrayList(supportVersions);
                String first = Collections.min(list);
                String message = String.format("Project depends on %1$s:%2$s:%3$s, so it must "
                                + "also depend (as a provided dependency) on %4$s:%5$s:%6$s",
                        GOOGLE_SUPPORT_GROUP_ID,
                        WEARABLE_ARTIFACT_ID,
                        first,
                        ANDROID_WEAR_GROUP_ID,
                        WEARABLE_ARTIFACT_ID,
                        first);
                if (cookie != null) {
                    reportFatalCompatibilityIssue(context, cookie, message);
                } else {
                    reportFatalCompatibilityIssue(context,
                            guessGradleLocation(context.getProject()),
                            message);
                }
            } else {
                // Check that they have the same versions
                if (!supportVersions.equals(wearableVersions)) {
                    List<String> sortedSupportVersions = Lists.newArrayList(supportVersions);
                    Collections.sort(sortedSupportVersions);
                    List<String> supportedWearableVersions = Lists.newArrayList(wearableVersions);
                    Collections.sort(supportedWearableVersions);
                    String message = String.format("The wearable libraries for %1$s and %2$s " +
                                    "must use **exactly** the same versions; found %3$s " +
                                    "and %4$s",
                            GOOGLE_SUPPORT_GROUP_ID,
                            ANDROID_WEAR_GROUP_ID,
                            sortedSupportVersions.size() == 1 ? sortedSupportVersions.get(0)
                                    : sortedSupportVersions.toString(),
                            supportedWearableVersions.size() == 1 ? supportedWearableVersions.get(0)
                                    : supportedWearableVersions.toString());
                    if (cookie != null) {
                        reportFatalCompatibilityIssue(context, cookie, message);
                    } else {
                        reportFatalCompatibilityIssue(context,
                                guessGradleLocation(context.getProject()),
                                message);
                    }
                }
            }
        }
    }

    private void checkConsistentLibraries(@NonNull Context context,
            @Nullable Object cookie, @NonNull String groupId, @Nullable String groupId2) {
        // Make sure we're using a consistent version across all play services libraries
        // (b/22709708)

        Project project = context.getMainProject();
        Multimap<String, MavenCoordinates> versionToCoordinate = ArrayListMultimap.create();
        Collection<AndroidLibrary> androidLibraries = getAndroidLibraries(project);
        for (AndroidLibrary library : androidLibraries) {
            MavenCoordinates coordinates = library.getResolvedCoordinates();
            // Claims to be non-null but may not be after a failed gradle sync
            //noinspection ConstantConditions
            if (coordinates != null && (coordinates.getGroupId().equals(groupId)
                     || (coordinates.getGroupId().equals(groupId2)))
                    // Historically the multidex library ended up in the support package but
                    // decided to do its own numbering (and isn't tied to the rest in terms
                    // of implementation dependencies)
                    && !coordinates.getArtifactId().startsWith("multidex")
                    // Similarly firebase job dispatcher doesn't follow normal firebase version
                    // numbering
                    && !coordinates.getArtifactId().startsWith("firebase-jobdispatcher")) {
                versionToCoordinate.put(coordinates.getVersion(), coordinates);
            }
        }

        for (JavaLibrary library : getJavaLibraries(project)) {
            MavenCoordinates coordinates = library.getResolvedCoordinates();
            // Claims to be non-null but may not be after a failed gradle sync
            //noinspection ConstantConditions
            if (coordinates != null && (coordinates.getGroupId().equals(groupId)
                    || (coordinates.getGroupId().equals(groupId2)))
                    // The Android annotations library is decoupled from the rest and doesn't
                    // need to be matched to the other exact support library versions
                    && !coordinates.getArtifactId().equals("support-annotations")) {
                versionToCoordinate.put(coordinates.getVersion(), coordinates);
            }
        }

        Set<String> versions = versionToCoordinate.keySet();
        if (versions.size() > 1) {
            List<String> sortedVersions = Lists.newArrayList(versions);
            sortedVersions.sort(Collections.reverseOrder());
            MavenCoordinates c1 = findFirst(versionToCoordinate.get(sortedVersions.get(0)));
            MavenCoordinates c2 = findFirst(versionToCoordinate.get(sortedVersions.get(1)));
            // Not using toString because in the IDE, these are model proxies which display garbage output
            String example1 = c1.getGroupId() + ":" + c1.getArtifactId() + ":" + c1 .getVersion();
            String example2 = c2.getGroupId() + ":" + c2.getArtifactId() + ":" + c2 .getVersion();
            String groupDesc = GMS_GROUP_ID.equals(groupId) ? "gms/firebase" : groupId;
            String message = "All " + groupDesc + " libraries must use the exact same "
                    + "version specification (mixing versions can lead to runtime crashes). "
                    + "Found versions " + Joiner.on(", ").join(sortedVersions) + ". "
                    + "Examples include `" + example1 + "` and `" + example2 + "`";

            // Create an improved error message for a confusing scenario where you use
            // data binding and end up with conflicting versions:
            // https://code.google.com/p/android/issues/detail?id=229664
            for (AndroidLibrary library : androidLibraries) {
                MavenCoordinates coordinates = library.getResolvedCoordinates();
                // Claims to be non-null but may not be after a failed gradle sync
                //noinspection ConstantConditions
                if (coordinates != null
                        && coordinates.getGroupId().equals("com.android.databinding")
                        && coordinates.getArtifactId().equals("library")) {
                    for (AndroidLibrary dep : library.getLibraryDependencies()) {
                        MavenCoordinates c = dep.getResolvedCoordinates();
                        // Claims to be non-null but may not be after a failed gradle sync
                        //noinspection ConstantConditions
                        if (c != null
                                && c.getGroupId().equals("com.android.support")
                                && c.getArtifactId().equals("support-v4") &&
                                !sortedVersions.get(0).equals(c.getVersion())) {
                            message += ". Note that this project is using data binding "
                                    + "(com.android.databinding:library:"
                                    + coordinates.getVersion()
                                    + ") which pulls in com.android.support:support-v4:"
                                    + c.getVersion() + ". You can try to work around this "
                                    + "by adding an explicit dependency on "
                                    + "com.android.support:support-v4:" + sortedVersions.get(0);
                            break;
                        }

                    }
                    break;
                }
            }

            if (cookie != null) {
                reportNonFatalCompatibilityIssue(context, cookie, message);
            } else {
                File projectDir = context.getProject().getDir();
                Location location1 = guessGradleLocation(context.getClient(), projectDir, example1);
                Location location2 = guessGradleLocation(context.getClient(), projectDir, example2);
                if (location1.getStart() != null) {
                    if (location2.getStart() != null) {
                        location1.setSecondary(location2);
                    }
                } else {
                    if (location2.getStart() == null) {
                        location1 = guessGradleLocation(context.getClient(), projectDir,
                                // Probably using version variable
                                c1.getGroupId() + ":" + c1.getArtifactId() + ":");
                        if (location1.getStart() == null) {
                            location1 = guessGradleLocation(context.getClient(), projectDir,
                                    // Probably using version variable
                                    c2.getGroupId() + ":" + c2.getArtifactId() + ":");
                        }
                    } else {
                        location1 = location2;
                    }
                }
                reportNonFatalCompatibilityIssue(context, location1, message);
            }
        }
    }

    private static MavenCoordinates findFirst(@NonNull Collection<MavenCoordinates> coordinates) {
        return Collections.min(coordinates, Comparator.comparing(Object::toString));
    }

    @NonNull
    public static Collection<AndroidLibrary> getAndroidLibraries(@NonNull Project project) {
        Dependencies compileDependencies = getCompileDependencies(project);
        if (compileDependencies == null) {
            return Collections.emptyList();
        }

        Set<AndroidLibrary> allLibraries = Sets.newHashSet();
        addIndirectAndroidLibraries(compileDependencies.getLibraries(), allLibraries);
        return allLibraries;
    }

    @NonNull
    public static Collection<JavaLibrary> getJavaLibraries(@NonNull Project project) {
        Dependencies compileDependencies = getCompileDependencies(project);
        if (compileDependencies == null) {
            return Collections.emptyList();
        }

        Set<JavaLibrary> allLibraries = Sets.newHashSet();
        addIndirectJavaLibraries(compileDependencies.getJavaLibraries(), allLibraries);
        return allLibraries;
    }

    private static void addIndirectAndroidLibraries(
            @NonNull Collection<? extends AndroidLibrary> libraries,
            @NonNull Set<AndroidLibrary> result) {
        for (AndroidLibrary library : libraries) {
            if (!result.contains(library)) {
                result.add(library);
                addIndirectAndroidLibraries(library.getLibraryDependencies(), result);
            }
        }
    }

    private static void addIndirectJavaLibraries(
            @NonNull Collection<? extends JavaLibrary> libraries,
            @NonNull Set<JavaLibrary> result) {
        for (JavaLibrary library : libraries) {
            if (!result.contains(library)) {
                result.add(library);
                addIndirectJavaLibraries(library.getDependencies(), result);
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getProject() == context.getMainProject() &&
                // Full analysis? Don't tie check to any specific Gradle DSL element
                context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
            checkConsistentPlayServices(context, null);
            checkConsistentSupportLibraries(context, null);
            checkConsistentWearableLibraries(context, null);
        }
    }

    private void checkLocalMavenVersions(Context context, GradleCoordinate dependency,
            Object cookie, String groupId, String artifactId, File repository) {
        GradleCoordinate max = MavenRepositories.getHighestInstalledVersion(
                groupId, artifactId, repository, null, false, FileOpUtils.create());
        if (max != null) {
            if (COMPARE_PLUS_HIGHER.compare(dependency, max) < 0
                    && context.isEnabled(DEPENDENCY)) {
                String message = getNewerVersionAvailableMessage(dependency, max.getRevision());
                report(context, cookie, DEPENDENCY, message);
            }
        }
    }

    private static Revision getNewerRevision(@NonNull GradleCoordinate dependency,
            @NonNull Revision revision) {
        assert dependency.getGroupId() != null;
        assert dependency.getArtifactId() != null;
        GradleCoordinate coordinate;
        if (revision.isPreview()) {
            String coordinateString = dependency.getGroupId()
                    + ":" + dependency.getArtifactId()
                    + ":" + revision.toString();
            coordinate = GradleCoordinate.parseCoordinateString(coordinateString);
        } else {
            coordinate = new GradleCoordinate(dependency.getGroupId(), dependency.getArtifactId(),
                    revision.getMajor(), revision.getMinor(), revision.getMicro());
        }
        if (COMPARE_PLUS_HIGHER.compare(dependency, coordinate) < 0) {
            return revision;
        } else {
            return null;
        }
    }

    private static boolean isOlderThan(@NonNull GradleCoordinate dependency, int major, int minor,
            int micro) {
        assert dependency.getGroupId() != null;
        assert dependency.getArtifactId() != null;
        return COMPARE_PLUS_HIGHER.compare(dependency,
                new GradleCoordinate(dependency.getGroupId(),
                        dependency.getArtifactId(), major, minor, micro)) < 0;
    }

    private void report(@NonNull Context context, @NonNull Object cookie, @NonNull Issue issue,
            @NonNull String message) {
        if (context.isEnabled(issue)) {
            // Suppressed?
            // Temporarily unconditionally checking for suppress comments in Gradle files
            // since Studio insists on an AndroidLint id prefix
            boolean checkComments = /*context.getClient().checkForSuppressComments()
                    &&*/ context.containsCommentSuppress();
            if (checkComments) {
                int startOffset = getStartOffset(context, cookie);
                if (startOffset >= 0 && context.isSuppressedWithComment(startOffset, issue)) {
                    return;
                }
            }

            context.report(issue, createLocation(context, cookie), message);
        }
    }

    /**
     * Normally, all warnings reported for a given issue will have the same severity, so
     * it isn't possible to have some of them reported as errors and others as warnings.
     * And this is intentional, since users should get to designate whether an issue is
     * an error or a warning (or ignored for that matter).
     * <p>
     * However, for {@link #COMPATIBILITY} we want to treat some issues as fatal (breaking
     * the build) but not others. To achieve this we tweak things a little bit.
     * All compatibility issues are now marked as fatal, and if we're *not* in the
     * "fatal only" mode, all issues are reported as before (with severity fatal, which has
     * the same visual appearance in the IDE as the previous severity, "error".)
     * However, if we're in a "fatal-only" build, then we'll stop reporting the issues
     * that aren't meant to be treated as fatal. That's what this method does; issues
     * reported to it should always be reported as fatal. There is a corresponding method,
     * {@link #reportNonFatalCompatibilityIssue(Context, Object, String)} which can be used
     * to report errors that shouldn't break the build; those are ignored in fatal-only
     * mode.
     */
    private void reportFatalCompatibilityIssue(
            @NonNull Context context,
            @NonNull Object cookie,
            @NonNull String message) {
        report(context, cookie, COMPATIBILITY, message);
    }

    private static void reportFatalCompatibilityIssue(
            @NonNull Context context,
            @NonNull Location location,
            @NonNull String message) {
        context.report(COMPATIBILITY, location, message);
    }

    /** See {@link #reportFatalCompatibilityIssue(Context, Object, String)} for an explanation. */
    private void reportNonFatalCompatibilityIssue(
            @NonNull Context context,
            @NonNull Object cookie,
            @NonNull String message) {
        if (context.getDriver().isFatalOnlyMode()) {
            return;
        }

        report(context, cookie, COMPATIBILITY, message);
    }

    /** See {@link #reportFatalCompatibilityIssue(Context, Object, String)} for an explanation. */
    private void reportNonFatalCompatibilityIssue(
            @NonNull Context context,
            @NonNull Location location,
            @NonNull String message) {
        if (context.getDriver().isFatalOnlyMode()) {
            return;
        }

        context.report(COMPATIBILITY, location, message);
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NonNull
    protected Object getPropertyKeyCookie(@NonNull Object cookie) {
        return cookie;
    }

    @SuppressWarnings({"MethodMayBeStatic", "UnusedDeclaration"})
    @NonNull
    protected Object getPropertyPairCookie(@NonNull Object cookie) {
      return cookie;
    }

    @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
    protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
        return -1;
    }

    @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
    protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
        return null;
    }

    @Nullable
    public static Dependencies getCompileDependencies(@NonNull Project project) {
        if (!project.isGradleProject()) {
            return null;
        }
        Variant variant = project.getCurrentVariant();
        if (variant == null) {
            return null;
        }

        AndroidArtifact artifact = variant.getMainArtifact();
        return artifact.getDependencies();
    }
}
