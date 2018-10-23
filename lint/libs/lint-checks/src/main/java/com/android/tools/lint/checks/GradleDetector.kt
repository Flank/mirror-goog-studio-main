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
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.FD_BUILD_TOOLS
import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION
import com.android.SdkConstants.SUPPORT_LIB_GROUP_ID
import com.android.annotations.VisibleForTesting
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.MavenRepositories
import com.android.ide.common.repository.SdkMavenRepository
import com.android.repository.io.FileOpUtils
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.lint.checks.ManifestDetector.TARGET_NEWER
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleContext.Companion.getIntLiteralValue
import com.android.tools.lint.detector.api.GradleContext.Companion.getStringLiteralValue
import com.android.tools.lint.detector.api.GradleContext.Companion.isNonNegativeInteger
import com.android.tools.lint.detector.api.GradleContext.Companion.isStringLiteral
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.guessGradleLocation
import com.android.tools.lint.detector.api.isNumberString
import com.android.tools.lint.detector.api.readUrlData
import com.android.tools.lint.detector.api.readUrlDataAsString
import com.google.common.base.Charsets.UTF_8
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.function.Predicate

/** Checks Gradle files for potential errors */
open class GradleDetector : Detector(), GradleScanner {

    private var minSdkVersion: Int = 0
    private var compileSdkVersion: Int = 0
    private var compileSdkVersionCookie: Any? = null
    private var targetSdkVersion: Int = 0

    protected open val gradleUserHome: File
        get() {
            // See org.gradle.initialization.BuildLayoutParameters
            var gradleUserHome: String? = System.getProperty("gradle.user.home")
            if (gradleUserHome == null) {
                gradleUserHome = System.getenv("GRADLE_USER_HOME")
                if (gradleUserHome == null) {
                    gradleUserHome = System.getProperty("user.home") + File.separator + ".gradle"
                }
            }

            return File(gradleUserHome)
        }

    private var artifactCacheHome: File? = null

    /**
     * If incrementally editing a single build.gradle file, tracks whether we've already
     * transitively checked GMS versions such that we don't flag the same error on every single
     * dependency declaration
     */
    private var mCheckedGms: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks whether we've already
     * transitively checked support library versions such that we don't flag the same error on every
     * single dependency declaration
     */
    private var mCheckedSupportLibs: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks whether we've already
     * transitively checked wearable library versions such that we don't flag the same error on
     * every single dependency declaration
     */
    private var mCheckedWearableLibs: Boolean = false

    private val blacklisted = HashMap<Project, BlacklistedDeps>()

    // ---- Implements GradleScanner ----

    private fun checkOctal(
        context: GradleContext,
        value: String,
        cookie: Any
    ) {
        if (value.length >= 2 &&
            value[0] == '0' &&
            (value.length > 2 || value[1] >= '8' && isNonNegativeInteger(value)) &&
            context.isEnabled(ACCIDENTAL_OCTAL)
        ) {
            var message =
                "The leading 0 turns this number into octal which is probably not what was intended"
            message += try {
                val numericValue = java.lang.Long.decode(value)
                " (interpreted as $numericValue)"
            } catch (exception: NumberFormatException) {
                " (and it is not a valid octal number)"
            }

            report(context, cookie, ACCIDENTAL_OCTAL, message)
        }
    }

    /** Called with for example "android", "defaultConfig", "minSdkVersion", "7" */
    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any
    ) {
        if (parent == "defaultConfig") {
            if (property == "targetSdkVersion") {
                val version = getSdkVersion(value)
                if (version > 0 && version < context.client.highestKnownApiLevel) {
                    var warned = false
                    if (version <= 25) {
                        val now = calendar ?: Calendar.getInstance()
                        val year = now.get(Calendar.YEAR)
                        val month = now.get(Calendar.MONTH)

                        // After November 1st 2018, the apps are required to use 26 or higher
                        if (year > 2018 || month >= 10) {
                            val message =
                                "Google Play requires that apps target API level 26 or higher.\n"
                            val highest = context.client.highestKnownApiLevel
                            val label = "Update targetSdkVersion to $highest"
                            val fix = fix().name(label)
                                .replace()
                                .all()
                                .with(Integer.toString(highest))
                                .build()

                            // Don't report if already suppressed with EXPIRING

                            val alreadySuppressed = context.containsCommentSuppress() &&
                                    context.isSuppressedWithComment(
                                        valueCookie,
                                        EXPIRING_TARGET_SDK_VERSION
                                    )

                            if (!alreadySuppressed) {
                                report(
                                    context,
                                    valueCookie,
                                    EXPIRED_TARGET_SDK_VERSION,
                                    message,
                                    fix
                                )
                            }
                            warned = true
                        } else if (month >= 4 && year == 2018) {
                            // Start warning about this earlier - in May.
                            // (Check for 2018 here: no, we don't have a time machine, but let's
                            // allow developers to go back in time.)
                            val message = "" +
                                    "Google Play will soon require that apps target API " +
                                    "level 26 or higher. This will be required for new apps " +
                                    "in August 2018, and for updates to existing apps in " +
                                    "November 2018."
                            val highest = context.client.highestKnownApiLevel
                            val label = "Update targetSdkVersion to $highest"
                            val fix = fix().name(label)
                                .replace()
                                .all()
                                .with(Integer.toString(highest))
                                .build()
                            report(context, valueCookie, EXPIRING_TARGET_SDK_VERSION, message, fix)
                            warned = true
                        }
                    }

                    if (!warned) {
                        val message =
                            "Not targeting the latest versions of Android; compatibility " +
                                    "modes apply. Consider testing and updating this version. " +
                                    "Consult the android.os.Build.VERSION_CODES javadoc for " +
                                    "details."

                        val highest = context.client.highestKnownApiLevel
                        val label = "Update targetSdkVersion to $highest"
                        val fix = fix().name(label)
                            .replace()
                            .all()
                            .with(Integer.toString(highest))
                            .build()
                        report(context, valueCookie, TARGET_NEWER, message, fix)
                    }
                }
                if (version > 0) {
                    targetSdkVersion = version
                    checkTargetCompatibility(context)
                } else {
                    checkIntegerAsString(context, value, valueCookie)
                }
            } else if (property == "minSdkVersion") {
                val version = getSdkVersion(value)
                if (version > 0) {
                    minSdkVersion = version
                    checkMinSdkVersion(context, version, valueCookie)
                } else {
                    checkIntegerAsString(context, value, valueCookie)
                }
            }

            if (value.startsWith("0")) {
                checkOctal(context, value, valueCookie)
            }

            if (property == "versionName" ||
                property == "versionCode" && !isNonNegativeInteger(value) ||
                !isStringLiteral(value)
            ) {
                // Method call -- make sure it does not match one of the getters in the
                // configuration!
                if (value == "getVersionCode" || value == "getVersionName") {
                    val message = "Bad method name: pick a unique method name which does not " +
                            "conflict with the implicit getters for the defaultConfig " +
                            "properties. For example, try using the prefix compute- " +
                            "instead of get-."
                    report(context, valueCookie, GRADLE_GETTER, message)
                }
            } else if (property == "packageName") {
                val message = "Deprecated: Replace 'packageName' with 'applicationId'"
                val fix = fix()
                    .name("Replace 'packageName' with 'applicationId'", true)
                    .replace().text("packageName").with("applicationId").autoFix().build()
                report(context, context.getPropertyKeyCookie(valueCookie), DEPRECATED, message, fix)
            }
            if (property == "versionCode" &&
                context.isEnabled(HIGH_APP_VERSION_CODE) &&
                isNonNegativeInteger(value)
            ) {
                val version = getIntLiteralValue(value, -1)
                if (version >= VERSION_CODE_HIGH_THRESHOLD) {
                    val message =
                        "The 'versionCode' is very high and close to the max allowed value"
                    report(context, valueCookie, HIGH_APP_VERSION_CODE, message)
                }
            }
        } else if (property == "compileSdkVersion" && parent == "android") {
            var version = -1
            if (isStringLiteral(value)) {
                // Try to resolve values like "android-O"
                val hash = getStringLiteralValue(value)
                if (hash != null && !isNumberString(hash)) {
                    val platformVersion = AndroidTargetHash.getPlatformVersion(hash)
                    if (platformVersion != null) {
                        version = platformVersion.featureLevel
                    }
                }
            } else {
                version = getIntLiteralValue(value, -1)
            }
            if (version > 0) {
                compileSdkVersion = version
                compileSdkVersionCookie = valueCookie
                checkTargetCompatibility(context)
            } else {
                checkIntegerAsString(context, value, valueCookie)
            }
        } else if (property == "buildToolsVersion" && parent == "android") {
            val versionString = getStringLiteralValue(value)
            if (versionString != null) {
                val version = GradleVersion.tryParse(versionString)
                if (version != null) {
                    var recommended = getLatestBuildTools(context.client, version.major)
                    if (recommended != null && version < recommended) {
                        val message = "Old buildToolsVersion " +
                                version +
                                "; recommended version is " +
                                recommended +
                                " or later"
                        val fix = getUpdateDependencyFix(version.toString(), recommended.toString())
                        report(context, valueCookie, DEPENDENCY, message, fix)
                    }

                    // 23.0.0 shipped with a serious bugs which affects program correctness
                    // (such as https://code.google.com/p/android/issues/detail?id=183180)
                    // Make developers aware of this and suggest upgrading
                    if (version.major == 23 &&
                        version.minor == 0 &&
                        version.micro == 0 &&
                        context.isEnabled(COMPATIBILITY)
                    ) {
                        // This specific version is actually a preview version which should
                        // not be used (https://code.google.com/p/android/issues/detail?id=75292)
                        if (recommended == null || recommended.major < 23) {
                            // First planned release to fix this
                            recommended = GradleVersion(23, 0, 3)
                        }
                        val message =
                            "Build Tools `23.0.0` should not be used; " +
                                    "it has some known serious bugs. Use version `$recommended` " +
                                    "instead."
                        reportFatalCompatibilityIssue(context, valueCookie, message)
                    }
                }
            }
        } else if (parent == "plugins") {
            // KTS declaration for plugins
            if (property == "id") {
                val plugin = getStringLiteralValue(value)
                val isOldAppPlugin = OLD_APP_PLUGIN_ID == plugin
                if (isOldAppPlugin || OLD_LIB_PLUGIN_ID == plugin) {
                    val replaceWith = if (isOldAppPlugin) APP_PLUGIN_ID else LIB_PLUGIN_ID
                    val message = "'$plugin' is deprecated; use '$replaceWith' instead"
                    val fix = fix()
                        .sharedName("Replace plugin")
                        .replace().text(plugin).with(replaceWith).autoFix().build()
                    report(context, valueCookie, DEPRECATED, message, fix)
                }
            }
        } else if (parent == "dependencies") {
            if (value.startsWith("files('") && value.endsWith("')")) {
                val path = value.substring("files('".length, value.length - 2)
                if (path.contains("\\\\")) {
                    val message =
                        "Do not use Windows file separators in .gradle files; use / instead"
                    report(context, valueCookie, PATH, message)
                } else if (path.startsWith("/") || File(
                        path.replace(
                            '/',
                            File.separatorChar
                        )
                    ).isAbsolute
                ) {
                    val message = "Avoid using absolute paths in .gradle files"
                    report(context, valueCookie, PATH, message)
                }
            } else {
                var dependency = getStringLiteralValue(value)
                if (dependency == null) {
                    dependency = getNamedDependency(value)
                }
                // If the dependency is a GString (i.e. it uses Groovy variable substitution,
                // with a $variable_name syntax) then don't try to parse it.
                if (dependency != null) {
                    var gc = GradleCoordinate.parseCoordinateString(dependency)
                    var isResolved = false
                    if (gc != null && dependency.contains("$")) {
                        if (value.startsWith("'") &&
                            value.endsWith("'") &&
                            context.isEnabled(NOT_INTERPOLATED)
                        ) {
                            val message = "It looks like you are trying to substitute a " +
                                    "version variable, but using single quotes ('). For Groovy " +
                                    "string interpolation you must use double quotes (\")."
                            val fix = fix().name("Replace single quotes with double quotes")
                                .replace()
                                .text(value)
                                .with(
                                    "\"" +
                                            value.substring(1, value.length - 1) +
                                            "\""
                                )
                                .build()
                            report(context, statementCookie, NOT_INTERPOLATED, message, fix)
                        }

                        gc = resolveCoordinate(context, gc)
                        isResolved = true
                    }
                    if (gc != null) {
                        if (gc.acceptsGreaterRevisions()) {
                            val message = "Avoid using + in version numbers; can lead " +
                                    "to unpredictable and unrepeatable builds (" +
                                    dependency +
                                    ")"
                            val fix = fix().data(gc)
                            report(context, valueCookie, PLUS, message, fix)
                        }

                        // Check dependencies without the PSI read lock, because we
                        // may need to make network requests to retrieve version info.
                        context.driver.runLaterOutsideReadAction(Runnable {
                            checkDependency(context, gc, isResolved, valueCookie, statementCookie)
                        })
                    }
                }
            }
        } else if (property == "packageNameSuffix") {
            val message = "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"
            val fix = fix()
                .name("Replace 'packageNameSuffix' with 'applicationIdSuffix'", true)
                .replace().text("packageNameSuffix").with("applicationIdSuffix").autoFix().build()
            report(context, context.getPropertyKeyCookie(valueCookie), DEPRECATED, message, fix)
        } else if (property == "applicationIdSuffix") {
            val suffix = getStringLiteralValue(value)
            if (suffix != null && !suffix.startsWith(".")) {
                val message = "Application ID suffix should probably start with a \".\""
                report(context, valueCookie, PATH, message)
            }
        } else if (property == "minSdkVersion" &&
            parent == "dev" &&
            "21" == value &&
            // Don't flag this error from Gradle; users invoking lint from Gradle may
            // still want dev mode for command line usage
            LintClient.CLIENT_GRADLE != LintClient.clientName
        ) {
            report(
                context,
                statementCookie,
                DEV_MODE_OBSOLETE,
                "You no longer need a `dev` mode to enable multi-dexing during development, and this can break API version checks"
            )
        }
    }

    private fun checkMinSdkVersion(context: GradleContext, version: Int, valueCookie: Any) {
        if (version in 1..(LOWEST_ACTIVE_API - 1)) {
            val message =
                "The value of minSdkVersion is too low. It can be incremented " +
                        "without noticeably reducing the number of supported devices."

            val label = "Update minSdkVersion to $LOWEST_ACTIVE_API"
            val fix = fix().name(label)
                .replace()
                .text(Integer.toString(version))
                .with(Integer.toString(LOWEST_ACTIVE_API))
                .build()
            report(context, valueCookie, MIN_SDK_TOO_LOW, message, fix)
        }
    }

    private fun checkIntegerAsString(context: GradleContext, value: String, valueCookie: Any) {
        // When done developing with a preview platform you might be tempted to switch from
        //     compileSdkVersion 'android-G'
        // to
        //     compileSdkVersion '19'
        // but that won't work; it needs to be
        //     compileSdkVersion 19
        val string = getStringLiteralValue(value)
        if (isNumberString(string)) {
            val message =
                "Use an integer rather than a string here (replace $value with just $string)"
            val fix =
                fix().name("Replace with integer", true).replace().text(value).with(string).build()
            report(context, valueCookie, STRING_INTEGER, message, fix)
        }
    }

    override fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    ) {
        val plugin = namedArguments["plugin"]
        if (statement == "apply" && parent == null) {
            val isOldAppPlugin = OLD_APP_PLUGIN_ID == plugin
            if (isOldAppPlugin || OLD_LIB_PLUGIN_ID == plugin) {
                val replaceWith = if (isOldAppPlugin) APP_PLUGIN_ID else LIB_PLUGIN_ID
                val message = "'$plugin' is deprecated; use '$replaceWith' instead"
                val fix = fix()
                    .sharedName("Replace plugin")
                    .replace().text(plugin).with(replaceWith).autoFix().build()
                report(context, cookie, DEPRECATED, message, fix)
            }
        }
    }

    private fun checkTargetCompatibility(context: GradleContext) {
        if (compileSdkVersion > 0 && targetSdkVersion > 0 && targetSdkVersion > compileSdkVersion) {
            val message = "The compileSdkVersion (" +
                    compileSdkVersion +
                    ") should not be lower than the targetSdkVersion (" +
                    targetSdkVersion +
                    ")"
            val fix = fix().name("Set compileSdkVersion to $targetSdkVersion")
                .replace()
                .text(Integer.toString(compileSdkVersion))
                .with(Integer.toString(targetSdkVersion))
                .build()
            reportNonFatalCompatibilityIssue(context, compileSdkVersionCookie!!, message, fix)
        }
    }

    // Important: This is called without the PSI read lock, since it may make network requests.
    // Any interaction with PSI or issue reporting should be wrapped in a read action.
    private fun checkDependency(
        context: GradleContext,
        dependency: GradleCoordinate,
        isResolved: Boolean,
        cookie: Any,
        statementCookie: Any
    ) {
        val version = dependency.version
        val groupId = dependency.groupId
        val artifactId = dependency.artifactId
        val revision = dependency.revision
        if (version == null || groupId == null || artifactId == null) {
            return
        }
        var newerVersion: GradleVersion? = null

        val filter = getUpgradeVersionFilter(groupId, artifactId)

        when (groupId) {
            SUPPORT_LIB_GROUP_ID, "com.android.support.test" -> {
                // Check to make sure you have the Android support repository installed.
                val sdkHome = context.client.getSdkHome()
                val repository = SdkMavenRepository.ANDROID.getRepositoryLocation(
                    sdkHome, true, FileOpUtils.create()
                )
                if (repository != null) {
                    val max = MavenRepositories.getHighestInstalledVersionNumber(
                        groupId,
                        artifactId,
                        repository,
                        filter,
                        false,
                        FileOpUtils.create()
                    )
                    if (max != null &&
                        version < max &&
                        context.isEnabled(DEPENDENCY)
                    ) {
                        newerVersion = max
                    }
                }
            }

            GMS_GROUP_ID, FIREBASE_GROUP_ID, GOOGLE_SUPPORT_GROUP_ID, ANDROID_WEAR_GROUP_ID -> {
                // Play services

                checkPlayServices(context, dependency, version, revision, cookie)

                val sdkHome = context.client.getSdkHome()
                val repository = SdkMavenRepository.GOOGLE.getRepositoryLocation(
                    sdkHome, true, FileOpUtils.create()
                )
                if (repository != null) {
                    val max = MavenRepositories.getHighestInstalledVersionNumber(
                        groupId,
                        artifactId,
                        repository,
                        filter,
                        false,
                        FileOpUtils.create()
                    )
                    if (max != null &&
                        version < max &&
                        context.isEnabled(DEPENDENCY)
                    ) {
                        newerVersion = max
                    }
                }
            }

            "com.android.tools.build" -> {
                if ("gradle" == artifactId) {
                    if (checkGradlePluginDependency(context, dependency, cookie)) {
                        return
                    }

                    // If it's available in maven.google.com, fetch latest available version
                    newerVersion = GradleVersion.max(
                        version,
                        getGoogleMavenRepoVersion(context, dependency, filter)
                    )
                }
            }

            "com.google.guava" -> {
                // TODO: 24.0-android
                if ("guava" == artifactId) {
                    newerVersion = getNewerVersion(version, 21, 0)
                }
            }

            "com.google.code.gson" -> {
                if ("gson" == artifactId) {
                    newerVersion = getNewerVersion(version, 2, 8, 2)
                }
            }
            "org.apache.httpcomponents" -> {
                if ("httpclient" == artifactId) {
                    newerVersion = getNewerVersion(version, 4, 5, 5)
                }
            }
            "com.squareup.okhttp3" -> {
                if ("okhttp" == artifactId) {
                    newerVersion = getNewerVersion(version, 3, 10, 0)
                }
            }
            "com.github.bumptech.glide" -> {
                if ("glide" == artifactId) {
                    newerVersion = getNewerVersion(version, 3, 7, 0)
                }
            }
            "io.fabric.tools" -> {
                if ("gradle" == artifactId) {
                    val parsed = GradleVersion.tryParse(revision)
                    if (parsed != null && parsed < "1.21.6") {
                        val fix = getUpdateDependencyFix(revision, "1.22.1")
                        report(
                            context,
                            cookie,
                            DEPENDENCY,
                            "Use Fabric Gradle plugin version 1.21.6 or later to " +
                                "improve Instant Run performance (was $revision)",
                            fix
                        )
                    } else {
                        // From https://s3.amazonaws.com/fabric-artifacts/public/io/fabric/tools/gradle/maven-metadata.xml
                        newerVersion = getNewerVersion(version, GradleVersion(1, 25, 1))
                    }
                }
            }
            "com.bugsnag" -> {
                if ("bugsnag-android-gradle-plugin" == artifactId) {
                    if (!version.isAtLeast(2, 1, 2)) {
                        val fix = getUpdateDependencyFix(revision, "2.4.1")
                        report(
                            context,
                            cookie,
                            DEPENDENCY,
                            "Use BugSnag Gradle plugin version 2.1.2 or later to " +
                                "improve Instant Run performance (was $revision)",
                            fix
                        )
                    } else {
                        // From http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.bugsnag%22%20AND
                        // %20a%3A%22bugsnag-android-gradle-plugin%22
                        newerVersion = getNewerVersion(version, 3, 2, 5)
                    }
                }
            }
        }

        val blacklistedDeps = blacklisted[context.project]
        if (blacklistedDeps != null) {
            val path = blacklistedDeps.checkDependency(groupId, artifactId, true)
            if (path != null) {
                val message = getBlacklistedDependencyMessage(context, path)
                if (message != null) {
                    val fix = fix().name("Delete dependency").replace().all().build()
                    report(context, statementCookie, DUPLICATE_CLASSES, message, fix)
                }
            }
        }

        val sdkRegistry = getDeprecatedLibraryLookup(context.client)
        val deprecated = sdkRegistry.getVersionInfo(dependency)
        if (deprecated != null) {
            val prefix: String
            val issue: Issue
            if (deprecated.status == "insecure") {
                prefix = "This version is known to be insecure."
                issue = RISKY_LIBRARY
            } else {
                prefix = "This version is ${deprecated.status}."
                issue = DEPRECATED_LIBRARY
            }

            val suffix: String
            val fix: LintFix?
            val recommended = deprecated.recommended
            if (recommended != null) {
                suffix = " Consider switching to recommended version $recommended."
                fix = getUpdateDependencyFix(dependency.revision, recommended)
            } else {
                suffix = ""
                fix = null
            }

            val separatorDot =
                if (deprecated.message.isNotEmpty() && !deprecated.message.endsWith("."))
                    "."
                else
                    ""

            val message = "$prefix Details: ${deprecated.message}$separatorDot$suffix"
            report(context, statementCookie, issue, message, fix)
        } else {
            val recommended = sdkRegistry.getRecommendedVersion(dependency)
            if (recommended != null && (newerVersion == null || recommended > newerVersion)) {
                newerVersion = recommended
            }
        }

        // Network check for really up to date libraries? Only done in batch mode.
        var issue = DEPENDENCY
        if (context.scope.size > 1 && context.isEnabled(REMOTE_VERSION)) {
            val latest = getLatestVersionFromRemoteRepo(
                context.client, dependency, filter, dependency.isPreview
            )
            if (latest != null && version < latest) {
                newerVersion = latest
                issue = REMOTE_VERSION
            }
        }

        // Compare with what's in the Gradle cache.
        newerVersion = GradleVersion.max(newerVersion, findCachedNewerVersion(dependency, filter))

        // Compare with IDE's repository cache, if available.
        newerVersion = GradleVersion.max(
            newerVersion,
            context.client.getHighestKnownVersion(dependency, filter)
        )

        // If it's available in maven.google.com, fetch latest available version.
        newerVersion = GradleVersion.max(
            newerVersion, getGoogleMavenRepoVersion(context, dependency, filter)
        )

        if (groupId == SUPPORT_LIB_GROUP_ID || groupId == "com.android.support.test") {
            checkSupportLibraries(context, dependency, version, newerVersion, cookie)
        }

        if (newerVersion != null && newerVersion > version) {
            val versionString = newerVersion.toString()
            val message = getNewerVersionAvailableMessage(dependency, versionString)
            val fix = if (!isResolved) getUpdateDependencyFix(revision, versionString) else null
            report(context, cookie, issue, message, fix)
        }
    }

    /**
     * Returns a predicate that encapsulates version constraints for the given library, or null if
     * there are no constraints.
     */
    private fun getUpgradeVersionFilter(
        groupId: String,
        artifactId: String
    ): Predicate<GradleVersion>? {
        // Logic here has to match checkSupportLibraries method to avoid creating contradictory
        // warnings.
        if (isSupportLibraryDependentOnCompileSdk(groupId, artifactId)) {
            if (compileSdkVersion >= 18) {
                return Predicate { version -> version.major == compileSdkVersion }
            } else if (targetSdkVersion > 0) {
                return Predicate { version -> version.major >= targetSdkVersion }
            }
        }
        return null
    }

    /** Home in the Gradle cache for artifact caches */
    @Suppress("MemberVisibilityCanBePrivate") // overridden in the IDE
    protected fun getArtifactCacheHome(): File {
        return artifactCacheHome ?: run {
            val home = File(
                gradleUserHome,
                "caches" + File.separator + "modules-2" + File.separator + "files-2.1"
            )
            artifactCacheHome = home
            home
        }
    }

    private fun findCachedNewerVersion(
        dependency: GradleCoordinate,
        filter: Predicate<GradleVersion>?
    ): GradleVersion? {
        val versionDir = File(
            getArtifactCacheHome(),
            dependency.groupId + File.separator + dependency.artifactId
        )
        return if (versionDir.exists()) {
            MavenRepositories.getHighestVersion(
                versionDir,
                filter,
                MavenRepositories.isPreview(dependency),
                FileOpUtils.create()
            )
        } else null
    }

    private fun ensureTargetCompatibleWithO(
        context: GradleContext,
        version: GradleVersion?,
        cookie: Any,
        major: Int,
        minor: Int,
        micro: Int
    ) {
        if (version != null && !version.isAtLeast(major, minor, micro)) {
            var revision = GradleVersion(major, minor, micro)
            val newest = getNewerVersion(version, revision)
            if (newest != null) {
                revision = newest
            }

            val message = "Version must be at least $revision when targeting O"
            reportFatalCompatibilityIssue(context, cookie, message)
        }
    }

    // Important: This is called without the PSI read lock, since it may make network requests.
    // Any interaction with PSI or issue reporting should be wrapped in a read action.
    private fun checkGradlePluginDependency(
        context: GradleContext,
        dependency: GradleCoordinate,
        cookie: Any
    ): Boolean {
        val minimum = GradleCoordinate.parseCoordinateString(
            SdkConstants.GRADLE_PLUGIN_NAME + GRADLE_PLUGIN_MINIMUM_VERSION
        )
        if (minimum != null && COMPARE_PLUS_HIGHER.compare(dependency, minimum) < 0) {
            val recommended = GradleVersion.max(
                getGoogleMavenRepoVersion(context, minimum, null),
                GradleVersion.tryParse(GRADLE_PLUGIN_RECOMMENDED_VERSION)
            )
            val message = "You must use a newer version of the Android Gradle plugin. The " +
                    "minimum supported version is " +
                    GRADLE_PLUGIN_MINIMUM_VERSION +
                    " and the recommended version is " +
                    recommended
            report(context, cookie, GRADLE_PLUGIN_COMPATIBILITY, message)
            return true
        }
        return false
    }

    private fun checkSupportLibraries(
        context: GradleContext,
        dependency: GradleCoordinate,
        version: GradleVersion,
        newerVersion: GradleVersion?,
        cookie: Any
    ) {
        val groupId = dependency.groupId ?: return
        val artifactId = dependency.artifactId ?: return

        // For artifacts that follow the platform numbering scheme, check that it matches the SDK
        // versions used.
        if (isSupportLibraryDependentOnCompileSdk(groupId, artifactId)) {
            if (compileSdkVersion >= 18 &&
                dependency.majorVersion != compileSdkVersion &&
                dependency.majorVersion != GradleCoordinate.PLUS_REV_VALUE &&
                context.isEnabled(COMPATIBILITY)
            ) {
                var fix: LintFix? = null
                if (newerVersion != null) {
                    fix = fix().name("Replace with $newerVersion")
                        .replace()
                        .text(version.toString())
                        .with(newerVersion.toString())
                        .build()
                }
                val message = "This support library should not use a different version (" +
                        dependency.majorVersion +
                        ") than the `compileSdkVersion` (" +
                        compileSdkVersion +
                        ")"
                reportNonFatalCompatibilityIssue(context, cookie, message, fix)
            }
        }

        if (!mCheckedSupportLibs &&
            !artifactId.startsWith("multidex") &&
            !artifactId.startsWith("renderscript") &&
            artifactId != "support-annotations"
        ) {
            mCheckedSupportLibs = true
            if (!context.scope.contains(Scope.ALL_RESOURCE_FILES)) {
                // Incremental editing: try flagging them in this file!
                checkConsistentSupportLibraries(context, cookie)
            }
        }

        if ("appcompat-v7" == artifactId) {
            val supportLib26Beta = version.isAtLeast(26, 0, 0, "beta", 1, true)
            var compile26Beta = compileSdkVersion >= 26
            // It's not actually compileSdkVersion 26, it's using O revision 2 or higher
            if (compileSdkVersion == 26) {
                val buildTarget = context.project.buildTarget
                if (buildTarget != null && buildTarget.version.isPreview) {
                    compile26Beta = buildTarget.revision != 1
                }
            }

            if (supportLib26Beta &&
                !compile26Beta &&
                // We already flag problems when these aren't matching.
                compileSdkVersion == version.major
            ) {
                reportNonFatalCompatibilityIssue(
                    context,
                    cookie,
                    "When using a `compileSdkVersion` older than android-O revision 2, " +
                            "the support library version must be 26.0.0-alpha1 or lower " +
                            "(was $version)"
                )
            } else if (!supportLib26Beta && compile26Beta) {
                reportNonFatalCompatibilityIssue(
                    context,
                    cookie,
                    "When using a `compileSdkVersion` android-O revision 2 " +
                            "or higher, the support library version should be 26.0.0-beta1 " +
                            "or higher (was $version)"
                )
            }

            if (minSdkVersion >= 14 && compileSdkVersion >= 1 && compileSdkVersion < 21) {
                report(
                    context,
                    cookie,
                    DEPENDENCY,
                    "Using the appcompat library when minSdkVersion >= 14 and compileSdkVersion < 21 is not necessary"
                )
            }
        }
    }

    private fun checkPlayServices(
        context: GradleContext,
        dependency: GradleCoordinate,
        version: GradleVersion,
        revision: String,
        cookie: Any
    ) {
        val groupId = dependency.groupId ?: return
        val artifactId = dependency.artifactId ?: return

        // 5.2.08 is not supported; special case and warn about this
        if ("5.2.08" == revision && context.isEnabled(COMPATIBILITY)) {
            // This specific version is actually a preview version which should
            // not be used (https://code.google.com/p/android/issues/detail?id=75292)
            var maxVersion = "10.2.1"
            // Try to find a more recent available version, if one is available
            val sdkHome = context.client.getSdkHome()
            val repository = SdkMavenRepository.GOOGLE.getRepositoryLocation(
                sdkHome, true, FileOpUtils.create()
            )
            if (repository != null) {
                val max = MavenRepositories.getHighestInstalledVersion(
                    groupId, artifactId, repository, null, false, FileOpUtils.create()
                )
                if (max != null) {
                    if (COMPARE_PLUS_HIGHER.compare(dependency, max) < 0) {
                        maxVersion = max.revision
                    }
                }
            }
            val fix = getUpdateDependencyFix(revision, maxVersion)
            val message =
                "Version `5.2.08` should not be used; the app " +
                        "can not be published with this version. Use version `$maxVersion` " +
                        "instead."
            reportFatalCompatibilityIssue(context, cookie, message, fix)
        }

        if (context.isEnabled(BUNDLED_GMS) &&
            PLAY_SERVICES_V650.isSameArtifact(dependency) &&
            COMPARE_PLUS_HIGHER.compare(dependency, PLAY_SERVICES_V650) >= 0
        ) {
            // Play services 6.5.0 is the first version to allow un-bundling, so if the user is
            // at or above 6.5.0, recommend un-bundling
            val message = "Avoid using bundled version of Google Play services SDK."
            report(context, cookie, BUNDLED_GMS, message)
        }

        if (GMS_GROUP_ID == groupId && "play-services-appindexing" == artifactId) {
            val message = "Deprecated: Replace '" +
                    GMS_GROUP_ID +
                    ":play-services-appindexing:" +
                    revision +
                    "' with 'com.google.firebase:firebase-appindexing:10.0.0' or above. " +
                    "More info: http://firebase.google.com/docs/app-indexing/android/migrate"
            val fix = fix().name("Replace with Firebase")
                .replace()
                .text("$GMS_GROUP_ID:play-services-appindexing:$revision")
                .with("com.google.firebase:firebase-appindexing:10.2.1")
                .build()
            report(context, cookie, DEPRECATED, message, fix)
        }

        if (targetSdkVersion >= 26) {
            // When targeting O the following libraries must be using at least version 10.2.1
            // (or 0.6.0 of the jobdispatcher API)
            // com.google.android.gms:play-services-gcm:V
            // com.google.firebase:firebase-messaging:V
            if (GMS_GROUP_ID == groupId && "play-services-gcm" == artifactId) {
                ensureTargetCompatibleWithO(context, version, cookie, 10, 2, 1)
            } else if (FIREBASE_GROUP_ID == groupId && "firebase-messaging" == artifactId) {
                ensureTargetCompatibleWithO(context, version, cookie, 10, 2, 1)
            } else if ("firebase-jobdispatcher" == artifactId || "firebase-jobdispatcher-with-gcm-dep" == artifactId) {
                ensureTargetCompatibleWithO(context, version, cookie, 0, 6, 0)
            }
        }

        if (GMS_GROUP_ID == groupId || FIREBASE_GROUP_ID == groupId) {
            if (!mCheckedGms) {
                mCheckedGms = true
                // Incremental analysis only? If so, tie the check to
                // a specific GMS play dependency if only, such that it's highlighted
                // in the editor
                if (!context.scope.contains(Scope.ALL_RESOURCE_FILES)) {
                    // Incremental editing: try flagging them in this file!
                    checkConsistentPlayServices(context, cookie)
                }
            }
        } else {
            if (!mCheckedWearableLibs) {
                mCheckedWearableLibs = true
                // Incremental analysis only? If so, tie the check to
                // a specific GMS play dependency if only, such that it's highlighted
                // in the editor
                if (!context.scope.contains(Scope.ALL_RESOURCE_FILES)) {
                    // Incremental editing: try flagging them in this file!
                    checkConsistentWearableLibraries(context, cookie)
                }
            }
        }
    }

    private fun checkConsistentSupportLibraries(
        context: Context,
        cookie: Any?
    ) {
        checkConsistentLibraries(context, cookie, SUPPORT_LIB_GROUP_ID, null)
    }

    private fun checkConsistentPlayServices(context: Context, cookie: Any?) {
        checkConsistentLibraries(context, cookie, GMS_GROUP_ID, FIREBASE_GROUP_ID)
    }

    private fun checkConsistentWearableLibraries(
        context: Context,
        cookie: Any?
    ) {
        // Make sure we have both
        //   compile 'com.google.android.support:wearable:2.0.0-alpha3'
        //   provided 'com.google.android.wearable:wearable:2.0.0-alpha3'
        val project = context.mainProject
        if (!project.isGradleProject) {
            return
        }
        val supportVersions = HashSet<String>()
        val wearableVersions = HashSet<String>()
        for (library in getAndroidLibraries(project)) {
            val coordinates = library.resolvedCoordinates
            // Claims to be non-null but may not be after a failed gradle sync
            @Suppress("SENSELESS_COMPARISON")
            if (coordinates != null &&
                WEARABLE_ARTIFACT_ID == coordinates.artifactId &&
                GOOGLE_SUPPORT_GROUP_ID == coordinates.groupId
            ) {
                supportVersions.add(coordinates.version)
            }
        }
        for (library in getJavaLibraries(project)) {
            val coordinates = library.resolvedCoordinates
            // Claims to be non-null but may not be after a failed gradle sync
            @Suppress("SENSELESS_COMPARISON")
            if (coordinates != null &&
                WEARABLE_ARTIFACT_ID == coordinates.artifactId &&
                ANDROID_WEAR_GROUP_ID == coordinates.groupId
            ) {
                if (!library.isProvided) {
                    if (cookie != null) {
                        val message =
                            "This dependency should be marked as `provided`, not `compile`"

                        reportFatalCompatibilityIssue(context, cookie, message)
                    } else {
                        val message =
                            "The $ANDROID_WEAR_GROUP_ID:$WEARABLE_ARTIFACT_ID dependency should be marked as `provided`, not `compile`"
                        reportFatalCompatibilityIssue(
                            context, guessGradleLocation(context.project), message
                        )
                    }
                }
                wearableVersions.add(coordinates.version)
            }
        }

        if (!supportVersions.isEmpty()) {
            if (wearableVersions.isEmpty()) {
                val list = ArrayList(supportVersions)
                val first = Collections.min(list)
                val message =
                    "Project depends on $GOOGLE_SUPPORT_GROUP_ID:$WEARABLE_ARTIFACT_ID:$first, " +
                            "so it must also depend (as a provided dependency) on " +
                            "$ANDROID_WEAR_GROUP_ID:$WEARABLE_ARTIFACT_ID:$first"
                if (cookie != null) {
                    reportFatalCompatibilityIssue(context, cookie, message)
                } else {
                    reportFatalCompatibilityIssue(
                        context, guessGradleLocation(context.project), message
                    )
                }
            } else {
                // Check that they have the same versions
                if (supportVersions != wearableVersions) {
                    val sortedSupportVersions = ArrayList(supportVersions)
                    sortedSupportVersions.sort()
                    val supportedWearableVersions = ArrayList(wearableVersions)
                    supportedWearableVersions.sort()
                    val message = String.format(
                        "The wearable libraries for %1\$s and %2\$s " +
                                "must use **exactly** the same versions; found %3\$s " +
                                "and %4\$s",
                        GOOGLE_SUPPORT_GROUP_ID,
                        ANDROID_WEAR_GROUP_ID,
                        if (sortedSupportVersions.size == 1)
                            sortedSupportVersions[0]
                        else sortedSupportVersions.toString(),
                        if (supportedWearableVersions.size == 1)
                            supportedWearableVersions[0]
                        else supportedWearableVersions.toString()
                    )
                    if (cookie != null) {
                        reportFatalCompatibilityIssue(context, cookie, message)
                    } else {
                        reportFatalCompatibilityIssue(
                            context, guessGradleLocation(context.project), message
                        )
                    }
                }
            }
        }
    }

    private fun checkConsistentLibraries(
        context: Context,
        cookie: Any?,
        groupId: String,
        groupId2: String?
    ) {
        // Make sure we're using a consistent version across all play services libraries
        // (b/22709708)

        val project = context.mainProject
        val versionToCoordinate = ArrayListMultimap.create<String, MavenCoordinates>()
        val androidLibraries = getAndroidLibraries(project)
        for (library in androidLibraries) {
            val coordinates = library.resolvedCoordinates
            // Claims to be non-null but may not be after a failed gradle sync
            @Suppress("SENSELESS_COMPARISON")
            if (coordinates != null &&
                (coordinates.groupId == groupId || coordinates.groupId == groupId2) &&
                // Historically the multidex library ended up in the support package but
                // decided to do its own numbering (and isn't tied to the rest in terms
                // of implementation dependencies)
                !coordinates.artifactId.startsWith("multidex") &&
                // Renderscript has stated in b/37630182 that they are built and
                // distributed separate from the rest and do not have any version
                // dependencies
                !coordinates.artifactId.startsWith("renderscript") &&
                // Similarly firebase job dispatcher doesn't follow normal firebase version
                // numbering
                !coordinates.artifactId.startsWith("firebase-jobdispatcher")
            ) {
                versionToCoordinate.put(coordinates.version, coordinates)
            }
        }

        for (library in getJavaLibraries(project)) {
            val coordinates = library.resolvedCoordinates
            // Claims to be non-null but may not be after a failed gradle sync
            @Suppress("SENSELESS_COMPARISON")
            if (coordinates != null &&
                (coordinates.groupId == groupId || coordinates.groupId == groupId2) &&
                // The Android annotations library is decoupled from the rest and doesn't
                // need to be matched to the other exact support library versions
                coordinates.artifactId != "support-annotations"
            ) {
                versionToCoordinate.put(coordinates.version, coordinates)
            }
        }

        val versions = versionToCoordinate.keySet()
        if (versions.size > 1) {
            val sortedVersions = ArrayList(versions)
            sortedVersions.sortWith(Collections.reverseOrder())
            val c1 = findFirst(versionToCoordinate.get(sortedVersions[0]))
            val c2 = findFirst(versionToCoordinate.get(sortedVersions[1]))

            // For GMS, the synced version requirement ends at version 14
            if (groupId == GMS_GROUP_ID || groupId == FIREBASE_GROUP_ID) {
                // c2 is the smallest of all the versions; if it is at least 14,
                // they all are
                val version = GradleVersion.tryParse(c2.version)
                if (version != null && (version.major >= 14 || version.major == 0)) {
                    return
                }
            }

            // Not using toString because in the IDE, these are model proxies which display garbage output
            val example1 = c1.groupId + ":" + c1.artifactId + ":" + c1.version
            val example2 = c2.groupId + ":" + c2.artifactId + ":" + c2.version
            val groupDesc = if (GMS_GROUP_ID == groupId) "gms/firebase" else groupId
            var message = "All " +
                    groupDesc +
                    " libraries must use the exact same " +
                    "version specification (mixing versions can lead to runtime crashes). " +
                    "Found versions " +
                    Joiner.on(", ").join(sortedVersions) +
                    ". " +
                    "Examples include `" +
                    example1 +
                    "` and `" +
                    example2 +
                    "`"

            // Create an improved error message for a confusing scenario where you use
            // data binding and end up with conflicting versions:
            // https://code.google.com/p/android/issues/detail?id=229664
            for (library in androidLibraries) {
                val coordinates = library.resolvedCoordinates

                // Claims to be non-null but may not be after a failed gradle sync
                @Suppress("SENSELESS_COMPARISON")
                if (coordinates != null &&
                    coordinates.groupId == "com.android.databinding" &&
                    coordinates.artifactId == "library"
                ) {
                    for (dep in library.libraryDependencies) {
                        val c = dep.resolvedCoordinates

                        // Claims to be non-null but may not be after a failed gradle sync
                        @Suppress("SENSELESS_COMPARISON")
                        if (c != null &&
                            c.groupId == "com.android.support" &&
                            c.artifactId == "support-v4" &&
                            sortedVersions[0] != c.version
                        ) {
                            message += ". Note that this project is using data binding " +
                                    "(com.android.databinding:library:" +
                                    coordinates.version +
                                    ") which pulls in com.android.support:support-v4:" +
                                    c.version +
                                    ". You can try to work around this " +
                                    "by adding an explicit dependency on " +
                                    "com.android.support:support-v4:" +
                                    sortedVersions[0]
                            break
                        }
                    }
                    break
                }
            }

            if (cookie != null) {
                reportNonFatalCompatibilityIssue(context, cookie, message)
            } else {
                val projectDir = context.project.dir
                var location1 = guessGradleLocation(context.client, projectDir, example1)
                val location2 = guessGradleLocation(context.client, projectDir, example2)
                if (location1.start != null) {
                    if (location2.start != null) {
                        location1.secondary = location2
                    }
                } else {
                    if (location2.start == null) {
                        location1 = guessGradleLocation(
                            context.client,
                            projectDir,
                            // Probably using version variable
                            c1.groupId + ":" + c1.artifactId + ":"
                        )
                        if (location1.start == null) {
                            location1 = guessGradleLocation(
                                context.client,
                                projectDir,
                                // Probably using version variable
                                c2.groupId + ":" + c2.artifactId + ":"
                            )
                        }
                    } else {
                        location1 = location2
                    }
                }
                reportNonFatalCompatibilityIssue(context, location1, message)
            }
        }
    }

    override fun beforeCheckRootProject(context: Context) {
        val project = context.project
        blacklisted[project] = BlacklistedDeps(project)
    }

    override fun afterCheckRootProject(context: Context) {
        val project = context.project
        if (project === context.mainProject &&
            // Full analysis? Don't tie check to any specific Gradle DSL element
            context.scope.contains(Scope.ALL_RESOURCE_FILES)
        ) {
            checkConsistentPlayServices(context, null)
            checkConsistentSupportLibraries(context, null)
            checkConsistentWearableLibraries(context, null)
        }

        // Check for blacklisted dependencies
        checkBlacklistedDependencies(context, project)
    }

    /**
     * Report any blacklisted dependencies that weren't found in the build.gradle source file during
     * processing (we don't have accurate position info at this point)
     */
    private fun checkBlacklistedDependencies(context: Context, project: Project) {
        val blacklistedDeps = blacklisted[project] ?: return
        val dependencies = blacklistedDeps.getBlacklistedDependencies()
        if (!dependencies.isEmpty()) {
            for (path in dependencies) {
                val message = getBlacklistedDependencyMessage(context, path) ?: continue
                val projectDir = context.project.dir
                var coordinates = path[0].requestedCoordinates
                if (coordinates == null) {
                    coordinates = path[0].resolvedCoordinates
                }
                var location = guessGradleLocation(
                    context.client,
                    projectDir,
                    coordinates.groupId + ":" + coordinates.artifactId
                )
                if (location.start == null) {
                    location = guessGradleLocation(
                        context.client, projectDir, coordinates.artifactId
                    )
                }
                context.report(DUPLICATE_CLASSES, location, message)
            }
        }
        blacklisted.remove(project)
    }

    private fun report(
        context: Context,
        cookie: Any,
        issue: Issue,
        message: String,
        fix: LintFix? = null
    ) {
        // Some methods in GradleDetector are run without the PSI read lock in order
        // to accommodate network requests, so we grab the read lock here.
        context.client.runReadAction(Runnable {
            if (context.isEnabled(issue) && context is GradleContext) {
                // Suppressed?
                // Temporarily unconditionally checking for suppress comments in Gradle files
                // since Studio insists on an AndroidLint id prefix
                val checkComments = /*context.getClient().checkForSuppressComments() &&*/
                    context.containsCommentSuppress()
                if (checkComments && context.isSuppressedWithComment(cookie, issue)) {
                    return@Runnable
                }

                val location = context.getLocation(cookie)
                context.report(issue, location, message, fix)
            }
        })
    }

    /**
     * Normally, all warnings reported for a given issue will have the same severity, so it isn't
     * possible to have some of them reported as errors and others as warnings. And this is
     * intentional, since users should get to designate whether an issue is an error or a warning
     * (or ignored for that matter).
     *
     *
     * However, for [.COMPATIBILITY] we want to treat some issues as fatal (breaking the
     * build) but not others. To achieve this we tweak things a little bit. All compatibility issues
     * are now marked as fatal, and if we're *not* in the "fatal only" mode, all issues are reported
     * as before (with severity fatal, which has the same visual appearance in the IDE as the
     * previous severity, "error".) However, if we're in a "fatal-only" build, then we'll stop
     * reporting the issues that aren't meant to be treated as fatal. That's what this method does;
     * issues reported to it should always be reported as fatal. There is a corresponding method,
     * [.reportNonFatalCompatibilityIssue] which can be used to
     * report errors that shouldn't break the build; those are ignored in fatal-only mode.
     */
    private fun reportFatalCompatibilityIssue(
        context: Context,
        cookie: Any,
        message: String
    ) {
        report(context, cookie, COMPATIBILITY, message)
    }

    private fun reportFatalCompatibilityIssue(
        context: Context,
        cookie: Any,
        message: String,
        fix: LintFix?
    ) {
        report(context, cookie, COMPATIBILITY, message, fix)
    }

    /** See [.reportFatalCompatibilityIssue] for an explanation. */
    private fun reportNonFatalCompatibilityIssue(
        context: Context,
        cookie: Any,
        message: String,
        lintFix: LintFix? = null
    ) {
        if (context.driver.fatalOnlyMode) {
            return
        }

        report(context, cookie, COMPATIBILITY, message, lintFix)
    }

    private fun reportFatalCompatibilityIssue(
        context: Context,
        location: Location,
        message: String
    ) {
        // Some methods in GradleDetector are run without the PSI read lock in order
        // to accommodate network requests, so we grab the read lock here.
        context.client.runReadAction(Runnable {
            context.report(COMPATIBILITY, location, message)
        })
    }

    /** See [.reportFatalCompatibilityIssue] for an explanation. */
    private fun reportNonFatalCompatibilityIssue(
        context: Context,
        location: Location,
        message: String
    ) {
        if (context.driver.fatalOnlyMode) {
            return
        }

        // Some methods in GradleDetector are run without the PSI read lock in order
        // to accommodate network requests, so we grab the read lock here.
        context.client.runReadAction(Runnable {
            context.report(COMPATIBILITY, location, message)
        })
    }

    private fun getSdkVersion(value: String): Int {
        var version = 0
        if (isStringLiteral(value)) {
            val codeName = getStringLiteralValue(value)
            if (codeName != null) {
                if (isNumberString(codeName)) {
                    // Don't access numbered strings; should be literal numbers (lint will warn)
                    return -1
                }
                val androidVersion = SdkVersionInfo.getVersion(codeName, null)
                if (androidVersion != null) {
                    version = androidVersion.featureLevel
                }
            }
        } else {
            version = getIntLiteralValue(value, -1)
        }
        return version
    }

    private fun resolveCoordinate(
        context: GradleContext,
        gc: GradleCoordinate
    ): GradleCoordinate? {
        assert(gc.revision.contains("$")) { gc.revision }
        val project = context.project
        val variant = project.currentVariant
        if (variant != null) {
            val dependencies = variant.mainArtifact.dependencies
            for (library in dependencies.libraries) {
                val mc = library.resolvedCoordinates
                // Even though the method is annotated as non-null, this code can run
                // after a failed sync and there are observed scenarios where it returns
                // null in that ase

                // Claims to be non-null but may not be after a failed gradle sync
                @Suppress("SENSELESS_COMPARISON")
                if (mc != null &&
                    mc.groupId == gc.groupId &&
                    mc.artifactId == gc.artifactId
                ) {
                    val revisions = GradleCoordinate.parseRevisionNumber(mc.version)
                    if (!revisions.isEmpty()) {
                        return GradleCoordinate(
                            mc.groupId, mc.artifactId, revisions, null
                        )
                    }
                    break
                }
            }
        }

        return null
    }

    /** True if the given project uses the legacy http library */
    private fun usesLegacyHttpLibrary(project: Project): Boolean {
        val model = project.gradleProjectModel ?: return false
        for (path in model.bootClasspath) {
            if (path.endsWith("org.apache.http.legacy.jar")) {
                return true
            }
        }

        return false
    }

    private fun getUpdateDependencyFix(
        currentVersion: String,
        suggestedVersion: String
    ): LintFix {
        return LintFix.create()
            .name("Change to $suggestedVersion")
            .sharedName("Update versions")
            .replace()
            .text(currentVersion)
            .with(suggestedVersion)
            .autoFix()
            .build()
    }

    private fun getNewerVersionAvailableMessage(
        dependency: GradleCoordinate,
        version: String
    ): String {
        return "A newer version of " +
                dependency.groupId +
                ":" +
                dependency.artifactId +
                " than " +
                dependency.revision +
                " is available: " +
                version
    }

    /**
     * Checks if the library with the given `groupId` and `artifactId` has to match
     * compileSdkVersion.
     */
    private fun isSupportLibraryDependentOnCompileSdk(
        groupId: String,
        artifactId: String
    ): Boolean {
        return (SUPPORT_LIB_GROUP_ID == groupId &&
                !artifactId.startsWith("multidex") &&
                !artifactId.startsWith("renderscript") &&
                // Support annotation libraries work with any compileSdkVersion
                artifactId != "support-annotations")
    }

    private fun findFirst(coordinates: Collection<MavenCoordinates>): MavenCoordinates {
        return Collections.min(
            coordinates,
            { o1, o2 -> o1.toString().compareTo(o2.toString()) }
        )
    }

    private fun getBlacklistedDependencyMessage(
        context: Context,
        path: List<Library>
    ): String? {
        if (context.mainProject.minSdkVersion.apiLevel >= 23 && !usesLegacyHttpLibrary(context.mainProject)) {
            return null
        }

        val direct = path.size == 1
        val message: String
        val resolution = "Solutions include " +
                "finding newer versions or alternative libraries that don't have the " +
                "same problem (for example, for `httpclient` use `HttpUrlConnection` or " +
                "`okhttp` instead), or repackaging the library using something like " +
                "`jarjar`."
        if (direct) {
            message =
                    "`${path[0].resolvedCoordinates.artifactId}` defines classes that conflict with classes now provided by Android. $resolution"
        } else {
            val sb = StringBuilder()
            var first = true
            for (library in path) {
                if (first) {
                    first = false
                } else {
                    sb.append(" \u2192 ") // right arrow
                }
                val coordinates = library.resolvedCoordinates
                sb.append(coordinates.groupId)
                sb.append(':')
                sb.append(coordinates.artifactId)
            }
            sb.append(") ")
            val chain = sb.toString()
            message = "`${path[0].resolvedCoordinates.artifactId}` depends on a library " +
                    "(${path[path.size - 1].resolvedCoordinates.artifactId}) which defines " +
                    "classes that conflict with classes now provided by Android. $resolution " +
                    "Dependency chain: $chain"
        }
        return message
    }

    private fun getNewerVersion(
        version1: GradleVersion,
        major: Int,
        minor: Int,
        micro: Int
    ): GradleVersion? {
        return if (!version1.isAtLeast(major, minor, micro)) {
            GradleVersion(major, minor, micro)
        } else null
    }

    private fun getNewerVersion(
        version1: GradleVersion,
        major: Int,
        minor: Int
    ): GradleVersion? {
        return if (!version1.isAtLeast(major, minor, 0)) {
            GradleVersion(major, minor)
        } else null
    }

    private fun getNewerVersion(
        version1: GradleVersion,
        version2: GradleVersion
    ): GradleVersion? {
        return if (version1 < version2) {
            version2
        } else null
    }

    companion object {
        /** Calendar to use to look up the current time (used by tests to set specific time */
        var calendar: Calendar? = null

        private val IMPLEMENTATION = Implementation(GradleDetector::class.java, Scope.GRADLE_SCOPE)

        /** Obsolete dependencies */
        @JvmField
        val DEPENDENCY = Issue.create(
            id = "GradleDependency",
            briefDescription = "Obsolete Gradle Dependency",
            explanation = """
                This detector looks for usages of libraries where the version you are using \
                is not the current stable release. Using older versions is fine, and there \
                are cases where you deliberately want to stick with an older version. \
                However, you may simply not be aware that a more recent version is \
                available, and that is what this lint check helps find.""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** Deprecated Gradle constructs */
        @JvmField
        val DEPRECATED = Issue.create(
            id = "GradleDeprecated",
            briefDescription = "Deprecated Gradle Construct",
            explanation = """
                This detector looks for deprecated Gradle constructs which currently work \
                but will likely stop working in a future update.""",
            category = Category.CORRECTNESS,
            priority = 6,
            androidSpecific = true,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** Incompatible Android Gradle plugin */
        @JvmField
        val GRADLE_PLUGIN_COMPATIBILITY = Issue.create(
            id = "GradlePluginVersion",
            briefDescription = "Incompatible Android Gradle Plugin",
            explanation = """
                Not all versions of the Android Gradle plugin are compatible with all \
                versions of the SDK. If you update your tools, or if you are trying to \
                open a project that was built with an old version of the tools, you may \
                need to update your plugin version number.""",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Invalid or dangerous paths */
        @JvmField
        val PATH = Issue.create(
            id = "GradlePath",
            briefDescription = "Gradle Path Issues",
            explanation = """
                Gradle build scripts are meant to be cross platform, so file paths use \
                Unix-style path separators (a forward slash) rather than Windows path \
                separators (a backslash). Similarly, to keep projects portable and \
                repeatable, avoid using absolute paths on the system; keep files within \
                the project instead. To share code between projects, consider creating \
                an android-library and an AAR dependency""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** Constructs the IDE support struggles with */
        @JvmField
        val IDE_SUPPORT = Issue.create(
            id = "GradleIdeError",
            briefDescription = "Gradle IDE Support Issues",
            explanation = """
                Gradle is highly flexible, and there are things you can do in Gradle \
                files which can make it hard or impossible for IDEs to properly handle \
                the project. This lint check looks for constructs that potentially \
                break IDE support.""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        /** Using + in versions */
        @JvmField
        val PLUS = Issue.create(
            id = "GradleDynamicVersion",
            briefDescription = "Gradle Dynamic Version",
            explanation = """
                Using `+` in dependencies lets you automatically pick up the latest \
                available version rather than a specific, named version. However, \
                this is not recommended; your builds are not repeatable; you may have \
                tested with a slightly different version than what the build server \
                used. (Using a dynamic version as the major version number is more \
                problematic than using it in the minor version position.)""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** Accidentally calling a getter instead of your own methods */
        @JvmField
        val GRADLE_GETTER = Issue.create(
            id = "GradleGetter",
            briefDescription = "Gradle Implicit Getter Call",
            explanation = """
                Gradle will let you replace specific constants in your build scripts \
                with method calls, so you can for example dynamically compute a version \
                string based on your current version control revision number, rather \
                than hardcoding a number.

                When computing a version name, it's tempting to for example call the \
                method to do that `getVersionName`. However, when you put that method \
                call inside the `defaultConfig` block, you will actually be calling the \
                Groovy getter for the `versionName` property instead. Therefore, you \
                need to name your method something which does not conflict with the \
                existing implicit getters. Consider using `compute` as a prefix instead \
                of `get`.""",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Using incompatible versions */
        @JvmField
        val COMPATIBILITY = Issue.create(
            id = "GradleCompatible",
            briefDescription = "Incompatible Gradle Versions",
            explanation = """
                There are some combinations of libraries, or tools and libraries, that \
                are incompatible, or can lead to bugs. One such incompatibility is \
                compiling with a version of the Android support libraries that is not \
                the latest version (or in particular, a version lower than your \
                `targetSdkVersion`).""",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Using a string where an integer is expected */
        @JvmField
        val STRING_INTEGER = Issue.create(
            id = "StringShouldBeInt",
            briefDescription = "String should be int",
            explanation = """
                The properties `compileSdkVersion`, `minSdkVersion` and `targetSdkVersion` \
                are usually numbers, but can be strings when you are using an add-on (in \
                the case of `compileSdkVersion`) or a preview platform (for the other two \
                properties).

                However, you can not use a number as a string (e.g. "19" instead of 19); \
                that will result in a platform not found error message at build/sync \
                time.""",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Attempting to use substitution with single quotes */
        @JvmField
        val NOT_INTERPOLATED = Issue.create(
            id = "NotInterpolated",
            briefDescription = "Incorrect Interpolation",
            explanation = """
                To insert the value of a variable, you can use `${"$"}{variable}` inside a \
                string literal, but **only** if you are using double quotes!""",
            moreInfo = "http://www.groovy-lang.org/syntax.html#_string_interpolation",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        /** A newer version is available on a remote server */
        @JvmField
        val REMOTE_VERSION = Issue.create(
            id = "NewerVersionAvailable",
            briefDescription = "Newer Library Versions Available",
            explanation = """
                This detector checks with a central repository to see if there are newer \
                versions available for the dependencies used by this project. This is \
                similar to the `GradleDependency` check, which checks for newer versions \
                available in the Android SDK tools and libraries, but this works with any \
                MavenCentral dependency, and connects to the library every time, which \
                makes it more flexible but also **much** slower.""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            enabledByDefault = false
        )

        /** The API version is set too low. */
        @JvmField
        val MIN_SDK_TOO_LOW = Issue.create(
            id = "MinSdkTooLow",
            briefDescription = "API Version Too Low",
            explanation = """
                The value of the `minSdkVersion` property is too low and can be \
                incremented without noticeably reducing the number of supported \
                devices.""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            androidSpecific = true,
            enabledByDefault = false
        )

        /** Accidentally using octal numbers */
        @JvmField
        val ACCIDENTAL_OCTAL = Issue.create(
            id = "AccidentalOctal",
            briefDescription = "Accidental Octal",
            explanation = """
                In Groovy, an integer literal that starts with a leading 0 will be \
                interpreted as an octal number. That is usually (always?) an accident \
                and can lead to subtle bugs, for example when used in the `versionCode` \
                of an app.""",
            category = Category.CORRECTNESS,
            priority = 2,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val BUNDLED_GMS = Issue.create(
            id = "UseOfBundledGooglePlayServices",
            briefDescription = "Use of bundled version of Google Play services",
            explanation = """
                Google Play services SDK's can be selectively included, which enables a \
                smaller APK size. Consider declaring dependencies on individual Google \
                Play services SDK's. If you are using Firebase API's \
                (http://firebase.google.com/docs/android/setup), Android Studio's \
                Tools → Firebase assistant window can automatically add just the \
                dependencies needed for each feature.""",
            moreInfo = "http://developers.google.com/android/guides/setup#split",
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Using a versionCode that is very high */
        @JvmField
        val HIGH_APP_VERSION_CODE = Issue.create(
            id = "HighAppVersionCode",
            briefDescription = "VersionCode too high",
            explanation = """
                The declared `versionCode` is an Integer. Ensure that the version number is \
                not close to the limit. It is recommended to monotonically increase this \
                number each minor or major release of the app. Note that updating an app \
                with a versionCode over `Integer.MAX_VALUE` is not possible.""",
            moreInfo = "https://developer.android.com/studio/publish/versioning.html",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Dev mode is no longer relevant */
        @JvmField
        val DEV_MODE_OBSOLETE = Issue.create(
            id = "DevModeObsolete",
            briefDescription = "Dev Mode Obsolete",
            explanation = """
                In the past, our documentation recommended creating a `dev` product flavor \
                with has a minSdkVersion of 21, in order to enable multidexing to speed up \
                builds significantly during development.

                That workaround is no longer necessary, and it has some serious downsides, \
                such as breaking API access checking (since the true `minSdkVersion` is no \
                longer known).

                In recent versions of the IDE and the Gradle plugin, the IDE automatically \
                passes the API level of the connected device used for deployment, and if \
                that device is at least API 21, then multidexing is automatically turned \
                on, meaning that you get the same speed benefits as the `dev` product \
                flavor but without the downsides.""",
            category = Category.PERFORMANCE,
            priority = 2,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Duplicate HTTP classes */
        @JvmField
        val DUPLICATE_CLASSES = Issue.create(
            id = "DuplicatePlatformClasses",
            briefDescription = "Duplicate Platform Classes",
            explanation = """
                There are a number of libraries that duplicate not just functionality \
                of the Android platform but using the exact same class names as the ones \
                provided in Android -- for example the apache http classes. This can \
                lead to unexpected crashes.

                To solve this, you need to either find a newer version of the library \
                which no longer has this problem, or to repackage the library (and all \
                of its dependencies) using something like the `jarjar` tool, or finally, \
                rewriting the code to use different APIs (for example, for http code, \
                consider using `HttpUrlConnection` or a library like `okhttp`).""",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** targetSdkVersion about to expiry */
        @JvmField
        val EXPIRING_TARGET_SDK_VERSION = Issue.create(
            id = "ExpiringTargetSdkVersion",
            briefDescription = "TargetSdkVersion Soon Expiring",
            explanation = """
                In the second half of 2018, Google Play will require that new apps and app \
                updates target API level 26 or higher. This will be required for new apps in \
                August 2018, and for updates to existing apps in November 2018.

                Configuring your app to target a recent API level ensures that users benefit \
                from significant security and performance improvements, while still allowing \
                your app to run on older Android versions (down to the `minSdkVersion`).

                This lint check starts warning you some months **before** these changes go \
                into effect if your `targetSdkVersion` is 25 or lower. This is intended to \
                give you a heads up to update your app, since depending on your current \
                `targetSdkVersion` the work can be nontrivial.

                To update your `targetSdkVersion`, follow the steps from \
                "Meeting Google Play requirements for target API level",
                https://developer.android.com/distribute/best-practices/develop/target-sdk.html
                """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )
            .addMoreInfo("https://support.google.com/googleplay/android-developer/answer/113469#targetsdk")
            .addMoreInfo("https://developer.android.com/distribute/best-practices/develop/target-sdk.html")

        /** targetSdkVersion no longer supported */
        @JvmField
        val EXPIRED_TARGET_SDK_VERSION = Issue.create(
            id = "ExpiredTargetSdkVersion",
            briefDescription = "TargetSdkVersion No Longer Supported",
            moreInfo = "https://support.google.com/googleplay/android-developer/answer/113469#targetsdk",
            explanation = """
                As of the second half of 2018, Google Play requires that new apps and app \
                updates target API level 26 or higher.

                Configuring your app to target a recent API level ensures that users benefit \
                from significant security and performance improvements, while still allowing \
                your app to run on older Android versions (down to the `minSdkVersion`).

                To update your `targetSdkVersion`, follow the steps from \
                "Meeting Google Play requirements for target API level",
                https://developer.android.com/distribute/best-practices/develop/target-sdk.html
                """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.FATAL,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )
            .addMoreInfo("https://support.google.com/googleplay/android-developer/answer/113469#targetsdk")
            .addMoreInfo("https://developer.android.com/distribute/best-practices/develop/target-sdk.html")

        /** Using a deprecated library */
        @JvmField
        val DEPRECATED_LIBRARY = Issue.create(
            id = "OutdatedLibrary",
            briefDescription = "Outdated Library",
            explanation = """
                Your app is using an outdated version of a library. This may cause violations \
                of Google Play policies (see https://play.google.com/about/monetization-ads/ads/) \
                and/or may affect your app’s visibility on the Play Store.

                Please try updating your app with an updated version of this library, or remove \
                it from your app.
                """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Using a vulnerable library */
        @JvmField
        val RISKY_LIBRARY = Issue.create(
            id = "RiskyLibrary",
            briefDescription = "Libraries with Privacy or Security Risks",
            explanation = """
                Your app is using a version of a library that has been identified by \
                the library developer as a potential source of privacy and/or security risks. \
                This may be a violation of Google Play policies (see \
                https://play.google.com/about/monetization-ads/ads/) and/or affect your app’s \
                visibility on the Play Store.

                When available, the individual error messages from lint will include details \
                about the reasons for this advisory.

                Please try updating your app with an updated version of this library, or remove \
                it from your app.
            """,
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** The Gradle plugin ID for Android applications */
        const val APP_PLUGIN_ID = "com.android.application"
        /** The Gradle plugin ID for Android libraries */
        const val LIB_PLUGIN_ID = "com.android.library"

        /** Previous plugin id for applications */
        const val OLD_APP_PLUGIN_ID = "android"
        /** Previous plugin id for libraries */
        const val OLD_LIB_PLUGIN_ID = "android-library"

        /** Group ID for GMS */
        const val GMS_GROUP_ID = "com.google.android.gms"

        const val FIREBASE_GROUP_ID = "com.google.firebase"
        const val GOOGLE_SUPPORT_GROUP_ID = "com.google.android.support"
        const val ANDROID_WEAR_GROUP_ID = "com.google.android.wearable"
        private const val WEARABLE_ARTIFACT_ID = "wearable"

        private val PLAY_SERVICES_V650 =
            GradleCoordinate.parseCoordinateString("$GMS_GROUP_ID:play-services:6.5.0")!!

        /**
         * Threshold to consider a versionCode very high and issue a warning.
         * https://developer.android.com/studio/publish/versioning.html indicates that the highest value
         * accepted by Google Play is 2100000000
         */
        private const val VERSION_CODE_HIGH_THRESHOLD = 2000000000

        /** TODO: Cache these results somewhere! */
        @JvmStatic
        fun getLatestVersionFromRemoteRepo(
            client: LintClient,
            dependency: GradleCoordinate,
            filter: Predicate<GradleVersion>?,
            allowPreview: Boolean
        ): GradleVersion? {
            val groupId = dependency.groupId
            val artifactId = dependency.artifactId
            if (groupId == null || artifactId == null) {
                return null
            }
            val query = StringBuilder()
            val encoding = UTF_8.name()
            try {
                query.append("http://search.maven.org/solrsearch/select?q=g:%22")
                query.append(URLEncoder.encode(groupId, encoding))
                query.append("%22+AND+a:%22")
                query.append(URLEncoder.encode(artifactId, encoding))
            } catch (e: UnsupportedEncodingException) {
                return null
            }

            query.append("%22&core=gav")
            if (filter == null && allowPreview) {
                query.append("&rows=1")
            }
            query.append("&wt=json")

            val response: String?
            try {
                response = readUrlDataAsString(client, query.toString(), 20000)
                if (response == null) {
                    return null
                }
            } catch (e: IOException) {
                client.log(
                    null,
                    "Could not connect to maven central to look up the latest " + "available version for %1\$s",
                    dependency
                )
                return null
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

            // Look for version info:  This is just a cheap skim of the above JSON results.
            var index = response.indexOf("\"response\"")
            while (index != -1) {
                index = response.indexOf("\"v\":", index)
                if (index != -1) {
                    index += 4
                    val start = response.indexOf('"', index) + 1
                    val end = response.indexOf('"', start + 1)
                    if (start in 0..(end - 1)) {
                        val substring = response.substring(start, end)
                        val revision = GradleVersion.tryParse(substring)
                        if (revision != null) {
                            // Guava unfortunately put "-jre" and "-android" in the version number
                            // instead of using a different artifact name; this turns off maven
                            // semantic versioning. Special case this.
                            val preview = revision.isPreview && !substring.endsWith("-android")
                            if ((allowPreview || !preview) && (filter == null || filter.test(
                                    revision
                                ))
                            ) {
                                return revision
                            }
                        }
                    }
                }
            }

            return null
        }

        @JvmStatic
        fun getCompileDependencies(project: Project): Dependencies? {
            if (!project.isGradleProject) {
                return null
            }
            val variant = project.currentVariant ?: return null

            val artifact = variant.mainArtifact
            return artifact.dependencies
        }

        fun getAndroidLibraries(project: Project): Collection<AndroidLibrary> {
            val compileDependencies = getCompileDependencies(project) ?: return emptyList()
            val allLibraries = HashSet<AndroidLibrary>()
            addIndirectAndroidLibraries(compileDependencies.libraries, allLibraries)
            return allLibraries
        }

        fun getJavaLibraries(project: Project): Collection<JavaLibrary> {
            val compileDependencies = getCompileDependencies(project) ?: return emptyList()
            val allLibraries = HashSet<JavaLibrary>()
            addIndirectJavaLibraries(compileDependencies.javaLibraries, allLibraries)
            return allLibraries
        }

        private fun addIndirectAndroidLibraries(
            libraries: Collection<AndroidLibrary>,
            result: MutableSet<AndroidLibrary>
        ) {
            for (library in libraries) {
                if (!result.contains(library)) {
                    result.add(library)
                    addIndirectAndroidLibraries(library.libraryDependencies, result)
                }
            }
        }

        private fun addIndirectJavaLibraries(
            libraries: Collection<JavaLibrary>,
            result: MutableSet<JavaLibrary>
        ) {
            for (library in libraries) {
                if (!result.contains(library)) {
                    result.add(library)
                    addIndirectJavaLibraries(library.dependencies, result)
                }
            }
        }

        // Convert a long-hand dependency, like
        //    group: 'com.android.support', name: 'support-v4', version: '21.0.+'
        // into an equivalent short-hand dependency, like
        //   com.android.support:support-v4:21.0.+
        @JvmStatic
        fun getNamedDependency(expression: String): String? {
            // if (value.startsWith("group: 'com.android.support', name: 'support-v4', version: '21.0.+'"))
            if (expression.indexOf(',') != -1 && expression.contains("version:")) {
                var artifact: String? = null
                var group: String? = null
                var version: String? = null
                val splitter = Splitter.on(',').omitEmptyStrings().trimResults()
                for (property in splitter.split(expression)) {
                    val colon = property.indexOf(':')
                    if (colon == -1) {
                        return null
                    }
                    var quote = '\''
                    var valueStart = property.indexOf(quote, colon + 1)
                    if (valueStart == -1) {
                        quote = '"'
                        valueStart = property.indexOf(quote, colon + 1)
                    }
                    if (valueStart == -1) {
                        // For example, "transitive: false"
                        continue
                    }
                    valueStart++
                    val valueEnd = property.indexOf(quote, valueStart)
                    if (valueEnd == -1) {
                        return null
                    }
                    val value = property.substring(valueStart, valueEnd)
                    when {
                        property.startsWith("group:") -> group = value
                        property.startsWith("name:") -> artifact = value
                        property.startsWith("version:") -> version = value
                    }
                }

                if (artifact != null && group != null && version != null) {
                    return group + ':'.toString() + artifact + ':'.toString() + version
                }
            }

            return null
        }

        private var majorBuildTools: Int = 0
        private var latestBuildTools: GradleVersion? = null

        /**
         * Returns the latest build tools installed for the given major version. We just cache this
         * once; we don't need to be accurate in the sense that if the user opens the SDK manager and
         * installs a more recent version, we capture this in the same IDE session.
         *
         * @param client the associated client
         * @param major the major version of build tools to look up (e.g. typically 18, 19, ...)
         * @return the corresponding highest known revision
         */
        private fun getLatestBuildTools(client: LintClient, major: Int): GradleVersion? {
            if (major != majorBuildTools) {
                majorBuildTools = major

                val revisions = ArrayList<GradleVersion>()
                when (major) {
                    267 -> revisions.add(GradleVersion(27, 0, 3))
                    26 -> revisions.add(GradleVersion(26, 0, 3))
                    25 -> revisions.add(GradleVersion(25, 0, 3))
                    24 -> revisions.add(GradleVersion(24, 0, 3))
                    23 -> revisions.add(GradleVersion(23, 0, 3))
                    22 -> revisions.add(GradleVersion(22, 0, 1))
                    21 -> revisions.add(GradleVersion(21, 1, 2))
                    20 -> revisions.add(GradleVersion(20, 0))
                    19 -> revisions.add(GradleVersion(19, 1))
                    18 -> revisions.add(GradleVersion(18, 1, 1))
                }

                // The above versions can go stale.
                // Check if a more recent one is installed. (The above are still useful for
                // people who haven't updated with the SDK manager recently.)
                val sdkHome = client.getSdkHome()
                if (sdkHome != null) {
                    val dirs = File(sdkHome, FD_BUILD_TOOLS).listFiles()
                    if (dirs != null) {
                        for (dir in dirs) {
                            val name = dir.name
                            if (!dir.isDirectory || !Character.isDigit(name[0])) {
                                continue
                            }
                            val v = GradleVersion.tryParse(name)
                            if (v != null && v.major == major) {
                                revisions.add(v)
                            }
                        }
                    }
                }

                if (!revisions.isEmpty()) {
                    latestBuildTools = Collections.max(revisions)
                }
            }

            return latestBuildTools
        }

        @VisibleForTesting
        @Suppress("unused")
        @TestOnly
        @JvmStatic
        fun cleanUp() {
            googleMavenRepository = null
            deprecatedSdkRegistry = null
        }

        private var googleMavenRepository: GoogleMavenRepository? = null
        private var deprecatedSdkRegistry: DeprecatedSdkRegistry? = null

        private fun getGoogleMavenRepoVersion(
            context: GradleContext,
            dependency: GradleCoordinate,
            filter: Predicate<GradleVersion>?
        ): GradleVersion? {
            val repository = getGoogleMavenRepository(context.client)
            return repository.findVersion(dependency, filter, dependency.isPreview)
        }

        private fun getGoogleMavenRepository(client: LintClient): GoogleMavenRepository {
            return synchronized(GradleDetector::class.java) {
                googleMavenRepository ?: run {
                    val cacheDir = client.getCacheDir(MAVEN_GOOGLE_CACHE_DIR_KEY, true)
                    val repository = object : GoogleMavenRepository(cacheDir) {

                        public override fun readUrlData(url: String, timeout: Int): ByteArray? =
                            readUrlData(client, url, timeout)

                        public override fun error(throwable: Throwable, message: String?) =
                            client.log(throwable, message)
                    }

                    googleMavenRepository = repository
                    repository
                }
            }
        }

        private fun getDeprecatedLibraryLookup(client: LintClient): DeprecatedSdkRegistry {
            return synchronized(GradleDetector::class.java) {
                deprecatedSdkRegistry ?: run {
                    val cacheDir = client.getCacheDir(DEPRECATED_SDK_CACHE_DIR_KEY, true)
                    val repository = object : DeprecatedSdkRegistry(cacheDir) {

                        public override fun readUrlData(url: String, timeout: Int) =
                            readUrlData(client, url, timeout)

                        public override fun error(throwable: Throwable, message: String?) =
                            client.log(throwable, message)
                    }

                    deprecatedSdkRegistry = repository
                    repository
                }
            }
        }
    }
}
