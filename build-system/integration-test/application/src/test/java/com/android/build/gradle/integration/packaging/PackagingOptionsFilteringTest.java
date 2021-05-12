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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.ANDROIDTEST_DEBUG;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.RELEASE;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks that the packaging options filtering are honored.
 */
public class PackagingOptionsFilteringTest {

    private GradleTestProject app;
    private GradleTestProject lib;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("kotlinApp")
                    .create();

    @Before
    public void setup() {
        app = project.getSubproject(":app");
        lib = project.getSubproject(":library");
    }

    /**
     * Creates a java resource in the project's src/<srcDir>/resources/ folder
     *
     * @param project the project to add the java resource to
     * @param srcDir the name of the parent of the resources folder
     * @param contents the file's contents
     * @param paths the path to the file starting from the appropriate resources folder
     * @throws IOException I/O failed
     */
    private void addJavaRes(
            @NonNull GradleTestProject project,
            @NonNull String srcDir,
            @NonNull byte[] contents,
            @NonNull String... paths) throws Exception {
        File file =
                FileUtils.join(
                        FileUtils.join(project.getProjectDir(), "src", srcDir, "resources"), paths);
        FileUtils.mkdirs(file.getParentFile());
        Files.write(file.toPath(), contents);
    }

    /**
     * Creates a java resource in the project's src/<srcDir>/resources/ folder
     *
     * @param project the project to add the java resource to
     * @param srcDir the name of the parent of the resources folder
     * @param text the file's contents
     * @param paths the path to the file starting from the appropriate resources folder
     * @throws IOException I/O failed
     */
    private void addJavaRes(
            @NonNull GradleTestProject project,
            @NonNull String srcDir,
            @NonNull String text,
            @NonNull String... paths) throws Exception {
        File file =
                FileUtils.join(
                        FileUtils.join(project.getProjectDir(), "src", srcDir, "resources"), paths);
        FileUtils.mkdirs(file.getParentFile());
        Files.write(file.toPath(), Collections.singletonList(text));
    }

    /**
     * Appends text to the build file.
     *
     * @param project the project whose build file gets the text appended
     * @param text text to append
     * @throws IOException I/O failed
     */
    private void appendBuild(GradleTestProject project, @NonNull String text) throws Exception {
        File buildFile = project.getBuildFile();
        String contents = com.google.common.io.Files.toString(buildFile, Charsets.US_ASCII);
        contents += System.lineSeparator() + text;
        com.google.common.io.Files.asCharSink(buildFile, Charsets.US_ASCII).write(contents);
    }

    /**
     * Folders that are named {@code .svn} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultSvnFolderInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        addJavaRes(app, "main", c0, ".svn", "ignored-1");
        addJavaRes(app, "main", c0, "not-ignored-1");
        addJavaRes(app, "main", c1, "foo", ".svn", "ignored-2");
        addJavaRes(app, "main", c1, "foo", "not-ignored-2");
        addJavaRes(app, "main", c2, "foo", "bar", ".svn", "ignored-3");
        addJavaRes(app, "main", c2, "foo", "bar", "not-ignored-3");
        addJavaRes(app, "main", c3, "foo", "svn", "not-ignored-4");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContainJavaResource(".svn/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/.svn/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/.svn/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/svn/not-ignored-4", c3);
    }

    /**
     * Folders that are named {@code CVS} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultCvsFolderInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        addJavaRes(app, "main", c0, "CVS", "ignored-1");
        addJavaRes(app, "main", c0, "not-ignored-1");
        addJavaRes(app, "main", c1, "foo", "CVS", "ignored-2");
        addJavaRes(app, "main", c1, "foo", "not-ignored-2");
        addJavaRes(app, "main", c2, "foo", "bar", "CVS", "ignored-3");
        addJavaRes(app, "main", c2, "foo", "bar", "not-ignored-3");
        addJavaRes(app, "main", c3, "foo", "cvs.cvs", "not-ignored-4");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContainJavaResource("CVS/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/cvs/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/Cvs/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/cvs.cvs/not-ignored-4", c3);
    }

    /**
     * Folders that are named {@code SCCS} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultSccsFolderInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        addJavaRes(app, "main", c0, "SCCS", "ignored-1");
        addJavaRes(app, "main", c0, "not-ignored-1");
        addJavaRes(app, "main", c1, "foo", "sccs", "ignored-2");
        addJavaRes(app, "main", c1, "foo", "not-ignored-2");
        addJavaRes(app, "main", c2, "foo", "bar", "SccS", "ignored-3");
        addJavaRes(app, "main", c2, "foo", "bar", "not-ignored-3");
        addJavaRes(app, "main", c3, "foo", "SCCS.1", "not-ignored-4");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContainJavaResource("SCCS/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/SCCS/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/SCCS/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/SCCS.1/not-ignored-4", c3);
    }

    /**
     * Folders that are named {@code SCCS} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultUnderscoreFoldersInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        addJavaRes(app, "main", c0, "_", "ignored-1");
        addJavaRes(app, "main", c0, "not-ignored-1");
        addJavaRes(app, "main", c1, "foo", "__", "ignored-2");
        addJavaRes(app, "main", c1, "foo", "not-ignored-2");
        addJavaRes(app, "main", c2, "foo", "bar", "_blah", "ignored-3");
        addJavaRes(app, "main", c2, "foo", "bar", "not-ignored-3");
        addJavaRes(app, "main", c3, "foo", "x_", "not-ignored-4");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContainJavaResource("_/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/__/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/_blah/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/x_/not-ignored-4", c3);
    }

    @Test
    public void byDefaultKotlinMetaDataAreIgnored() throws Exception {
        app.execute("assembleDebug");
        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContain("/kotlin/text/UStringsKt.kotlin_metadata");
    }

    /**
     * Exclude patterns can be redefined.
     *
     * @throws Exception test failed
     */
    @Test
    public void redefineExcludePatterns() throws Exception {
        appendBuild(app, "android {");
        appendBuild(app, "    packagingOptions {");
        appendBuild(app, "        exclude '**/*ign'");
        appendBuild(app, "        exclude '**/sensitive/**'");
        appendBuild(app, "    }");
        appendBuild(app, "}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };

        // FIXME figure out what to do with the test using folders now excluded by Gradle's Sync task or that windows handles differently.
        addJavaRes(app, "main", c0, "I_am_ign");
        addJavaRes(app, "main", c0, "ssccs", "I stay");
        addJavaRes(app, "main", c1, "Ignoring", "this", "fileign");
        addJavaRes(app, "main", c1, "SSensitive", "files", "may", "leak");
        addJavaRes(app, "main", c2, "some", "sensitive", "files", "dont");
        addJavaRes(app, "main", c2, "pkg", "ccvs", "very-sensitive-info");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContainJavaResource("I_am_ign");
        apk.containsJavaResourceWithContent("ssccs/I stay", c0);
        apk.doesNotContainJavaResource("Ignoring/this/fileign");
        apk.containsJavaResourceWithContent("SSensitive/files/may/leak", c1);
        apk.doesNotContainJavaResource("some/sensitive/files/dont");
        apk.containsJavaResourceWithContent("pkg/ccvs/very-sensitive-info", c2);
    }

    /**
     * Exclude patterns can be redefined (same as {@link #redefineExcludePatterns()}, but using
     * a different syntax).
     *
     * @throws Exception test failed
     */
    @Test
    public void redefineExcludePatterns2() throws Exception {
        appendBuild(app, "android {");
        appendBuild(app, "    packagingOptions {");
        appendBuild(app, "        excludes = [");
        appendBuild(app, "            '**/*ign',");
        appendBuild(app, "            '**/sensitive/**',");
        appendBuild(app, "            '/META-INF/MANIFEST.MF',");
        appendBuild(app, "        ]");
        appendBuild(app, "    }");
        appendBuild(app, "}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };

        // FIXME figure out what to do with the test using folders now excluded by Gradle's Sync task
        addJavaRes(app, "main", c0, "I_am_ign");
        addJavaRes(app, "main", c0, "sccs2", "I stay");
        addJavaRes(app, "main", c1, "Ignoring", "this", "fileign");
        addJavaRes(app, "main", c1, "SSensitive", "files", "may", "leak");
        addJavaRes(app, "main", c2, "some", "sensitive", "files", "dont");
        addJavaRes(app, "main", c2, "pkg", "cvs2", "very-sensitive-info");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContainJavaResource("I_am_ign");
        apk.containsJavaResourceWithContent("sccs2/I stay", c0);
        apk.doesNotContainJavaResource("Ignoring/this/fileign");
        apk.containsJavaResourceWithContent("SSensitive/files/may/leak", c1);
        apk.doesNotContainJavaResource("some/sensitive/files/dont");
        apk.containsJavaResourceWithContent("pkg/cvs2/very-sensitive-info", c2);
    }

    /**
     * Exclude patterns can be changed with new DSL.
     */
    @Test
    public void addExcludePatternsViaNewDsl() throws Exception {
        // modify app's build file
        appendBuild(app, "android {");
        appendBuild(app, "    packagingOptions {");
        appendBuild(app, "        resources {");
        appendBuild(app, "            excludes += '**/*.exclude'");
        appendBuild(app, "        }");
        appendBuild(app, "    }");
        appendBuild(app, "}");

        // modify lib's build file
        appendBuild(lib, "android {");
        appendBuild(lib, "    packagingOptions {");
        appendBuild(lib, "        resources {");
        appendBuild(lib, "            excludes += '**/*.exclude'");
        appendBuild(lib, "        }");
        appendBuild(lib, "    }");
        appendBuild(lib, "}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };

        // add java resources to app and lib
        addJavaRes(app, "main", c0, "foo.exclude");
        addJavaRes(app, "main", c0, "foo.keep");
        addJavaRes(lib, "main", c0, "foo.exclude");
        addJavaRes(lib, "main", c0, "foo.keep");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.doesNotContainJavaResource("foo.exclude");
        apk.containsJavaResourceWithContent("foo.keep", c0);

        lib.execute("assembleDebug");

        lib.assertThatAar(
                "debug",
                aar -> {
                    aar.doesNotContainJavaResource("foo.exclude");
                    aar.containsJavaResourceWithContent("foo.keep", c0);
                    return null;
                }
        );
    }

    /**
     * PickFirst patterns can be changed with new DSL.
     */
    @Test
    public void addPickFirstPatternsViaNewDsl() throws Exception {
        appendBuild(app, "android {");
        appendBuild(app, "    packagingOptions {");
        appendBuild(app, "        resources {");
        appendBuild(app, "            pickFirsts += '**/*.pickFirst'");
        appendBuild(app, "        }");
        appendBuild(app, "    }");
        appendBuild(app, "}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };

        // add java resources to app and lib
        addJavaRes(app, "main", c0, "foo.pickFirst");
        addJavaRes(lib, "main", c1, "foo.pickFirst");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.containsJavaResourceWithContent("foo.pickFirst", c0);
    }

    /**
     * Merge patterns can be changed with new DSL.
     */
    @Test
    public void addMergePatternsViaNewDsl() throws Exception {
        appendBuild(app, "android {");
        appendBuild(app, "    packagingOptions {");
        appendBuild(app, "        resources {");
        appendBuild(app, "            merges += '**/*.merge'");
        appendBuild(app, "        }");
        appendBuild(app, "    }");
        appendBuild(app, "}");

        // add java resources to app and lib
        addJavaRes(app, "main", "fromApp", "foo.merge");
        addJavaRes(lib, "main", "fromLib", "foo.merge");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.containsJavaResourceWithContent("foo.merge", "fromApp\nfromLib");
    }

    /**
     * Exclude patterns can be changed via variant properties API
     */
    @Test
    public void addExcludePatternsViaVariantPropertiesApi() throws Exception {
        // modify app's build file
        appendBuild(app, "androidComponents {");
        appendBuild(app, "    onVariants(selector().withName('debug'), {");
        appendBuild(app, "        packaging.resources.excludes.add('**/*.debugExclude')");
        appendBuild(app, "    })");
        appendBuild(app, "    onVariants(selector().withName('release'), {");
        appendBuild(app, "        packaging.resources.excludes.add('**/*.releaseExclude')");
        appendBuild(app, "    })");
        appendBuild(app, "    onVariants(selector().all(), {");
        appendBuild(
                app,
                "        androidTest?.packaging?.resources?.excludes?.add('**/*.testExclude')");
        appendBuild(app, "    })");
        appendBuild(app, "}");

        // modify lib's build file
        appendBuild(lib, "androidComponents {");
        appendBuild(lib, "    onVariants(selector().all(), {");
        appendBuild(lib, "        packaging.resources.excludes.add('**/*.libExclude')");
        appendBuild(
                lib,
                "        androidTest?.packaging?.resources?.excludes?.add('**/*.testExclude')");
        appendBuild(lib, "    })");
        appendBuild(lib, "}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };

        // add java resources to app
        addJavaRes(app, "main", c0, "foo.debugExclude");
        addJavaRes(app, "main", c0, "foo.debugKeep");
        addJavaRes(app, "main", c0, "foo.releaseExclude");
        addJavaRes(app, "main", c0, "foo.releaseKeep");
        addJavaRes(app, "androidTest", c0, "foo.testExclude");
        addJavaRes(app, "androidTest", c0, "foo.testKeep");

        // add java resources to lib
        addJavaRes(lib, "main", c0, "foo.libExclude");
        addJavaRes(lib, "main", c0, "foo.libKeep");
        addJavaRes(lib, "androidTest", c0, "foo.testExclude");
        addJavaRes(lib, "androidTest", c0, "foo.testKeep");

        // http://b/149978740 - disable dependency info in order to run with configuration caching
        app.executor()
                .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                .run("assemble", "assembleDebugAndroidTest");

        assertThat(app.getApk(DEBUG).getFile()).exists();
        ApkSubject debugApk = TruthHelper.assertThat(app.getApk(DEBUG));
        debugApk.doesNotContainJavaResource("foo.debugExclude");
        debugApk.containsJavaResourceWithContent("foo.debugKeep", c0);
        debugApk.containsJavaResourceWithContent("foo.releaseExclude", c0);
        debugApk.containsJavaResourceWithContent("foo.releaseKeep", c0);

        assertThat(app.getApk(RELEASE).getFile()).exists();
        ApkSubject releaseApk = TruthHelper.assertThat(app.getApk(RELEASE));
        releaseApk.doesNotContainJavaResource("foo.releaseExclude");
        releaseApk.containsJavaResourceWithContent("foo.debugExclude", c0);
        releaseApk.containsJavaResourceWithContent("foo.debugKeep", c0);
        releaseApk.containsJavaResourceWithContent("foo.releaseKeep", c0);

        assertThat(app.getApk(ANDROIDTEST_DEBUG).getFile()).exists();
        ApkSubject androidTestApk = TruthHelper.assertThat(app.getApk(ANDROIDTEST_DEBUG));
        androidTestApk.doesNotContainJavaResource("foo.testExclude");
        androidTestApk.containsJavaResourceWithContent("foo.testKeep", c0);

        lib.execute("assembleDebug", "assembleDebugAndroidTest");

        lib.assertThatAar(
                "debug",
                aar -> {
                    aar.doesNotContainJavaResource("foo.libExclude");
                    aar.containsJavaResourceWithContent("foo.libKeep", c0);
                    return null;
                }
        );

        assertThat(lib.getApk(ANDROIDTEST_DEBUG).getFile()).exists();
        ApkSubject libAndroidTestApk = TruthHelper.assertThat(lib.getApk(ANDROIDTEST_DEBUG));
        libAndroidTestApk.doesNotContainJavaResource("foo.testExclude");
        libAndroidTestApk.containsJavaResourceWithContent("foo.testKeep", c0);
    }


    /**
     * PickFirst patterns can be changed via variant properties API
     */
    @Test
    public void addPickFirstPatternsViaVariantPropertiesApi() throws Exception {
        appendBuild(app, "androidComponents {");
        appendBuild(app, "    onVariants(selector().all(), {");
        appendBuild(app, "        packaging.resources.pickFirsts.add('**/*.pickFirst')");
        appendBuild(app, "    })");
        appendBuild(app, "}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };

        // add java resources to app and lib
        addJavaRes(app, "main", c0, "foo.pickFirst");
        addJavaRes(lib, "main", c1, "foo.pickFirst");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.containsJavaResourceWithContent("foo.pickFirst", c0);
    }

    /**
     * Merge patterns can be changed via variant properties API
     */
    @Test
    public void addMergePatternsViaVariantPropertiesApi() throws Exception {
        appendBuild(app, "androidComponents {");
        appendBuild(app, "    onVariants(selector().all(), {");
        appendBuild(app, "        packaging.resources.merges.add('**/*.merge')");
        appendBuild(app, "    })");
        appendBuild(app, "}");

        // add java resources to app and lib
        addJavaRes(app, "main", "fromApp", "foo.merge");
        addJavaRes(lib, "main", "fromLib", "foo.merge");

        app.execute("assembleDebug");

        ApkSubject apk = TruthHelper.assertThat(app.getApk(DEBUG));
        apk.containsJavaResourceWithContent("foo.merge", "fromApp\nfromLib");
    }
}
