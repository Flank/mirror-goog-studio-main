/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.dsl

import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.Variant
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer

/**
 * Common extension properties for the Android Application. Library and Dynamic Feature Plugins.
 *
 *
 * Only the Android Gradle Plugin should create instances of this interface.
 */
interface CommonExtension<
        AndroidSourceSetT : AndroidSourceSet,
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : ProductFlavor,
        SigningConfigT : SigningConfig,
        VariantBuilderT : VariantBuilder,
        VariantT : Variant> {

    /**
     * Specifies options for the Android Asset Packaging Tool (AAPT).
     *
     * For more information about the properties you can configure in this block, see [AaptOptions].
     */
    @get:Incubating
    @Deprecated("Replaced by ", replaceWith = ReplaceWith("androidResources"))
    val aaptOptions: AaptOptions

    /**
     * Specifies options for the Android Asset Packaging Tool (AAPT).
     *
     * For more information about the properties you can configure in this block, see [AaptOptions].
     */
    @Incubating
    @Deprecated("Replaced by ", replaceWith = ReplaceWith("androidResources"))
    fun aaptOptions(action: AaptOptions.() -> Unit)

    /**
     * Specifies options related to the processing of Android Resources.
     *
     * For more information about the properties you can configure in this block, see [AndroidResources].
     */
    val androidResources: AndroidResources

    /**
     * Specifies options related to the processing of Android Resources.
     *
     * For more information about the properties you can configure in this block, see [AndroidResources].
     */
    fun androidResources(action: AndroidResources.() -> Unit)

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [AdbOptions].
     */
    @get:Incubating
    val adbOptions: AdbOptions

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [AdbOptions].
     */
    @Incubating
    fun adbOptions(action: AdbOptions.() -> Unit)

    /**
     * Specifies Java compiler options, such as the language level of the Java source code and
     * generated bytecode.
     *
     * For more information about the properties you can configure in this block, see [CompileOptions].
     */
    val compileOptions: CompileOptions

    /**
     * Specifies Java compiler options, such as the language level of the Java source code and
     * generated bytecode.
     *
     * For more information about the properties you can configure in this block, see [CompileOptions].
     */
    fun compileOptions(action: CompileOptions.() -> Unit)

    /**
     * A list of build features that can be enabled or disabled on the Android Project.
     */
    @get:Incubating
    val buildFeatures: BuildFeaturesT

    /**
     * A list of build features that can be enabled or disabled on the Android Project.
     */
    @Incubating
    fun buildFeatures(action: BuildFeaturesT.() -> Unit)

    /**
     * Encapsulates all build type configurations for this project.
     *
     * Unlike using [ProductFlavor] to create
     * different versions of your project that you expect to co-exist on a single device, build
     * types determine how Gradle builds and packages each version of your project. Developers
     * typically use them to configure projects for various stages of a development lifecycle. For
     * example, when creating a new project from Android Studio, the Android plugin configures a
     * 'debug' and 'release' build type for you. By default, the 'debug' build type enables
     * debugging options and signs your APK with a generic debug keystore. Conversely, The 'release'
     * build type strips out debug symbols and requires you to
     * [create a release key and keystore](https://developer.android.com/studio/publish/app-signing.html#sign-apk)
     * for your app. You can then combine build types with product flavors to
     * [create build variants](https://developer.android.com/studio/build/build-variants.html).
     *
     * @see [BuildType
     */
    @get:Incubating
    val buildTypes: NamedDomainObjectContainer<BuildTypeT>

    /**
     * Encapsulates all build type configurations for this project.
     *
     * For more information about the properties you can configure in this block, see [BuildType]
     */
    @Incubating
    fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildTypeT>>)

    /**
     * Shortcut extension method to allow easy access to the predefined `debug` [BuildType]
     *
     * For example:
     * ```
     *  android {
     *      buildTypes {
     *          debug {
     *              // ...
     *          }
     *      }
     * }
     * ```
     */
    @Incubating
    fun NamedDomainObjectContainer<BuildTypeT>.debug(action: BuildTypeT.() -> Unit)
    /**
     * Shortcut extension method to allow easy access to the predefined `release` [BuildType]
     *
     * For example:
     * ```
     *  android {
     *      buildTypes {
     *          release {
     *              // ...
     *          }
     *      }
     * }
     * ```
     */
    @Incubating
    fun NamedDomainObjectContainer<BuildTypeT>.release(action: BuildTypeT.() -> Unit)

    /**
     * Specifies options for the
     * [Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).
     *
     * For more information about the properties you can configure in this block, see [DataBinding]
     */
    val dataBinding: DataBinding

    /**
     * Specifies options for the
     * [Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).
     *
     * For more information about the properties you can configure in this block, see [DataBinding]
     */
    fun dataBinding(action: DataBinding.() -> Unit)

    /**
     * Configure the gathering of code-coverage from tests.
     *
     * This is replaced by [testCoverage].
     */
    @get:Incubating
    @Deprecated("Renamed to testCoverage", replaceWith = ReplaceWith("testCoverage"))
    val jacoco: JacocoOptions

    /**
     * Configure the gathering of code-coverage from tests.
     *
     * This is replaced by [testCoverage].
     */
    @Incubating
    @Deprecated("Renamed to testCoverage", replaceWith = ReplaceWith("testCoverage"))
    fun jacoco(action: JacocoOptions.() -> Unit)

    /**
     * Configure the gathering of code-coverage from tests.
     *
     * To override the JaCoCo version that is used for offline instrumentation and coverage report,
     * add the following to `build.gradle` file:
     *
     * ```
     * android {
     *     testCoverage {
     *         jacocoVersion "<jacoco-version>"
     *     }
     * }
     * ```
     *
     * For more information about the properties you can configure in this block, see [TestCoverage].
     */
    val testCoverage: TestCoverage

    /**
     * Configure the gathering of code-coverage from tests.
     *
     * To override the JaCoCo version that is used for offline instrumentation and coverage report,
     * add the following to `build.gradle` file:
     *
     * ```
     * android {
     *     testCoverage {
     *         jacocoVersion "<jacoco-version>"
     *     }
     * }
     * ```
     *
     * For more information about the properties you can configure in this block, see [TestCoverage].
     */
    fun testCoverage(action: TestCoverage.() -> Unit)

    /**
     * Specifies options for the lint tool.
     *
     * For more information about the properties you can configure in this block, see [LintOptions].
     */
    @get:Incubating
    val lintOptions: LintOptions

    /**
     * Specifies options for the lint tool.
     *
     * For more information about the properties you can configure in this block, see [LintOptions].
     */
    @Incubating
    fun lintOptions(action: LintOptions.() -> Unit)

    /**
     * Specifies options and rules that determine which files the Android plugin packages into your
     * APK.
     *
     * For more information about the properties you can configure in this block, see [PackagingOptions].
     */
    @get:Incubating
    val packagingOptions: PackagingOptions

    /**
     * Specifies options and rules that determine which files the Android plugin packages into your
     * APK.
     *
     * For more information about the properties you can configure in this block, see [PackagingOptions].
     */
    @Incubating
    fun packagingOptions(action: PackagingOptions.() -> Unit)

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     *
     * Product flavors represent different versions of your project that you expect to co-exist
     * on a single device, the Google Play store, or repository. For example, you can configure
     * 'demo' and 'full' product flavors for your app, and each of those flavors can specify
     * different features, device requirements, resources, and application ID's--while sharing
     * common source code and resources. So, product flavors allow you to output different versions
     * of your project by simply changing only the components and settings that are different
     * between them.
     *
     *
     * Configuring product flavors is similar to
     * [configuring build types](https://developer.android.com/studio/build/build-variants.html#build-types):
     * add them to the `productFlavors` block of your project's `build.gradle` file
     * and configure the settings you want.
     * Product flavors support the same properties as the `defaultConfig`
     * block--this is because `defaultConfig` defines an object that the plugin uses as the base
     * configuration for all other flavors. Each flavor you configure can then override any of the
     * default values in `defaultConfig`, such as the
     * [`applicationId`](https://d.android.com/studio/build/application-id.html).
     *
     *
     * When using Android plugin 3.0.0 and higher, *each flavor must belong to a
     * [`flavorDimension`](com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[]))
     * value*. By default, when you specify only one
     * dimension, all flavors you configure belong to that dimension. If you specify more than one
     * flavor dimension, you need to manually assign each flavor to a dimension. To learn more, read
     * [Use Flavor Dimensions for variant-aware dependency management](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware).
     *
     *
     * When you configure product flavors, the Android plugin automatically combines them with
     * your [BuildType] configurations to
     * [create build variants](https://developer.android.com/studio/build/build-variants.html).
     * If the plugin creates certain build variants that you don't want, you can
     * [filter variants](https://developer.android.com/studio/build/build-variants.html#filter-variants).
     *
     * @see [ProductFlavor]
     */
    @get:Incubating
    val productFlavors: NamedDomainObjectContainer<ProductFlavorT>

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     * For more information about the properties you can configure in this block,
     * see [ProductFlavor]
     */
    @Incubating
    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavorT>>)


    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * You can override any `defaultConfig` property when
     * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     *
     * For more information about the properties you can configure in this block, see [DefaultConfig].
     */
    @get:Incubating
    val defaultConfig: DefaultConfigT

    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * You can override any `defaultConfig` property when
     * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     *
     * For more information about the properties you can configure in this block, see [DefaultConfig].
     */
    @Incubating
    fun defaultConfig(action: Action<DefaultConfigT>)


    /**
     * Encapsulates signing configurations that you can apply to [ ] and [ ] configurations.
     *
     *
     * Android requires that all APKs be digitally signed with a certificate before they can be
     * installed onto a device. When deploying a debug version of your project from Android Studio,
     * the Android plugin automatically signs your APK with a generic debug certificate. However, to
     * build an APK for release, you must
     * [sign the APK](https://developer.android.com/studio/publish/app-signing.html)
     * with a release key and keystore.
     * You can do this by either
     * [using the Android Studio UI](https://developer.android.com/studio/publish/app-signing.html#sign-apk)
     * or manually
     * [configuring your `build.gradle` file](https://developer.android.com/studio/publish/app-signing.html#gradle-sign).
     *
     * @see [SigningConfig]
     */
    @get:Incubating
    val signingConfigs: NamedDomainObjectContainer<SigningConfigT>

    /**
     * Encapsulates signing configurations that you can apply to
     * [BuildType] and [ProductFlavor] configurations.
     *
     * For more information about the properties you can configure in this block,
     * see [SigningConfig].
     */
    @Incubating
    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfigT>>)


    /**
     * Specifies options for external native build using [CMake](https://cmake.org/) or
     * [ndk-build](https://developer.android.com/ndk/guides/ndk-build.html).
     *
     *
     * When using
     * [Android Studio 2.2 or higher](https://developer.android.com/studio/index.html) with
     * [Android plugin 2.2.0 or higher](https://developer.android.com/studio/releases/gradle-plugin.html),
     * you can compile C and C++ code into a native library that Gradle packages into your APK.
     *
     *
     * To learn more, read
     * [Add C and C++ Code to Your Project](https://developer.android.com/studio/projects/add-native-code.html).
     *
     * @see ExternalNativeBuild
     *
     * @since 2.2.0
     */

    @get:Incubating
    val externalNativeBuild: ExternalNativeBuild
    /**
     * Specifies options for external native build using [CMake](https://cmake.org/) or
     * [ndk-build](https://developer.android.com/ndk/guides/ndk-build.html).
     *
     *
     * When using
     * [Android Studio 2.2 or higher](https://developer.android.com/studio/index.html) with
     * [Android plugin 2.2.0 or higher](https://developer.android.com/studio/releases/gradle-plugin.html),
     * you can compile C and C++ code into a native library that Gradle packages into your APK.
     *
     *
     * To learn more, read
     * [Add C and C++ Code to Your Project](https://developer.android.com/studio/projects/add-native-code.html).
     *
     * @see ExternalNativeBuild
     *
     * @since 2.2.0
     */
    @Incubating
    fun externalNativeBuild(action: ExternalNativeBuild.()->Unit)

    /**
     * Specifies options for how the Android plugin should run local and instrumented tests.
     *
     * For more information about the properties you can configure in this block, see [TestOptions].
     */
    @get:Incubating
    val testOptions: TestOptions

    /**
     * Specifies options for how the Android plugin should run local and instrumented tests.
     *
     * For more information about the properties you can configure in this block, see [TestOptions].
     */
    @Incubating
    fun testOptions(action: TestOptions.() -> Unit)

    /**
     * Specifies configurations for
     * [building multiple APKs](https://developer.android.com/studio/build/configure-apk-splits.html)
     * or APK splits.
     *
     * For more information about the properties you can configure in this block, see [Splits].
     */
    @get:Incubating
    val splits: Splits

    /**
     * Specifies configurations for
     * [building multiple APKs](https://developer.android.com/studio/build/configure-apk-splits.html)
     * or APK splits.
     *
     * For more information about the properties you can configure in this block, see [Splits].
     */
    @Incubating
    fun splits(action: Splits.() -> Unit)

    @get:Incubating
    val composeOptions: ComposeOptions

    @Incubating
    fun composeOptions(action: ComposeOptions.() -> Unit)

    /**
     * Encapsulates source set configurations for all variants.
     *
     * Note that the Android plugin uses its own implementation of source sets. For more
     * information about the properties you can configure in this block, see [AndroidSourceSet].
     */
    @get:Incubating
    val sourceSets: NamedDomainObjectContainer<AndroidSourceSetT>

    /**
     * Encapsulates source set configurations for all variants.
     *
     * Note that the Android plugin uses its own implementation of source sets. For more
     * information about the properties you can configure in this block, see [AndroidSourceSet].
     */
    @Incubating
    fun sourceSets(action: NamedDomainObjectContainer<AndroidSourceSetT>.() -> Unit)

    /**
     * Specifies the names of product flavor dimensions for this project.
     *
     * When configuring product flavors with Android plugin 3.0.0 and higher, you must specify at
     * least one flavor dimension, using the
     * [`flavorDimensions`][flavorDimensions] property, and then assign each flavor to a dimension.
     * Otherwise, you will get the following build error:
     *
     * ```
     * Error:All flavors must now belong to a named flavor dimension.
     * The flavor 'flavor_name' is not assigned to a flavor dimension.
     * ```
     *
     * By default, when you specify only one dimension, all flavors you configure automatically
     * belong to that dimension. If you specify more than one dimension, you need to manually assign
     * each flavor to a dimension, as shown in the sample below.
     *
     * Flavor dimensions allow you to create groups of product flavors that you can combine with
     * flavors from other flavor dimensions. For example, you can have one dimension that includes a
     * 'free' and 'paid' version of your app, and another dimension for flavors that support
     * different API levels, such as 'minApi21' and 'minApi24'. The Android plugin can then combine
     * flavors from these dimensions—including their settings, code, and resources—to create
     * variants such as 'debugFreeMinApi21' and 'releasePaidMinApi24', and so on. The sample below
     * shows you how to specify flavor dimensions and add product flavors to them.
     *
     * ```
     * android {
     *     ...
     *     // Specifies the flavor dimensions you want to use. The order in which you
     *     // list each dimension determines its priority, from highest to lowest,
     *     // when Gradle merges variant sources and configurations. You must assign
     *     // each product flavor you configure to one of the flavor dimensions.
     *     flavorDimensions 'api', 'version'
     *
     *     productFlavors {
     *       demo {
     *         // Assigns this product flavor to the 'version' flavor dimension.
     *         dimension 'version'
     *         ...
     *     }
     *
     *       full {
     *         dimension 'version'
     *         ...
     *       }
     *
     *       minApi24 {
     *         // Assigns this flavor to the 'api' dimension.
     *         dimension 'api'
     *         minSdkVersion '24'
     *         versionNameSuffix "-minApi24"
     *         ...
     *       }
     *
     *       minApi21 {
     *         dimension "api"
     *         minSdkVersion '21'
     *         versionNameSuffix "-minApi21"
     *         ...
     *       }
     *    }
     * }
     * ```
     *
     * To learn more, read
     * [Combine multiple flavors](https://developer.android.com/studio/build/build-variants.html#flavor-dimensions).
     */
    @get:Incubating
    val flavorDimensions: MutableList<String>

    /**
     * Specifies this project's resource prefix to Android Studio for editor features, such as Lint
     * checks. This property is useful only when using Android Studio.
     *
     * Including unique prefixes for project resources helps avoid naming collisions with
     * resources from other projects.
     *
     * For example, when creating a library with String resources,
     * you may want to name each resource with a unique prefix, such as "`mylib_`"
     * to avoid naming collisions with similar resources that the consumer defines.
     *
     * You can then specify this prefix, as shown below, so that Android Studio expects this prefix
     * when you name project resources:
     *
     * ```
     * // This property is useful only when developing your project in Android Studio.
     * resourcePrefix 'mylib_'
     * ```
     */
    @get:Incubating
    @set:Incubating
    var resourcePrefix: String?

    /**
     * Requires the specified NDK version to be used.
     *
     * Use this to specify a fixed NDK version. Without this, each new version of the Android
     * Gradle Plugin will choose a specific version of NDK to use, so upgrading the plugin also
     * means upgrading the NDK. Locking to a specific version can increase repeatability of the
     * build.
     *
     * ```
     * android {
     *     // Use a fixed NDK version
     *     ndkVersion '20.1.5948944'
     * }
     * ```
     *
     * If this field is specified then android.ndkPath may not be specified.
     *
     * The required format of the version is <code>major.minor.build</code>. It's not legal to
     * specify less precision.
     * If `ndk.dir` is specified in `local.properties` file then the NDK that it points to must
     * match the `android.ndkVersion`.
     *
     * Prior to Android Gradle Plugin version 3.5, the highest installed version of NDK will be
     * used.
     * In Android Gradle Plugin 3.4, specifying `android.ndkVersion` was not an error, but the value
     * would be ignored.
     * Prior to Android Gradle Plugin version 3.4, it was illegal to specify `android.ndkVersion`.
     *
     * For additional information about NDK installation see
     * [Install and configure the NDK](https://developer.android.com/studio/projects/install-ndk).
     */
    @get:Incubating
    @set:Incubating
    var ndkVersion: String?

    /**
     * Requires the specified path to NDK be used.
     *
     * Use this to specify a path to the NDK folder to be used for C and C++ builds.
     *
     * ```
     * android {
     *     // Path to custom NDK location
     *     ndkPath '/path/to/ndk'
     * }
     * ```
     *
     * If this field is specified then android.ndkVersion may not be specified.
     *
     * For additional information about NDK installation see
     * [Install and configure the NDK](https://developer.android.com/studio/projects/install-ndk).
     */
    @get:Incubating
    @set:Incubating
    var ndkPath: String?

    /**
     * Specifies the version of the
     * [SDK Build Tools](https://developer.android.com/studio/releases/build-tools.html)
     * to use when building your project.
     *
     * When using Android plugin 3.0.0 or later, configuring this property is optional. By
     * default, the plugin uses the minimum version of the build tools required by the
     * [version of the plugin](https://developer.android.com/studio/releases/gradle-plugin.html#revisions)
     * you're using.
     * To specify a different version of the build tools for the plugin to use,
     * specify the version as follows:
     *
     * ```
     * android {
     *     // Specifying this property is optional.
     *     buildToolsVersion "26.0.0"
     * }
     * ```
     *
     * For a list of build tools releases, read
     * [the release notes](https://developer.android.com/studio/releases/build-tools.html#notes).
     *
     * Note that the value assigned to this property is parsed and stored in a normalized form,
     * so reading it back may give a slightly different result.
     */
    @get:Incubating
    @set:Incubating
    var buildToolsVersion: String

    /**
     * Includes the specified library to the classpath.
     *
     * You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * ```
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * ```
     *
     * To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories,
     * [add the libraries as dependencies](https://developer.android.com/studio/build/dependencies.html)
     * in the `dependencies` block. Note that Android plugin 3.0.0 and later introduce
     * [new dependency configurations](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations).
     * To learn more about Gradle dependencies, read
     * [Dependency Management Basics](https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html).
     *
     * @param name the name of the library.
     */
    @Incubating
    fun useLibrary(name: String)
    /**
     * Includes the specified library to the classpath.
     *
     * You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * ```
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * ```
     *
     * To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories,
     * [add the libraries as dependencies]("https://developer.android.com/studio/build/dependencies.html)
     * in the `dependencies` block. Note that Android plugin 3.0.0 and later introduce
     * [new dependency configurations](new dependency configurations).
     * To learn more about Gradle dependencies, read
     * [Dependency Management Basics](https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html)
     *
     * @param name the name of the library.
     * @param required if using the library requires a manifest entry, the entry will indicate that
     *     the library is not required.
     */
    @Incubating
    fun useLibrary(name: String, required: Boolean)

    @get:Incubating
    @Deprecated(
        message = "This API will be removed in AGP 7.0, replaced with AndroidComponents::sdkComponents")
    val sdkComponents: SdkComponents

    /**
     * Specifies the API level to compile your project against. The Android plugin requires you to
     * configure this property.
     *
     * This means your code can use only the Android APIs included in that API level and lower.
     * You can configure the compile sdk version by adding the following to the `android`
     * block: `compileSdk 26`.
     *
     * You should generally
     * [use the most up-to-date API level](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels)
     * available.
     * If you are planning to also support older API levels, it's good practice to
     * [use the Lint tool](https://developer.android.com/studio/write/lint.html)
     * to check if you are using APIs that are not available in earlier API levels.
     *
     * The value you assign to this property is parsed and stored in a normalized form, so
     * reading it back may return a slightly different value.
     */
    var compileSdk: Int?

    var compileSdkPreview: String?

    fun compileSdkAddon(vendor: String, name: String, version: Int)

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     *
     * This value supersedes any value specified by the `package` attribute in the source
     * AndroidManifest.xml, but doing a 'get' on this property will not retrieve the value specified
     * in the AndroidManifest.xml.
     */
    var namespace: String?
}
