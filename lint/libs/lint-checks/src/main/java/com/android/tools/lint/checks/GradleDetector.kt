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
import com.android.SdkConstants.ANDROIDX_PKG_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.FD_BUILD_TOOLS
import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION
import com.android.SdkConstants.SUPPORT_LIB_GROUP_ID
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.MavenRepositories
import com.android.io.CancellableFileIo
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.lint.checks.ManifestDetector.Companion.TARGET_NEWER
import com.android.tools.lint.client.api.IssueRegistry
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
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.getLanguageLevel
import com.android.tools.lint.detector.api.guessGradleLocation
import com.android.tools.lint.detector.api.isNumberString
import com.android.tools.lint.detector.api.readUrlData
import com.android.tools.lint.detector.api.readUrlDataAsString
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.tools.lint.model.LintModelModuleType
import com.android.utils.appendCapitalized
import com.android.utils.iterator
import com.android.utils.usLocaleCapitalize
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
import com.intellij.pom.java.LanguageLevel.JDK_1_7
import com.intellij.pom.java.LanguageLevel.JDK_1_8
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.function.Predicate
import kotlin.text.Charsets.UTF_8

/** Checks Gradle files for potential errors. */
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
     * If incrementally editing a single build.gradle file, tracks
     * whether we've already transitively checked GMS versions such
     * that we don't flag the same error on every single dependency
     * declaration.
     */
    private var mCheckedGms: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks
     * whether we've already transitively checked support library
     * versions such that we don't flag the same error on every single
     * dependency declaration.
     */
    private var mCheckedSupportLibs: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks
     * whether we've already transitively checked wearable library
     * versions such that we don't flag the same error on every single
     * dependency declaration.
     */
    private var mCheckedWearableLibs: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks
     * whether we've already applied kotlin-android plugin.
     */
    private var mAppliedKotlinAndroidPlugin: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks
     * whether we've already applied kotlin-kapt plugin.
     */
    private var mAppliedKotlinKaptPlugin: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks
     * whether we have applied a java plugin (e.g. application,
     * java-library)
     */
    private var mAppliedJavaPlugin: Boolean = false

    data class JavaPluginInfo(
        val cookie: Any
    )

    private var mJavaPluginInfo: JavaPluginInfo? = null

    private var mDeclaredSourceCompatibility: Boolean = false
    private var mDeclaredTargetCompatibility: Boolean = false

    /**
     * If incrementally editing a single build.gradle file, tracks
     * whether we have declared the google maven repository in the
     * buildscript block.
     */
    private var mDeclaredGoogleMavenRepository: Boolean = false

    data class AgpVersionCheckInfo(
        val newerVersion: GradleVersion,
        val newerVersionIsSafe: Boolean,
        val safeReplacement: GradleVersion?,
        val dependency: GradleCoordinate,
        val isResolved: Boolean,
        val cookie: Any
    )

    /**
     * Stores information for a check of the Android gradle plugin
     * dependency version.
     */
    private var agpVersionCheckInfo: AgpVersionCheckInfo? = null

    private val blockedDependencies = HashMap<Project, BlockedDependencies>()

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

    /**
     * Called with for example "android", "defaultConfig",
     * "minSdkVersion", "7"
     */
    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    ) {
        if (parent == "defaultConfig") {
            if (property == "targetSdkVersion") {
                val version = getSdkVersion(value)
                if (version > 0 && version < context.client.highestKnownApiLevel) {
                    var warned = false
                    if (version < 29) {
                        val now = calendar ?: Calendar.getInstance()
                        val year = now.get(Calendar.YEAR)
                        val month = now.get(Calendar.MONTH)

                        // After November 1st 2020, the apps are required to use 29 or higher
                        // (month is zero-based)
                        // https://developer.android.com/distribute/play-policies
                        val required: Int
                        val issue: Issue
                        if (year > 2020 || month >= 10) {
                            required = 29
                            issue = EXPIRED_TARGET_SDK_VERSION
                        } else if (version < 28 && year > 2018) {
                            required = 28
                            issue = EXPIRED_TARGET_SDK_VERSION
                        } else if (year == 2020) {
                            // Meets last year's requirement but not yet the upcoming one.
                            // Start warning 6 months in advance.
                            // (Check for 2020 here: no, we don't have a time machine, but let's
                            // allow developers to go back in time.)
                            required = 29
                            issue = EXPIRING_TARGET_SDK_VERSION
                        } else {
                            required = -1
                            issue = IssueRegistry.LINT_ERROR
                        }
                        if (required != -1) {
                            val message = if (issue == EXPIRED_TARGET_SDK_VERSION)
                                "Google Play requires that apps target API level $required or higher.\n"
                            else
                                "Google Play will soon require that apps target API " +
                                    "level 29 or higher. This will be required for new apps " +
                                    "in August 2020, and for updates to existing apps in " +
                                    "November 2020."

                            val highest = context.client.highestKnownApiLevel
                            val label = "Update targetSdkVersion to $highest"
                            val fix = fix().name(label)
                                .replace()
                                .text(value)
                                .with(highest.toString())
                                .build()

                            // Don't report if already suppressed with EXPIRING
                            val alreadySuppressed =
                                issue != EXPIRING_TARGET_SDK_VERSION &&
                                    context.containsCommentSuppress() &&
                                    context.isSuppressedWithComment(statementCookie, issue)

                            if (!alreadySuppressed) {
                                report(context, statementCookie, issue, message, fix, true)
                            }
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
                            .text(value)
                            .with(highest.toString())
                            .build()
                        report(context, statementCookie, TARGET_NEWER, message, fix)
                    }
                }
                if (version > 0) {
                    targetSdkVersion = version
                    checkTargetCompatibility(context)
                } else {
                    checkIntegerAsString(context, value, statementCookie)
                }
            } else if (property == "minSdkVersion") {
                val version = getSdkVersion(value)
                if (version > 0) {
                    minSdkVersion = version
                    checkMinSdkVersion(context, version, statementCookie)
                } else {
                    checkIntegerAsString(context, value, statementCookie)
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
                    report(context, statementCookie, GRADLE_GETTER, message)
                }
            } else if (property == "packageName") {
                val message = "Deprecated: Replace 'packageName' with 'applicationId'"
                val fix = fix()
                    .name("Replace 'packageName' with 'applicationId'", true)
                    .replace().text("packageName").with("applicationId").autoFix().build()
                report(context, propertyCookie, DEPRECATED, message, fix)
            }
            if (property == "versionCode" &&
                context.isEnabled(HIGH_APP_VERSION_CODE) &&
                isNonNegativeInteger(value)
            ) {
                val version = getIntLiteralValue(value, -1)
                if (version >= VERSION_CODE_HIGH_THRESHOLD) {
                    val message =
                        "The 'versionCode' is very high and close to the max allowed value"
                    report(context, statementCookie, HIGH_APP_VERSION_CODE, message)
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
                compileSdkVersionCookie = statementCookie
                checkTargetCompatibility(context)
            } else {
                checkIntegerAsString(context, value, statementCookie)
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
                        report(context, statementCookie, DEPENDENCY, message, fix)
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
                        reportFatalCompatibilityIssue(context, statementCookie, message)
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

                if (plugin == "kotlin-android") {
                    mAppliedKotlinAndroidPlugin = true
                }
                if (plugin == "kotlin-kapt") {
                    mAppliedKotlinKaptPlugin = true
                }
                if (JAVA_PLUGIN_IDS.contains(plugin)) {
                    mAppliedJavaPlugin = true
                    mJavaPluginInfo = JavaPluginInfo(statementCookie)
                }
            }
        } else if (parent == "dependencies") {
            if (value.startsWith("files('") && value.endsWith("')")) {
                val path = value.substring("files('".length, value.length - 2)
                if (path.contains("\\\\")) {
                    val fix = fix().replace().text(path).with(path.replace("\\\\", "/")).build()
                    val message =
                        "Do not use Windows file separators in .gradle files; use / instead"
                    report(context, valueCookie, PATH, message, fix)
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

                        gc = resolveCoordinate(context, property, gc)
                        isResolved = true
                    }
                    if (gc != null) {
                        if (gc.acceptsGreaterRevisions()) {
                            val message = "Avoid using + in version numbers; can lead " +
                                "to unpredictable and unrepeatable builds (" +
                                dependency +
                                ")"
                            val fix = fix().data(KEY_COORDINATE, gc.toString())
                            report(context, valueCookie, PLUS, message, fix)
                        }

                        // Check dependencies without the PSI read lock, because we
                        // may need to make network requests to retrieve version info.
                        context.driver.runLaterOutsideReadAction {
                            checkDependency(context, gc, isResolved, valueCookie, statementCookie)
                        }
                    }
                    if (hasLifecycleAnnotationProcessor(dependency) &&
                        targetJava8Plus(context.project)
                    ) {
                        report(
                            context, valueCookie, LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8,
                            "Use the Lifecycle Java 8 API provided by the " +
                                "`lifecycle-common-java8` library instead of Lifecycle annotations " +
                                "for faster incremental build.",
                            null
                        )
                    }
                    checkAnnotationProcessorOnCompilePath(
                        property,
                        dependency,
                        context,
                        propertyCookie
                    )
                }
                checkDeprecatedConfigurations(property, context, propertyCookie)
            }
        } else if (property == "packageNameSuffix") {
            val message = "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"
            val fix = fix()
                .name("Replace 'packageNameSuffix' with 'applicationIdSuffix'", true)
                .replace().text("packageNameSuffix").with("applicationIdSuffix").autoFix().build()
            report(context, propertyCookie, DEPRECATED, message, fix)
        } else if (property == "applicationIdSuffix") {
            val suffix = getStringLiteralValue(value)
            if (suffix != null && !suffix.startsWith(".")) {
                val message = "Application ID suffix should probably start with a \".\""
                report(context, statementCookie, PATH, message)
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
        } else if (parent == "dataBinding" && ((property == "enabled" || property == "isEnabled")) ||
            (parent == "buildFeatures" && property == "dataBinding")
        ) {
            // Note: "enabled" is used by build.gradle and "isEnabled" is used by build.gradle.kts
            if (value == SdkConstants.VALUE_TRUE) {
                if (mAppliedKotlinAndroidPlugin && !mAppliedKotlinKaptPlugin) {
                    val message =
                        "If you plan to use data binding in a Kotlin project, you should apply the kotlin-kapt plugin."
                    report(context, statementCookie, DATA_BINDING_WITHOUT_KAPT, message, null)
                }
            }
        } else if ((parent == "" || parent == "java") && property == "sourceCompatibility") {
            mDeclaredSourceCompatibility = true
        } else if ((parent == "" || parent == "java") && property == "targetCompatibility") {
            mDeclaredTargetCompatibility = true
        }
    }

    private enum class DeprecatedConfiguration(
        private val deprecatedName: String,
        private val replacementName: String
    ) {
        COMPILE("compile", "implementation"),
        PROVIDED("provided", "compileOnly"),
        APK("apk", "runtimeOnly"),
        ;

        private val deprecatedSuffix: String = deprecatedName.usLocaleCapitalize()
        private val replacementSuffix: String = replacementName.usLocaleCapitalize()

        fun matches(configurationName: String): Boolean {
            return configurationName == deprecatedName || configurationName.endsWith(
                deprecatedSuffix
            )
        }

        fun replacement(configurationName: String): String {
            return if (configurationName == deprecatedName) {
                replacementName
            } else {
                configurationName.removeSuffix(deprecatedSuffix) + replacementSuffix
            }
        }
    }

    private fun checkDeprecatedConfigurations(
        configuration: String,
        context: GradleContext,
        propertyCookie: Any
    ) {
        if (context.project.gradleModelVersion?.isAtLeastIncludingPreviews(3, 0, 0) == false) {
            // All of these deprecations were made in AGP 3.0.0
            return
        }

        for (deprecatedConfiguration in DeprecatedConfiguration.values()) {
            if (deprecatedConfiguration.matches(configuration)) {
                // Compile was replaced by API and Implementation, but only suggest API if it was used
                if (deprecatedConfiguration == DeprecatedConfiguration.COMPILE &&
                    suggestApiConfigurationUse(context.project, configuration)
                ) {
                    val implementation: String
                    val api: String
                    if (configuration == "compile") {
                        implementation = "implementation"
                        api = "api"
                    } else {
                        val prefix = configuration.removeSuffix("Compile")
                        implementation = "${prefix}Implementation"
                        api = "${prefix}Api"
                    }

                    val message =
                        "`$configuration` is deprecated; " +
                            "replace with either `$api` to maintain current behavior, " +
                            "or `$implementation` to improve build performance " +
                            "by not sharing this dependency transitively."
                    val apiFix = fix()
                        .name("Replace '$configuration' with '$api'")
                        .family("Replace compile with api")
                        .replace()
                        .text(configuration)
                        .with(api)
                        .autoFix()
                        .build()
                    val implementationFix = fix()
                        .name("Replace '$configuration' with '$implementation'")
                        .family("Replace compile with implementation")
                        .replace()
                        .text(configuration)
                        .with(implementation)
                        .autoFix()
                        .build()

                    val fixes = fix()
                        .alternatives()
                        .name("Replace '$configuration' with '$api' or '$implementation'")
                        .add(apiFix)
                        .add(implementationFix)
                        .build()

                    report(
                        context,
                        propertyCookie,
                        DEPRECATED_CONFIGURATION,
                        message,
                        fixes
                    )
                } else {
                    // Unambiguous replacement case
                    val replacement = deprecatedConfiguration.replacement(configuration)
                    val message = "`$configuration` is deprecated; replace with `$replacement`"
                    val fix = fix()
                        .name("Replace '$configuration' with '$replacement'")
                        .family("Replace deprecated configurations")
                        .replace()
                        .text(configuration)
                        .with(replacement)
                        .autoFix()
                        .build()
                    report(context, propertyCookie, DEPRECATED_CONFIGURATION, message, fix)
                }
            }
        }
    }

    private fun checkAnnotationProcessorOnCompilePath(
        configuration: String,
        dependency: String,
        context: GradleContext,
        propertyCookie: Any
    ) {
        for (compileConfiguration in CompileConfiguration.values()) {
            if (compileConfiguration.matches(configuration) &&
                isCommonAnnotationProcessor(dependency)
            ) {
                val replacement: String = compileConfiguration.replacement(configuration)
                val fix = fix()
                    .name("Replace $configuration with $replacement")
                    .family("Replace compile classpath with annotationProcessor")
                    .replace()
                    .text(configuration)
                    .with(replacement)
                    .autoFix()
                    .build()
                val message = "Add annotation processor to processor path using `$replacement`" +
                    " instead of `$configuration`"
                report(context, propertyCookie, ANNOTATION_PROCESSOR_ON_COMPILE_PATH, message, fix)
            }
        }
    }

    private fun checkMinSdkVersion(context: GradleContext, version: Int, valueCookie: Any) {
        if (version in 1 until LOWEST_ACTIVE_API) {
            val message =
                "The value of minSdkVersion is too low. It can be incremented " +
                    "without noticeably reducing the number of supported devices."

            val label = "Update minSdkVersion to $LOWEST_ACTIVE_API"
            val fix = fix().name(label)
                .replace()
                .text(version.toString())
                .with(LOWEST_ACTIVE_API.toString())
                .build()
            report(context, valueCookie, MIN_SDK_TOO_LOW, message, fix)
        }
    }

    private fun checkIntegerAsString(context: GradleContext, value: String, cookie: Any) {
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
            report(context, cookie, STRING_INTEGER, message, fix)
        }
    }

    override fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        parentParent: String?,
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

            if (plugin == "kotlin-android") {
                mAppliedKotlinAndroidPlugin = true
            }
            if (plugin == "kotlin-kapt") {
                mAppliedKotlinKaptPlugin = true
            }
            if (JAVA_PLUGIN_IDS.contains(plugin)) {
                mAppliedJavaPlugin = true
                mJavaPluginInfo = JavaPluginInfo(cookie)
            }
        }
        if (statement == "google" && parent == "repositories" && parentParent == "buildscript") {
            mDeclaredGoogleMavenRepository = true
            maybeReportAgpVersionIssue(context)
        }
        if (statement == "jcenter" && parent == "repositories") {
            val message = "JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere"
            val replaceFix = fix().name("Replace with mavenCentral")
                .replace().text("jcenter").with("mavenCentral").build()
            val deleteFix = fix().name("Delete this repository declaration")
                .replace().all().with("").build()
            report(context, cookie, JCENTER_REPOSITORY_OBSOLETE, message, fix().alternatives(replaceFix, deleteFix))
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
                .text(compileSdkVersion.toString())
                .with(targetSdkVersion.toString())
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
        var safeReplacement: GradleVersion? = null
        if (version == null) {
            return
        }
        var newerVersion: GradleVersion? = null

        val filter = getUpgradeVersionFilter(context, groupId, artifactId, revision)

        when (groupId) {
            GMS_GROUP_ID, FIREBASE_GROUP_ID, GOOGLE_SUPPORT_GROUP_ID, ANDROID_WEAR_GROUP_ID -> {
                // Play services

                checkPlayServices(context, dependency, version, revision, cookie, statementCookie)
            }

            "com.android.tools.build" -> {
                if ("gradle" == artifactId) {
                    if (checkGradlePluginDependency(context, dependency, statementCookie)) {
                        return
                    }

                    // If it's available in maven.google.com, fetch latest available version
                    newerVersion = GradleVersion.max(
                        version,
                        getGoogleMavenRepoVersion(context, dependency, filter)
                    )

                    // Compare with what's in the Gradle cache.
                    newerVersion =
                        GradleVersion.max(newerVersion, findCachedNewerVersion(dependency, filter))

                    // Compare with IDE's repository cache, if available.
                    newerVersion = GradleVersion.max(
                        newerVersion,
                        context.client.getHighestKnownVersion(dependency, filter)
                    )

                    // Don't just offer the latest available version, but if that is more than
                    // a micro-level different, and there is a newer micro version of the
                    // version that the user is currently using, offer that one as well as it
                    // may be easier to upgrade to.
                    if (newerVersion != null && !version.isPreview && newerVersion != version &&
                        version.minorSegment?.acceptsGreaterValue() == false &&
                        (version.major != newerVersion.major || version.minor != newerVersion.minor)
                    ) {
                        safeReplacement = getGoogleMavenRepoVersion(
                            context, dependency
                        ) { filterVersion ->
                            filterVersion.major == version.major &&
                                filterVersion.minor == version.minor &&
                                filterVersion.micro > version.micro &&
                                !filterVersion.isPreview &&
                                filterVersion < newerVersion!!
                        }
                    }
                    if (newerVersion != null && newerVersion > version) {
                        agpVersionCheckInfo = AgpVersionCheckInfo(
                            newerVersion,
                            newerVersion.major == version.major &&
                                newerVersion.minor == version.minor,
                            safeReplacement, dependency, isResolved, statementCookie
                        )
                        maybeReportAgpVersionIssue(context)
                    }
                    return
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
                            statementCookie,
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
                            statementCookie,
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

            // https://issuetracker.google.com/120098460
            "org.robolectric" -> {
                if ("robolectric" == artifactId &&
                    System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")
                ) {
                    if (!version.isAtLeast(4, 2, 1)) {
                        val fix = getUpdateDependencyFix(revision, "4.2.1")
                        report(
                            context,
                            cookie,
                            DEPENDENCY,
                            "Use robolectric version 4.2.1 or later to " +
                                "fix issues with parsing of Windows paths",
                            fix
                        )
                    }
                }
            }
        }

        checkForKtxExtension(context, groupId, artifactId, version, cookie)

        val blockedDependencies = blockedDependencies[context.project]
        if (blockedDependencies != null) {
            val path = blockedDependencies.checkDependency(groupId, artifactId, true)
            if (path != null) {
                val message = getBlockedDependencyMessage(path)
                val fix = fix().name("Delete dependency").replace().all().build()
                // Provisional: have to check consuming app's targetSdkVersion
                report(context, statementCookie, DUPLICATE_CLASSES, message, fix, partial = true)
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
            report(context, cookie, issue, message, fix)
        } else {
            val recommended = sdkRegistry.getRecommendedVersion(dependency)
            if (recommended != null && (newerVersion == null || recommended > newerVersion)) {
                newerVersion = recommended
            }
        }

        // Network check for really up to date libraries? Only done in batch mode.
        var issue = DEPENDENCY
        if (context.scope.size > 1 && context.isEnabled(REMOTE_VERSION) &&
            // Common but served from maven.google.com so no point to
            // ping other maven repositories about these
            !groupId.startsWith("androidx.")
        ) {
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

        if (newerVersion != null && version > GradleVersion(0, 0, 0) && newerVersion > version) {
            val versionString = newerVersion.toString()
            val message = getNewerVersionAvailableMessage(dependency, versionString, null)
            val fix = if (!isResolved) getUpdateDependencyFix(revision, versionString) else null
            report(context, cookie, issue, message, fix)
        }
    }

    /**
     * Returns a predicate that encapsulates version constraints for the
     * given library, or null if there are no constraints.
     */
    private fun getUpgradeVersionFilter(
        context: GradleContext,
        groupId: String,
        artifactId: String,
        revision: String
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

        if (groupId == "com.android.tools.build" && LintClient.isStudio) {
            val clientRevision = context.client.getClientRevision() ?: return null
            val ideVersion = GradleVersion.parse(clientRevision)
            val version = GradleVersion.parse(revision)
            // TODO(b/145606749): this assumes that the IDE version and the AGP version are directly comparable
            return Predicate { v ->
                // Any higher IDE version that matches major and minor
                // (e.g. from 3.3.0 offer 3.3.2 but not 3.4.0)
                (
                    v.major == ideVersion.major &&
                        v.minor == ideVersion.minor
                    ) ||
                    // Also allow matching latest current existing major/minor version
                    (
                        v.major == version.major &&
                            v.minor == version.minor
                        )
            }
        }
        return null
    }

    /** Home in the Gradle cache for artifact caches. */
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
        val versionDir =
            getArtifactCacheHome().toPath().resolve(
                dependency.groupId + File.separator + dependency.artifactId
            )
        return if (CancellableFileIo.exists(versionDir)) {
            MavenRepositories.getHighestVersion(
                versionDir,
                filter,
                MavenRepositories.isPreview(dependency)
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
        val groupId = dependency.groupId
        val artifactId = dependency.artifactId

        // For artifacts that follow the platform numbering scheme, check that it matches the SDK
        // versions used.
        if (isSupportLibraryDependentOnCompileSdk(groupId, artifactId)) {
            if (compileSdkVersion >= 18 &&
                dependency.majorVersion != compileSdkVersion &&
                dependency.majorVersion != GradleCoordinate.PLUS_REV_VALUE &&
                context.isEnabled(COMPATIBILITY)
            ) {
                if (compileSdkVersion >= 29 && dependency.majorVersion < 29) {
                    reportNonFatalCompatibilityIssue(
                        context, cookie,
                        "Version 28 (intended for Android Pie and below) is the last " +
                            "version of the legacy support library, so we recommend that " +
                            "you migrate to AndroidX libraries when using Android Q and " +
                            "moving forward. The IDE can help with this: " +
                            "Refactor > Migrate to AndroidX..."
                    )
                    return
                }

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
            if (!context.scope.contains(Scope.ALL_RESOURCE_FILES) &&
                context.isGlobalAnalysis()
            ) {
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
        }
    }

    private fun checkPlayServices(
        context: GradleContext,
        dependency: GradleCoordinate,
        version: GradleVersion,
        revision: String,
        cookie: Any,
        statementCookie: Any
    ) {
        val groupId = dependency.groupId
        val artifactId = dependency.artifactId

        // 5.2.08 is not supported; special case and warn about this
        if ("5.2.08" == revision && context.isEnabled(COMPATIBILITY)) {
            // This specific version is actually a preview version which should
            // not be used (https://code.google.com/p/android/issues/detail?id=75292)
            val maxVersion = GradleVersion.max(
                GradleVersion.parse("10.2.1"),
                getGoogleMavenRepoVersion(context, dependency, null)
            )

            val fix = getUpdateDependencyFix(revision, maxVersion.toString())
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
                if (!context.scope.contains(Scope.ALL_RESOURCE_FILES) &&
                    context.isGlobalAnalysis()
                ) {
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
                if (!context.scope.contains(Scope.ALL_RESOURCE_FILES) &&
                    context.isGlobalAnalysis()
                ) {
                    // Incremental editing: try flagging them in this file!
                    checkConsistentWearableLibraries(context, cookie, statementCookie)
                }
            }
        }
    }

    private fun LintModelMavenName.isSupportLibArtifact() =
        isSupportLibraryDependentOnCompileSdk(groupId, artifactId)

    /**
     * Returns if the given group id belongs to an AndroidX artifact.
     * This usually means that it starts with "androidx." but there is
     * an special case for the navigation artifact which does start with
     * "androidx." but links to non-androidx classes.
     */
    private fun LintModelMavenName.isAndroidxArtifact() =
        groupId.startsWith(ANDROIDX_PKG_PREFIX) && groupId != "androidx.navigation"

    private fun checkConsistentSupportLibraries(
        context: Context,
        cookie: Any?
    ) {
        checkConsistentLibraries(context, cookie, SUPPORT_LIB_GROUP_ID, null)

        val androidLibraries =
            getAllLibraries(context.project).filterIsInstance<LintModelExternalLibrary>()
        var usesOldSupportLib: LintModelMavenName? = null
        var usesAndroidX: LintModelMavenName? = null
        for (library in androidLibraries) {
            val coordinates = library.resolvedCoordinates
            if (usesOldSupportLib == null && coordinates.isSupportLibArtifact()) {
                usesOldSupportLib = coordinates
            }
            if (usesAndroidX == null && coordinates.isAndroidxArtifact()) {
                usesAndroidX = coordinates
            }

            if (usesOldSupportLib != null && usesAndroidX != null) {
                break
            }
        }

        if (usesOldSupportLib != null && usesAndroidX != null) {
            val message = "Dependencies using groupId " +
                "`$SUPPORT_LIB_GROUP_ID` and `$ANDROIDX_PKG_PREFIX*` " +
                "can not be combined but " +
                "found `$usesOldSupportLib` and `$usesAndroidX` incompatible dependencies"
            if (cookie != null) {
                reportNonFatalCompatibilityIssue(context, cookie, message)
            } else {
                val location = getDependencyLocation(context, usesOldSupportLib, usesAndroidX)
                reportNonFatalCompatibilityIssue(context, location, message)
            }
        }
    }

    private fun checkConsistentPlayServices(context: Context, cookie: Any?) {
        checkConsistentLibraries(context, cookie, GMS_GROUP_ID, FIREBASE_GROUP_ID)
    }

    private fun checkConsistentWearableLibraries(
        context: Context,
        cookie: Any?,
        statementCookie: Any?
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
        for (library in getAllLibraries(project).filterIsInstance<LintModelExternalLibrary>()) {
            val coordinates = library.resolvedCoordinates
            if (WEARABLE_ARTIFACT_ID == coordinates.artifactId &&
                GOOGLE_SUPPORT_GROUP_ID == coordinates.groupId
            ) {
                supportVersions.add(coordinates.version)
            }

            // Claims to be non-null but may not be after a failed gradle sync
            if (WEARABLE_ARTIFACT_ID == coordinates.artifactId &&
                ANDROID_WEAR_GROUP_ID == coordinates.groupId
            ) {
                if (!library.provided) {
                    var message =
                        "This dependency should be marked as `compileOnly`, not `compile`"
                    if (statementCookie != null) {
                        reportFatalCompatibilityIssue(context, statementCookie, message)
                    } else {
                        val location = getDependencyLocation(context, coordinates)
                        if (location.start == null) {
                            message =
                                "The $ANDROID_WEAR_GROUP_ID:$WEARABLE_ARTIFACT_ID dependency should be marked as `compileOnly`, not `compile`"
                        }
                        reportFatalCompatibilityIssue(context, location, message)
                    }
                }
                wearableVersions.add(coordinates.version)
            }
        }

        if (supportVersions.isNotEmpty()) {
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
                    val location = getDependencyLocation(
                        context, GOOGLE_SUPPORT_GROUP_ID,
                        WEARABLE_ARTIFACT_ID, first
                    )
                    reportFatalCompatibilityIssue(context, location, message)
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
                        val location = getDependencyLocation(
                            context, GOOGLE_SUPPORT_GROUP_ID,
                            WEARABLE_ARTIFACT_ID, sortedSupportVersions[0], ANDROID_WEAR_GROUP_ID,
                            WEARABLE_ARTIFACT_ID, supportedWearableVersions[0]
                        )
                        reportFatalCompatibilityIssue(context, location, message)
                    }
                }
            }
        }
    }

    private fun getAllLibraries(project: Project): List<LintModelLibrary> {
        return project.buildVariant?.mainArtifact?.dependencies?.getAll() ?: emptyList()
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
        val versionToCoordinate = ArrayListMultimap.create<String, LintModelMavenName>()
        val allLibraries = getAllLibraries(project).filterIsInstance<LintModelExternalLibrary>()
        for (library in allLibraries) {
            val coordinates = library.resolvedCoordinates
            if ((coordinates.groupId == groupId || coordinates.groupId == groupId2) &&
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
                !coordinates.artifactId.startsWith("firebase-jobdispatcher") &&
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
            val allItems = project.buildVariant?.mainArtifact?.dependencies
                ?.compileDependencies?.getAllGraphItems() ?: emptyList()
            for (library in allItems) {
                if (library.artifactName == "com.android.databinding:library") {
                    for (dep in library.dependencies) {
                        if (dep.artifactName == "com.android.support:support-v4" &&
                            sortedVersions[0] != (dep.findLibrary() as? LintModelExternalLibrary)?.resolvedCoordinates?.version
                        ) {
                            message += ". Note that this project is using data binding " +
                                "(com.android.databinding:library:" +
                                (library.findLibrary() as? LintModelExternalLibrary)?.resolvedCoordinates?.version +
                                ") which pulls in com.android.support:support-v4:" +
                                (dep.findLibrary() as? LintModelExternalLibrary)?.resolvedCoordinates?.version +
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
                val location = getDependencyLocation(context, c1, c2)
                reportNonFatalCompatibilityIssue(context, location, message)
            }
        }
    }

    override fun beforeCheckRootProject(context: Context) {
        val project = context.project
        blockedDependencies[project] = BlockedDependencies(project)
    }

    override fun afterCheckRootProject(context: Context) {
        val project = context.project
        // Check for disallowed dependencies
        checkBlockedDependencies(context, project)
    }

    private fun checkLibraryConsistency(context: Context) {
        checkConsistentPlayServices(context, null)
        checkConsistentSupportLibraries(context, null)
        checkConsistentWearableLibraries(context, null, null)
    }

    override fun afterCheckFile(context: Context) {
        if (mAppliedJavaPlugin && !(mDeclaredSourceCompatibility && mDeclaredTargetCompatibility)) {
            val file = context.file
            val contents = context.client.readFile(file).toString()
            val message = when {
                mDeclaredTargetCompatibility -> "no Java sourceCompatibility directive"
                mDeclaredSourceCompatibility -> "no Java targetCompatibility directive"
                else -> "no Java language level directives"
            }
            val fixDisplayName = when {
                mDeclaredTargetCompatibility -> "Insert sourceCompatibility directive for JDK8"
                mDeclaredSourceCompatibility -> "Insert targetCompatibility directive for JDK8"
                else -> "Insert JDK8 language level directives"
            }
            val insertion = when {
                // Note that these replacement texts must be valid in both Groovy and KotlinScript Gradle files
                mDeclaredTargetCompatibility -> "\njava.sourceCompatibility = JavaVersion.VERSION_1_8"
                mDeclaredSourceCompatibility -> "\njava.targetCompatibility = JavaVersion.VERSION_1_8"
                else -> """

                    java {
                        sourceCompatibility = JavaVersion.VERSION_1_8
                        targetCompatibility = JavaVersion.VERSION_1_8
                    }
                """.trimIndent()
            }
            val fix = fix().replace()
                .name(fixDisplayName)
                .range(Location.create(context.file, contents, 0, contents.length))
                .end()
                .with(insertion)
                .build()
            report(context, mJavaPluginInfo!!.cookie, JAVA_PLUGIN_LANGUAGE_LEVEL, message, fix)
        }
    }

    private fun maybeReportAgpVersionIssue(context: Context) {
        // b/144442233: surface check for outdated AGP only if google() is in buildscript repositories
        if (mDeclaredGoogleMavenRepository) {
            agpVersionCheckInfo?.let {
                val versionString = it.newerVersion.toString()
                val message = getNewerVersionAvailableMessage(
                    it.dependency,
                    versionString,
                    it.safeReplacement
                )
                val fix = when {
                    it.isResolved -> null
                    else -> getUpdateDependencyFix(
                        it.dependency.revision, versionString,
                        it.newerVersionIsSafe, it.safeReplacement
                    )
                }
                report(context, it.cookie, AGP_DEPENDENCY, message, fix)
            }
        }
    }

    /**
     * Checks to see if a KTX extension is available for the given
     * library. If so, we offer a suggestion to switch the dependency to
     * the KTX version. See https://developer.android.com/kotlin/ktx for
     * details.
     *
     * This should be called outside of a read action, since it may
     * trigger network requests.
     */
    private fun checkForKtxExtension(
        context: Context,
        groupId: String,
        artifactId: String,
        version: GradleVersion,
        cookie: Any
    ) {
        if (!mAppliedKotlinAndroidPlugin) return
        if (artifactId.endsWith("-ktx")) return

        val mavenName = "$groupId:$artifactId"
        if (!libraryHasKtxExtension(mavenName)) {
            return
        }

        // Make sure the Kotlin stdlib is used by the main artifact (not just by tests).
        val variant = context.project.buildVariant ?: return
        variant.mainArtifact.findCompileDependency("org.jetbrains.kotlin:kotlin-stdlib") ?: return

        // Make sure the KTX extension exists for this version of the library.
        val repository = getGoogleMavenRepository(context.client)
        repository.findVersion(
            groupId, "$artifactId-ktx",
            filter = { it == version }, allowPreview = true
        ) ?: return

        // Note: once b/155974293 is fixed, we can check whether the KTX extension is
        // already a direct dependency. If it is, then we could offer a slightly better
        // warning message along the lines of: "There is no need to declare this dependency
        // because the corresponding KTX extension pulls it in automatically."

        val msg = "Add suffix `-ktx` to enable the Kotlin extensions for this library"
        val fix = fix()
            .name("Replace with KTX dependency")
            .replace().text(mavenName).with("$mavenName-ktx")
            .build()
        report(context, cookie, KTX_EXTENSION_AVAILABLE, msg, fix)
    }

    /**
     * Report any blocked dependencies that weren't found in the
     * build.gradle source file during processing (we don't have
     * accurate position info at this point)
     */
    private fun checkBlockedDependencies(context: Context, project: Project) {
        val blockedDependencies = blockedDependencies[project] ?: return
        val dependencies = blockedDependencies.getForbiddenDependencies()
        if (dependencies.isNotEmpty()) {
            for (path in dependencies) {
                val message = getBlockedDependencyMessage(path) ?: continue
                val projectDir = context.project.dir
                val gc = LintModelMavenName.parse(path[0].artifactAddress)
                val location = if (gc != null) {
                    getDependencyLocation(context, gc.groupId, gc.artifactId, gc.version)
                } else {
                    val mavenName = path[0].artifactName
                    guessGradleLocation(context.client, projectDir, mavenName)
                }
                context.report(Incident(DUPLICATE_CLASSES, location, message), map())
            }
        }
        this.blockedDependencies.remove(project)
    }

    private fun report(
        context: Context,
        cookie: Any,
        issue: Issue,
        message: String,
        fix: LintFix? = null,
        partial: Boolean = false
    ) {
        // Some methods in GradleDetector are run without the PSI read lock in order
        // to accommodate network requests, so we grab the read lock here.
        context.client.runReadAction(
            Runnable {
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
                    val incident = Incident(issue, location, message, fix)
                    if (partial) {
                        context.report(incident, map())
                    } else {
                        context.report(incident)
                    }
                }
            }
        )
    }

    /**
     * Normally, all warnings reported for a given issue will have the
     * same severity, so it isn't possible to have some of them reported
     * as errors and others as warnings. And this is intentional, since
     * users should get to designate whether an issue is an error or a
     * warning (or ignored for that matter).
     *
     * However, for [COMPATIBILITY] we want to treat some issues as
     * fatal (breaking the build) but not others. To achieve this we
     * tweak things a little bit. All compatibility issues are now
     * marked as fatal, and if we're *not* in the "fatal only" mode, all
     * issues are reported as before (with severity fatal, which has
     * the same visual appearance in the IDE as the previous severity,
     * "error".) However, if we're in a "fatal-only" build, then we'll
     * stop reporting the issues that aren't meant to be treated as
     * fatal. That's what this method does; issues reported to it should
     * always be reported as fatal. There is a corresponding method,
     * [reportNonFatalCompatibilityIssue] which can be used to report
     * errors that shouldn't break the build; those are ignored in
     * fatal-only mode.
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

    /** See [reportFatalCompatibilityIssue] for an explanation. */
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
        context.client.runReadAction {
            context.report(COMPATIBILITY, location, message)
        }
    }

    /** See [reportFatalCompatibilityIssue] for an explanation. */
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
        context.client.runReadAction {
            context.report(COMPATIBILITY, location, message)
        }
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

    @SuppressWarnings("ExpensiveAssertion")
    private fun resolveCoordinate(
        context: GradleContext,
        property: String,
        gc: GradleCoordinate
    ): GradleCoordinate? {
        assert(gc.revision.contains("$")) { gc.revision }
        val project = context.project
        val variant = project.buildVariant
        if (variant != null) {
            val artifact =
                when {
                    property.startsWith("androidTest") -> variant.androidTestArtifact
                    property.startsWith("test") -> variant.testArtifact
                    else -> variant.mainArtifact
                } ?: return null
            for (library in artifact.dependencies.getAll()) {
                if (library is LintModelExternalLibrary) {
                    val mc = library.resolvedCoordinates
                    if (mc.groupId == gc.groupId &&
                        mc.artifactId == gc.artifactId
                    ) {
                        val revisions = GradleCoordinate.parseRevisionNumber(mc.version)
                        if (revisions.isNotEmpty()) {
                            return GradleCoordinate(
                                mc.groupId, mc.artifactId, revisions, null
                            )
                        }
                        break
                    }
                }
            }
        }
        return null
    }

    /** True if the given project uses the legacy http library. */
    private fun usesLegacyHttpLibrary(project: Project): Boolean {
        val model = project.buildModule ?: return false
        for (path in model.bootClassPath) {
            if (path.endsWith("org.apache.http.legacy.jar")) {
                return true
            }
        }

        return false
    }

    private fun getUpdateDependencyFix(
        currentVersion: String,
        suggestedVersion: String,
        suggestedVersionIsSafe: Boolean = false,
        safeReplacement: GradleVersion? = null
    ): LintFix {
        val fix = fix()
            .name("Change to $suggestedVersion")
            .sharedName("Update versions")
            .replace()
            .text(currentVersion)
            .with(suggestedVersion)
            .autoFix(suggestedVersionIsSafe, suggestedVersionIsSafe)
            .build()
        return if (safeReplacement != null) {
            val stableVersion = safeReplacement.toString()
            val stableFix = fix()
                .name("Change to $stableVersion")
                .sharedName("Update versions")
                .replace()
                .text(currentVersion)
                .with(stableVersion)
                .autoFix()
                .build()
            fix().alternatives(fix, stableFix)
        } else {
            fix
        }
    }

    private fun getNewerVersionAvailableMessage(
        dependency: GradleCoordinate,
        version: String,
        stable: GradleVersion?
    ): String {
        val message = StringBuilder()
        with(message) {
            append("A newer version of ")
            append(dependency.groupId)
            append(":")
            append(dependency.artifactId)
            append(" than ")
            append(dependency.revision)
            append(" is available: ")
            append(version)
            if (stable != null) {
                append(". (There is also a newer version of ")
                append(stable.major.toString())
                append(".")
                append(stable.minor.toString())
                // \uD835\uDC65 is 𝑥, unicode for Mathematical Italic Small X
                append(".\uD835\uDC65 available, if upgrading to ")
                append(version)
                append(" is difficult: ")
                append(stable.toString())
                append(")")
            }
        }
        return message.toString()
    }

    /**
     * Checks if the library with the given `groupId` and `artifactId`
     * has to match compileSdkVersion.
     */
    private fun isSupportLibraryDependentOnCompileSdk(
        groupId: String,
        artifactId: String
    ): Boolean {
        return (
            SUPPORT_LIB_GROUP_ID == groupId &&
                !artifactId.startsWith("multidex") &&
                !artifactId.startsWith("renderscript") &&
                // Support annotation libraries work with any compileSdkVersion
                artifactId != "support-annotations"
            )
    }

    private fun findFirst(coordinates: Collection<LintModelMavenName>): LintModelMavenName {
        return Collections.min(coordinates) { o1, o2 -> o1.toString().compareTo(o2.toString()) }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val issue = incident.issue
        if (issue === DUPLICATE_CLASSES) {
            return context.mainProject.minSdk < 23 || usesLegacyHttpLibrary(context.mainProject)
        } else if (issue == EXPIRING_TARGET_SDK_VERSION || issue == EXPIRED_TARGET_SDK_VERSION) {
            // These checks only apply if the merged manifest does not mark this app as a wear app
            // (which may not appear in the manifest of the app module)
            return !isWearApp(context)
        } else {
            error(issue.id)
        }
    }

    private fun isWearApp(context: Context): Boolean {
        val manifest = context.mainProject.mergedManifest?.documentElement ?: return false
        for (element in manifest) {
            if (element.tagName == TAG_USES_FEATURE &&
                element.getAttributeNS(ANDROID_URI, ATTR_NAME) == "android.hardware.type.watch"
            ) {
                return true
            }
        }
        return false
    }

    override fun checkMergedProject(context: Context) {
        if (context.isGlobalAnalysis() && context.driver.isIsolated()) {
            // Already performed on occurrences in the file being edited
            return
        }
        checkLibraryConsistency(context)
    }

    private fun getBlockedDependencyMessage(
        path: List<LintModelDependency>
    ): String {
        val direct = path.size == 1
        val message: String
        val resolution = "Solutions include " +
            "finding newer versions or alternative libraries that don't have the " +
            "same problem (for example, for `httpclient` use `HttpUrlConnection` or " +
            "`okhttp` instead), or repackaging the library using something like " +
            "`jarjar`."
        if (direct) {
            message =
                "`${path[0].getArtifactId()}` defines classes that conflict with classes now provided by Android. $resolution"
        } else {
            val sb = StringBuilder()
            var first = true
            for (library in path) {
                if (first) {
                    first = false
                } else {
                    sb.append(" \u2192 ") // right arrow
                }
                val coordinates = library.artifactName
                sb.append(coordinates)
            }
            sb.append(") ")
            val chain = sb.toString()
            message = "`${path[0].getArtifactId()}` depends on a library " +
                "(${path[path.size - 1].artifactName}) which defines " +
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
        return if (version1 > GradleVersion(0, 0, 0) && !version1.isAtLeast(major, minor, micro)) {
            GradleVersion(major, minor, micro)
        } else null
    }

    private fun getNewerVersion(
        version1: GradleVersion,
        major: Int,
        minor: Int
    ): GradleVersion? {
        return if (version1 > GradleVersion(0, 0, 0) && !version1.isAtLeast(major, minor, 0)) {
            GradleVersion(major, minor)
        } else null
    }

    private fun getNewerVersion(
        version1: GradleVersion,
        version2: GradleVersion
    ): GradleVersion? {
        return if (version1 > GradleVersion(0, 0, 0) && version1 < version2) {
            version2
        } else null
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
        return googleMavenRepository ?: run {
            val cacheDir = client.getCacheDir(MAVEN_GOOGLE_CACHE_DIR_KEY, true)
            val repository = object : GoogleMavenRepository(cacheDir?.toPath()) {

                public override fun readUrlData(url: String, timeout: Int): ByteArray? =
                    readUrlData(client, url, timeout)

                public override fun error(throwable: Throwable, message: String?) =
                    client.log(throwable, message)
            }

            googleMavenRepository = repository
            repository
        }
    }

    private fun getDeprecatedLibraryLookup(client: LintClient): DeprecatedSdkRegistry {
        return deprecatedSdkRegistry ?: run {
            val cacheDir = client.getCacheDir(DEPRECATED_SDK_CACHE_DIR_KEY, true)
            val repository = object : DeprecatedSdkRegistry(cacheDir?.toPath()) {

                public override fun readUrlData(url: String, timeout: Int) =
                    readUrlData(client, url, timeout)

                public override fun error(throwable: Throwable, message: String?) =
                    client.log(throwable, message)
            }

            deprecatedSdkRegistry = repository
            repository
        }
    }

    companion object {
        /**
         * Calendar to use to look up the current time (used by tests to
         * set specific time.
         */
        var calendar: Calendar? = null

        const val KEY_COORDINATE = "coordinate"

        private val IMPLEMENTATION = Implementation(GradleDetector::class.java, Scope.GRADLE_SCOPE)

        /** Obsolete dependencies. */
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

        /**
         * A dependency on an obsolete version of the Android Gradle
         * Plugin.
         */
        @JvmField
        val AGP_DEPENDENCY = Issue.create(
            id = "AndroidGradlePluginVersion",
            briefDescription = "Obsolete Android Gradle Plugin Version",
            explanation = """
                This detector looks for usage of the Android Gradle Plugin where the version \
                you are using is not the current stable release. Using older versions is fine, \
                and there are cases where you deliberately want to stick with an older version. \
                However, you may simply not be aware that a more recent version is available, \
                and that is what this lint check helps find.""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Deprecated Gradle constructs. */
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

        /** Deprecated Gradle configurations. */
        @JvmField
        val DEPRECATED_CONFIGURATION = Issue.create(
            id = "GradleDeprecatedConfiguration",
            briefDescription = "Deprecated Gradle Configuration",
            explanation = """
                Some Gradle configurations have been deprecated since Android Gradle Plugin 3.0.0 \
                and will be removed in a future version of the Android Gradle Plugin.
             """,
            category = Category.CORRECTNESS,
            moreInfo = "https://d.android.com/r/tools/update-dependency-configurations",
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** Incompatible Android Gradle plugin. */
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

        /** Invalid or dangerous paths. */
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

        /** Constructs the IDE support struggles with. */
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

        /** Using + in versions. */
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

        /**
         * Accidentally calling a getter instead of your own methods.
         */
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

        /** Using incompatible versions. */
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

        /** Using a string where an integer is expected. */
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

        /** Attempting to use substitution with single quotes. */
        @JvmField
        val NOT_INTERPOLATED = Issue.create(
            id = "NotInterpolated",
            briefDescription = "Incorrect Interpolation",
            explanation = """
                To insert the value of a variable, you can use `${"$"}{variable}` inside a \
                string literal, but **only** if you are using double quotes!""",
            moreInfo = "https://www.groovy-lang.org/syntax.html#_string_interpolation",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        /** A newer version is available on a remote server. */
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

        /** Accidentally using octal numbers. */
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
                (https://firebase.google.com/docs/android/setup), Android Studio's \
                Tools → Firebase assistant window can automatically add just the \
                dependencies needed for each feature.""",
            moreInfo = "https://developers.google.com/android/guides/setup#split",
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Using a versionCode that is very high. */
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

        /** Dev mode is no longer relevant. */
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

        /** Duplicate HTTP classes. */
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

        /** targetSdkVersion about to expire */
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
            .addMoreInfo("https://developer.android.com/distribute/best-practices/develop/target-sdk.html")

        /** Using a deprecated library. */
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

        /**
         * Using data binding with Kotlin but not Kotlin annotation
         * processing.
         */
        @JvmField
        val DATA_BINDING_WITHOUT_KAPT = Issue.create(
            id = "DataBindingWithoutKapt",
            briefDescription = "Data Binding without Annotation Processing",
            moreInfo = "https://kotlinlang.org/docs/reference/kapt.html",
            explanation = """
                Apps that use Kotlin and data binding should also apply the kotlin-kapt plugin.
                """,
            category = Category.CORRECTNESS,
            priority = 1,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Using Lifecycle annotation processor with java8. */
        @JvmField
        val LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8 = Issue.create(
            id = "LifecycleAnnotationProcessorWithJava8",
            briefDescription = "Lifecycle Annotation Processor with Java 8 Compile Option",
            moreInfo = "https://d.android.com/r/studio-ui/lifecycle-release-notes",
            explanation = """
                For faster incremental build, switch to the Lifecycle Java 8 API with these steps:

                First replace
                ```gradle
                annotationProcessor "androidx.lifecycle:lifecycle-compiler:*version*"
                kapt "androidx.lifecycle:lifecycle-compiler:*version*"
                ```
                with
                ```gradle
                implementation "androidx.lifecycle:lifecycle-common-java8:*version*"
                ```
                Then remove any `OnLifecycleEvent` annotations from `Observer` classes \
                and make them implement the `DefaultLifecycleObserver` interface.
                """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Using a vulnerable library. */
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

        @JvmField
        val ANNOTATION_PROCESSOR_ON_COMPILE_PATH = Issue.create(
            id = "AnnotationProcessorOnCompilePath",
            briefDescription = "Annotation Processor on Compile Classpath",
            explanation = """
               This dependency is identified as an annotation processor. Consider adding it to the \
               processor path using `annotationProcessor` instead of including it to the
               compile path.
            """,
            category = Category.PERFORMANCE,
            priority = 8,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val KTX_EXTENSION_AVAILABLE = Issue.create(
            id = "KtxExtensionAvailable",
            briefDescription = "KTX Extension Available",
            explanation = """
                Android KTX extensions augment some libraries with support for modern Kotlin \
                language features like extension functions, extension properties, lambdas, named \
                parameters, coroutines, and more.

                In Kotlin projects, use the KTX version of a library by replacing the \
                dependency in your `build.gradle` file. For example, you can replace \
                `androidx.fragment:fragment` with `androidx.fragment:fragment-ktx`.
            """,
            category = Category.PRODUCTIVITY,
            priority = 4,
            severity = Severity.INFORMATIONAL,
            androidSpecific = true,
            implementation = IMPLEMENTATION,
            moreInfo = "https://developer.android.com/kotlin/ktx"
        )

        @JvmField
        val JAVA_PLUGIN_LANGUAGE_LEVEL = Issue.create(
            id = "JavaPluginLanguageLevel",
            briefDescription = "No Explicit Java Language Level Given",
            explanation = """
                In modules using plugins deriving from the Gradle `java` plugin (e.g. \
                `java-library` or `application`), the java source and target compatibility \
                default to the version of the JDK being used to run Gradle, which may cause \
                compatibility problems with Android (or other) modules.

                You can specify an explicit sourceCompatibility and targetCompatibility in this \
                module to maintain compatibility no matter which JDK is used to run Gradle.
            """,
            category = Category.INTEROPERABILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val JCENTER_REPOSITORY_OBSOLETE = Issue.create(
            id = "JcenterRepositoryObsolete",
            briefDescription = "JCenter Maven repository is read-only",
            explanation = """
                The JCenter Maven repository is no longer accepting submissions of Maven \
                artifacts since 31st March 2021.  Ensure that the project is configured \
                to search in repositories with the latest versions of its dependencies.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://developer.android.com/r/tools/jcenter-end-of-service"
        )

        /** Gradle plugin IDs based on the Java plugin. */
        val JAVA_PLUGIN_IDS = listOf("java", "java-library", "application")
            .flatMap { listOf(it, "org.gradle.$it") }

        /** The Gradle plugin ID for Android applications. */
        const val APP_PLUGIN_ID = "com.android.application"

        /** The Gradle plugin ID for Android libraries. */
        const val LIB_PLUGIN_ID = "com.android.library"

        /** Previous plugin id for applications. */
        const val OLD_APP_PLUGIN_ID = "android"

        /** Previous plugin id for libraries. */
        const val OLD_LIB_PLUGIN_ID = "android-library"

        /** Group ID for GMS. */
        const val GMS_GROUP_ID = "com.google.android.gms"

        const val FIREBASE_GROUP_ID = "com.google.firebase"
        const val GOOGLE_SUPPORT_GROUP_ID = "com.google.android.support"
        const val ANDROID_WEAR_GROUP_ID = "com.google.android.wearable"
        private const val WEARABLE_ARTIFACT_ID = "wearable"

        private val PLAY_SERVICES_V650 =
            GradleCoordinate.parseCoordinateString("$GMS_GROUP_ID:play-services:6.5.0")!!

        /**
         * Threshold to consider a versionCode
         * very high and issue a warning.
         * https://developer.android.com/studio/publish/versioning.html
         * indicates that the highest value accepted by Google Play is
         * 2100000000.
         */
        private const val VERSION_CODE_HIGH_THRESHOLD = 2000000000

        /**
         * Returns the best guess for where a dependency is declared in
         * the given project.
         */
        fun getDependencyLocation(context: Context, c: LintModelMavenName): Location {
            return getDependencyLocation(context, c.groupId, c.artifactId, c.version)
        }

        /**
         * Returns the best guess for where a dependency is declared in
         * the given project.
         */
        fun getDependencyLocation(
            context: Context,
            groupId: String,
            artifactId: String,
            version: String
        ): Location {
            val client = context.client
            val projectDir = context.project.dir
            val withoutQuotes = "$groupId:$artifactId:$version"
            var location = guessGradleLocation(client, projectDir, withoutQuotes)
            if (location.start != null) return location
            // Try with just the group+artifact (relevant for example when using
            // version variables)
            location = guessGradleLocation(client, projectDir, "$groupId:$artifactId:")
            if (location.start != null) return location
            // Just the artifact -- important when using the other dependency syntax,
            // e.g. variations of
            //   group: 'comh.android.support', name: 'support-v4', version: '21.0.+'
            location = guessGradleLocation(client, projectDir, artifactId)
            if (location.start != null) return location
            // just the group: less precise but better than just the gradle file
            location = guessGradleLocation(client, projectDir, groupId)
            return location
        }

        /**
         * Returns the best guess for where two dependencies are
         * declared in a project.
         */
        fun getDependencyLocation(
            context: Context,
            address1: LintModelMavenName,
            address2: LintModelMavenName
        ): Location {
            return getDependencyLocation(
                context, address1.groupId, address1.artifactId,
                address1.version, address2.groupId, address2.artifactId, address2.version
            )
        }

        /**
         * Returns the best guess for where two dependencies are
         * declared in a project.
         */
        fun getDependencyLocation(
            context: Context,
            groupId1: String,
            artifactId1: String,
            version1: String,
            groupId2: String,
            artifactId2: String,
            version2: String,
            message: String? = null
        ): Location {
            val location1 = getDependencyLocation(context, groupId1, artifactId1, version1)
            val location2 = getDependencyLocation(context, groupId2, artifactId2, version2)
            //noinspection FileComparisons
            if (location2.start != null || location1.file != location2.file) {
                location1.secondary = location2
                message?.let { location2.message = it }
            }
            return location1
        }

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
            if (groupId == "com.google.guava" || artifactId == "kotlinx-coroutines-core") {
                // These libraries aren't releasing previews in their version strings;
                // instead, the suffix is used to indicate different variants (JRE vs Android,
                // JVM vs Kotlin Native)
            } else if (filter == null && allowPreview) {
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
            val versions = mutableListOf<GradleVersion>()
            while (index != -1) {
                index = response.indexOf("\"v\":", index)
                if (index != -1) {
                    index += 4
                    val start = response.indexOf('"', index) + 1
                    val end = response.indexOf('"', start + 1)
                    if (start in 0 until end) {
                        val substring = response.substring(start, end)
                        val revision = GradleVersion.tryParse(substring)
                        if (revision != null) {
                            versions.add(revision)
                        }
                    }
                }
            }

            // Some special cases for specific artifacts that were versioned
            // incorrectly (using a string suffix to delineate separate branches
            // whereas Gradle will just use an alphabetical sort on these). See
            // 171369798 for an example.

            if (groupId == "com.google.guava") {
                val version = dependency.version
                if (version != null) {
                    // GradleVersion does not store unknown suffixes so do simple string lookup here
                    val suffix = version.toString()
                    val jre: (GradleVersion) -> Boolean = { v -> v.toString().endsWith("-jre") }
                    val android: (GradleVersion) -> Boolean = { v -> !v.toString().endsWith("-jre") }
                    return versions.filter(if (suffix.endsWith("-jre")) jre else android).max()
                }
            } else if (artifactId == "kotlinx-coroutines-core") {
                val version = dependency.version
                if (version != null) {
                    val suffix = version.toString()
                    return versions.filter(
                        when {
                            suffix.indexOf('-') == -1 -> {
                                { (allowPreview || !it.isPreview) && !it.toString().contains("-native-mt") }
                            }
                            suffix.contains("-native-mt-2") -> {
                                { it.toString().contains("-native-mt-2") }
                            }
                            suffix.contains("-native-mt") -> {
                                {
                                    it.toString().contains("-native-mt") && !it.toString().contains("-native-mt-2")
                                }
                            }
                            else -> {
                                { (allowPreview || !it.isPreview) && !it.toString().contains("-native-mt") }
                            }
                        }
                    ).max()
                }
            }

            return versions
                .filter { filter == null || filter.test(it) }
                .filter { allowPreview || !it.isPreview }
                .max()
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
                    return "$group:$artifact:$version"
                }
            }

            return null
        }

        private var majorBuildTools: Int = 0
        private var latestBuildTools: GradleVersion? = null

        /**
         * Returns the latest build tools installed for the given
         * major version. We just cache this once; we don't need to be
         * accurate in the sense that if the user opens the SDK manager
         * and installs a more recent version, we capture this in the
         * same IDE session.
         *
         * @param client the associated client
         * @param major the major version of build tools to look up
         *     (e.g. typically 18, 19, ...)
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

                if (revisions.isNotEmpty()) {
                    latestBuildTools = Collections.max(revisions)
                }
            }

            return latestBuildTools
        }

        private fun suggestApiConfigurationUse(project: Project, configuration: String): Boolean {
            return when {
                configuration.startsWith("test") || configuration.startsWith("androidTest") -> false
                else -> when (project.type) {
                    LintModelModuleType.APP ->
                        // Applications can only generally be consumed if there are dynamic features
                        // (Ignoring the test-only project for this purpose)
                        project.hasDynamicFeatures()
                    LintModelModuleType.LIBRARY -> true
                    LintModelModuleType.JAVA_LIBRARY -> true
                    LintModelModuleType.FEATURE, LintModelModuleType.DYNAMIC_FEATURE -> true
                    LintModelModuleType.TEST -> false
                    LintModelModuleType.INSTANT_APP -> false
                }
            }
        }

        private fun targetJava8Plus(project: Project): Boolean {
            return getLanguageLevel(project, JDK_1_7).isAtLeast(JDK_1_8)
        }

        private fun hasLifecycleAnnotationProcessor(dependency: String) =
            dependency.contains("android.arch.lifecycle:compiler") ||
                dependency.contains("androidx.lifecycle:lifecycle-compiler")

        private fun isCommonAnnotationProcessor(dependency: String): Boolean =
            when (val index = dependency.lastIndexOf(":")) {
                -1 -> false
                else -> dependency.substring(0, index) in commonAnnotationProcessors
            }

        private enum class CompileConfiguration(
            private val compileConfigName: String
        ) {
            API("api"),
            COMPILE("compile"),
            IMPLEMENTATION("implementation"),
            COMPILE_ONLY("compileOnly")
            ;

            private val annotationProcessor = "annotationProcessor"
            private val compileConfigSuffix = compileConfigName.usLocaleCapitalize()

            fun matches(configurationName: String): Boolean {
                return configurationName == compileConfigName ||
                    configurationName.endsWith(compileConfigSuffix)
            }

            fun replacement(configurationName: String): String {
                return if (configurationName == compileConfigName) {
                    annotationProcessor
                } else {
                    configurationName.removeSuffix(compileConfigSuffix)
                        .appendCapitalized(annotationProcessor)
                }
            }
        }

        private val commonAnnotationProcessors: Set<String> = setOf(
            "com.jakewharton:butterknife-compiler",
            "com.github.bumptech.glide:compiler",
            "androidx.databinding:databinding-compiler",
            "com.google.dagger:dagger-compiler",
            "com.google.auto.service:auto-service",
            "android.arch.persistence.room:compiler",
            "android.arch.lifecycle:compiler",
            "io.realm:realm-annotations-processor",
            "com.google.dagger:dagger-android-processor",
            "androidx.room:room-compiler",
            "com.android.databinding:compiler",
            "androidx.lifecycle:lifecycle-compiler",
            "org.projectlombok:lombok",
            "com.google.auto.value:auto-value",
            "org.parceler:parceler",
            "com.github.hotchemi:permissionsdispatcher-processor",
            "com.alibaba:arouter-compiler",
            "org.androidannotations:androidannotations",
            "com.github.Raizlabs.DBFlow:dbflow-processor",
            "frankiesardo:icepick-processor",
            "org.greenrobot:eventbus-annotation-processor",
            "com.ryanharter.auto.value:auto-value-gson",
            "io.objectbox:objectbox-processor",
            "com.arello-mobile:moxy-compiler",
            "com.squareup.dagger:dagger-compiler",
            "io.realm:realm-android",
            "com.bluelinelabs:logansquare-compiler",
            "com.tencent.tinker:tinker-android-anno",
            "com.raizlabs.android:DBFlow-Compiler",
            "com.google.auto.factory:auto-factory",
            "com.airbnb:deeplinkdispatch-processor",
            "com.alipay.android.tools:androidannotations",
            "org.permissionsdispatcher:permissionsdispatcher-processor",
            "com.airbnb.android:epoxy-processor",
            "org.immutables:value",
            "com.github.stephanenicolas.toothpick:toothpick-compiler",
            "com.mindorks.android:placeholderview-compiler",
            "com.github.frankiesardo:auto-parcel-processor",
            "com.hannesdorfmann.fragmentargs:processor",
            "com.evernote:android-state-processor",
            "org.mapstruct:mapstruct-processor",
            "com.iqiyi.component.router:qyrouter-compiler",
            "com.iqiyi.component.mm:mm-compiler",
            "dk.ilios:realmfieldnameshelper",
            "com.lianjia.common.android.router2:compiler",
            "com.smile.gifshow.annotation:invoker_processor",
            "com.f2prateek.dart:dart-processor",
            "com.sankuai.waimai.router:compiler",
            "org.qiyi.card:card-action-compiler",
            "com.iqiyi.video:eventbus-annotation-processor",
            "ly.img.android.pesdk:build-processor",
            "org.apache.logging.log4j:log4j-core",
            "com.github.jokermonn:permissions4m",
            "com.arialyy.aria:aria-compiler",
            "com.smile.gifshow.annotation:provide_processor",
            "com.smile.gifshow.annotation:preference_processor",
            "com.smile.gifshow.annotation:plugin_processor",
            "org.inferred:freebuilder",
            "com.smile.gifshow.annotation:router_processor"
        )

        private fun libraryHasKtxExtension(mavenName: String): Boolean {
            // From https://developer.android.com/kotlin/ktx/extensions-list.
            return when (mavenName) {
                "androidx.activity:activity",
                "androidx.collection:collection",
                "androidx.core:core",
                "androidx.dynamicanimation:dynamicanimation",
                "androidx.fragment:fragment",
                "androidx.lifecycle:lifecycle-livedata-core",
                "androidx.lifecycle:lifecycle-livedata",
                "androidx.lifecycle:lifecycle-reactivestreams",
                "androidx.lifecycle:lifecycle-runtime",
                "androidx.lifecycle:lifecycle-viewmodel",
                "androidx.navigation:navigation-runtime",
                "androidx.navigation:navigation-fragment",
                "androidx.navigation:navigation-ui",
                "androidx.paging:paging-common",
                "androidx.paging:paging-runtime",
                "androidx.paging:paging-rxjava2",
                "androidx.palette:palette",
                "androidx.preference:preference",
                "androidx.slice:slice-builders",
                "androidx.sqlite:sqlite",
                "com.google.android.play:core" -> true
                else -> false
            }
        }
    }
}
