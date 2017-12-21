/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * a Build Type. This is only the configuration of the build type.
 *
 * It does not include the sources or the dependencies. Those are available on the container
 * or in the artifact info.
 *
 * @see BuildTypeContainer
 * @see AndroidArtifact#getDependencies()
 */
public interface BuildType extends BaseConfig {

    /**
     * Returns the name of the build type.
     *
     * @return the name of the build type.
     */
    @Override
    @NonNull
    String getName();

    /**
     * Returns whether the build type is configured to generate a debuggable apk.
     *
     * @return true if the apk is debuggable
     */
    boolean isDebuggable();

    /**
     * Returns whether the build type is configured to be build with support for code coverage.
     *
     * @return true if code coverage is enabled.
     */
    boolean isTestCoverageEnabled();

    /**
     * Returns whether the build type is configured to be build with support for pseudolocales.
     *
     * @return true if code coverage is enabled.
     */
    boolean isPseudoLocalesEnabled();

    /**
     * Returns whether the build type is configured to generate an apk with debuggable native code.
     *
     * @return true if the apk is debuggable
     */
    boolean isJniDebuggable();

    /**
     * Returns whether the build type is configured to generate an apk with debuggable
     * renderscript code.
     *
     * @return true if the apk is debuggable
     */
    boolean isRenderscriptDebuggable();

    /**
     * Returns the optimization level of the renderscript compilation.
     *
     * @return the optimization level.
     */
    int getRenderscriptOptimLevel();

    /**
     * Specifies whether to enable code shrinking for this build type.
     *
     * <p>By default, when you enable code shrinking by setting this property to <code>true</code>,
     * the Android plugin uses ProGuard. However while deploying your app using Android Studio's <a
     * href="https://d.android.com/studio/run/index.html#instant-run">Instant Run</a> feature, which
     * doesn't support ProGuard, the plugin switches to using a custom experimental code shrinker.
     *
     * <p>If you experience issues using the experimental code shrinker, you can disable code
     * shrinking while using Instant Run by setting <a
     * href="http://google.github.io/android-gradle-dsl/current/com.android.build.gradle.internal.dsl.BuildType.html#com.android.build.gradle.internal.dsl.BuildType:useProguard">
     * <code>useProguard</code></a> to <code>true</code>.
     *
     * <p>To learn more, read <a
     * href="https://developer.android.com/studio/build/shrink-code.html">Shrink Your Code and
     * Resources</a>.
     *
     * @return true if code shrinking is enabled.
     */
    boolean isMinifyEnabled();

    /**
     * Return whether zipalign is enabled for this build type.
     *
     * @return true if zipalign is enabled.
     */
    boolean isZipAlignEnabled();

    /**
     * Returns whether the variant embeds the micro app.
     */
    boolean isEmbedMicroApp();

    /**
     * Returns the associated signing config or null if none are set on the build type.
     */
    @Nullable
    SigningConfig getSigningConfig();
}
