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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.PLAY_SERVICES_VERSION;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.TestUtils;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
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
                        + TestUtils.KOTLIN_VERSION_FOR_TESTS
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
                project.file("WordPress/build.gradle"), "9.0.2", PLAY_SERVICES_VERSION);

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

        // matches: classpath ('com.uber:okbuck:1.0.0') {\n exclude module: 'gradle'\n }
        TestFileUtils.searchAndReplace(
                project.getBuildFile().toPath(),
                "classpath\\s\\('com.uber:okbuck:\\d+.\\d+.\\d+'\\)(\\s\\{\n.*\n.*})?",
                "");

        TestFileUtils.searchAndReplace(
                project.getBuildFile().toPath(), "apply plugin: 'com.uber.okbuck'", "");

        TestFileUtils.searchAndReplace(
                project.getBuildFile().toPath(), "okbuck\\s\\{\n(.*\n){3}+.*}", "");

        TestFileUtils.searchAndReplace(
                project.getBuildFile().toPath(),
                "compile 'com.google.auto.service:auto-service:1.0-rc2'",
                "");

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

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "playServicesVersion: '\\d+.\\d+.\\d+'",
                "playServicesVersion: '" + PLAY_SERVICES_VERSION + "'");
        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "leakCanaryVersion\\s*: '\\d+.\\d+'",
                "leakCanaryVersion: '1.4'");
        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "daggerVersion\\s*: '\\d+.\\d+'",
                "daggerVersion: '2.7'");
        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "autoCommon\\s*: 'com.google.auto:auto-common:\\d+.\\d+'",
                "autoCommon: 'com.google.auto:auto-common:0.6'");

        TestFileUtils.appendToFile(
                project.file("dependencies.gradle"),
                "\n\n// Fixes for support lib versions.\n"
                        + "ext.deps.other.appcompat = [\n"
                        + "        ext.deps.support.appCompat,\n"
                        + "        ext.deps.other.appcompat,\n"
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

        List<Path> allBuildFiles =
                Files.find(
                                project.getTestDir().toPath(),
                                Integer.MAX_VALUE,
                                (path, attrs) ->
                                        path.getFileName().toString().equals("build.gradle"))
                        .filter(
                                p ->
                                        !PathUtils.toSystemIndependentPath(p)
                                                .endsWith("gradle/SourceTemplate/app/build.gradle"))
                        .collect(Collectors.toList());

        modifyBuildFiles(allBuildFiles);
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

    private static void modifyBuildFiles(@NonNull List<Path> buildFiles) throws IOException {
        Pattern appPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.application['\"]");
        Pattern libPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.library['\"]");
        Pattern javaPlugin = Pattern.compile("apply plugin:\\s*['\"]java['\"]");

        for (Path build : buildFiles) {
            String fileContent = new String(Files.readAllBytes(build));
            if (appPlugin.matcher(fileContent).find() || libPlugin.matcher(fileContent).find()) {
                TestFileUtils.appendToFile(
                        build.toFile(),
                        "\n"
                                + "android.defaultConfig.javaCompileOptions {\n"
                                + "    annotationProcessorOptions.includeCompileClasspath = false\n"
                                + "}");

                replaceIfPresent(fileContent, build, "\\s*compile\\s(.*)", "\napi $1");

                replaceIfPresent(fileContent, build, "\\s*provided\\s(.*)", "\ncompileOnly $1");

                replaceIfPresent(
                        fileContent, build, "\\s*testCompile\\s(.*)", "\ntestImplementation $1");

                replaceIfPresent(
                        fileContent, build, "\\s*debugCompile\\s(.*)", "\ndebugImplementation $1");
                replaceIfPresent(
                        fileContent,
                        build,
                        "\\s*releaseCompile\\s(.*)",
                        "\nreleaseImplementation $1");
                replaceIfPresent(
                        fileContent,
                        build,
                        "\\s*androidTestCompile\\s(.*)",
                        "\nandroidTestImplementation $1");
            } else if (javaPlugin.matcher(fileContent).find()) {
                TestFileUtils.searchAndReplace(
                        build, javaPlugin.pattern(), "apply plugin: 'java-library'");
            }
        }
    }

    private static void replaceIfPresent(
            @NonNull String content,
            @NonNull Path destination,
            @NonNull String pattern,
            @NonNull String replace)
            throws IOException {
        Pattern compiledPattern = Pattern.compile(pattern);
        if (compiledPattern.matcher(content).find()) {
            TestFileUtils.searchAndReplace(destination, compiledPattern.pattern(), replace);
        }
    }
}
