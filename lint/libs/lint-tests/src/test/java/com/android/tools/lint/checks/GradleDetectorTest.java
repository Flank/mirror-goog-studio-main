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

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.lint.checks.GradleDetector.ACCIDENTAL_OCTAL;
import static com.android.tools.lint.checks.GradleDetector.BUNDLED_GMS;
import static com.android.tools.lint.checks.GradleDetector.COMPATIBILITY;
import static com.android.tools.lint.checks.GradleDetector.DEPENDENCY;
import static com.android.tools.lint.checks.GradleDetector.DEPRECATED;
import static com.android.tools.lint.checks.GradleDetector.GRADLE_GETTER;
import static com.android.tools.lint.checks.GradleDetector.GRADLE_PLUGIN_COMPATIBILITY;
import static com.android.tools.lint.checks.GradleDetector.HIGH_APP_VERSION_CODE;
import static com.android.tools.lint.checks.GradleDetector.NOT_INTERPOLATED;
import static com.android.tools.lint.checks.GradleDetector.PATH;
import static com.android.tools.lint.checks.GradleDetector.PLUS;
import static com.android.tools.lint.checks.GradleDetector.REMOTE_VERSION;
import static com.android.tools.lint.checks.GradleDetector.STRING_INTEGER;
import static com.android.tools.lint.checks.GradleDetector.getNamedDependency;
import static com.android.tools.lint.checks.GradleDetector.getNewValue;
import static com.android.tools.lint.checks.GradleDetector.getOldValue;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.infrastructure.TestIssueRegistry;
import com.android.tools.lint.checks.infrastructure.TestLintTask;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;

/**
 * NOTE: Many of these tests are duplicated in the Android Studio plugin to
 * test the custom GradleDetector subclass, IntellijGradleDetector, which
 * customizes some behavior to be based on top of PSI rather than the Groovy parser.
 */
public class GradleDetectorTest extends AbstractCheckTest {

    private File sdkDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (sdkDir != null) {
            deleteFile(sdkDir);
            sdkDir = null;
        }
    }

    @Override
    @NonNull
    protected TestLintTask lint() {
        TestLintTask task = super.lint();
        task.sdkHome(getMockSupportLibraryInstallation());
        return task;
    }

    /** Creates a mock SDK installation structure, containing a fixed set of dependencies */
    private File getMockSupportLibraryInstallation() {
        if (sdkDir == null) {
            // Make fake SDK "installation" such that we can predict the set
            // of Maven repositories discovered by this test
            sdkDir = TestUtils.createTempDirDeletedOnExit();

            String[] paths = new String[]{
                    // Android repository
                    "extras/android/m2repository/com/android/support/appcompat-v7/18.0.0/appcompat-v7-18.0.0.aar",
                    "extras/android/m2repository/com/android/support/appcompat-v7/19.0.0/appcompat-v7-19.0.0.aar",
                    "extras/android/m2repository/com/android/support/appcompat-v7/19.0.1/appcompat-v7-19.0.1.aar",
                    "extras/android/m2repository/com/android/support/appcompat-v7/19.1.0/appcompat-v7-19.1.0.aar",
                    "extras/android/m2repository/com/android/support/appcompat-v7/20.0.0/appcompat-v7-20.0.0.aar",
                    "extras/android/m2repository/com/android/support/appcompat-v7/21.0.0/appcompat-v7-21.0.0.aar",
                    "extras/android/m2repository/com/android/support/appcompat-v7/21.0.2/appcompat-v7-21.0.2.aar",
                    "extras/android/m2repository/com/android/support/cardview-v7/21.0.0/cardview-v7-21.0.0.aar",
                    "extras/android/m2repository/com/android/support/cardview-v7/21.0.2/cardview-v7-21.0.2.aar",
                    "extras/android/m2repository/com/android/support/support-v13/20.0.0/support-v13-20.0.0.aar",
                    "extras/android/m2repository/com/android/support/support-v13/21.0.0/support-v13-21.0.0.aar",
                    "extras/android/m2repository/com/android/support/support-v13/21.0.2/support-v13-21.0.2.aar",
                    "extras/android/m2repository/com/android/support/support-v4/20.0.0/support-v4-20.0.0.aar",
                    "extras/android/m2repository/com/android/support/support-v4/21.0.0/support-v4-21.0.0.aar",
                    "extras/android/m2repository/com/android/support/support-v4/21.0.2/support-v4-21.0.2.aar",
                    "extras/android/m2repository/com/android/support/test/runner/0.5/runner-0.5.aar",
                    "extras/android/m2repository/com/android/support/multidex/1.0.1/multidex-1.0.1.aar",

                    // Google repository
                    "extras/google/m2repository/com/google/android/gms/play-services/3.1.36/play-services-3.1.36.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/3.1.59/play-services-3.1.59.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/3.2.25/play-services-3.2.25.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/3.2.65/play-services-3.2.65.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/4.0.30/play-services-4.0.30.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/4.1.32/play-services-4.1.32.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/4.2.42/play-services-4.2.42.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/4.3.23/play-services-4.3.23.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/4.4.52/play-services-4.4.52.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/5.0.89/play-services-5.0.89.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/6.1.11/play-services-6.1.11.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services/6.1.71/play-services-6.1.71.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services-wearable/5.0.77/play-services-wearable-5.0.77.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services-wearable/6.1.11/play-services-wearable-6.1.11.aar",
                    "extras/google/m2repository/com/google/android/gms/play-services-wearable/6.1.71/play-services-wearable-6.1.71.aar",
                    "extras/google/m2repository/com/google/android/support/wearable/1.0.0/wearable-1.0.0.aar",
                    "extras/google/m2repository/com/google/android/wearable/wearable/1.0.0/wearable-1.0.0.aar",
                    "extras/google//m2repository/com/google/android/support/wearable/1.2.0/wearable-1.2.0.aar",
                    "extras/google//m2repository/com/google/android/support/wearable/1.3.0/wearable-1.3.0.aar",

                    // build tools
                    "build-tools/23.0.0/aapt",
                    "build-tools/23.0.3/aapt"
            };

            createSdkPaths(sdkDir, paths);
        }

        return sdkDir;
    }

    public static void createSdkPaths(File sdkDir, String[] paths) {
        for (String path : paths) {
            File file = new File(sdkDir, path.replace('/', File.separatorChar));
            File parent = file.getParentFile();
            if (!parent.exists()) {
                boolean ok = parent.mkdirs();
                assertTrue(ok);
            }
            try {
                boolean created = file.createNewFile();
                assertTrue(created);
            } catch (IOException e) {
                fail(e.toString());
            }
        }
    }

    public void testGetOldValue() {
        assertEquals("11.0.2", getOldValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0",
                TEXT));
        assertNull(getOldValue(DEPENDENCY, "Bogus", TEXT));
        assertNull(getOldValue(DEPENDENCY, "bogus", TEXT));
        // targetSdkVersion 20, compileSdkVersion 19: Should replace targetVersion 20 with 19
        assertEquals("20", getOldValue(DEPENDENCY,
                "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)",
                TEXT));
        assertEquals("'19'", getOldValue(STRING_INTEGER,
                "Use an integer rather than a string here (replace '19' with just 19)", TEXT));
        assertEquals("android", getOldValue(DEPRECATED,
                "'android' is deprecated; use 'com.android.application' instead", TEXT));
        assertEquals("android-library", getOldValue(DEPRECATED,
                "'android-library' is deprecated; use 'com.android.library' instead", TEXT));
        assertEquals("packageName", getOldValue(DEPRECATED,
                "Deprecated: Replace 'packageName' with 'applicationId'", TEXT));
        assertEquals("packageNameSuffix", getOldValue(DEPRECATED,
                "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'", TEXT));
        assertEquals("18.0.0", getOldValue(DEPENDENCY,
                "Old buildToolsVersion 18.0.0; recommended version is 19.1 or later", TEXT));
    }

    public void testGetNewValue() {
        assertEquals("17.0.0", getNewValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0",
                TEXT));
        assertNull(getNewValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available", TEXT));
        assertNull(getNewValue(DEPENDENCY, "bogus", TEXT));
        // targetSdkVersion 20, compileSdkVersion 19: Should replace targetVersion 20 with 19
        assertEquals("19", getNewValue(DEPENDENCY,
                "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)",
                TEXT));
        assertEquals("19", getNewValue(STRING_INTEGER,
                "Use an integer rather than a string here (replace '19' with just 19)", TEXT));
        assertEquals("com.android.application", getNewValue(DEPRECATED,
                "'android' is deprecated; use 'com.android.application' instead", TEXT));
        assertEquals("com.android.library", getNewValue(DEPRECATED,
                "'android-library' is deprecated; use 'com.android.library' instead", TEXT));
        assertEquals("applicationId", getNewValue(DEPRECATED,
                "Deprecated: Replace 'packageName' with 'applicationId'", TEXT));
        assertEquals("applicationIdSuffix", getNewValue(DEPRECATED,
                "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'", TEXT));
        assertEquals("19.1", getNewValue(DEPENDENCY,
                "Old buildToolsVersion 18.0.0; recommended version is 19.1 or later", TEXT));
    }

    public void test() throws Exception {
        String expected = ""
                + "build.gradle:25: Error: This support library should not use a different version (13) than the compileSdkVersion (19) [GradleCompatible]\n"
                + "    compile 'com.android.support:appcompat-v7:13.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:1: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]\n"
                + "apply plugin: 'android'\n"
                + "~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:5: Warning: Old buildToolsVersion 19.0.0; recommended version is 19.1 or later [GradleDependency]\n"
                + "    buildToolsVersion \"19.0.0\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:24: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 21.0 [GradleDependency]\n"
                + "    freeCompile 'com.google.guava:guava:11.0.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:25: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 21.0.2 [GradleDependency]\n"
                + "    compile 'com.android.support:appcompat-v7:13.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:26: Warning: A newer version of com.google.android.support:wearable than 1.2.0 is available: 1.3.0 [GradleDependency]\n"
                + "    compile 'com.google.android.support:wearable:1.2.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:27: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]\n"
                + "    compile 'com.android.support:multidex:1.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:29: Warning: A newer version of com.android.support.test:runner than 0.3 is available: 0.5 [GradleDependency]\n"
                + "    androidTestCompile 'com.android.support.test:runner:0.3'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:23: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+) [GradleDynamicVersion]\n"
                + "    compile 'com.android.support:appcompat-v7:+'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 8 warnings\n";

        lint().files(
                mDependencies)
                .issues(COMPATIBILITY, DEPRECATED, DEPENDENCY, PLUS)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testCompatibility() throws Exception {
        String expected = ""
                + "build.gradle:16: Error: This support library should not use a lower version (18) than the targetSdkVersion (19) [GradleCompatible]\n"
                + "    compile 'com.android.support:support-v4:18.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 18\n"
                        + "    buildToolsVersion \"19.0.0\"\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 7\n"
                        + "        targetSdkVersion 19\n"
                        + "        versionCode 1\n"
                        + "        versionName \"1.0\"\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:support-v4:18.0.0'\n"
                        + "    compile 'com.android.support.test:espresso:0.2'\n"
                        + "    compile 'com.android.support:multidex:1.0.1'\n"
                        + "    compile 'com.android.support:multidex-instrumentation:1.0.1'\n"
                        + "\n"
                        + "    // Suppressed:\n"
                        + "    //noinspection GradleCompatible\n"
                        + "    compile 'com.android.support:support-v4:18.0.0'\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testIncompatiblePlugin() throws Exception {
        String expected = ""
                + "build.gradle:6: Error: You must use a newer version of the Android Gradle plugin. The minimum supported version is "
                + GRADLE_PLUGIN_MINIMUM_VERSION + " and the recommended version is "
                + GRADLE_PLUGIN_RECOMMENDED_VERSION + " [GradlePluginVersion]\n"
                + "    classpath 'com.android.tools.build:gradle:0.1.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        lint().files(
                gradle(""
                        + "buildscript {\n"
                        + "  repositories {\n"
                        + "    mavenCentral()\n"
                        + "  }\n"
                        + "  dependencies {\n"
                        + "    classpath 'com.android.tools.build:gradle:0.1.0'\n"
                        + "  }\n"
                        + "}\n"
                        + "\n"
                        + "allprojects {\n"
                        + "  repositories {\n"
                        + "    mavenCentral()\n"
                        + "  }\n"
                        + "}\n"))
                .issues(GRADLE_PLUGIN_COMPATIBILITY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);

    }

    public void testSetter() throws Exception {
        String expected = ""
                + "build.gradle:18: Error: Bad method name: pick a unique method name which does not conflict with the implicit getters for the defaultConfig properties. For example, try using the prefix compute- instead of get-. [GradleGetter]\n"
                + "        versionCode getVersionCode\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:19: Error: Bad method name: pick a unique method name which does not conflict with the implicit getters for the defaultConfig properties. For example, try using the prefix compute- instead of get-. [GradleGetter]\n"
                + "        versionName getVersionName\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "def getVersionName() {\n"
                        + "    \"1.0\"\n"
                        + "}\n"
                        + "\n"
                        + "def getVersionCode() {\n"
                        + "    50\n"
                        + "}\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 19\n"
                        + "    buildToolsVersion \"19.0.0\"\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 7\n"
                        + "        targetSdkVersion 17\n"
                        + "        versionCode getVersionCode\n"
                        + "        versionName getVersionName\n"
                        + "    }\n"
                        + "}\n"))
                .issues(GRADLE_GETTER)
                .sdkHome(getMockSupportLibraryInstallation())
                .ignoreUnknownGradleConstructs()
                .run()
                .expect(expected);
    }

    public void testDependencies() throws Exception {
        String expected = ""
                + "build.gradle:5: Warning: Old buildToolsVersion 19.0.0; recommended version is 19.1 or later [GradleDependency]\n"
                + "    buildToolsVersion \"19.0.0\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:24: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 21.0 [GradleDependency]\n"
                + "    freeCompile 'com.google.guava:guava:11.0.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:25: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 21.0.2 [GradleDependency]\n"
                + "    compile 'com.android.support:appcompat-v7:13.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:26: Warning: A newer version of com.google.android.support:wearable than 1.2.0 is available: 1.3.0 [GradleDependency]\n"
                + "    compile 'com.google.android.support:wearable:1.2.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:27: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]\n"
                + "    compile 'com.android.support:multidex:1.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:29: Warning: A newer version of com.android.support.test:runner than 0.3 is available: 0.5 [GradleDependency]\n"
                + "    androidTestCompile 'com.android.support.test:runner:0.3'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 6 warnings\n";

        lint().files(
                mDependencies)
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testLongHandDependencies() throws Exception {
        String expected = ""
                + "build.gradle:9: Warning: A newer version of com.android.support:support-v4 than 19.0 is available: 21.0.2 [GradleDependency]\n"
                + "    compile group: 'com.android.support', name: 'support-v4', version: '19.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 19\n"
                        + "    buildToolsVersion \"19.1\"\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile group: 'com.android.support', name: 'support-v4', version: '19.0'\n"
                        + "}\n"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);

    }

    public void testDependenciesMinSdkVersion() throws Exception {
        String expected = ""
                + "build.gradle:13: Warning: Using the appcompat library when minSdkVersion >= 14 and compileSdkVersion < 21 is not necessary [GradleDependency]\n"
                + "    compile 'com.android.support:appcompat-v7:+'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 19\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15\n"
                        + "        targetSdkVersion 17\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:appcompat-v7:+'\n"
                        + "}\n"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testDependenciesMinSdkVersionLollipop() throws Exception {
        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 21\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15\n"
                        + "        targetSdkVersion 17\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:appcompat-v7:+'\n"
                        + "}\n"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expectClean();
    }

    public void testDependenciesNoMicroVersion() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=77594
        String expected = ""
                + "build.gradle:13: Warning: A newer version of com.google.code.gson:gson than 2.2 is available: 2.8.0 [GradleDependency]\n"
                + "    compile 'com.google.code.gson:gson:2.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 19\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15\n"
                        + "        targetSdkVersion 17\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.google.code.gson:gson:2.2'\n"
                        + "}\n"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testPaths() throws Exception {
        String expected = ""
                + "build.gradle:4: Warning: Do not use Windows file separators in .gradle files; use / instead [GradlePath]\n"
                + "    compile files('my\\\\libs\\\\http.jar')\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:5: Warning: Avoid using absolute paths in .gradle files [GradlePath]\n"
                + "    compile files('/libs/android-support-v4.jar')\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile files('my\\\\libs\\\\http.jar')\n"
                        + "    compile files('/libs/android-support-v4.jar')\n"
                        + "}\n"))
                .issues(PATH)
                .sdkHome(getMockSupportLibraryInstallation())
                .ignoreUnknownGradleConstructs()
                .run()
                .expect(expected);
    }

    public void testIdSuffix() throws Exception {
        String expected = ""
                + "build.gradle:6: Warning: Application ID suffix should probably start with a \".\" [GradlePath]\n"
                + "            applicationIdSuffix \"debug\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            applicationIdSuffix \"debug\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"))
                .issues(PATH)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testPackage() throws Exception {
        String expected = ""
                + "build.gradle:5: Warning: Deprecated: Replace 'packageName' with 'applicationId' [GradleDeprecated]\n"
                + "        packageName 'my.pkg'\n"
                + "        ~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:9: Warning: Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix' [GradleDeprecated]\n"
                + "            packageNameSuffix \".debug\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        packageName 'my.pkg'\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            packageNameSuffix \".debug\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"))
                .issues(DEPRECATED)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testPlus() throws Exception {
        String expected = ""
                + "build.gradle:9: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+) [GradleDynamicVersion]\n"
                + "    compile 'com.android.support:appcompat-v7:+'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:10: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:support-v4:21.0.+) [GradleDynamicVersion]\n"
                + "    compile group: 'com.android.support', name: 'support-v4', version: '21.0.+'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:11: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+@aar) [GradleDynamicVersion]\n"
                + "    compile 'com.android.support:appcompat-v7:+@aar'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 3 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 19\n"
                        + "    buildToolsVersion \"19.0.1\"\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:appcompat-v7:+'\n"
                        + "    compile group: 'com.android.support', name: 'support-v4', version: '21.0.+'\n"
                        + "    compile 'com.android.support:appcompat-v7:+@aar'\n"
                        + "}\n"))
                .issues(PLUS)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);

    }

    public void testStringInt() throws Exception {
        String expected = ""
                + "build.gradle:4: Error: Use an integer rather than a string here (replace '19' with just 19) [StringShouldBeInt]\n"
                + "    compileSdkVersion '19'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:7: Error: Use an integer rather than a string here (replace '8' with just 8) [StringShouldBeInt]\n"
                + "        minSdkVersion '8'\n"
                + "        ~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:8: Error: Use an integer rather than a string here (replace '16' with just 16) [StringShouldBeInt]\n"
                + "        targetSdkVersion '16'\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion '19'\n"
                        + "    buildToolsVersion \"19.0.1\"\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion '8'\n"
                        + "        targetSdkVersion '16'\n"
                        + "    }\n"
                        + "}\n"))
                .issues(STRING_INTEGER)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testSuppressLine2() throws Exception {
        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "//noinspection GradleDeprecated\n"
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "android {\n"
                        + "}\n"))
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expectClean();
    }

    public void testDeprecatedPluginId() throws Exception {
        String expected = ""
                + "build.gradle:4: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]\n"
                + "apply plugin: 'android'\n"
                + "~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:5: Warning: 'android-library' is deprecated; use 'com.android.library' instead [GradleDeprecated]\n"
                + "apply plugin: 'android-library'\n"
                + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "apply plugin: 'java'\n"
                        + "apply plugin: 'android'\n"
                        + "apply plugin: 'android-library'\n"
                        + "\n"
                        + "android {\n"
                        + "}\n"))
                .issues(DEPRECATED)
                .sdkHome(getMockSupportLibraryInstallation())
                .ignoreUnknownGradleConstructs()
                .run()
                .expect(expected);
    }

    public void testIgnoresGStringsInDependencies() throws Exception {
        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "buildscript {\n"
                        + "  ext.androidGradleVersion = '0.11.0'\n"
                        + "  dependencies {\n"
                        + "    classpath \"com.android.tools.build:gradle:$androidGradleVersion\"\n"
                        + "  }\n"
                        + "}\n"))
                .sdkHome(getMockSupportLibraryInstallation())
                .ignoreUnknownGradleConstructs()
                .run()
                .expectClean();
    }

    public void testAccidentalOctal() throws Exception {
        String expected = ""
                + "build.gradle:13: Error: The leading 0 turns this number into octal which is probably not what was intended (interpreted as 8) [AccidentalOctal]\n"
                + "        versionCode 010\n"
                + "        ~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        // Ok: not octal\n"
                        + "        versionCode 1\n"
                        + "        versionCode 10\n"
                        + "        versionCode 100\n"
                        + "        // ok: octal == decimal\n"
                        + "        versionCode 01\n"
                        + "\n"
                        + "        // Errors\n"
                        + "        versionCode 010\n"
                        + "\n"
                        + "        // Lint Groovy Bug:\n"
                        + "        versionCode 01 // line suffix comments are not handled correctly\n"
                        + "    }\n"
                        + "}\n"))
                .issues(ACCIDENTAL_OCTAL)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testBadPlayServicesVersion() throws Exception {
        String expected = ""
                + "build.gradle:5: Error: Version 5.2.08 should not be used; the app can not be published with this version. Use version 6.1.71 instead. [GradleCompatible]\n"
                + "    compile 'com.google.android.gms:play-services:5.2.08'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "\n"
                        + "    compile 'com.google.android.gms:play-services:5.2.08'\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testRemoteVersions() throws Exception {
        mEnabled = Collections.singleton(REMOTE_VERSION);
        try {
            HashMap<String, String> data = Maps.newHashMap();
            GradleDetector.sMockData = data;
            data.put("http://search.maven.org/solrsearch/select?q=g:%22joda-time%22+AND+a:%22joda-time%22&core=gav&rows=1&wt=json",
                    "{\"responseHeader\":{\"status\":0,\"QTime\":1,\"params\":{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"joda-time\\\" AND a:\\\"joda-time\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"rows\":\"1\",\"version\":\"2.2\"}},\"response\":{\"numFound\":17,\"start\":0,\"docs\":[{\"id\":\"joda-time:joda-time:2.3\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.3\",\"p\":\"jar\",\"timestamp\":1376674285000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"]}]}}");
            data.put("http://search.maven.org/solrsearch/select?q=g:%22com.squareup.dagger%22+AND+a:%22dagger%22&core=gav&rows=1&wt=json",
                    "{\"responseHeader\":{\"status\":0,\"QTime\":1,\"params\":{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.squareup.dagger\\\" AND a:\\\"dagger\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"rows\":\"1\",\"version\":\"2.2\"}},\"response\":{\"numFound\":5,\"start\":0,\"docs\":[{\"id\":\"com.squareup.dagger:dagger:1.2.1\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.1\",\"p\":\"jar\",\"timestamp\":1392614597000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}]}}");

            //noinspection all // Sample code
            assertEquals(""
                + "build.gradle:9: Warning: A newer version of joda-time:joda-time than 2.1 is available: 2.3 [NewerVersionAvailable]\n"
                + "    compile 'joda-time:joda-time:2.1'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:10: Warning: A newer version of com.squareup.dagger:dagger than 1.2.0 is available: 1.2.1 [NewerVersionAvailable]\n"
                + "    compile 'com.squareup.dagger:dagger:1.2.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

                    lintProject(source("build.gradle", ""
                            + "apply plugin: 'com.android.application'\n"
                            + "\n"
                            + "android {\n"
                            + "    compileSdkVersion 19\n"
                            + "    buildToolsVersion \"19.0.0\"\n"
                            + "}\n"
                            + "\n"
                            + "dependencies {\n"
                            + "    compile 'joda-time:joda-time:2.1'\n"
                            + "    compile 'com.squareup.dagger:dagger:1.2.0'\n"
                            + "}\n")));
        } finally {
            GradleDetector.sMockData = null;
        }
    }

    public void testRemoteVersionsWithPreviews() throws Exception {
        // If the most recent version is a rc version, query for all versions
        mEnabled = Collections.singleton(REMOTE_VERSION);
        try {
            HashMap<String, String> data = Maps.newHashMap();
            GradleDetector.sMockData = data;
            data.put("http://search.maven.org/solrsearch/select?q=g:%22com.google.guava%22+AND+a:%22guava%22&core=gav&rows=1&wt=json",
                    "{\"responseHeader\":{\"status\":0,\"QTime\":0,\"params\":{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.google.guava\\\" AND a:\\\"guava\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"rows\":\"1\",\"version\":\"2.2\"}},\"response\":{\"numFound\":38,\"start\":0,\"docs\":[{\"id\":\"com.google.guava:guava:18.0-rc1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"18.0-rc1\",\"p\":\"bundle\",\"timestamp\":1407266204000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]}]}}");
            data.put("http://search.maven.org/solrsearch/select?q=g:%22com.google.guava%22+AND+a:%22guava%22&core=gav&wt=json",
                    "{\"responseHeader\":{\"status\":0,\"QTime\":1,\"params\":{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.google.guava\\\" AND a:\\\"guava\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}},\"response\":{\"numFound\":38,\"start\":0,\"docs\":[{\"id\":\"com.google.guava:guava:18.0-rc1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"18.0-rc1\",\"p\":\"bundle\",\"timestamp\":1407266204000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:17.0\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"17.0\",\"p\":\"bundle\",\"timestamp\":1398199666000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:17.0-rc2\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"17.0-rc2\",\"p\":\"bundle\",\"timestamp\":1397162341000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:17.0-rc1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"17.0-rc1\",\"p\":\"bundle\",\"timestamp\":1396985408000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:16.0.1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"16.0.1\",\"p\":\"bundle\",\"timestamp\":1391467528000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:16.0\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"16.0\",\"p\":\"bundle\",\"timestamp\":1389995088000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:16.0-rc1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"16.0-rc1\",\"p\":\"bundle\",\"timestamp\":1387495574000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:15.0\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"15.0\",\"p\":\"bundle\",\"timestamp\":1378497169000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"inject\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"that\",\"more\",\"utility\",\"guava\",\"dependencies\",\"javax\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\",\"-cdi1.0.jar\"]},{\"id\":\"com.google.guava:guava:15.0-rc1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"15.0-rc1\",\"p\":\"bundle\",\"timestamp\":1377542588000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"inject\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"that\",\"more\",\"utility\",\"guava\",\"dependencies\",\"javax\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]},{\"id\":\"com.google.guava:guava:14.0.1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"14.0.1\",\"p\":\"bundle\",\"timestamp\":1363305439000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"inject\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"that\",\"more\",\"utility\",\"guava\",\"dependencies\",\"javax\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]}]}}");

            //noinspection all // Sample code
            assertEquals(""
                + "build.gradle:9: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 17.0 [NewerVersionAvailable]\n"
                + "    compile 'com.google.guava:guava:11.0.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:10: Warning: A newer version of com.google.guava:guava than 16.0-rc1 is available: 18.0.0-rc1 [NewerVersionAvailable]\n"
                + "    compile 'com.google.guava:guava:16.0-rc1'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

                    lintProject(source("build.gradle", ""
                            + "apply plugin: 'com.android.application'\n"
                            + "\n"
                            + "android {\n"
                            + "    compileSdkVersion 19\n"
                            + "    buildToolsVersion \"19.0.0\"\n"
                            + "}\n"
                            + "\n"
                            + "dependencies {\n"
                            + "    compile 'com.google.guava:guava:11.0.2'\n"
                            + "    compile 'com.google.guava:guava:16.0-rc1'\n"
                            + "}\n")));
        } finally {
            GradleDetector.sMockData = null;
        }
    }

    public void testPreviewVersions() throws Exception {
        // This test only works when SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION contains
        // a preview string:
        if (!GRADLE_PLUGIN_RECOMMENDED_VERSION.startsWith("1.0.0-rc")) {
            return;
        }
        String expected = ""
                + "build.gradle:6: Warning: A newer version of com.android.tools.build:gradle than 1.0.0-rc0 is available: "
                + GRADLE_PLUGIN_RECOMMENDED_VERSION + " [GradleDependency]\n"
                + "        classpath 'com.android.tools.build:gradle:1.0.0-rc0'\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                gradle(""
                        + "buildscript {\n"
                        + "    repositories {\n"
                        + "        jcenter()\n"
                        + "    }\n"
                        + "    dependencies {\n"
                        + "        classpath 'com.android.tools.build:gradle:1.0.0-rc0'\n"
                        + "        classpath 'com.android.tools.build:gradle:1.0.0-rc8'\n"
                        + "        classpath 'com.android.tools.build:gradle:1.0.0'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "allprojects {\n"
                        + "    repositories {\n"
                        + "        jcenter()\n"
                        + "    }\n"
                        + "}\n"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testDependenciesInVariables() {
        String expected = ""
                + "build.gradle:10: Warning: A newer version of com.google.android.gms:play-services-wearable than 5.0.77 is available: 6.1.71 [GradleDependency]\n"
                + "    compile \"com.google.android.gms:play-services-wearable:${GPS_VERSION}\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        lint().files(
                source("build.gradle",""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 21\n"
                        + "}\n"
                        + "\n"
                        + "final GPS_VERSION = '5.0.77'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \"com.google.android.gms:play-services-wearable:${GPS_VERSION}\"\n"
                        + "}\n"),
                gradle("internal-only.gradle", ""
                        // Not part of the lint check; used only to provide a mock model to
                        // the infrastructure
                        + "dependencies {\n"
                        + "    compile 'com.google.android.gms:play-services-wearable:5.0.77'\n"
                        + "}"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testPlayServiceConsistency() {
        String expected = ""
                + "build.gradle:4: Error: All com.google.android.gms libraries must use the exact same version specification (mixing versions can lead to runtime crashes). Found versions 7.5.0, 7.3.0. Examples include com.google.android.gms:play-services-wearable:7.5.0 and com.google.android.gms:play-services-location:7.3.0 [GradleCompatible]\n"
                + "    compile 'com.google.android.gms:play-services-wearable:7.5.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.google.android.gms:play-services-wearable:7.5.0'\n"
                        + "    compile 'com.google.android.gms:play-services-location:7.3.0'\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .incremental()
                .run()
                .expect(expected);
    }

    public void testSupportLibraryConsistency() throws Exception {
        String expected = ""
                + "build.gradle:4: Error: All com.android.support libraries must use the exact same version specification (mixing versions can lead to runtime crashes). Found versions 25.0-SNAPSHOT, 24.2, 24.1. Examples include com.android.support:preference-v7:25.0-SNAPSHOT and com.android.support:animated-vector-drawable:24.2 [GradleCompatible]\n"
                + "    compile \"com.android.support:appcompat-v7:24.2\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "dependencies {\n"
                        + "    compile \"com.android.support:multidex:1.0.1\"\n"
                        + "    compile \"com.android.support:appcompat-v7:24.2\"\n"
                        + "    compile \"com.android.support:support-v13:24.1\"\n"
                        + "    compile \"com.android.support:preference-v7:25.0-SNAPSHOT\"\n"
                        + "    compile \"com.android.support:cardview-v7:24.2\"\n"
                        + "    compile \"com.android.support:support-annotations:25.0.0\"\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .incremental()
                .run()
                .expect(expected);
    }

    public void testSupportLibraryConsistencyWithDataBinding() throws Exception {
        String expected = ""
                + "build.gradle:3: Error: All com.android.support libraries must use the exact "
                + "same version specification (mixing versions can lead to runtime crashes). "
                + "Found versions 25.0.0, 21.0.3. Examples include "
                + "com.android.support:recyclerview-v7:25.0.0 and "
                + "com.android.support:support-v4:21.0.3. "
                + "Note that this project is using data binding "
                + "(com.android.databinding:library:1.3.1) which pulls in "
                + "com.android.support:support-v4:21.0.3. "
                + "You can try to work around this by adding an explicit dependency on"
                + " com.android.support:support-v4:25.0.0 [GradleCompatible]\n"
                + "    compile \"com.android.support:recyclerview-v7:25.0.0\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().projects(project(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "dependencies {\n"
                        + "    compile \"com.android.support:recyclerview-v7:25.0.0\"\n"
                        + "    compile \"com.android.databinding:library:1.3.1\"\n"
                        + "    compile \"com.android.databinding:baseLibrary:2.3.0-alpha2\"\n"
                        + "}\n"))
                .withDependencyGraph(""
                        + "+--- com.android.support:recyclerview-v7:25.0.0\n"
                        + "|    +--- com.android.support:support-annotations:25.0.0\n"
                        + "|    +--- com.android.support:support-compat:25.0.0\n"
                        + "|    |    \\--- com.android.support:support-annotations:25.0.0\n"
                        + "|    \\--- com.android.support:support-core-ui:25.0.0\n"
                        + "|         \\--- com.android.support:support-compat:25.0.0 (*)\n"
                        + "+--- com.android.databinding:library:1.3.1\n"
                        + "|    +--- com.android.support:support-v4:21.0.3\n"
                        + "|    |    \\--- com.android.support:support-annotations:21.0.3 -> 25.0.0\n"
                        + "|    \\--- com.android.databinding:baseLibrary:2.3.0-dev -> 2.3.0-alpha2\n"
                        + "+--- com.android.databinding:baseLibrary:2.3.0-alpha2\n"
                        + "\\--- com.android.databinding:adapters:1.3.1\n"
                        + "     +--- com.android.databinding:library:1.3 -> 1.3.1 (*)\n"
                        + "     \\--- com.android.databinding:baseLibrary:2.3.0-dev -> 2.3.0-alpha2"))
                .issues(COMPATIBILITY)
                .incremental()
                .run()
                .expect(expected);
    }

    public void testWearableConsistency1() {
        // Regression test 1 for b/29006320.
        String expected = ""
                + "build.gradle:4: Error: Project depends on com.google.android.support:wearable:2.0.0-alpha3, so it must also depend (as a provided dependency) on com.google.android.wearable:wearable:2.0.0-alpha3 [GradleCompatible]\n"
                + "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .incremental("build.gradle")
                .run()
                .expect(expected);
    }

    public void testWearableConsistency2() {
        // Regression test 2 for b/29006320.
        String expected = ""
                + "build.gradle:4: Error: The wearable libraries for com.google.android.support and com.google.android.wearable must use exactly the same versions; found 2.0.0-alpha3 and 2.0.0-alpha4 [GradleCompatible]\n"
                + "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n"
                        + "    provided \"com.google.android.wearable:wearable:2.0.0-alpha4\"\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .incremental()
                .run()
                .expect(expected);
    }

    public void testWearableConsistency3() {
        // Regression test 3 for b/29006320.
        String expected = ""
                + "build.gradle:4: Error: This dependency should be marked as provided, not compile [GradleCompatible]\n"
                + "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n"
                        + "    compile \"com.google.android.wearable:wearable:2.0.0-alpha3\"\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .incremental()
                .run()
                .expect(expected);
    }

    public void testWearableConsistency4() {
        // Regression test for 226240; gracefully handle null resolved coordinates.
        String expected = "No warnings.";
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n"
                        + "    compile \"com.google.android.wearable:wearable:2.0.0-alpha3\"\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .incremental()
                .modifyGradleMocks((project, variant) -> {
                    // Null out the resolved coordinates in the result to simulate the
                    // observed failure in issue 226240
                    //noinspection ConstantConditions
                    Dependencies dependencies = variant.getMainArtifact().getDependencies();
                    AndroidLibrary library1 = dependencies.getLibraries().iterator().next();
                    JavaLibrary library2 = dependencies.getJavaLibraries().iterator()
                            .next();
                    when(library1.getResolvedCoordinates()).thenReturn(null);
                    when(library2.getResolvedCoordinates()).thenReturn(null);
                })
                .run()
                .expect(expected);
    }

    public void testSupportLibraryConsistencyNonIncremental() throws Exception {
        String expected = ""
                + "build.gradle:6: Error: All com.android.support libraries must use the exact same version specification (mixing versions can lead to runtime crashes). Found versions 25.0-SNAPSHOT, 24.2, 24.1. Examples include com.android.support:preference-v7:25.0-SNAPSHOT and com.android.support:animated-vector-drawable:24.2 [GradleCompatible]\n"
                + "    compile \"com.android.support:preference-v7:25.0-SNAPSHOT\"\n"
                + "             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \"com.android.support:appcompat-v7:24.2\"\n"
                        + "    compile \"com.android.support:support-v13:24.1\"\n"
                        + "    compile \"com.android.support:preference-v7:25.0-SNAPSHOT\"\n"
                        + "    compile \"com.android.support:cardview-v7:24.2\"\n"
                        + "    compile \"com.android.support:multidex:1.0.1\"\n"
                        + "    compile \"com.android.support:support-annotations:25.0.0\"\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testPlayServiceConsistencyNonIncremental() throws Exception {
        String expected = ""
                + "build.gradle:4: Error: All com.google.android.gms libraries must use the exact same version specification (mixing versions can lead to runtime crashes). Found versions 7.5.0, 7.3.0. Examples include com.google.android.gms:play-services-wearable:7.5.0 and com.google.android.gms:play-services-location:7.3.0 [GradleCompatible]\n"
                + "    compile 'com.google.android.gms:play-services-wearable:7.5.0'\n"
                + "             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    build.gradle:5: <No location-specific message\n"
                + "1 errors, 0 warnings\n";

        lint().files(
                gradle(""
                        + "apply plugin: 'android'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.google.android.gms:play-services-wearable:7.5.0'\n"
                        + "    compile 'com.google.android.gms:play-services-location:7.3.0'\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testWrongQuotes() throws Exception {
        String expected = ""
                + "build.gradle:5: Error: It looks like you are trying to substitute a version variable, but using single quotes ('). For Groovy string interpolation you must use double quotes (\"). [NotInterpolated]\n"
                + "    compile 'com.android.support:design:${supportLibVersion}'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        lint().files(
                gradle(""
                        + "ext {\n"
                        + "    supportLibVersion = \"23.1.1\"\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:design:${supportLibVersion}'\n"
                        + "    compile \"com.android.support:appcompat-v7:${supportLibVersion}\"\n"
                        + "}\n"))
                .issues(NOT_INTERPOLATED)
                .sdkHome(getMockSupportLibraryInstallation())
                .ignoreUnknownGradleConstructs()
                .run()
                .expect(expected);
    }

    public void testOldFabric() throws Exception {
        // This version of Fabric created a unique string for every build which results in
        // Hotswaps getting disabled due to resource changes
        String expected = ""
                + "build.gradle:3: Warning: Use Fabric Gradle plugin version 1.21.6 or later to improve Instant Run performance (was 1.21.2) [GradleDependency]\n"
                + "    classpath 'io.fabric.tools:gradle:1.21.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:4: Warning: Use Fabric Gradle plugin version 1.21.6 or later to improve Instant Run performance (was 1.20.0) [GradleDependency]\n"
                + "    classpath 'io.fabric.tools:gradle:1.20.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:5: Warning: A newer version of io.fabric.tools:gradle than 1.22.0 is available: 1.22.1 [GradleDependency]\n"
                + "    classpath 'io.fabric.tools:gradle:1.22.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 3 warnings\n";

        lint().files(
                gradle(""
                        + "buildscript {\n"
                        + "  dependencies {\n"
                        + "    classpath 'io.fabric.tools:gradle:1.21.2'\n" // Not OK
                        + "    classpath 'io.fabric.tools:gradle:1.20.0'\n" // Not OK
                        + "    classpath 'io.fabric.tools:gradle:1.22.0'\n" // Old
                        + "    classpath 'io.fabric.tools:gradle:1.+'\n" // OK
                        + "  }\n"
                        + "}\n"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testOldBugSnag() throws Exception {
        // This version of BugSnag triggered instant run full rebuilds
        String expected = ""
                + "build.gradle:3: Warning: Use BugSnag Gradle plugin version 2.1.2 or later to improve Instant Run performance (was 2.1.0) [GradleDependency]\n"
                + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:4: Warning: Use BugSnag Gradle plugin version 2.1.2 or later to improve Instant Run performance (was 2.1.1) [GradleDependency]\n"
                + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.1'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:5: Warning: A newer version of com.bugsnag:bugsnag-android-gradle-plugin than 2.1.2 is available: 2.4.1 [GradleDependency]\n"
                + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:6: Warning: A newer version of com.bugsnag:bugsnag-android-gradle-plugin than 2.2 is available: 2.4.1 [GradleDependency]\n"
                + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n";

        lint().files(
                gradle(""
                        + "buildscript {\n"
                        + "  dependencies {\n"
                        + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.0'\n" // Bad
                        + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.1'\n" // Bad
                        + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.2'\n" // Old
                        + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.2'\n" // Old
                        + "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.5'\n" // OK
                        + "  }\n"
                        + "}\n"))
                .issues(DEPENDENCY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);

    }

    public void testDeprecatedAppIndexingDependency() throws Exception {
        String expected = ""
                + "build.gradle:9: Warning: Deprecated: Replace 'com.google.android.gms:play-services-appindexing:9.8.0' with 'com.google.firebase:firebase-appindexing:10.0.0' or above. More info: http://firebase.google.com/docs/app-indexing/android/migrate [GradleDeprecated]\n"
                + "compile 'com.google.android.gms:play-services-appindexing:9.8.0'\n"
                + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";

        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 25\n"
                        + "    buildToolsVersion \"25.0.2\"\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "compile 'com.google.android.gms:play-services-appindexing:9.8.0'\n"
                        + "}\n"))
                .issues(DEPRECATED)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    public void testBadBuildTools() throws Exception {
        // Warn about build tools 23.0.0 which is known to be a bad version
        String expected = ""
                + "build.gradle:7: Error: Build Tools 23.0.0 should not be used; it has some known serious bugs. Use version 23.0.3 instead. [GradleCompatible]\n"
                + "    buildToolsVersion \"23.0.0\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 18\n"
                        + "    buildToolsVersion \"19.0.0\"\n" // OK
                        + "    buildToolsVersion \"22.1.0\"\n" // OK
                        + "    buildToolsVersion \"23.0.0\"\n" // ERROR
                        + "    buildToolsVersion \"23.0.1\"\n" // OK
                        + "    buildToolsVersion \"23.1.0\"\n" // OK
                        + "    buildToolsVersion \"24.0.0\"\n" // OK
                        + "    buildToolsVersion \"23.0.+\"\n" // OK
                        + "}"))
                .issues(COMPATIBILITY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @NonNull Location location, @NonNull String message) {
        if (issue == DEPENDENCY && message.startsWith("Using the appcompat library when ")) {
            // No data embedded in this specific message
            return;
        }

        // Issues we're supporting getOldFrom
        if (issue == DEPENDENCY
                || issue == STRING_INTEGER
                || issue == DEPRECATED
                || issue == PLUS) {
            assertNotNull("Could not extract message tokens from " + message,
                    GradleDetector.getOldValue(issue, message, TEXT));
        }

        if (issue == DEPENDENCY
                || issue == STRING_INTEGER
                || issue == DEPRECATED) {
            assertNotNull("Could not extract message tokens from " + message,
                    GradleDetector.getNewValue(issue, message, TEXT));
        }

        if (issue == COMPATIBILITY) {
            if (message.startsWith("Version ")) {
                assertNotNull("Could not extract message tokens from " + message,
                        GradleDetector.getNewValue(issue, message, TEXT));
            }
        }
    }

    public void testGetNamedDependency() {
        assertEquals("com.android.support:support-v4:21.0.+", getNamedDependency(
                "group: 'com.android.support', name: 'support-v4', version: '21.0.+'"
        ));
        assertEquals("com.android.support:support-v4:21.0.+", getNamedDependency(
                "name:'support-v4', group: \"com.android.support\", version: '21.0.+'"
        ));
        assertEquals("junit:junit:4.+", getNamedDependency(
                "group: 'junit', name: 'junit', version: '4.+'"
        ));
        assertEquals("com.android.support:support-v4:19.0.+", getNamedDependency(
                "group: 'com.android.support', name: 'support-v4', version: '19.0.+'"
        ));
        assertEquals("com.google.guava:guava:11.0.1", getNamedDependency(
                "group: 'com.google.guava', name: 'guava', version: '11.0.1', transitive: false"
        ));
        assertEquals("com.google.api-client:google-api-client:1.6.0-beta", getNamedDependency(
                "group: 'com.google.api-client', name: 'google-api-client', version: '1.6.0-beta', transitive: false"
        ));
        assertEquals("org.robolectric:robolectric:2.3-SNAPSHOT", getNamedDependency(
                "group: 'org.robolectric', name: 'robolectric', version: '2.3-SNAPSHOT'"
        ));
    }

    public void testSupportAnnotations() throws Exception {
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion 19\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    testCompile 'com.android.support:support-annotations:24.0.0'\n"
                        + "    compile 'com.android.support:appcompat-v7:+'\n"
                        + "}\n"))
                .issues(COMPATIBILITY)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expectClean();
    }

    public void testBundledGmsDependency() throws Exception {
        lint().files(
                gradle(""
                        + "dependencies {\n"
                        + "    compile 'com.google.android.gms:play-services:8.5.6'\n"
                        + "}\n"))
                .issues(BUNDLED_GMS)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(""
                        + "build.gradle:2: Warning: Avoid using bundled version of Google Play services SDK. [UseOfBundledGooglePlayServices]\n"
                        + "    compile 'com.google.android.gms:play-services:8.5.6'\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n");
    }

    public void testUnbundledGmsDependency() throws Exception {
        lint().files(
                gradle(""
                        + "dependencies {\n"
                        + "    compile 'com.google.android.gms:play-services-auth:9.2.1'\n"
                        + "}\n"))
                .issues(BUNDLED_GMS)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expectClean();
    }

    public void testHighAppVersionCode() throws Exception {
        String expected = ""
                + "build.gradle:5: Error: The 'versionCode' is very high and close to the max allowed value [HighAppVersionCode]\n"
                + "        versionCode 2146435071\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                gradle(""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 2146435071\n"
                        + "    }\n"
                        + "}"))
                .issues(HIGH_APP_VERSION_CODE)
                .sdkHome(getMockSupportLibraryInstallation())
                .run()
                .expect(expected);
    }

    // -------------------------------------------------------------------------------------------
    // Test infrastructure below here
    // -------------------------------------------------------------------------------------------

    static final Implementation IMPLEMENTATION = new Implementation(
            GroovyGradleDetector.class,
            Scope.GRADLE_SCOPE);
    static {
        for (Issue issue : new TestIssueRegistry().getIssues()) {
            if (issue.getImplementation().getDetectorClass() == GradleDetector.class) {
                issue.setImplementation(IMPLEMENTATION);
            }
        }
    }

    @Override
    protected Detector getDetector() {
        return new GroovyGradleDetector();
    }

    private Set<Issue> mEnabled;

    @Override
    protected boolean isEnabled(Issue issue) {
        return super.isEnabled(issue) && (mEnabled == null || mEnabled.contains(issue));
    }

    // Copy of com.android.build.gradle.tasks.GroovyGradleDetector (with "static" added as
    // a modifier, and the unused field IMPLEMENTATION removed, and with fail(t.toString())
    // inserted into visitBuildScript's catch handler.
    //
    // THIS CODE DUPLICATION IS NOT AN IDEAL SITUATION! But, it's preferable to a lack of
    // tests.
    //
    // A more proper fix would be to extract the groovy detector into a library shared by
    // the testing framework and the gradle plugin.

    public static class GroovyGradleDetector extends GradleDetector {
        @Override
        public void visitBuildScript(@NonNull final Context context, Map<String, Object> sharedData) {
            try {
                visitQuietly(context, sharedData);
            } catch (Throwable t) {
                // ignore
                // Parsing the build script can involve class loading that we sometimes can't
                // handle. This happens for example when running lint in build-system/tests/api/.
                // This is a lint limitation rather than a user error, so don't complain
                // about these. Consider reporting a Issue#LINT_ERROR.
                StringWriter writer = new StringWriter();
                t.printStackTrace(new PrintWriter(writer));
                fail(writer.toString());
            }
        }

        private void visitQuietly(@NonNull final Context context,
                @SuppressWarnings("UnusedParameters") Map<String, Object> sharedData) {
            final CharSequence contents = context.getContents();
            if (contents == null) {
                return;
            }

            String source = contents.toString();

            List<ASTNode> astNodes = new AstBuilder().buildFromString(source);
            GroovyCodeVisitor visitor = new CodeVisitorSupport() {
                private final List<MethodCallExpression> mMethodCallStack = Lists.newArrayList();
                @Override
                public void visitMethodCallExpression(MethodCallExpression expression) {
                    mMethodCallStack.add(expression);
                    super.visitMethodCallExpression(expression);
                    Expression arguments = expression.getArguments();
                    String parent = expression.getMethodAsString();
                    String parentParent = getParentParent();
                    if (arguments instanceof ArgumentListExpression) {
                        ArgumentListExpression ale = (ArgumentListExpression)arguments;
                        List<Expression> expressions = ale.getExpressions();
                        if (expressions.size() == 1 &&
                                expressions.get(0) instanceof ClosureExpression) {
                            if (isInterestingBlock(parent, parentParent)) {
                                ClosureExpression closureExpression =
                                        (ClosureExpression)expressions.get(0);
                                Statement block = closureExpression.getCode();
                                if (block instanceof BlockStatement) {
                                    BlockStatement bs = (BlockStatement)block;
                                    for (Statement statement : bs.getStatements()) {
                                        if (statement instanceof ExpressionStatement) {
                                            ExpressionStatement e = (ExpressionStatement)statement;
                                            if (e.getExpression() instanceof MethodCallExpression) {
                                                checkDslProperty(parent,
                                                        (MethodCallExpression)e.getExpression(),
                                                        parentParent);
                                            }
                                        } else if (statement instanceof ReturnStatement) {
                                            // Single item in block
                                            ReturnStatement e = (ReturnStatement)statement;
                                            if (e.getExpression() instanceof MethodCallExpression) {
                                                checkDslProperty(parent,
                                                        (MethodCallExpression)e.getExpression(),
                                                        parentParent);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (arguments instanceof TupleExpression) {
                        if (isInterestingStatement(parent, parentParent)) {
                            TupleExpression te = (TupleExpression) arguments;
                            Map<String, String> namedArguments = Maps.newHashMap();
                            List<String> unnamedArguments = Lists.newArrayList();
                            for (Expression subExpr : te.getExpressions()) {
                                if (subExpr instanceof NamedArgumentListExpression) {
                                    NamedArgumentListExpression nale = (NamedArgumentListExpression) subExpr;
                                    for (MapEntryExpression mae : nale.getMapEntryExpressions()) {
                                        namedArguments.put(mae.getKeyExpression().getText(),
                                                mae.getValueExpression().getText());
                                    }
                                }
                            }
                            checkMethodCall(context, parent, parentParent, namedArguments, unnamedArguments, expression);
                        }
                    }
                    assert !mMethodCallStack.isEmpty();
                    assert mMethodCallStack.get(mMethodCallStack.size() - 1) == expression;
                    mMethodCallStack.remove(mMethodCallStack.size() - 1);
                }

                private String getParentParent() {
                    for (int i = mMethodCallStack.size() - 2; i >= 0; i--) {
                        MethodCallExpression expression = mMethodCallStack.get(i);
                        Expression arguments = expression.getArguments();
                        if (arguments instanceof ArgumentListExpression) {
                            ArgumentListExpression ale = (ArgumentListExpression)arguments;
                            List<Expression> expressions = ale.getExpressions();
                            if (expressions.size() == 1 &&
                                    expressions.get(0) instanceof ClosureExpression) {
                                return expression.getMethodAsString();
                            }
                        }
                    }

                    return null;
                }

                private void checkDslProperty(String parent, MethodCallExpression c,
                        String parentParent) {
                    String property = c.getMethodAsString();
                    if (isInterestingProperty(property, parent, getParentParent())) {
                        String value = getText(c.getArguments());
                        checkDslPropertyAssignment(context, property, value, parent, parentParent, c, c);
                    }
                }

                private String getText(ASTNode node) {
                    Pair<Integer, Integer> offsets = getOffsets(node, context);
                    return source.substring(offsets.getFirst(), offsets.getSecond());
                }
            };

            for (ASTNode node : astNodes) {
                node.visit(visitor);
            }
        }

        @NonNull
        private static Pair<Integer, Integer> getOffsets(ASTNode node, Context context) {
            if (node.getLastLineNumber() == -1 && node instanceof TupleExpression) {
                // Workaround: TupleExpressions yield bogus offsets, so use its
                // children instead
                TupleExpression exp = (TupleExpression) node;
                List<Expression> expressions = exp.getExpressions();
                if (!expressions.isEmpty()) {
                    return Pair.of(
                        getOffsets(expressions.get(0), context).getFirst(),
                        getOffsets(expressions.get(expressions.size() - 1), context).getSecond());
                }
            }

            if (node instanceof ArgumentListExpression) {
                List<Expression> expressions = ((ArgumentListExpression) node).getExpressions();
                if (expressions.size() == 1) {
                    return getOffsets(expressions.get(0), context);
                }
            }

            CharSequence source = context.getContents();
            assert source != null; // because we successfully parsed
            int start = 0;
            int end = source.length();
            int line = 1;
            int startLine = node.getLineNumber();
            int startColumn = node.getColumnNumber();
            int endLine = node.getLastLineNumber();
            int endColumn = node.getLastColumnNumber();
            int column = 1;
            for (int index = 0, len = end; index < len; index++) {
                if (line == startLine && column == startColumn) {
                    start = index;
                }
                if (line == endLine && column == endColumn) {
                    end = index;
                    break;
                }

                char c = source.charAt(index);
                if (c == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }

            return Pair.of(start, end);
        }

        @Override
        protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
            ASTNode node = (ASTNode) cookie;
            Pair<Integer, Integer> offsets = getOffsets(node, context);
            return offsets.getFirst();
        }

        @Override
        protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
            ASTNode node = (ASTNode) cookie;
            Pair<Integer, Integer> offsets = getOffsets(node, context);
            int fromLine = node.getLineNumber() - 1;
            int fromColumn = node.getColumnNumber() - 1;
            int toLine = node.getLastLineNumber() - 1;
            int toColumn = node.getLastColumnNumber() - 1;
            return Location.create(context.file,
                    new DefaultPosition(fromLine, fromColumn, offsets.getFirst()),
                    new DefaultPosition(toLine, toColumn, offsets.getSecond()));
        }
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mDependencies = source("build.gradle", ""
            + "apply plugin: 'android'\n"
            + "\n"
            + "android {\n"
            + "    compileSdkVersion 19\n"
            + "    buildToolsVersion \"19.0.0\"\n"
            + "\n"
            + "    defaultConfig {\n"
            + "        minSdkVersion 7\n"
            + "        targetSdkVersion 17\n"
            + "        versionCode 1\n"
            + "        versionName \"1.0\"\n"
            + "    }\n"
            + "\n"
            + "    productFlavors {\n"
            + "        free {\n"
            + "        }\n"
            + "        pro {\n"
            + "        }\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "dependencies {\n"
            + "    compile 'com.android.support:appcompat-v7:+'\n"
            + "    freeCompile 'com.google.guava:guava:11.0.2'\n"
            + "    compile 'com.android.support:appcompat-v7:13.0.0'\n"
            + "    compile 'com.google.android.support:wearable:1.2.0'\n"
            + "    compile 'com.android.support:multidex:1.0.0'\n"
            + "\n"
            + "    androidTestCompile 'com.android.support.test:runner:0.3'\n"
            + "}\n");

}
