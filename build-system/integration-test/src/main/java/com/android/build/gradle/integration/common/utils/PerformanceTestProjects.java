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

package com.android.build.gradle.integration.common.utils;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PerformanceTestProjects {

    private static String generateLocalRepositoriesSnippet() {
        StringBuilder localRepositoriesSnippet = new StringBuilder();
        for (Path repo : GradleTestProject.getLocalRepositories()) {
            localRepositoriesSnippet.append(GradleTestProject.mavenSnippet(repo));
        }
        return localRepositoriesSnippet.toString();
    }

    public static void initializeAntennaPod(GradleTestProject mainProject) throws IOException {
        GradleTestProject project = mainProject.getSubproject("AntennaPod");

        Files.move(
                mainProject.file(SdkConstants.FN_LOCAL_PROPERTIES).toPath(),
                project.file(SdkConstants.FN_LOCAL_PROPERTIES).toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath \"com.android.tools.build:gradle:\\d+.\\d+.\\d+\"",
                "classpath \"com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + '"');



        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                generateLocalRepositoriesSnippet().replace("\\", "\\\\"));

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "buildToolsVersion = \".*\"",
                "buildToolsVersion = \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\" // Updated by test");

        List<String> subprojects =
                ImmutableList.of("AudioPlayer/library", "afollestad/commons", "afollestad/core");

        for (String subproject: subprojects) {
            TestFileUtils.searchAndReplace(
                    mainProject.getSubproject(subproject).getBuildFile(),
                    "buildToolsVersion \".*\"",
                    "buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                            + "\" // Updated by test");
        }

        // Update the support lib and fix resulting issue:
        List<File> filesWithSupportLibVersion =
                ImmutableList.of(
                        project.getBuildFile(),
                        mainProject.file("afollestad/core/build.gradle"),
                        mainProject.file("afollestad/commons/build.gradle"));

        for (File buildFile : filesWithSupportLibVersion) {
            TestFileUtils.searchAndReplace(
                    buildFile,
                    " 23",
                    " " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION);

            TestFileUtils.searchAndReplace(
                    buildFile,
                    "23.1.1",
                    GradleTestProject.SUPPORT_LIB_VERSION);
        }

        TestFileUtils.searchAndReplace(
                mainProject.file("afollestad/core/build.gradle"),
                "minSdkVersion 8",
                "minSdkVersion 10 // Updated by test");

        TestFileUtils.searchAndReplace(
                mainProject.file("afollestad/commons/build.gradle"),
                "minSdkVersion 8",
                "minSdkVersion 10 // Updated by test");

        TestFileUtils.searchAndReplace(
                mainProject.file("afollestad/core/src/main/res/values-v11/styles.xml"),
                "abc_ic_ab_back_mtrl_am_alpha",
                "abc_ic_ab_back_material");

        TestFileUtils.searchAndReplace(
                mainProject.file("AntennaPod/core/src/main/res/values/styles.xml"),
                "<item name=\"attr/",
                "<item type=\"att\" name=\""
        );

        TestFileUtils.searchAndReplace(
                project.file("app/build.gradle"),
                ",\\s*commit: \"git rev-parse --short HEAD\".execute\\(\\).text\\]",
                "]");

        antennaPodSetRetrolambdaEnabled(mainProject, false);
    }

    public static void antennaPodSetRetrolambdaEnabled(
            @NonNull GradleTestProject mainProject, boolean enableRetrolambda) throws IOException {
        GradleTestProject project = mainProject.getSubproject("AntennaPod");

        String searchPrefix;
        String replacePrefix;
        if (enableRetrolambda) {
            searchPrefix = "//";
            replacePrefix = "";
        } else {
            searchPrefix = "";
            replacePrefix = "//";
        }

        TestFileUtils.searchAndReplace(
                project.file("app/build.gradle"),
                searchPrefix + "apply plugin: \"me.tatarka.retrolambda\"",
                replacePrefix + "apply plugin: \"me.tatarka.retrolambda\"");

        TestFileUtils.searchAndReplace(
                mainProject.file("AntennaPod/core/build.gradle"),
                searchPrefix + "apply plugin: \"me.tatarka.retrolambda\"",
                replacePrefix + "apply plugin: \"me.tatarka.retrolambda\"");
    }


    public static void initializeWordpress(@NonNull GradleTestProject project) throws IOException {

        Files.copy(
                project.file("WordPress/gradle.properties-example").toPath(),
                project.file("WordPress/gradle.properties").toPath());


        String localRepositoriesSnippet = generateLocalRepositoriesSnippet();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "buildscript {\n"
                                + "    repositories {\n"
                                + "        %1$s\n"
                                + "    }\n"
                                + "    dependencies {\n"
                                + "        classpath 'com.android.tools.build:gradle:%2$s'\n"
                                + "    }\n"
                                + "}\n",
                        localRepositoriesSnippet,
                        GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION));

        List<Path> buildGradleFiles =
                Stream.of(
                        "WordPress/build.gradle",
                        "libs/utils/WordPressUtils/build.gradle",
                        "libs/editor/example/build.gradle",
                        "libs/editor/WordPressEditor/build.gradle",
                        "libs/networking/WordPressNetworking/build.gradle",
                        "libs/analytics/WordPressAnalytics/build.gradle")
                        .map(name -> project.file(name).toPath())
                        .collect(Collectors.toList());

        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "maven \\{ url (('.*')|(\".*\")) \\}",
                "");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "productFlavors \\{",
                "flavorDimensions 'version'\n"
                        + "productFlavors {");

        // replace manual variant aware dependency with automatic one.
        // remove one line and edit the other for each dependency.
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("releaseCompile project(path:':libs:utils:WordPressUtils', configuration: 'release')"),
                "compile project(path:':libs:utils:WordPressUtils') // replaced by test\n");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("debugCompile project(path:':libs:utils:WordPressUtils', configuration: 'debug')"),
                "");

        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("releaseCompile project(path:':libs:networking:WordPressNetworking', configuration: 'release')"),
                "compile project(path:':libs:networking:WordPressNetworking') // replaced by test\n");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("debugCompile project(path:':libs:networking:WordPressNetworking', configuration: 'debug')"),
                "");

        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("releaseCompile project(path:':libs:analytics:WordPressAnalytics', configuration: 'release')"),
                "compile project(path:':libs:analytics:WordPressAnalytics') // replaced by test\n");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("debugCompile project(path:':libs:analytics:WordPressAnalytics', configuration: 'debug')"),
                "");

        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("releaseCompile project(path:':libs:editor:WordPressEditor', configuration: 'release')"),
                "compile project(path:':libs:editor:WordPressEditor') // replaced by test\n");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote("debugCompile project(path:':libs:editor:WordPressEditor', configuration: 'debug')"),
                "");


        for (Path file : buildGradleFiles) {
            TestFileUtils.searchAndReplace(
                    file, "classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'", "");

            TestFileUtils.searchAndReplace(
                    file,
                    "jcenter\\(\\)",
                    localRepositoriesSnippet.replace("\\", "\\\\"));

            TestFileUtils.searchAndReplace(
                    file,
                    "buildToolsVersion \"[^\"]+\"",
                    String.format("buildToolsVersion \"%s\"", AndroidBuilder.MIN_BUILD_TOOLS_REV));
        }


        // Remove crashlytics
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "classpath 'io.fabric.tools:gradle:1.+'",
                "");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "apply plugin: 'io.fabric'",
                "");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "compile\\('com.crashlytics.sdk.android:crashlytics:2.5.5\\@aar'\\) \\{\n"
                        + "        transitive = true;\n"
                        + "    \\}",
                "");

        TestFileUtils.searchAndReplace(
                project.file("WordPress/src/main/java/org/wordpress/android/util/CrashlyticsUtils.java"),
                "\n     ",
                "\n     //"
        );

        TestFileUtils.searchAndReplace(
                project.file("WordPress/src/main/java/org/wordpress/android/util/CrashlyticsUtils.java"),
                "\nimport",
                "\n// import"
        );

        TestFileUtils.searchAndReplace(
                project.file("WordPress/src/main/java/org/wordpress/android/WordPress.java"),
                "\n(.*Fabric)",
                "\n// $1");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/src/main/java/org/wordpress/android/WordPress.java"),
                "import com\\.crashlytics\\.android\\.Crashlytics;",
                "//import com.crashlytics.android.Crashlytics;");

        //TODO: Upstream some of this?

        // added to force version upgrade.
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "\ndependencies \\{\n",
                "dependencies {\n"
                        + "    compile 'org.jetbrains.kotlin:kotlin-stdlib:"
                        + SdkConstants.KOTLIN_LATEST_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:support-v4:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "'\n");

        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "compile 'org.wordpress:drag-sort-listview:0.6.1'",
                "compile ('org.wordpress:drag-sort-listview:0.6.1') {\n"
                        + "    exclude group:'com.android.support'\n"
                        + "}");
        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "compile 'org.wordpress:slidinguppanel:1.0.0'",
                "compile ('org.wordpress:slidinguppanel:1.0.0') {\n"
                        + "    exclude group:'com.android.support'\n"
                        + "}");

        TestFileUtils.searchAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "classpath 'com\\.novoda:bintray-release:0\\.3\\.4'",
                "");
        TestFileUtils.searchAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "apply plugin: 'com\\.novoda\\.bintray-release'",
                "");
        TestFileUtils.searchAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "publish \\{[\\s\\S]*\\}",
                "");

        TestFileUtils.searchAndReplace(
                project.file("libs/editor/WordPressEditor/build.gradle"),
                "\ndependencies \\{\n",
                "dependencies {\n"
                        + "    compile 'com.android.support:support-v13:" + GradleTestProject.SUPPORT_LIB_VERSION + "'\n");

        TestFileUtils.searchAndReplace(
                project.file("libs/networking/WordPressNetworking/build.gradle"),
                "maven \\{ url 'http://wordpress-mobile\\.github\\.io/WordPress-Android' \\}",
                "");

        List<Path> useEditor =
                Stream.of(
                        "libs/editor/WordPressEditor/build.gradle",
                        "libs/networking/WordPressNetworking/build.gradle",
                        "libs/analytics/WordPressAnalytics/build.gradle")
                        .map(name -> project.file(name).toPath())
                        .collect(Collectors.toList());
        for (Path path: useEditor) {
            TestFileUtils.searchAndReplace(
                    path,
                    "compile 'org\\.wordpress:utils:1\\.11\\.0'",
                    "compile project(':libs:utils:WordPressUtils')\n");
        }

        // There is an extraneous BOM in the values-ja/strings.xml
        Files.copy(
                project.file("WordPress/src/main/res/values-en-rCA/strings.xml").toPath(),
                project.file("WordPress/src/main/res/values-ja/strings.xml").toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        List<File> filesWithSupportLibVersion =
                ImmutableList.of(
                        project.file("WordPress/build.gradle"),
                        project.file("libs/editor/WordPressEditor/build.gradle"),
                        project.file("libs/utils/WordPressUtils/build.gradle"));

        // Replace support lib version
        for (File buildFile : filesWithSupportLibVersion) {
            TestFileUtils.searchAndReplace(
                    buildFile,
                    "24.2.1",
                    GradleTestProject.SUPPORT_LIB_VERSION);
        }

        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "9.0.2",
                GradleTestProject.PLAY_SERVICES_VERSION);

        TestFileUtils.searchAndReplace(
                project.file("WordPress/build.gradle"),
                "androidTestCompile 'com.squareup.okhttp:mockwebserver:2.7.5'",
                "androidTestCompile 'com.squareup.okhttp:mockwebserver:2.7.4'");

        TestFileUtils.searchAndReplace(
                project.file("WordPress/src/main/AndroidManifest.xml"),
                "<action android:name=\"com\\.google\\.android\\.c2dm\\.intent\\.REGISTRATION\" />",
                "");

    }


    public static void initializeUberSkeleton(@NonNull GradleTestProject project)
            throws IOException {

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                PerformanceTestProjects.generateLocalRepositoriesSnippet());

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'",
                "classpath 'com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + "'");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "(classpath 'com.uber:okbuck:[^']+')", "// $0");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "(classpath 'com.jakewharton:butterknife-gradle-plugin:8.4.0')",
                "// $1");

        String content = new String(Files.readAllBytes(project.getBuildFile().toPath()));
        int pos = content.indexOf("apply plugin: 'com.uber");
        Files.write(project.getBuildFile().toPath(), content.substring(0, pos - 1).getBytes());

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "buildToolsVersion: '\\d+.\\d+.\\d+',",
                "buildToolsVersion: '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "',");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "supportVersion *: '\\d+.\\d+.\\d+',",
                "supportVersion: '" + GradleTestProject.SUPPORT_LIB_VERSION + "',");

        TestFileUtils.searchAndReplace(
                project.file("build.gradle"),
                "(force 'com.android.support:[^:]*):[^']*'",
                "$1:" + GradleTestProject.SUPPORT_LIB_VERSION + "'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"), "('io.reactivex:rxjava):[^']*'", "$1:1.2.3'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.jakewharton:butterknife[^:]*):[^']*'",
                "$1:8.4.0'");
        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.squareup.okio:okio):[^']*'",
                "$1:1.9.0'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.jakewharton.rxbinding:rxbinding[^:]*):[^']+'",
                "$1:1.0.0'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.google.auto.value:auto-value):[^']*'",
                "$1:1.3'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.google.code.gson:gson):[^']+'",
                "$1:2.8.0'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "def support = \\[",
                "def support = [\n"
                        + "leanback : \"com.android.support:leanback-v17:\\${versions.supportVersion}\",\n"
                        + "mediarouter : \"com.android.support:mediarouter-v7:\\${versions.supportVersion}\",\n");

        TestFileUtils.appendToFile(
                project.file("dependencies.gradle"),
                "\n\n// Fixes for support lib versions.\n"
                        + "ext.deps.other.appcompat = [\n"
                        + "        ext.deps.support.appCompat,\n"
                        + "        ext.deps.other.appcompat,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.external.butterKnife = [\n"
                        + "        ext.deps.support.annotations,\n"
                        + "        ext.deps.external.butterKnife,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.apt.butterKnifeCompiler = [\n"
                        + "        ext.deps.support.annotations,\n"
                        + "        ext.deps.apt.butterKnifeCompiler,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.cast = [\n"
                        + "        ext.deps.other.cast,\n"
                        + "        ext.deps.support.mediarouter,\n"
                        + "        ext.deps.support.appCompat\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.design = [\n"
                        + "        ext.deps.support.design,\n"
                        + "        ext.deps.other.design,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.facebook = [\n"
                        + "        ext.deps.other.facebook,\n"
                        + "        ext.deps.support.cardView,\n"
                        + "        \"com.android.support:customtabs:${versions.supportVersion}\",\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.fresco = [\n"
                        + "        ext.deps.other.fresco,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.googleMap = [\n"
                        + "        ext.deps.other.googleMap,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.leanback = [\n"
                        + "        ext.deps.other.leanback,\n"
                        + "        ext.deps.support.leanback,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.playServices.maps = [\n"
                        + "        ext.deps.playServices.maps,\n"
                        + "        ext.deps.support.appCompat,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.rave = [\n"
                        + "        ext.deps.other.gson,\n"
                        + "        ext.deps.other.rave,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.recyclerview = [\n"
                        + "        ext.deps.support.recyclerView,\n"
                        + "        ext.deps.other.recyclerview,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.utils = [\n"
                        + "        ext.deps.other.utils,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "\n // End support lib version fixes. \n");

        // Fix project compilation.
        TestFileUtils.searchAndReplace(
                project.file("outissue/cyclus/build.gradle"),
                "dependencies \\{\n",
                "dependencies {\n"
                        + "compile deps.support.leanback\n"
                        + "compile deps.support.appCompat\n"
                        + "compile deps.external.rxjava\n");

        TestFileUtils.searchAndReplace(
                project.file("outissue/embrace/build.gradle"),
                "dependencies \\{\n",
                "dependencies { compile deps.external.rxjava\n");

        TestFileUtils.searchAndReplace(
                project.file("outissue/nutate/build.gradle"),
                "dependencies \\{\n",
                "dependencies { compile deps.support.mediarouter\n");

        // Remove butterknife plugin.
        for (String path :
                ImmutableList.of(
                        "outissue/carnally",
                        "outissue/airified",
                        "Padraig/follicle",
                        "outissue/Glumaceae",
                        "fratry/sodden",
                        "subvola/zelator",
                        "subvola/doored",
                        "subvola/transpire",
                        "subvola/atbash",
                        "subvola/gorgoneum/Chordata",
                        "subvola/gorgoneum/metanilic/agaric",
                        "subvola/gorgoneum/teerer/polytonal",
                        "subvola/gorgoneum/teerer/Cuphea",
                        "harvestry/Timbira")) {
            TestFileUtils.searchAndReplace(
                    project.file(path + "/build.gradle"),
                    "apply plugin: 'com.jakewharton.butterknife'",
                    "/* $0 */");
        }

        // Remove the android-apt plugin

        Set<String> aptConfigurationProjects =
                ImmutableSet.of(
                        "Padraig/endocoele",
                        "Padraig/follicle",
                        "Padraig/ratafee",
                        "Tripoline",
                        "fratry/Cosmati",
                        "fratry/Krapina",
                        "fratry/cepaceous",
                        "fratry/crankum",
                        "fratry/crapple",
                        "fratry/crippling",
                        "fratry/endothys",
                        "fratry/fortunate",
                        "fratry/halsen",
                        "fratry/linotype",
                        "fratry/matchy",
                        "fratry/passbook",
                        "fratry/psoriasis",
                        "fratry/savory",
                        "fratry/sodden",
                        "fratry/subradius",
                        "fratry/wiredraw",
                        "harvestry/Bokhara",
                        "harvestry/Timbira",
                        "harvestry/digallate",
                        "harvestry/isocryme",
                        "harvestry/suchness",
                        "harvestry/thribble",
                        "outissue/Glumaceae",
                        "outissue/airified",
                        "outissue/carnally",
                        "outissue/caudate",
                        "outissue/eyesore",
                        "outissue/nonparty",
                        "outissue/nursing",
                        "outissue/situla",
                        "outissue/worldway",
                        "preprice",
                        "subvola/Dipnoi",
                        "subvola/Leporis",
                        "subvola/absconsa",
                        "subvola/aluminize",
                        "subvola/atbash",
                        "subvola/cleithral",
                        "subvola/copsewood",
                        "subvola/doored",
                        "subvola/emergency",
                        "subvola/gorgoneum/Chordata",
                        "subvola/gorgoneum/metanilic/agaric",
                        "subvola/gorgoneum/teerer/Cuphea",
                        "subvola/gorgoneum/teerer/Onondaga",
                        "subvola/gorgoneum/teerer/lucrific",
                        "subvola/gorgoneum/teerer/perscribe",
                        "subvola/gorgoneum/teerer/polytonal",
                        "subvola/gorgoneum/teerer/revalenta",
                        "subvola/gorgoneum/unwincing",
                        "subvola/graphite",
                        "subvola/haploidic",
                        "subvola/inhumanly",
                        "subvola/liming",
                        "subvola/ocracy",
                        "subvola/remigrate",
                        "subvola/suborder",
                        "subvola/tourer",
                        "subvola/transpire",
                        "subvola/unmilked",
                        "subvola/wordsmith",
                        "subvola/zealotic",
                        "subvola/zelator");
        for (String path : aptConfigurationProjects) {
            File buildDotGradle = project.file(path + "/build.gradle");
            TestFileUtils.searchAndReplace(
                    buildDotGradle, "apply plugin: 'com\\.neenbedankt\\.android-apt'", "/* $0 */");
            TestFileUtils.searchAndReplace(buildDotGradle, " apt ", " annotationProcessor ");
        }

        for (String path : ImmutableList.of("subvola/absconsa", "phthalic", "fratry/endothys")) {
            TestFileUtils.searchAndReplace(
                    project.file(path + "/build.gradle"), "estApt", "estAnnotationProcessor");
        }

        Set<String> aptPluginProjects =
                ImmutableSet.of(
                        "Padraig/arbitrate",
                        "Padraig/cuminoin",
                        "Padraig/decollete",
                        "Padraig/emerse",
                        "Padraig/limitary",
                        "Padraig/paegle",
                        "Padraig/quaestor/triduum",
                        "Padraig/signist",
                        "fratry/Ormond",
                        "fratry/assumpsit",
                        "fratry/asteep",
                        "fratry/audience",
                        "fratry/tentlet",
                        "harvestry/Savannah/penumbra",
                        "harvestry/eelgrass",
                        "harvestry/unwormy",
                        "outissue/aricine",
                        "outissue/bracciale",
                        "outissue/browntail",
                        "outissue/caricetum/midship",
                        "outissue/caricetum/scientist",
                        "outissue/caricetum/skiapod",
                        "outissue/coherence",
                        "outissue/cyclus",
                        "outissue/defusion",
                        "outissue/embrace",
                        "outissue/extended",
                        "outissue/gliadin",
                        "outissue/nonjurant",
                        "outissue/nonunion",
                        "outissue/nutate",
                        "outissue/oleometer",
                        "outissue/phasmatid",
                        "outissue/shortsome",
                        "outissue/synarchy",
                        "outissue/tetragram",
                        "phthalic",
                        "subvola/Brittany",
                        "subvola/Brittany",
                        "subvola/papistry");
        assertThat(aptPluginProjects).containsNoneIn(aptConfigurationProjects);
        for (String path : aptPluginProjects) {
            TestFileUtils.searchAndReplace(
                    project.file(path + "/build.gradle"),
                    "apply plugin: 'com\\.neenbedankt\\.android-apt'",
                    "/* $0 */");
        }

        // because we are testing the source generation which will trigger the test manifest
        // merging, minSdkVersion has to be at least 17
        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"), "(minSdkVersion *): \\d+,", "$1: 17,");

        Stream<Path> allBuildFiles =
                Files.find(
                        project.getTestDir().toPath(),
                        Integer.MAX_VALUE,
                        (path, attrs) -> path.getFileName().toString().equals("build.gradle"));
        Pattern appPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.application['\"]");
        Pattern libPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.library['\"]");
        allBuildFiles.forEach(
                buildGradle -> {
                    String fileContent;
                    try {
                        fileContent = new String(Files.readAllBytes(buildGradle));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    if (appPlugin.matcher(fileContent).find()
                            || libPlugin.matcher(fileContent).find()) {
                        try {
                            TestFileUtils.appendToFile(
                                    buildGradle.toFile(),
                                    "\n"
                                            + "android.defaultConfig.javaCompileOptions {\n"
                                            + "annotationProcessorOptions.includeCompileClasspath = true\n"
                                            + "}");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
    }

    public static void assertNoSyncErrors(@NonNull Map<String, AndroidProject> models) {
        models.forEach(
                (path, project) -> {
                    List<SyncIssue> severeIssues =
                            project.getSyncIssues()
                                    .stream()
                                    .filter(
                                            issue ->
                                                    issue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                                    .collect(Collectors.toList());
                    assertThat(severeIssues).named("Issues for " + path).isEmpty();
                });
    }
}
